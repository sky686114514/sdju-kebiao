# 课刻 · 本机构建环境说明（BUILD-ENV）

> 建立日期：2026-09-20
> 状态：**已实测可用**（`./gradlew --version` 通过；依赖解析已落盘 186MB 实证）
> 关联：`Spec.md` §4.4（构建环境与代差风险）、§12.2（端到端验证步骤）
> 目的：把所有环境问题与踩坑记录固化成可复现步骤，避免下次重新摸索。**环境问题与代码问题必须分开排查。**

---

## 1. 落位总表（全部在 F 盘，零占 C 盘）

| 组件 | 版本 | 路径 |
|------|------|------|
| Temurin JDK | **17.0.20.1** | `F:\AndroidDev\jdk-17` |
| Gradle | **9.5.0** | `F:\AndroidDev\gradle-9.5.0` |
| Android SDK · platform-tools | 37.0.1 | `F:\AndroidDev\Sdk\platform-tools` |
| Android SDK · platform | **android-37.0** | `F:\AndroidDev\Sdk\platforms\android-37.0` |
| Android SDK · build-tools | 36.0.0 | `F:\AndroidDev\Sdk\build-tools\36.0.0` |
| Gradle 用户目录 | — | `F:\AndroidDev\gradle-home` |
| 安装包留存 | — | `F:\AndroidDev\installers\`、`F:\下载\` |
| Android Studio（**仅作可选 IDE**） | 2025.3.1.5 | `F:\下载\android-studio-2025.3.1.5-windows.exe` |

---

## 2. 每次构建会话必须先导出的环境变量

```bash
export PATH="/usr/bin:/bin:/c/Windows/System32:$PATH"   # 本机 Bash 的 PATH 被污染，dirname/head 缺失
export JAVA_HOME="F:/AndroidDev/jdk-17"
export ANDROID_HOME="F:/AndroidDev/Sdk"
export ANDROID_SDK_ROOT="F:/AndroidDev/Sdk"
export GRADLE_USER_HOME="F:/AndroidDev/gradle-home"
```

**`GRADLE_USER_HOME` 不是可选项。** 理由见第 3 节——代理配置写在它下面，不设就必然连接超时。

---

## 3. 网络：JVM 出网必须走代理（本项目最隐蔽的坑）

本机存在**会话级 HTTP 代理**：`http_proxy` / `https_proxy` = `http://127.0.0.1:58233`。

实测结论：

| 客户端 | 是否自动使用代理 | 证据 |
|--------|------------------|------|
| Node `fetch` | 是 | `services.gradle.org` → 200 |
| Java `HttpURLConnection` + 默认 `ProxySelector` | 是 | `services.gradle.org` → 200（1071ms） |
| **Gradle wrapper 的下载器** | **否** | `SocketTimeoutException: Connect timed out` |

因此 `distributionUrl` 指向网络地址时，`gradlew` 必然失败。**解法是让 wrapper 完全不需要联网**：

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=file:///F:/AndroidDev/installers/gradle-9.5.0-bin.zip
networkTimeout=60000
validateDistributionUrl=false
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

代理配置写在 **GRADLE_USER_HOME 级别**（`F:\AndroidDev\gradle-home\gradle.properties`），不污染项目文件：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=58233
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=58233
systemProp.http.nonProxyHosts=localhost|127.0.0.1
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
org.gradle.daemon=false
```

> **代理端口会变。** 构建报连接超时时，先 `env | grep -i proxy` 取新端口，替换上面两处 `58233`。

**代理可用性实证**：隔离探针项目解析 `AGP 9.3.3` / `kotlin-gradle-plugin 2.4.20` / `room-runtime 2.8.5` 后，`F:\AndroidDev\gradle-home\caches` 落盘 **186MB**，含 `com.android.tools.build`、`androidx.compose`、`androidx.room`、`androidx.sqlite`。
**结论：经代理访问 Google Maven 与 Maven Central 可用。**

---

## 4. 踩坑记录（按遇到顺序）

### 4.1 SDK 命令行工具
- `sdkmanager` **已被官方弃用**，会转发到新的 `android` CLI（替代品是 `android.exe sdk ...`）。
- 新 CLI 用**斜杠记法，不是分号**：写 `platforms/android-37.0`、`build-tools/36.0.0`。
  传 `"platforms;android-37"` 会被拆成独立参数并报 `Package platforms not found`。
- **API 37 已改为次要版本发布**：包 ID 是 `platforms/android-37.0` / `37.1` / `37.2`，**不存在 `platforms/android-37`**。
  注意 `grep -oE "platforms/android-3[0-9]"` 会匹配到 `android-37.0` 的前缀，**造成"包存在但装不上"的假象**——必须用能匹配到行尾的精确正则。
- **cmdline-tools 的 build 号不要猜**，从 `https://dl.google.com/android/repository/repository2-3.xml` 解析真实包名。

### 4.2 Gradle wrapper 生成
- `gradle wrapper` 任务**默认会联网校验 distributionUrl**；JVM 走不通直连 → 失败。**加 `--offline` 跳过校验**即可成功。
- 生成 wrapper 的目录**必须含 settings 文件**（`settings.gradle.kts`），否则报 `does not contain a Gradle build`。
- 项目最初缺 `gradlew` / `gradlew.bat` / `gradle-wrapper.jar`（只有 `gradle-wrapper.properties`）。已在 `F:\AndroidDev\_wrappergen` 生成后拷入。

### 4.3 平台目录名与 compileSdk
`platforms/android-37.0`（而非 `android-37`）能否被 `compileSdk = 37` 正确解析，**是首次构建必须验证的未知项**。

### 4.4 AGP 9 的破坏性变更（第一次真实构建即撞上）
```
An exception occurred applying plugin request [id: 'org.jetbrains.kotlin.android', version: '2.4.20']
> The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
  Solution: Remove the 'org.jetbrains.kotlin.android' plugin from this project's build file.
  See https://kotl.in/gradle/agp-built-in-kotlin for more details.
```
**AGP 9.0 起内置 Kotlin 支持，再显式应用 `org.jetbrains.kotlin.android` 会被 AGP 主动拒绝。**

这条**不装工具链永远发现不了**——它不在任何 release notes 摘要里，只在真实构建时暴露。`ARCHITECTURE.md` 与 `Spec.md` 均未覆盖，属调研盲区。

修复时必须读官方原文确认三件事（不要凭印象改）：
1. 移除 Kotlin 插件后，**Compose 编译器插件**（`org.jetbrains.kotlin.plugin.compose`）是否仍需显式应用；
2. **KSP 插件**在 AGP 9 + Kotlin 2.4.20 下的正确写法；
3. Kotlin 版本改由哪里配置。

### 4.5 Gradle Wrapper 自己的下载器吃不到本机代理

**现象**：`distributionUrl` 改为官方 HTTPS 后首次执行 `./gradlew`，报

```
Connection refused: getsockopt
```

堆栈落在 `org.gradle.wrapper.Download.download`。

**原因**：`GRADLE_OPTS` 里传 `-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=<端口>` 对 wrapper 的 JVM **无效**——wrapper 的下载逻辑跑在 `GradleWrapperMain` 里，不走这套。

**解法**：绕过 wrapper 自己的下载器，用 curl 把分发包直接放进它的 hash 缓存目录：

```bash
# <hash> 目录名由 distributionUrl 决定：改 URL 就会换一个
cd "$GRADLE_USER_HOME/wrapper/dists/gradle-9.5.0-bin/<hash>/"
curl -L -o gradle-9.5.0-bin.zip \
  https://services.gradle.org/distributions/gradle-9.5.0-bin.zip
unzip gradle-9.5.0-bin.zip
touch gradle-9.5.0-bin.zip.ok   # 空文件即可，wrapper 用它判定"已解压完成"
```

**附带事实**：官方地址本身没问题。实测 `https://services.gradle.org/distributions/gradle-9.5.0-bin.zip` 返回 **307** 跳到 `release-assets.githubusercontent.com`，跟随重定向可正常下载，**140,319,124 字节**。

> **对第 3 节的修正**：第 3 节"因此 `distributionUrl` 指向网络地址时，`gradlew` 必然失败"这句，是在"本机代理没被 wrapper 吃到"这个前提下得出的结论，**已被本节推翻**。真实结论是：**官方地址可用，只是 wrapper 自己的下载器在本机走不通代理**。第 3 节那段 `distributionUrl=file:///...` 的 properties 是**历史值**，仓库当前已固化为官方 HTTPS 地址。

### 4.6 失败的 wrapper 会留下 0 字节 `.lck`，下次直接"拒绝访问"

**现象**：

```
FileNotFoundException: ...gradle-9.5.0-bin.zip.lck (拒绝访问。)
```

堆栈落在 `org.gradle.wrapper.Install.createDist`。

**原因**：上一次失败残留的 **0 字节**锁文件。

**解法**：把 `.lck`（以及 `.part`）改名挪开再跑，即可通过。

**同类**：这类"拒绝访问"在 F: 盘上是**间歇性**的——包名重命名那次在 `build-cache-1/*.part` 上也撞到过，原样重试一次即成功。**不要**为了绕开它去改代码或改构建配置。

---

## 5. Android Studio 与 AGP 的代差（Spec §4.4 裁决 D-4 的落实）

Google CDN 上可获取的最新 Android Studio 稳定版为 **2025.3.1.5**，而 Spec 锁定 AGP **9.3.3**（2026-07 发布），IDE 与 AGP 存在代差，AS 内可能提示 AGP 版本超出支持范围。

**处置**：**以 Gradle Wrapper 命令行构建为唯一权威路径，Android Studio 仅作可选 IDE。**
AGP 由 Google Maven 解析，**不依赖 IDE**；`./gradlew assembleDebug` 与 AS 版本无关，可彻底规避代差耦合。这已由第 1 节落位表与第 3 节的实证支撑。

---

## 6. 冒烟验证清单（环境问题与依赖问题分开排查）

```bash
# 0) 环境
export JAVA_HOME="F:/AndroidDev/jdk-17"
export ANDROID_HOME="F:/AndroidDev/Sdk"
export ANDROID_SDK_ROOT="F:/AndroidDev/Sdk"
export GRADLE_USER_HOME="F:/AndroidDev/gradle-home"
"F:/AndroidDev/jdk-17/bin/java" -version          # 预期 Temurin 17.0.20.1
"F:/AndroidDev/Sdk/platform-tools/adb.exe" version # 预期 1.0.41 / 37.0.1
"F:/AndroidDev/Sdk/cmdline-tools/latest/bin/sdkmanager.bat" --version

# 1) wrapper（不触及业务代码）
./gradlew --version                                # 预期 Gradle 9.5.0 / JVM 17.0.20.1

# 2) 依赖解析（不触及业务代码）
./gradlew :app:dependencies --configuration debugRuntimeClasspath

# 3) 构建
./gradlew :app:assembleDebug --stacktrace

# 4) 单测
./gradlew :app:testDebugUnitTest

# 5) Lint
./gradlew :app:lintDebug
```

**注意**：`gradle-wrapper.properties` 与 `gradle-home/gradle.properties` 中已关闭 daemon（`org.gradle.daemon=false`），构建会稍慢但更可预测，且不会留下僵尸进程。需要提速时再开启。
