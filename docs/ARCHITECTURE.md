# 电机课表 App 技术架构文档

- 版本：v1.0（Phase 1 产出）
- 首席架构师：高见远（MVP 开发专家团）
- 状态：**待用户在三文档确认环节裁决**
- 上游输入：`docs/PRD.md` v1.0（产品经理 许清楚）
- 项目性质：**个人自用 Android App**（用户本人使用，不上架、无账号体系、无后端服务器、无多人协作）

---

## 0. 知识库引用与合规声明（门禁要求）

### 0.1 本文件实际读取并引用的知识库

基线路径：`C:\Users\28766\.workbuddy\plugins\cache\experts\mvp-dev-expert-team\2.1.1\references\`

| 文件 | 用于本文何处 |
|------|--------------|
| `01-standards/spec-as-contract.md` | 第 4 章版本锁定、第 11 章目录约束、第 16 章端到端验证、第 17 章 out-of-scope |
| `01-standards/context-engineering.md` | 第 6.3 / 6.6 节模块边界与"指针而非全文"的目录组织约定 |
| `01-standards/generated-code-failure-modes.md` | 第 14 章测试策略（沉默逻辑错误定向加固）、第 4 章"依赖存在性核验" |
| `01-standards/open-decisions-register.md` | 第 5 章待定项与 `docs/decisions/OPEN-DECISIONS.md` 的字段规范 |
| `architecture/mvp-stack.md` | 第 3 章分层架构与第 4 章选型基线（嵌入式 SQLite 归属、移动端优先） |
| `cost-models/development-costs.md` | 第 15 章工作量估算与隐性成本 |

未引用：`architecture/ai-agent-patterns.md`、`architecture/rag-knowledge-base.md`（本产品不含 AI 生成与知识库检索能力，见 `docs/PRD.md` 第 7 节 Out-of-Scope）。

补充说明：`mvp-stack.md` 中"AI 能力接入 / 向量数据库"段落与本项目无关，未采纳；其"SQLite = 嵌入式 / 桌面端"归类为本项目本地存储选型的基线依据。

### 0.2 P0 红线合规

1. **全篇无 emoji**：本文所有功能图标以文字描述 + 统一 SVG 图标库命名（见第 10.4 节），无任何 emoji 字符。
2. **无紫→粉渐变**：配色由 Material 3 主题 token 承载，禁用项见第 10.3 节；文中仅出现 `#fff` / `#000` 两个硬编码色值。
3. **无空洞占位**：所有数据模型、示例与验收均引用 PRD 第 4 节的真实课表数据（大学物理 B(1)、自动化专业导论与职业生涯规划、大学物理实验 B(1)、尔雅《航空与航天》、军事技能）。
4. **示例即示例**：本文所有代码片段均标注"以 X 为例（示例，非指定）"。选型结论写在 ADR 与第 4 章锁定表中，代码片段仅用于说明形状。

---

## 1. 文档定位

本文回答四个问题：

1. **本机能不能产出可安装的 APK？**（第 2 章，实机探测结论，**最高优先级**）
2. **用哪一套版本、为什么？**（第 4 章锁定表 + `docs/decisions/ADR-00x`）
3. **"今天该上什么课"这个核心问题在架构上怎么被正确回答？**（第 6 章）
4. **教务系统导入这条最大风险路径怎么落地？**（第 7 章）

本文**不是** PRD 的复述。需求与验收标准以 `docs/PRD.md` 为准；本文只承接其技术约束并给出实现路径。

---

## 2. 可行性验证：本机 Android 构建环境实机探测结论

> 本章为**实测结论**，非推断。探测命令与原始输出见第 2.5 节。

### 2.1 探测结论（逐项）

| 检查项 | 探测方式 | 结果 |
|--------|----------|------|
| JDK / JRE | `where.exe java javac`；`java -version`；枚举 `C:\Program Files\Java`、`Eclipse Adoptium`、`Microsoft\jdk*`、`Android Studio\jbr`、`Zulu`、`Corretto`、`BellSoft`；注册表 `HKLM\SOFTWARE\JavaSoft`、`Eclipse Adoptium`、`Microsoft\JDK`、`Azul Systems\Zulu` | **不存在**。`java`/`javac` 均 `NOT FOUND`；`JAVA_HOME` 为空字符串；上述目录与注册表键全部缺失 |
| Android SDK | 检查 `%LOCALAPPDATA%\Android\Sdk`；环境变量 `ANDROID_HOME` / `ANDROID_SDK_ROOT`；`C:\Android`、`C:\Sdk`、`D:\Android` | **不存在**。目录缺失，两个环境变量均为空 |
| Android Studio | 检查 `C:\Program Files\Android\Android Studio`；全盘 `studio64.exe`（`Program Files` + `Program Files (x86)`，深度 3）；注册表 Uninstall 项匹配 `Android|Studio` | **不存在**。Uninstall 匹配结果仅命中 `Cherry Studio`（无关软件） |
| Gradle | `where.exe gradle`；`%USERPROFILE%\.gradle` | **不存在**。命令缺失，缓存目录不存在 |
| adb / platform-tools | `where.exe adb`；`%LOCALAPPDATA%\Android\platform-tools\adb.exe` | **不存在** |
| sdkmanager / kotlinc | `where.exe sdkmanager kotlinc` | **均不存在** |
| 已连接设备 / 模拟器 | `adb devices` | **无法执行**（adb 不存在）。`%USERPROFILE%\.android` 不存在，即从未连接过设备 |
| 构建缓存 | `%USERPROFILE%\.gradle`、`%USERPROFILE%\.android` | **均不存在**（证明本机从未构建过 Android 工程） |
| `Program Files` 顶层目录 | 枚举 | 7-Zip、AntiCheatExpert、Common Files、Dell、dotnet、Intel、Internet Explorer、LevelZeroSDK、MCHOSE HUB、Microsoft Office、ModifiableWindowsApps、Tencent、UGREEN、Windows Defender 等 —— **无 Java / Android 相关目录** |

### 2.2 明确结论

- **本机当前无法产出可安装的 APK。** 构建链条上的每一个环节（JDK → Android SDK → Build Tools → Gradle → platform-tools）全部缺失，缺口为 **100% 而非部分**。
- **本机也无法运行到真机或模拟器。** 无 adb、无 platform-tools、无 emulator、无系统镜像、`.android` 目录不存在（从未配对过设备）。
- **本机可以做的**：编写与评审全部 Kotlin / Gradle 源码；编写并在本机**真实运行**桌面端导入脚本（本机已有 Python 3.13.14 与 Node 22.22.2，第 7.5 节）；用脚本导出的真实 JSON 作为解析器测试夹具。
- **本机不能做的**：`assembleDebug`、`flutter build` 类构建、单元测试执行、Instrumentation 测试、UI 渲染验证、任何"构建通过 / 测试通过"的断言。

> **给项目总监的硬话**：在工具链安装完成前，任何关于"已构建成功 / 单元测试已通过 / 动画流畅"的描述都**不成立**，不得写入验收结论。本机唯一的可执行验证是**纯 Kotlin/JVM 逻辑的桌面端测试**（见第 14.4 节的可移植测试策略），以及**导入脚本的端到端实跑**。

### 2.3 若要让本机产出 APK，需要安装什么（精确清单）

两条路线，按用户是"大一新生、非专业开发"的画像给建议。

**路线 A（推荐给用户本人，图形界面、一次性装齐）**

| 序号 | 软件 | 版本 | 下载来源 | 下载体积 | 安装后占用 | 说明 |
|------|------|------|----------|----------|-----------|------|
| A1 | Android Studio | 最新稳定版（含 JBR JDK 21、SDK Manager、AVD Manager） | `https://developer.android.com/studio` | 约 1.1 - 1.5 GB | 约 3 - 4 GB | 自带 JBR JDK，**无需单独装 JDK**；界面内即可装 SDK / Build Tools / 平台 / 模拟器 |
| A2 | Android SDK Platform | `platforms;android-37` | Studio 内 SDK Manager | 约 60 - 90 MB | 约 100 - 150 MB | compileSdk 37 所需（第 4 章） |
| A3 | Android SDK Build-Tools | `build-tools;36.0.0` | Studio 内 SDK Manager | 约 60 MB | 约 100 MB | AGP 9.3 的默认 Build Tools 版本 |
| A4 | Android SDK Platform-Tools | `platform-tools` | Studio 内 SDK Manager | 约 15 MB | 约 30 MB | 提供 adb（真机安装 / 调试） |
| A5 | 模拟器（可选，无实体机时必需） | `emulator` + `system-images;android-36;google_apis;x86_64` | Studio 内 SDK Manager | 约 1.5 - 2 GB | 约 3 - 5 GB | 实体机可跳过 |

- **路线 A 合计**：仅构建 APK 约 **1.3 - 1.8 GB 下载 / 4 - 5 GB 磁盘**；含模拟器约 **3 - 4 GB 下载 / 8 - 10 GB 磁盘**。
- **工作量**：下载 + 安装 + 首次构建依赖拉取，**约 1.5 - 3 小时**（视网速，国内建议配置镜像，见第 15.3 节）。

**路线 B（最小命令行，适合已懂 Gradle 的人）**

| 序号 | 软件 | 版本 | 下载来源 | 下载体积 | 安装后占用 |
|------|------|------|----------|----------|-----------|
| B1 | JDK（Eclipse Temurin 或 Microsoft Build of OpenJDK） | **17 LTS**（AGP 9 最低要求 JDK 17） | `https://adoptium.net/temurin/releases/?version=17` 或 `https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.msi` | 约 180 MB | 约 350 - 450 MB |
| B2 | Android Command-line Tools | 最新稳定版 | `https://developer.android.com/studio#command-tools`（`commandlinetools-win-*.zip`） | 约 150 MB | 约 300 MB |
| B3 | SDK 组件（经 `sdkmanager` 安装） | `platform-tools`、`platforms;android-37`、`build-tools;36.0.0` | B2 的 `sdkmanager` | 约 135 MB | 约 250 MB |
| B4 | Gradle | **不需要单独装**：使用工程内 Gradle Wrapper，首次构建自动下载 Gradle 9.5.0 到 `%USERPROFILE%\.gradle` | `services.gradle.org`（可换镜像） | 约 130 - 180 MB | 约 300 MB |

- **路线 B 合计**（仅出 APK、不用模拟器）：约 **600 - 800 MB 下载 / 1.3 - 1.8 GB 磁盘**；**不产生模拟器，无法在无实体机的情况下运行验证**。
- 收尾必做：`sdkmanager --licenses` 逐条接受许可，否则 Gradle 构建会以 "licenses not accepted" 失败。

**若选择"不装工具链"的替代交付路径**：在具备工具链的机器或 CI 上构建（例如 GitHub Actions `ubuntu-latest` + `actions/setup-java@v4` 的 JDK 17 + `android-actions/setup-android@v3`）。该路线本机无法验证（无仓库、无凭据），仅作为备选记录，不构成本次交付的前提。

### 2.4 本机已具备、与本项目相关的能力

| 能力 | 版本 | 对本项目的价值 |
|------|------|----------------|
| Python | 3.13.14（`%LOCALAPPDATA%\Programs\Python\Launcher\py` 与 WorkBuddy 内置） | **可立即在本机实现并实跑第 7.5 节的桌面导入脚本** |
| Node.js | 22.22.2 | 同上（若倾向 JS 实现提取器） |
| curl | 8.21.0（Windows Schannel） | 可实跑网络可达性探测 |
| Microsoft Edge | 已安装（`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`） | 桌面脚本若需无人值守登录可用 Edge/Chromium 内核 |

### 2.5 网络可达性探测（实测，用于第 7 章导入路径判断）

在本机（非校园网环境假设）以 curl 实测：

| 目标 | 结果 |
|------|------|
| `https://jwgl.sdju.edu.cn/home` | **HTTP 302**（约 1.11s）—— 可连通，返回重定向（符合"未登录 → 跳 SSO"预期） |
| `https://authserver.sdju.edu.cn/authserver/login` | **HTTP 200**（约 0.14s）—— 可连通 |
| `https://dl.google.com/android/repository/repository2-3.xml` | **HTTP 200** —— Google Maven / SDK 源可达 |
| `https://api.adoptium.net/v3/info/available_releases` | **HTTP 200** —— JDK 下载源可达 |

> **重要推论**：教务系统与统一身份认证从**公网可达**（至少从本机所在网络可达），无需校园网或 VPN 即能拿到 SSO 登录页。但"公网可达"≠"从手机流量可达"，也≠"课表 iframe 接口无需校内 IP"——这两点仍需实测，已登记为待定项（`OPEN-DECISIONS.md` OD-002）。
>
> 另附探测中出现的 `curl: (23) client returned ERROR on write` —— 这是写入 `/dev/null` 时的本地写入限制报错，与 HTTP 状态码无关，状态码已成功返回，结论有效。

### 2.6 建议的推进顺序（架构视角）

1. **先做能在本机跑的**：桌面端导入脚本 + 周次表达式解析器（纯 Kotlin/JVM 或 Python 双实现，互相校验）。这一步今天即可产出可运行证据。
2. **同时**：源码全部按本文档编写（源码可评审、不可编译，需在文档中如实标注）。
3. **待用户装好工具链**（路线 A）后：`./gradlew assembleDebug` 首次真编译，此时才产生"构建通过"的第一份真实证据。

---

## 3. 分层架构

### 3.1 分层与依赖方向

```text
+---------------------------------------------------------------------------+
|  表现层 (Presentation / feature)                                          |
|  Compose Screen + ViewModel + UiState (sealed interface)                  |
|  feature/today  feature/week  feature/semester  feature/import  feature/settings
+---------------------------------------------------------------------------+
        |  依赖 (只向下依赖)             ^  单向数据流: State 向下 / Event 向上
        v                                |
+---------------------------------------------------------------------------+
|  领域层 (Domain) —— 纯 Kotlin，无 Android 依赖，可 JVM 单元测试            |
|  model/        Semester Course ClassSession SessionOccurrence ClassStatus |
|  schedule/     WeekExpressionParser  ScheduleCalculator  SemesterResolver |
|  time/         TimeProvider (可注入)                                      |
+---------------------------------------------------------------------------+
        ^                                |
        |  依赖                          v
+---------------------------------------------------------------------------+
|  数据层 (Data)                                                            |
|  local/     Room: KebiaoDatabase + Dao + Entity + 迁移                    |
|  repository/ SemesterRepository  ScheduleRepository  (唯一数据出口)        |
|  import/    ImportSource(接口) -> WebViewImportSource / JsonFileImportSource
|             parser/ ScheduleHtmlParser  ImportMapper  WeekExpressionParser |
|             session/ CookieStore  SsoLoginSession                         |
+---------------------------------------------------------------------------+
        ^                                |
        |                                v
+---------------------------------------------------------------------------+
|  平台能力层 (Platform) —— Android 框架交互，全部经接口隔离                 |
|  reminder/  ReminderScheduler  AlarmReceiver  ReminderWorker  BootReceiver |
|  widget/    KebiaoWidget(Glance)  KebiaoWidgetReceiver  WidgetUpdater      |
|  di/        AppContainer (手写依赖容器)                                    |
|  core/      AppResult  AppError  LocalEventLog                            |
+---------------------------------------------------------------------------+
```

依赖规则（硬约束）：

- `domain` **不得** import 任何 `android.*` / `androidx.*`。领域层是纯 Kotlin，这是"数据与今天解耦"能被单元测试证明的前提。
- `feature` 只能通过 `repository` 接口与 `domain` 交互，**不得**直接 import Room 的 Dao 或 Entity。
- `data` 可以依赖 `domain`；`domain` **不得**依赖 `data`。
- 跨层通信只经 `repository` 与 `domain` 模型，不传递 Room Entity 到 `feature`（Entity 到领域模型的映射在 `data/repository` 内完成）。

### 3.2 运行时数据流（一次"今天"渲染）

```text
Room(class_sessions, courses, semesters)
   |  Flow（表变更自动重发）
   v
ScheduleRepository.observeSessionsOfCurrentSemester() : Flow<List<ClassSession>>
   |                                        SemesterRepository.observeCurrentSemester() : Flow<Semester?>
   |                                                             |
   +---------------- combine -----------------+                 |
                                              v                 |
                        TimeProvider.todayFlow() : Flow<LocalDate>  (跨零点/时区/改时间自动重发)
                                              v                 |
                        ScheduleCalculator.occurrencesOn(date, semester, sessions, now)
                                              v
                        TodayViewModel -> TodayUiState (Loading / Empty / Content / Error)
                                              v
                            Compose UI: AnimatedContent + animateItem（第 10 节）
```

关键点：**UI 从不自己"算今天"**。它只订阅 `Flow<TodaySchedule>`。学期切换、周次推进、单双周交替、临时调课、跨零点，全部通过"数据源变化 → Flow 重发 → 重算"这一条链路自动生效，无需任何手动刷新（对应 PRD 第 9.3 节"切换学期后 3 秒内刷新"）。

---

## 4. 技术栈版本锁定表

> 规则来源：`spec-as-contract.md` 第 3 节"版本锚定"。**所有版本均为 2026-09-20 联网核对官方 release notes 后写入，禁止按"通用印象"写 API。**

### 4.1 主锁定组合（推荐）

| 组件 | 锁定版本 | 发布日期 | 锚定来源 | 选择理由 |
|------|----------|----------|----------|----------|
| JDK | **17 (LTS)** | 长期支持 | AGP 9.x release notes："JDK 17（最低/默认）" | AGP 9 的硬性最低要求；Temurin / Microsoft OpenJDK 17 均可 |
| Gradle | **9.5.0**（Wrapper） | 2026-07 前后 | AGP 9.3 release notes："Gradle 最低 9.5.0 / 默认 9.5.0" | 与 AGP 9.3 的默认值严格一致，规避版本推断 |
| AGP | **9.3.3** | 2026-07（9.3.0 起，9.3.1/9.3.2/9.3.3 为补丁） | `developer.android.com/build/releases/agp-9-3-0-release-notes` | 已吃到三个补丁、比当周的 9.4.0 更稳；支持 compileSdk 最高 37 |
| Kotlin | **2.4.20** | 2026-09-07 | `kotlinlang.org/docs/releases.html` | 当前稳定版；2.4 支持窗口至 2027-12-03 |
| Kotlin Compose 编译器插件 | **随 Kotlin 2.4.20**（`org.jetbrains.kotlin.plugin.compose`） | 同 Kotlin | Kotlin 2.0+ 起 Compose 编译器并入 KGP | 旧 `composeOptions.kotlinCompilerExtensionVersion` 已废弃，禁止使用 |
| KSP | **2.3.11** | 2026-09 前后 | KotlinPoet changelog："New: Kotlin 2.4.20. New: KSP 2.3.11" | Room 注解处理；**KSP2**（KSP1 与 Kotlin ≥2.3 / AGP ≥9 不兼容，见 google/ksp README） |
| Compose BOM | **2026.09.00** | 2026-09 | `developer.android.com/develop/ui/compose/bom` 官方示例当前值 | 一次声明对齐全部 Compose 版本；指向 Compose 1.12.1 + Material3 1.4.0 |
| Compose 核心 | 1.12.1（由 BOM 决定） | 2026-09-09 | jetc.dev / BOM 映射 | BOM 2026.09.00 的生产版本指向 1.12.1 |
| Material3 | **1.4.0**（由 BOM 决定） | 2026-09-09 | `releases/compose-material3` 稳定版 | Expressive 组件（1.5.0-alpha）不作为稳定依赖引入 |
| compileSdk | **37** | Android 17 (API 37) | Compose 官方依赖文档："自 Compose 1.12.0 起，项目必须使用 compileSdk 37 与 AGP 9" | **由 Compose 1.12 强制**，非自由选择 |
| targetSdk | **36** | Android 16 (API 36) | Play target API 要求（2026-08-31 起 API 36） | 自用不上架，取 36 而非 37 以规避首日 Android 17 行为变更未知项 |
| minSdk | **26**（Android 8.0） | — | 见 `ADR-006` | 与 PRD 第 11 节"Android 8.0 及以上"一致；`java.time` 原生可用（第 6.7 节） |
| SDK Build Tools | **36.0.0** | — | AGP 9.x release notes："SDK Build Tools 36.0.0（最低/默认）" | AGP 9.3 默认值 |
| AndroidX Core | **1.19.0** | 2026-06-03 | `releases/core` | 注意：1.19.0-alpha01 起 core-ktx 已并入 core，`core-ktx` 变为空兼容包 |
| Lifecycle (ViewModel/Runtime Compose) | **2.11.0** | 2026-09-09 | KMP 支持列表 / Compose 依赖文档 | `collectAsStateWithLifecycle()` 来源 |
| Activity Compose | **1.13.0** | 2026-09 | Compose 官方依赖文档示例值 | `enableEdgeToEdge()` 侧 |
| Navigation Compose (Nav2) | **2.10.1** | 2026-09-09 | KMP 支持列表 | Nav3（`navigation3` 1.2.0-rc01）仍是 RC，不用于稳定交付 |
| Room | **2.8.5** | 2026-09-09 | `releases/room` | 稳定线；Room 3.0（`androidx.room3`）仍为 Alpha，不采用 |
| DataStore (Preferences) | **1.2.1** | 2026-03-11 | `releases/datastore` | 仅存标量设置项（提醒提前量、主题偏好） |
| WorkManager | **2.11.2** | 2026-03-25 | `releases/work` | 兜底提醒与定周期刷新；minSdk 23、compileSdk ≥33 |
| Glance AppWidget | **1.2.0** | 2026-08-26 | `releases/glance` | 稳定版；1.3.0-alpha02 为 Alpha，不采用 |
| OkHttp | **5.5.0** | 2026-08-17 | Maven Central（mvnrepository） | 会话抓取（CookieJar / 重定向 / TLS） |
| jsoup | **1.23.2** | 2026-08-27 | `jsoup.org` | HTML 表格解析；Android 侧需开启 core library desugaring 的 NIO 规范（官方明示） |
| kotlinx-datetime | **0.8.0** | 2026-05-07 | GitHub releases | 若需要跨平台日期类型；**JVM/Android 侧优先直接用 `java.time`**（minSdk 26 已原生支持），kotlinx-datetime 仅作可选 |
| kotlinx-serialization-json | **1.11.0** | 2026-01 前后 | Maven（Renovate 记录，基于 Kotlin 2.3.20） | 导入 JSON 文件兜底路径的序列化与反序列化 |
| Vector 图标库 | **Material Symbols**（Google Fonts，Apache-2.0） | 持续维护 | `androidx.compose.material.icons` 官方 API 页明确"不再推荐，Material Symbols 是新方向" | 见第 10.4 节 |

### 4.2 未锚定项（诚实标注，构建时必须校验）

下列版本在本次调研中**未取得官方一手确认**，因此**不写入锁定值**，避免幻觉版本号：

| 组件 | 状态 | 处理方式 |
|------|------|----------|
| `org.jetbrains.kotlinx:kotlinx-coroutines-*` | 未锚定 | 由 Lifecycle/Compose 传递引入；如需显式固定，构建时以 `./gradlew :app:dependencies` 实际解析结果为准 |
| `androidx.compose.material3.adaptive:*` | 未锚定 | 非 MVP 必需，MVP 不引入 |
| `androidx.core:core-splashscreen` | 未锚定 | 非必需；若引入需单独核版本 |
| 各 AndroidX 测试库（`androidx.test.*`、`compose ui-test`） | 未锚定 | 由 BOM 与 Studio 模板提供，构建时确认 |

**纪律**：上述任一项在真正写进 `gradle/libs.versions.toml` 之前，必须先由构建环境解析成功（存在性核验，见 `generated-code-failure-modes.md` 第 3 节）。**不接受"看起来合理"的版本号。**

### 4.3 保守回退组合（若 AGP 9.x 出现构建阻塞）

| 组件 | 回退值 |
|------|--------|
| AGP | 8.13.x（2025-09 稳定线） |
| Gradle | 8.13 |
| Compose BOM | 2025.12.00（Compose 1.10 / Material3 1.4.0） |
| compileSdk | 36 |
| 其余 | 同主组合（Kotlin 2.4.20 仍需自行确认与 AGP 8.13 的 KGP 兼容） |

回退代价：失去 Compose 1.12 的 `Modifier.onPlaced` 等性能改进与 pausable composition 的默认可暂停组合能力（对"页面切换与内容更新必须有过渡动画 + 性能预算"这一硬要求有轻微不利）。

---

## 5. 待定项总览

完整登记册见 `docs/decisions/OPEN-DECISIONS.md`（字段规范取自 `open-decisions-register.md` 第 3 节，采用三类固定 slug）。

| 编号 | 类别 | 事项 | 状态 |
|------|------|------|------|
| OD-001 | design-decision-to-evaluate | 教务导入路径最终定型（内建 WebView 中继 vs 文件导入） | OPEN |
| OD-002 | waiting-on-external-condition | 手机流量下教务系统 / SSO 是否可达；是否需校园网/VPN | OPEN |
| OD-003 | waiting-on-external-condition | SSO 登录是否需要短信验证码 / 手机绑定（2026 新生信明示"首次登录需绑定手机号"） | OPEN |
| OD-004 | design-decision-to-evaluate | 学期第 1 周周一（`startDate`）的获取方式（解析 / 推导 / 用户校准） | OPEN |
| OD-005 | design-decision-to-evaluate | 课表 iframe 的具体数据接口形态（URL / POST 参数 / 学期 id 枚举） | OPEN |
| OD-006 | existing-design-boundary | minSdk 26 的取舍（若用户设备低于 8.0 需上调整体方案） | OPEN |
| OD-007 | design-decision-to-evaluate | 是否启用 Room 数据库加密 / SSO 凭据存储强度 | OPEN |
| OD-008 | existing-design-boundary | 国产 ROM 后台清理下的提醒送达率（PRD 目标 ≥95%）无实测样本 | OPEN |

---

## 6. 核心架构设计：数据与"今天"解耦

> 这是本项目的架构核心。**课表数据是静态的学期数据；"今天上什么课"是随日期变化的计算结果。** 二者的解耦方式决定了 PRD 第 9.3 节（换学期刷新）、第 9.7 节（过渡动画）、第 9.1 节（单双周）能否同时成立。

### 6.1 概念分层（四层）与术语映射

| 层 | 概念 | 性质 | 是否落库 | 对应 PRD 术语 |
|----|------|------|----------|---------------|
| L1 | **Semester 学期** | 实体 | 是 | 学期 |
| L2 | **Course 课程** | 实体（逻辑课程，"是什么课"） | 是 | 课程 |
| L3 | **ClassSession 上课安排** | 实体（一条 = 一组"周次集合 + 星期 + 节次 + 地点 + 教师"） | 是 | PRD 称"课程实例 / CourseOccurrence" |
| L4 | **SessionOccurrence 课次** | **计算产物**（某一天命中 L3 之后的实例） | **否**（每次求值现算） | "当天课程"里的一条 |

**关键约定（务必在前后端/文档中统一）**：PRD 第 4 节把 L3 命名为"课程实例 / CourseOccurrence"。本文档为避免与 L4 混淆，统一采用 **ClassSession（上课安排）** 指 L3、**SessionOccurrence（课次）** 指 L4。术语映射已写入本表，后续文档以本表为准。

**为什么 L3 必须拆成多行而不是一行？** 因为真实数据里"同一门课不同周换教室换教师"（PRD 第 4.2 节第 1、2 条）。若按"课程表一行"存，则"第 2 周 E教305 / 第 3-16 周 B105"无法表达。拆成两条 L3、周次集合互斥，是唯一能同时满足"当周取到正确教室"与"周视图正确"的建模方式。

以真实数据为例（示例，非指定）：

```text
Course: 大学物理B(1)  code=053017P1-15
  ClassSession #1: 星期=三 节次=1-2 08:10-09:40 临港校区 E教305 教师=李彬彬 周次={2}
  ClassSession #2: 星期=三 节次=1-2 08:10-09:40 临港校区 B105   教师=李彬彬 周次={3,4,...,16}

Course: 自动化专业导论与职业生涯规划
  ClassSession #1: 星期=? 周次={2}     地点=D教203 教师=陈国初
  ClassSession #2: 星期=? 周次={3,4}   地点=B203   教师=蒋璐峥
  ClassSession #3: 星期=? 周次={6,7}   地点=B203   教师=于妍
  ClassSession #4: 星期=? 周次={5,8}   地点=B203   教师=陈国初

Course: 大学物理实验B(1)
  ClassSession #1: 周次={9,10,11}  实验室=204(实验室2)
  ClassSession #2: 周次={12}       实验室=202(实验室1)
  ClassSession #3: 周次={13}       实验室=309(实验室11)
  ClassSession #4: 周次={14}       实验室=305(实验室7)
  ClassSession #5: 周次={15,16}    实验室=308(实验室10)

Course: 航空与航天（尔雅通识课）  isOnline=true
  ClassSession #1: 周次={5..16}  无实体教室（isOnline=true, room=null）

Course: 军事技能  无固定时间地点
  ClassSession #1: 星期=null / 节次=null / startTime=null / endTime=null
```

### 6.2 领域模型（纯 Kotlin）

```kotlin
// 示例，非指定：领域模型形状说明，非最终 API
enum class WeekParity { ALL, ODD, EVEN }          // 全周 / 单周 / 双周

@JvmInline value class WeekSet(val weeks: Set<Int>)   // 规范化后的周次集合，1-based

enum class ClassStatus { UPCOMING, ONGOING, FINISHED, UNKNOWN_TIME }

data class Semester(
    val id: Long,
    val name: String,               // "2026-2027 学年第一学期"
    val academicYear: String,       // "2026-2027"
    val termIndex: Int,             // 1=秋 / 2=春 / 3=短学期
    val startDate: LocalDate,       // 第 1 周的周一，如 2026-09-14
    val totalWeeks: Int,            // 如 16 / 18
    val isCurrent: Boolean,
)

data class Course(
    val id: Long,
    val semesterId: Long,
    val name: String,               // "大学物理B(1)"
    val code: String?,              // "053017P1-15"
    val totalHours: Int?,           // 48
    val isOnline: Boolean,          // 纯线上课
)

data class ClassSession(
    val id: Long,
    val courseId: Long,
    val weekday: DayOfWeek?,        // null = 无固定星期（如"军事技能"）
    val periodStart: Int?,          // 1
    val periodEnd: Int?,            // 2
    val startTime: LocalTime?,      // 08:10
    val endTime: LocalTime?,        // 09:40
    val campus: String?,            // 临港校区
    val room: String?,              // "E教116" / "204(实验室2)" / null
    val teacher: String?,           // 李彬彬
    val weeksRaw: String,           // 原始串 "2-16 双周"（保留以便审计回溯）
    val weekNumbers: WeekSet,       // 解析后的规范集合 {2,4,6,...,16}
    val source: SessionSource,      // IMPORT / MANUAL
    val importBatchId: Long?,
)

data class SessionOccurrence(       // 计算产物，不落库
    val courseId: Long,
    val courseName: String,
    val courseCode: String?,
    val date: LocalDate,
    val weekOfSemester: Int,
    val weekday: DayOfWeek,
    val periodStart: Int?, val periodEnd: Int?,
    val startTime: LocalTime?, val endTime: LocalTime?,
    val campus: String?, val room: String?,
    val teacher: String?,
    val isOnline: Boolean,
    val status: ClassStatus,
)
```

### 6.3 数据库 Schema（Room）

Room 配置：`exportSchema = true`（schema JSON 入库版本管理）、KSP 处理、迁移用 `Migration` 显式声明（禁止 `fallbackToDestructiveMigration`——个人项目丢了课表比崩溃更痛）。

```sql
-- 学期
CREATE TABLE semesters (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL,
    academic_year TEXT    NOT NULL,
    term_index    INTEGER NOT NULL,
    start_date    TEXT    NOT NULL,   -- ISO-8601 "2026-09-14"（LocalDate 存 TEXT）
    total_weeks   INTEGER NOT NULL,
    is_current    INTEGER NOT NULL DEFAULT 0,
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_semesters_year_term ON semesters(academic_year, term_index);

-- 课程
CREATE TABLE courses (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    semester_id  INTEGER NOT NULL REFERENCES semesters(id) ON DELETE CASCADE,
    name         TEXT    NOT NULL,
    code         TEXT,
    total_hours  INTEGER,
    is_online    INTEGER NOT NULL DEFAULT 0,
    created_at   INTEGER NOT NULL,
    updated_at   INTEGER NOT NULL
);
CREATE INDEX idx_courses_semester ON courses(semester_id);

-- 上课安排（核心表）
CREATE TABLE class_sessions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    course_id       INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    weekday         INTEGER,            -- 1=周一 .. 7=周日；NULL=无固定星期
    period_start    INTEGER,
    period_end      INTEGER,
    start_time      TEXT,              -- "08:10"
    end_time        TEXT,              -- "09:40"
    campus          TEXT,
    room            TEXT,
    teacher         TEXT,
    weeks_raw       TEXT    NOT NULL,   -- 原始串，审计用
    week_numbers    TEXT    NOT NULL,   -- 规范化 ",2,4,6,8,"（前后加分隔符便于 LIKE 查询）
    source          INTEGER NOT NULL,   -- 0=IMPORT 1=MANUAL
    import_batch_id INTEGER REFERENCES import_batches(id) ON DELETE SET NULL,
    remark          TEXT,
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL
);
CREATE INDEX idx_sessions_course  ON class_sessions(course_id);
CREATE INDEX idx_sessions_weekday ON class_sessions(weekday);

-- 导入批次（审计 / 回滚 / import_verify_diff 日志的载体）
CREATE TABLE import_batches (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    semester_id   INTEGER NOT NULL REFERENCES semesters(id) ON DELETE CASCADE,
    source        INTEGER NOT NULL,     -- 0=WEBVIEW 1=JSON_FILE
    status        INTEGER NOT NULL,     -- 0=RUNNING 1=SUCCESS 2=FAILED 3=ROLLED_BACK
    payload_ref   TEXT,                 -- 原始 HTML/JSON 的本地文件路径（不外传）
    course_count  INTEGER,
    session_count INTEGER,
    message       TEXT,
    started_at    INTEGER NOT NULL,
    finished_at   INTEGER
);
CREATE INDEX idx_batches_semester ON import_batches(semester_id);
```

**索引策略说明**（避免过早优化，同时保证查询可预期）：

- `courses(semester_id)`、`class_sessions(course_id)`：外键查询，必建。
- `class_sessions(weekday)`：周视图按天取数。
- `week_numbers` **不建索引**。原因：单学期数据量在 100 行量级（PRD 载明 14 门课 + 2 门备注课，拆解后 L3 约数十行），"某天有哪些课"采用**内存过滤**（一次 `SELECT` 出该学期全部 L3，在 `ScheduleCalculator` 内过滤），复杂度 O(n) 且 n 极小。这正是 `mvp-stack.md` 对嵌入式 SQLite 的适用边界，也是"避免过早优化"原则的直接应用。若未来数据量增长（例如支持整届课表），再引入 `week_numbers LIKE` 索引或位图列。

### 6.4 周次表达式解析算法（`WeekExpressionParser`）

这是本项目**最容易出现沉默逻辑错误**的地方（`generated-code-failure-modes.md` 第 2 节点名的类型），必须独立成模块 + 穷举测试。

**输入形态**（PRD 第 4.2 节实测）：

| 原始串 | 语义 |
|--------|------|
| `2-16 双周` | 2,4,6,8,10,12,14,16 |
| `3-15 单周` | 3,5,7,9,11,13,15 |
| `9-16` | 9,10,11,12,13,14,15,16 |
| `2,3-4,5,8,6-7` | 2,3,4,5,6,7,8（乱序 + 重复区间，需并集去重） |
| `1-16` | 1..16（全周） |
| `第1-16周` | 同上（带"第…周"修饰） |

**算法**：

```kotlin
// 示例，非指定：算法形状说明
fun parse(raw: String, totalWeeks: Int): Result<WeekSet> {
    val text = raw.trim()
    val parity = when {
        text.contains("单") -> WeekParity.ODD
        text.contains("双") -> WeekParity.EVEN
        else                -> WeekParity.ALL
    }
    // 1) 归一：全角逗号->半角；去掉"周""第""单双周""(双)"等非数字/非逗号/非连字符字符
    val cleaned = text
        .replace('，', ',')
        .replace(Regex("[^0-9,\\-]"), "")
    if (cleaned.isBlank()) return Result.failure(ParseError.EmptyWeeks(raw))

    // 2) 分词：按逗号切分，每段要么 "a" 要么 "a-b"
    val weeks = mutableSetOf<Int>()
    for (token in cleaned.split(',').filter { it.isNotBlank() }) {
        val parts = token.split('-').filter { it.isNotBlank() }
        when (parts.size) {
            1 -> weeks += parts[0].toInt()
            2 -> {
                val (a, b) = parts[0].toInt() to parts[1].toInt()
                if (a > b) return Result.failure(ParseError.ReversedRange(raw, a, b))
                weeks += a..b
            }
            else -> return Result.failure(ParseError.MalformedToken(raw, token))
        }
    }

    // 3) 奇偶过滤
    val filtered = when (parity) {
        WeekParity.ALL  -> weeks
        WeekParity.ODD  -> weeks.filter { it % 2 == 1 }.toSet()
        WeekParity.EVEN -> weeks.filter { it % 2 == 0 }.toSet()
    }

    // 4) 夹逼到 [1, totalWeeks]；过滤后为空则视为语义错误（不是静默返回空集）
    val clamped = filtered.filter { it in 1..totalWeeks }.toSet()
    if (clamped.isEmpty()) return Result.failure(ParseError.OutOfSemesterRange(raw, totalWeeks))
    return Result.success(WeekSet(clamped))
}
```

**必须覆盖的边界用例**（`ScheduleCalculatorTest` + `WeekExpressionParserTest`）：

1. `2-16 双周` 且 `totalWeeks=16` → `{2,4,6,8,10,12,14,16}`。
2. `3-15 单周` 且 `totalWeeks=16` → `{3,5,7,9,11,13,15}`。
3. `2,3-4,5,8,6-7` → `{2,3,4,5,6,7,8}`（**去重 + 乱序归一**，这是变异测试重点）。
4. `1-20` 且 `totalWeeks=16` → `{1..16}`（**上溢夹逼**）。
5. `2-16 双周` 且 `totalWeeks=16`：**第 17 周求值必须返回空**（且不抛异常）。
6. `""` / `"周"` / `"  "` → **显式失败**，不得静默返回空集（否则表现为"这门课凭空消失"——典型的沉默逻辑错误）。
7. `16-2`（反向区间）→ 显式失败。
8. 单周与双周全周交集：`1-16 单周` + `2-16 双周` 在同一天不得重复计课。
9. 缺 `totalWeeks` 信息时（教务系统未给学期总周数）→ 采用 `max(weeks) ` 作为下界推导并记 `import_verify_diff`，而非丢弃。

**纪律**：解析失败**必须**产生可见的 `ParseError` 并进入 `import_verify_diff` 事件与导入核对视图；**禁止** `catch` 后返回空集合。这条直接对应 `generated-code-failure-modes.md` 的"Happy-path 偏差"与"静默缺失"两条失效模式。

### 6.5 "今天有哪些课"求值算法（`ScheduleCalculator`）

```kotlin
// 示例，非指定：算法形状说明
fun occurrencesOn(
    date: LocalDate,
    semester: Semester,
    sessions: List<ClassSession>,
    coursesById: Map<Long, Course>,
    now: LocalTime,
): List<SessionOccurrence> {

    // 1) 日期 -> 学期周次
    val week = semester.weekOf(date) ?: return emptyList()   // 学期外/假期 => 空

    // 2) 星期匹配（DayOfWeek 1=Mon..7=Sun，与 DB weekday 编码一致）
    val dow = date.dayOfWeek

    // 3) 命中过滤：星期一致 且 本周在 weekNumbers 内
    val hits = sessions.filter { s ->
        s.weekday == dow && week in s.weekNumbers.weeks
    }

    // 4) 组装课次 + 状态判定
    val nowMinutes = now.hour * 60 + now.minute
    return hits.map { s ->
        val course = coursesById.getValue(s.courseId)
        val status = when {
            s.startTime == null || s.endTime == null -> ClassStatus.UNKNOWN_TIME
            nowMinutes < s.startTime.toMinutes()      -> ClassStatus.UPCOMING
            nowMinutes < s.endTime.toMinutes()        -> ClassStatus.ONGOING
            else                                      -> ClassStatus.FINISHED
        }
        SessionOccurrence(/* 逐字段映射 */)
    }
    // 5) 排序：有时间 > 无时间；同组按 startTime 升序，无时间者按 periodStart 升序，仍无则按课程名
      .sortedWith(compareBy({ it.startTime == null }, { it.startTime }, { it.periodStart }, { it.courseName }))
}

// Semester.weekOf：核心日期数学
fun Semester.weekOf(date: LocalDate): Int? {
    if (date < startDate) return null
    val days = ChronoUnit.DAYS.between(startDate, date)
    val week = (days / 7).toInt() + 1
    return if (week in 1..totalWeeks) week else null
}
```

**为什么要单独抽 `Semester.weekOf`？** 因为"今天是第几周"是本项目**唯一一处**真正依赖"当前时间"的日期数学（其余都是纯数据变换）。把它抽成一个纯函数、用 `startDate` 与 `date` 作为唯一输入，就能在 JVM 单测里穷举"第 1 周 / 边界周 / 学期外 / 单双周交界"，**完全不依赖系统时间**。这是把"时间"从"数据"里彻底剥离的关键手法。

**三种"没有课"必须可区分**（PRD 第 10 节要求）：

| 状态 | 判定 | UI 文案方向 |
|------|------|-------------|
| 今天确实没课 | `week != null` 且 `hits.isEmpty()` | "今天没有课" |
| 假期 / 学期未开始或已结束 | `week == null` | "今天在学期之外" |
| 尚未导入课表 | `semester` 为 null 或无 `ClassSession` | "还没有课表，去导入" |

### 6.6 学期实体与"当前学期"的确定与切换

**数据源唯一**：`semesters.is_current`（DB 列）。不允许在 DataStore 与 DB 各存一份"当前学期"，避免双真相源导致的刷新不一致（PRD 第 10 节"并发：不得出现新旧数据混合"）。

**`is_current` 的两阶段语义**：

1. **导入 / 首次启动时（自动推断）**：`SemesterResolver.inferCurrent(today, semesters)` 按序判定：
   - 若某学期满足 `startDate <= today < startDate + totalWeeks*7` → 命中，若多命中取 `termIndex` 最大者；
   - 否则取"距今天最近的学期"（上一个学期末优先于下一个学期初，因为学期之间是假期，用户更可能想看刚结束的学期）；
   - 全库为空 → 返回 null。
2. **用户显式切换后（手动权威）**：`is_current` 由用户操作改写，`inferCurrent` 不再覆盖它（除非用户新增/导入学期）。**`inferCurrent` 只在"学期集合发生变化"时运行一次**，不在每次启动时运行——避免用户在假期切换学期后被自动改回。

**切换事务**（保证"切换到哪就把唯一标记挪到哪"）：

```kotlin
// 示例，非指定：事务形状说明
@Transaction
suspend fun switchCurrentSemester(targetId: Long) {
    semesterDao.clearCurrent()          // UPDATE semesters SET is_current = 0
    semesterDao.markCurrent(targetId)   // UPDATE semesters SET is_current = 1 WHERE id = :targetId
}
```

**不变式**：任意时刻**至多一行** `is_current = 1`。SQLite 无"部分唯一索引"由 Room 直接声明（Room 2.8 的 `@Index` 不支持 `WHERE` 子句），因此该不变式由**上述事务 + `SemesterRepository` 单点写入口**保证，并由单测断言（`SemesterInvariantTest`）。

**刷新链路（对应 PRD 第 9.3 节验收）**：

```text
switchCurrentSemester(id)
  -> Room 事务提交 -> semesters 表变更
  -> SemesterRepository.observeCurrentSemester() 重发新 Semester
  -> ScheduleRepository.observeSessionsOfCurrentSemester() 以新 semesterId 重查
  -> combine() 产生新 TodaySchedule
  -> TodayUiState.Content 更新 -> AnimatedContent 过渡
```

默认无过渡（瞬时切库）会让"旧课残留"看起来像 bug；因此第 10.2 节要求 UI 层用 `AnimatedContent` 呈现新旧更替，"旧数据不留、但切换有过渡"两者同时满足。

### 6.7 时间源抽象（`TimeProvider`）

```kotlin
// 示例，非指定：接口形状说明
interface TimeProvider {
    fun today(): LocalDate
    fun now(): LocalTime
    fun zone(): ZoneId
    fun todayFlow(): Flow<LocalDate>   // 跨零点/时区变更/用户改系统时间时重发
}

class SystemTimeProvider(private val zone: ZoneId = ZoneId.systemDefault()) : TimeProvider { /* java.time */ }

class FixedTimeProvider(var date: LocalDate, var time: LocalTime) : TimeProvider { /* 测试用，可任意设定"某天某刻" */ }
```

**为什么必须有这层抽象？**

- **可测**：`FixedTimeProvider` 让"2026-09-28（第 3 周周一）第 1 节课是否已下课"这类断言变成纯函数测试，无需 mock 系统时间。
- **可换**：将来若加"手动切换查看某一天"功能，只需换 TimeProvider，`ScheduleCalculator` 一行不改。
- **可解释**：所有"依赖今天"的地方都必须显式接收一个 `TimeProvider`，**代码评审时若发现谁在 domain 层直接调 `LocalDate.now()`，即为不合格。** 这条要写进 Review Checklist。

**`todayFlow()` 的触发条件**（容易漏，逐条列出）：

1. 跨零点：可用一次性的定时器或用 `WorkManager` 的日更任务唤醒；**不使用 `AlarmManager` 常驻**（省电，且精确闹钟要留给上课提醒）。
2. `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` / `ACTION_DATE_CHANGED` 广播。
3. App 回到前台（`ON_RESUME`）时校验窗口焦点（`focused`）后立即对比日期是否变化。

> 冷启动到"今日课程" < 2s（PRD 第 11 节）：链路是 `Room(首屏查询) -> Calculator -> UI`，其中不包含网络。首屏用 `Loading` + 骨架屏，Room 数据到位后 `AnimatedContent` 过渡到 `Content`。

### 6.8 单双周与"跨周换教室"正确性的验证锚点

PRD 第 9.1 节的验收用例已给出精确期望值，架构侧对应到具体测试：

| PRD 验收 | 架构侧测试 |
|----------|-----------|
| 大学物理B(1) 第 2 周 `E教305`、第 3 周起 `B105` | `ScheduleCalculatorTest.occurrence_week2_picks_E305__week3_picks_B105` |
| 自动化专业导论 第 2/3-4/5/6-7/8 周教师依次 陈国初/蒋璐峥/陈国初/于妍/陈国初 | `ScheduleCalculatorTest.intro_teacher_rotates_by_week`（5 个断言点） |
| 单周仅单周课、双周仅双周课 | `ScheduleCalculatorTest.odd_even_isolation`（配对断言：奇偶各命中断言 + 互斥断言）|
| 大学物理实验B(1) 每周不同实验室 | `ScheduleCalculatorTest.physicsLab_room_by_week`（9-16 周逐周断言）|

> 这些测试是**纯 JVM 测试**（domain 层无 Android 依赖），因此**在本机 Python/Node 之外，还需要 JDK 才能跑**——工具链就绪前无法执行，如实登记。

---

## 7. 教务系统导入架构（本项目最大技术风险）

### 7.1 目标系统识别（实测 + 官方资料）

| 事实 | 来源 |
|------|------|
| 统一身份认证入口 `https://authserver.sdju.edu.cn/authserver/login` | 实测 HTTP 200 + 学校官网 `sdju.edu.cn/xywl/` |
| 路径含 `/authserver/login` + `service=` 参数 + `CASTGC` 型票据语义 | 与 **Apereo CAS** 协议（`/login` 作为 credential requestor/acceptor、`service` 参数、TGC/TGT、ST 重定向）高度一致 |
| 教务系统 `https://jwgl.sdju.edu.cn/home`，未登录返回 302 跳 SSO | 实测 HTTP 302 |
| 账号 = 学号；**首次登录需绑定手机号**；登录涉及**短信验证码** | `info.sdju.edu.cn/2026/0810/c6623a154020/page.htm`（2026 级新生信，原文"首次登录时需绑定手机号""获取短信验证码时，请勿连续点击'发送验证码'按钮"） |
| 课表位于"我的课表"**iframe** 内 | 用户实测（见任务书） |

**结论**：这是一个 **CAS 系 SSO + 校园教务子系统** 的典型组合，且**登录链路存在人机校验类环节（手机绑定 / 短信验证码）**。这一点直接决定了导入路径的选型（ADR-003）。

### 7.2 三条导入路径对比

| 维度 | 路径 a：App 内直连抓取 | 路径 b：电脑脚本抓取 → 导出文件 → App 导入 | 路径 c：两者都要（内建中继为主 + 文件导入兜底） |
|------|----------------------|------------------------------------------|---------------------------------------------|
| 登录能力 | 需在 App 内复刻表单 POST：解析隐藏域 `execution`/`lt`、`_eventId=submit`，且可能需复刻前端密码加密（盐值 + AES） | 人在真实浏览器里登录，**所有 JS / 加密 / 验证码天然过关** | App 内用 **WebView 让真人登录**，天然过关（无需复刻任何 JS） |
| 短信验证码 / 手机绑定 | **致命阻塞**：App 需申请 SMS 读取权限拦截验证码，或引导用户切换 App 抄码（体验极差、权限敏感） | 无障碍（人在浏览器里自己收码） | 无障碍（WebView 内真人收码后填入） |
| 图形验证码 | 需接入打码服务或自行识别（不可靠） | 无障碍 | 无障碍 |
| Cookie / 会话维持 | 需自管 CookieJar、TGT 生命周期、`route` 粘滞 cookie | 浏览器自管 | WebView 自管；仅需**同一 WebView 上下文内**继续取数 |
| 教务改版适应性 | 改版即失效，需重新逆向 | 脚本需改，但有真实页面可对照 | **解析只需匹配一次 HTML 结构**；登录流程改版由真人自适应 |
| 一次性 vs 高频 | 一次性操作，却要背全部反爬成本 —— **性价比最差** | 一次性操作 + 一次性电脑操作，**成本结构匹配** | 日常零成本（App 内）；极端情况退化为脚本 |
| 用户体验 | 最佳（纯 App 内完成） | 最差（需开电脑 + 传文件） | 好（App 内一键进入登录页） |
| 实现复杂度 | 高（逆向 + 加密复刻 + 权限） | 中（Python/Node 脚本 + 文件格式） | 中（WebView + cookie 复用 + JS 抽取），但**最难的部分（登录）交给 WebView 与真人** |
| 本机今天能否实跑验证 | 不能（无 Android 环境） | **能**（本机已有 Python 3.13.14 / Node 22） | 脚本部分能，App 部分不能 |
| 符合"一学期一次低频"的性质 | 不匹配（为低频操作付高频成本） | 匹配 | 匹配 |

### 7.3 选型结论与理由

**选定：路径 c（内建 WebView 中继登录为主 + 文件导入兜底），并把"登录"这一步交给真人 + 系统 WebView。**

三条理由（按权重）：

1. **最大的不确定性来自"登录"，而不是"解析"。** 手机绑定 + 短信验证码（OD-003）意味着任何"App 内静默直连"的方案都会在登录环节撞墙，而撞墙的方式是"需要短信权限"——这既触碰隐私红线（PRD 第 11 节"不采集隐私"），也是竞品被吐槽的同类问题。WebView 方案让**真人完成登录**，App 只负责"登录成功后取数"，把不确定性彻底移出代码。
2. **课表数据在同一个 WebView 上下文里，同源可直读。** 登录完成后，App 在同一个 WebView 中导航到课表页，通过 `evaluateJavascript` 注入脚本读取 DOM（含同源 iframe 内容）并回传 JSON。**这样连 Cookie 都不需要手工搬运**，也不需要自己实现 HTTP 会话，避免了"App 复刻浏览器会话"这一最容易出错的部分。
3. **兜底路径能让风险可回退。** 若教务系统对 WebView 有 UA 反制、或同源策略阻断 iframe 读取、或用户就是不想在手机登录，则退化为"电脑脚本 → JSON 文件 → App 导入"。文件导入路径的实现与解析器**共用同一套 `ImportMapper` 与 `WeekExpressionParser`**，因此兜底不是第二套代码，只是第二个数据入口。

**必须同时保留"手动编辑课程"为 P1**（PRD 场景 E / 开放问题 1）。理由：即使前两条都失败，用户仍能维持"App 可用"。**架构上预留的扩展点**：`ClassSession.source = MANUAL` 字段 + `SessionEditScreen` 复用同一领域模型，导入与手编在 DB 层天然并存（无需特殊处理，`source` 仅用于展示与统计）。

### 7.4 组件设计

```text
feature/import/ImportScreen (Compose)
  |  ImportViewModel  -- 状态机（7.6 节）
  v
data/import/
  ImportSource (interface)
    +-- WebViewImportSource      （由 WebViewLoginScreen 驱动，App 内）
    +-- JsonFileImportSource     （SAF/文件选择，读取 JSON）
  ImportMapper : 任意来源的中间表示 -> (Semester, Course[], ClassSession[])
  parser/ScheduleHtmlParser : jsoup，把课表 HTML 表格 -> 中间表示
  parser/WeekExpressionParser : 第 6.4 节
  session/CookieStore, SsoLoginSession : WebView 侧的会话观察（只读，不落明文）
```

三条数据入口统一汇入 `ImportMapper`，因此**校验、去重、周次解析、核对视图只有一份实现**：

```text
[WebView JS 抽取的 JSON] --+
                           |--> ImportMapper.map() --> ImportPreview --> 用户核对 --> ImportCommitter.commit()
[电脑脚本导出的 JSON 文件] -+
[手动编辑（未来 P1）] ------+
```

**WebView 方案的技术细节（关键，防止踩坑）**：

1. `WebView` 必须开启 `javaScriptEnabled = true`、`domStorageEnabled = true`；**不设置** `setSupportMultipleWindows` 相关 hack。
2. **绝不在 JS 桥里暴露敏感能力**：只用 `evaluateJavascript(callback)` 单向取值，**不注册** `@JavascriptInterface` 对象把原生能力暴露给页面（页面内容来自校园网，属于外部输入，必须按不可信处理）。
3. **Cookie 处理**：使用系统 `CookieManager`（与 WebView 共享），`setAcceptThirdPartyCookies` 按实际需要；**不手工拼接 Cookie 头**。CAS 的 `route` / `JSESSIONID` / `CASTGC` 由系统管理。
4. **iframe 读取**：`iframe` 若为**同源**，注入脚本可 `contentDocument` 访问；若**跨子域**（如课表在 `jwgl-xxx.sdju.edu.cn`），则需在该 iframe 内导航、或分两步：先在 WebView 里直接打开 iframe 的 `src`（此时它成为主文档，同源可读）。**这一步必须在真实环境先验证**（OD-005）。
5. **UA 反制**：若教务系统对 WebView UA 有拦截，可配置 `WebSettings.userAgentString` 或自定义 `WebViewClient.shouldInterceptRequest`（**只做 UA 兼容，不做请求改写**——改写请求会把自身拖进"复刻浏览器"的泥潭）。
6. **HTTPS / 证书**：若学校证书链不被系统信任，需在 `network_security_config.xml` 中显式声明信任锚，**禁止全局 `onReceivedSslError { handler.proceed() }`**（那等于关闭 TLS 校验，属安全红线）。
7. **不落明文凭据**：WebView 里的密码由用户在页面输入，**App 不读取密码字段**。若为"下次免登录"做凭据缓存（OD-007），只允许存到 **Android Keystore 加密后的偏好**，且默认可关闭。
8. **`import_verify_diff`**：导入后进入核对视图，对照课程数 / 周次集合 / 教室差异（PRD 第 12 节），差异项高亮并可编辑（此处复用 P1 手编能力）。

### 7.5 电脑端脚本路径（本机今天可实跑）

**形态**：一个 Python（或 Node）脚本，用 Playwright/Selenium 驱动 **Edge**（本机已装）完成登录（用户在弹窗里手动收短信码），登录后定位课表 iframe，抓取表格 HTML，导出为 **规范化 JSON**（同时保留原始 HTML 以便排查）。

**输出格式（与 App 共用）**：

```json
{
  "schemaVersion": 1,
  "exportedAt": "2026-09-20T17:40:00+08:00",
  "semester": {
    "name": "2026-2027 学年第一学期",
    "academicYear": "2026-2027",
    "termIndex": 1,
    "startDate": "2026-09-14",
    "totalWeeks": 16
  },
  "courses": [
    {
      "name": "大学物理B(1)",
      "code": "053017P1-15",
      "totalHours": 48,
      "isOnline": false,
      "sessions": [
        { "weekday": 3, "periodStart": 1, "periodEnd": 2,
          "startTime": "08:10", "endTime": "09:40",
          "campus": "临港校区", "room": "E教305", "teacher": "李彬彬",
          "weeksRaw": "2" },
        { "weekday": 3, "periodStart": 1, "periodEnd": 2,
          "startTime": "08:10", "endTime": "09:40",
          "campus": "临港校区", "room": "B105", "teacher": "李彬彬",
          "weeksRaw": "3-16" }
      ]
    }
  ]
}
```

**为什么这条路径值得优先实现（架构建议）**：

1. 它是**本机唯一能立即产出可运行证据**的部分（Python 3.13.14 已在位）。
2. 它产出的 JSON 直接作为 **App 解析器的测试夹具**（fixture），让第 6 章算法的测试数据来自真实课表而非手工编造——这正是 `spec-as-contract.md` 要求的"内嵌已知坑 + 真实数据"。
3. 它是导入路径的**兜底**，先有它，"导入可能失败"就从"项目级风险"降级为"已有一条可用退路"。

### 7.6 导入状态机（`ImportViewModel`）

```text
Idle
  -> (用户选择来源)
AwaitingLogin        // WebView 打开 SSO，等待真人登录完成（含短信/验证码）
  -> (检测到已进入课表页)
Fetching             // 取课表 HTML / JSON
  -> Parsing         // jsoup / JSON -> 中间表示 -> ImportMapper
  -> Preview(可编辑)  // 核对视图：课程数、周次、教室、教师；差异高亮
  -> (用户确认) Committing   // 单事务写入 import_batches + courses + class_sessions
  -> Done
任意阶段 -> Error(AppError)   // 见第 12 章错误分类
```

硬约束：

- **`Preview` 是强制环节**，不可跳过（防止"导入后才发现乱排"这类竞品差评在本地重演，也满足 PRD 第 5.3 节第 2 痛点）。
- **`Committing` 必须单事务**：要么整批成功、要么整批回滚，绝不产生"半截数据"（PRD 第 9.1 节"不产生半截错误数据"）。回滚后 `import_batches.status = ROLLED_BACK` 留痕。
- **重复导入策略**：同一 `semester_id` 再次导入时，把上一次 `IMPORT` 来源的数据标记为待替换（或整体替换），`MANUAL` 来源的数据**保留**（手编是兜底，不能被导入覆盖）。

---

## 8. 上课提醒架构

### 8.1 选型：AlarmManager 精确闹钟为主 + WorkManager 兜底降级

| 方案 | 精确性 | Android 12+ 约束 | 是否适合"早八不迟到" |
|------|--------|------------------|---------------------|
| `AlarmManager.setExactAndAllowWhileIdle` | 精确（Doze 下仍可触发，受 per-app 配额限制） | 需 `SCHEDULE_EXACT_ALARM` 特殊权限（Android 14+ **默认拒绝**，需用户授予） | **适合**（主方案） |
| `AlarmManager.setAlarmClock` | 最精确（系统不调整，可退出低功耗） | 会展现在系统闹钟图标，语义是"闹钟" | 备选（对"闹钟"语义的应用更强，但会占用用户系统闹钟可见位） |
| `AlarmManager.setAndAllowWhileIdle` | 不精确（可被批量延迟） | 无需特殊权限 | 降级方案 |
| `WorkManager` 周期任务 | **不精确**（最小 15 分钟周期 + flex） | 无需特殊权限 | 仅作兜底（例如每日唤醒重排闹钟、以及权限被拒时的降级通知） |

**决策**：主路径 `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)`；权限被拒时**自动降级**为 `setAndAllowWhileIdle` + 前台可见的设置引导。详见 `ADR-004`。

**为什么不用 `USE_EXACT_ALARM`？** 该权限虽自动授予，但**仅限闹钟 / 日历类应用**且受 Play 政策约束；本 App 是"课表提醒"，用 `SCHEDULE_EXACT_ALARM`（用户可授予、适用面更广）更恰当，且本 App 不上架，无需迁就 Play 政策。此点已在 PRD 与本文一致。

### 8.2 组件与调度策略

```text
reminder/
  ReminderScheduler       : 计算"未来 N 天每个课次的提醒时刻"，逐个 setExactAndAllowWhileIdle
  ReminderAlarmReceiver   : 到点发通知（含课程名 + 时间 + 地点）
  ReminderWorker          : 每日兜底：重排闹钟（应对重启后丢失、权限变更、系统清理）
  BootReceiver            : BOOT_COMPLETED / MY_PACKAGE_REPLACED -> 触发 ReminderWorker 重排
  ExactAlarmPermissionReceiver : ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> 权限恢复后重排
  NotificationChannels    : 建 channel（上课提醒 / 每日早课汇总）
```

调度策略（兼顾可靠性与省电）：

1. **滚动窗口**：每次 App 打开、每日兜底任务运行、导入完成、学期切换时，重排**未来 7 天**的课次闹钟。窗口小 → 系统闹钟数量少 → 不怕配额；窗口滚动 → 长期可靠。
2. **请求码唯一**：`requestCode = hash(classSessionId, date)`，避免"新闹钟覆盖旧闹钟"（这是提醒类应用最常见的静默失效原因）。
3. **`PendingIntent` 用 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`**（API 31+ 强制要求 mutability）。
4. **提前量可配**：默认课前 15 分钟（PRD 开放问题 3），存储于 DataStore；变更后立即重排。
5. **每日早课汇总**：可选，例如 07:00 一条汇总通知（"今天 3 节课，第一节 08:10 E教116"），不依赖精确闹钟（用 WorkManager 的粗略时间即可）。
6. **通知内容**：课程名 + 起止时间 + 地点（+ 校区）；线上课显示"线上"；无地点课显示"地点待定"。
7. **点击行为**：深链进 `today` 页；通知带"知道了"与"稍后提醒（10 分钟）"两个动作。

### 8.3 权限与国产 ROM 现实（PRD 第 9.4 / 11 节）

| 权限 | 用途 | 时机 |
|------|------|------|
| `POST_NOTIFICATIONS`（API 33+） | 发通知 | 首次进入提醒设置页时请求；被拒则 App 内明确提示"提醒将无法送达" |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟 | 首次开启提醒时，用 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` 引导到系统"闹钟与提醒"页 |
| `RECEIVE_BOOT_COMPLETED` | 重启后重排 | 静态声明 |

**国产 ROM 后台清理**：`ReminderWorker` 的日更兜底 + `BOOT_COMPLETED` 重排，能覆盖"被杀后重启"类失效；但**"被杀且未重启"窗口内的精确提醒仍可能丢失**。因此：

- 落地"电池优化豁免"引导（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 需谨慎，建议只做**说明 + 跳转系统设置的引导**，不强弹）；
- **PRD 的"≥95% 送达率"目前无实测样本**，登记为 OD-008。架构侧只保证"机制正确 + 可测量"（`remind_scheduled` / `remind_delivered` / `remind_missed` 三个本地事件，PRD 第 12 节），最终数字需真机实测。

---

## 9. 桌面小组件架构

### 9.1 选型：Glance 1.2.0

| 方案 | 写法 | 最低 API | 与 Compose 一致性 | 结论 |
|------|------|----------|-------------------|------|
| **Glance AppWidget 1.2.0** | Compose 风格 API | 低（Glance 1.x 支持到 API 23 量级，本项目 minSdk 26 满足） | 与主 UI 同源，复用主题 token 与数据模型 | **选定** |
| 传统 RemoteViews | XML 布局 + `RemoteViews` 手动拼装 | 全版本 | 需维护第二套布局 / 状态同步，易与主 UI 分裂 | 备选（若 Glance 出现阻塞） |
| Glance 1.3.0-alpha02 | 同上 | 需 AGP ≥9.2.0、compileSdk 37 | — | 不采用（Alpha） |

**为什么 Glance 而不是 RemoteViews？** 小组件要展示的是"今天的课次列表"，其数据形状与主界面**完全一致**（同一 `SessionOccurrence`）。Glance 可直接复用 Compose 的编程模型与主题 token，避免"两套 UI 代码、两种更新时机、两种渲染差异"的长期维护成本；RemoteViews 需另写 XML 且对列表支持差。

**Glance 的已知约束（必须写进实现约束，避免乐观预期）**：

1. Glance 运行在**独立的 `RemoteViews` 渲染路径**，**不支持任意 Compose 组件**（例如复杂动画、任意 `Modifier`）；只能用它提供的基础组件（`Text` / `Image` / `Row` / `Column` / `LazyColumn` 的子集等）。因此小组件的"过渡动画"能力有限，与主界面动画预算（第 10.2 节）不是同一套规则。
2. 小组件的刷新**不能依赖用户的页面交互**，必须由数据变化触发（见 9.2）。

### 9.2 刷新时机（对应 PRD 第 9.8 节"换学期或改课后能刷新"）

```text
WidgetUpdater 在以下时机调用 KebiaoWidget().updateAll(context)：
  1. 课表数据变更（Room 写入成功后，Repository 发事件）
  2. 学期切换（is_current 变更）
  3. 跨零点（复用 TimeProvider.todayFlow 的日期变更信号）
  4. WorkManager 周期性刷新（兜底，最小 15 分钟）
  5. App 冷启动 / 回到前台
```

小组件内容（示例，非指定）：日期 + 周次（"第 3 周 周一"）+ 今日课次列表（时间 + 课程名 + 地点）或"今天没有课"。尺寸建议（PRD 开放问题 2）：先做 **4x2（全天课程）**，1x1（下一节课）作为 P1。

---

## 10. UI 层架构：导航、动画、性能预算、图标库

### 10.1 导航

采用 Navigation Compose 2.10.1（Nav2，稳定线）+ **类型安全路由**（`kotlinx-serialization` 的 `@Serializable` 路由对象）。不采用 Nav3（`navigation3` 1.2.0-rc01 仍为 RC）。

顶层目的地：`today` / `week` / `semester` / `import` / `settings`，底部导航栏承载前三个 + 设置；`import` 与 `semester` 以全屏子流程呈现。

### 10.2 过渡动画（PRD 硬要求，第 9.7 节）

| 场景 | 机制 |
|------|------|
| 页面切换 | `NavHost` 的 `enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition` 配置（如 fade + 轻量 slide，方向与层级一致） |
| 列表内容更新 | `LazyColumn` 的 `Modifier.animateItem()`（新增 / 移除 / 重排均有动画） |
| 状态切换（Loading → Content → Empty） | `AnimatedContent` 按 `UiState` 类型切换 |
| 卡片尺寸变化（展开课程详情） | `Modifier.animateContentSize()` |
| 学期切换 | `AnimatedContent` 包裹课程列表，key 为 `semesterId`，实现"新旧更替"过渡（同时消除"旧数据残留"的观感） |

**动画性能预算（必须有量化口径，不能只说"流畅"）**：

- 目标 **60fps**（≤16.6 ms/帧）；使用 Macrobenchmark 的 `FrameTimingMetric` 作为验收工具（需工具链就绪后执行）。
- 禁止在动画帧内做 IO / DB 查询；动画期间的状态变更走 `Flow` 预取好的不可变数据。
- 列表项模型标注 `@Immutable`，`LazyColumn` 必须传稳定 `key`（`classSessionId` 或 `occurrenceKey`），避免整表重排。
- 仅在有实测依据时才加 `derivedStateOf` / `remember`（Compose 自 Kotlin 2.0.20 起默认开启 Strong Skipping，无谓包裹反而增加噪声）。
- 长列表（周视图 7 列 × 节次的网格）避免嵌套可滚动容器；周视图优先用一次性计算好的格子矩阵渲染，而非嵌套 `LazyColumn`。

### 10.3 主题与配色（P0 红线）

- 基于 **Material 3 主题 token**（`MaterialTheme.colorScheme` / `typography` / `shapes`）构建，颜色**不硬编码**在组件内（本文档唯一出现的硬编码色值为 `#fff` / `#000`）。
- 支持 **Material You 动态取色**（Android 12+，minSdk 26 上以静态回退色板兜底），并支持深色模式（PRD 功能表 P2，但主题层从第一天就按"亮/暗双套 token"设计，避免后期返工）。
- **明确禁止**：紫色→粉色渐变主视觉。主题基准色板走 PRD 第 0 节所选的 Indigo / Slate Blue 纯色体系（PRD 依据 `industries/saas-b2b.md`）。**架构侧只约束"渐变禁用"与"token 化"两条，具体色值由设计阶段与用户确认。**

### 10.4 图标库锁定（P0 规则 1：必须锁定一套 SVG 图标库）

**锁定：Material Symbols（Google Fonts，Apache-2.0）。**

理由与约束：

1. `androidx.compose.material.icons` 的官方 API 页已明确写明："该库不再推荐用于在 Compose 中展示 Material Icons，Material Symbols 是新方向，我们已停止对该库的更新，且它已从最新的 Material 3 release 中移除。"——**而我们锁定的正是 Material3 1.4.0**，即该库已不在传递依赖中。若不锁定替代方案，代码里出现 `Icons.Filled.*` 会**直接编译失败**。
2. 落地方式：从 Google Fonts 的 "Android" 标签页下载所需图标的 **VectorDrawable XML**，放入 `res/drawable/`，在 Compose 中用 `painterResource()` 引用。所需图标数量有限（约 10-15 个：日历、时钟、位置、铃铛、学期、设置、刷新、导入、线上、警告、空状态等），逐个下载不会造成体积问题。
3. **全项目统一，不得混用**：不引入第二套图标库、不使用 emoji 充当功能图标（P0 规则 1）。图标在文档与 UI 规格中以**文字命名**（"日历图标""时钟图标""位置图标""铃铛提醒图标"）。
4. 版本锚定：Material Symbols 为持续维护的图标目录，无版本号概念；落地时以 Google Fonts 当前提供的 XML 为准，不写死"某年某版"。

> 备选（仅当 Material Symbols 的 XML 获取受阻时）：在版本目录中显式引入 `androidx.compose.material:material-icons-core`（需自行指定版本并做存在性核验），但**这是一个已停止更新的库**，不作为首选。

---

## 11. 目录结构与文件组织约束

按 `code-organization.md` 精神 + `context-engineering.md` 的"指针而非全文"原则，采用**单模块 `:app` + package-by-feature**。个人自用 MVP 不拆多模块（避免过早引入 Gradle 模块间依赖管理的复杂度）。

**硬约束**：

1. **单文件 ≤ 300 行**；超过即拆分。
2. **单一职责**：一个文件一个主角（一个 Screen / 一个 ViewModel / 一个纯算法）。
3. **入口只装配**：`MainActivity` / `KebiaoApplication` 只做装配与转发，不含业务逻辑。
4. **按资源/特性分包**，不按"技术类型"全局横切。

```text
app/src/main/java/com/kebiao/app/
├── KebiaoApplication.kt              # Application：装配 AppContainer
├── MainActivity.kt                   # 单 Activity：enableEdgeToEdge + setContent
├── di/
│   └── AppContainer.kt               # 手写依赖容器（第 13 章）
├── core/
│   ├── AppResult.kt                  # Success / Failure 封装
│   ├── AppError.kt                   # 错误分类（第 12 章）
│   ├── LocalEventLog.kt              # 本地事件日志（PRD 第 12 节）
│   └── time/
│       ├── TimeProvider.kt           # 时间源抽象（第 6.7 节）
│       └── SystemTimeProvider.kt
├── data/
│   ├── local/
│   │   ├── KebiaoDatabase.kt
│   │   ├── Migrations.kt
│   │   ├── entity/{SemesterEntity,CourseEntity,ClassSessionEntity,ImportBatchEntity}.kt
│   │   ├── dao/{SemesterDao,CourseDao,ClassSessionDao,ImportBatchDao}.kt
│   │   └── converter/Converters.kt   # LocalDate/LocalTime/Duration <-> 原始类型
│   ├── repository/
│   │   ├── SemesterRepository.kt     # 含 is_current 不变式的唯一写入口
│   │   ├── ScheduleRepository.kt
│   │   └── mapper/{SemesterMapper,CourseMapper,ClassSessionMapper}.kt
│   └── import/
│       ├── ImportSource.kt           # 接口
│       ├── WebViewImportSource.kt
│       ├── JsonFileImportSource.kt
│       ├── ImportMapper.kt           # 三入口共用
│       ├── ImportCommitter.kt        # 单事务提交 + 回滚
│       ├── parser/ScheduleHtmlParser.kt
│       ├── parser/WeekExpressionParser.kt
│       └── session/CookieStore.kt
├── domain/
│   ├── model/{Semester,Course,ClassSession,SessionOccurrence,WeekSet,WeekParity,ClassStatus,SessionSource}.kt
│   └── schedule/
│       ├── ScheduleCalculator.kt
│       ├── SemesterResolver.kt
│       └── SemesterFormatting.kt     # 周次/时间的展示格式化
├── feature/
│   ├── today/{TodayScreen,TodayViewModel,TodayUiState}.kt
│   ├── week/{WeekScreen,WeekViewModel,WeekUiState,WeekGrid.kt}
│   ├── semester/{SemesterScreen,SemesterViewModel,SemesterUiState}.kt
│   ├── import/{ImportScreen,ImportViewModel,ImportUiState,WebViewLoginScreen,CourseEditDialog}.kt
│   └── settings/{SettingsScreen,SettingsViewModel,SettingsUiState}.kt
├── ui/
│   ├── theme/{Color,Type,Shape,Theme}.kt
│   ├── components/{CourseCard,EmptyState,ErrorState,LoadingSkeleton,SectionHeader}.kt
│   ├── icons/KebiaoIcons.kt          # Material Symbols 矢量图标的统一引用点
│   └── navigation/{KebiaoNavHost,Routes}.kt
├── widget/
│   ├── KebiaoWidget.kt               # Glance
│   ├── KebiaoWidgetReceiver.kt
│   └── WidgetUpdater.kt
└── reminder/
    ├── ReminderScheduler.kt
    ├── ReminderAlarmReceiver.kt
    ├── ReminderWorker.kt
    ├── BootReceiver.kt
    ├── ExactAlarmPermissionReceiver.kt
    └── NotificationChannels.kt

app/src/main/res/
├── drawable/                          # Material Symbols VectorDrawable XML
├── values/{strings,themes}.xml
├── xml/{widget_info_*,network_security_config}.xml
└── mipmap-*/                          # 应用图标（自绘，不用 emoji）

app/src/test/java/...                  # 纯 JVM 单测（domain + parser）
app/src/androidTest/java/...           # Instrumentation（需设备）
```

**包名说明**：`com.kebiao.app` 为建议值（个人项目，反向域名不必真实持有）。注意 `applicationId` 一旦安装后再改，需要卸载重装——建议在首次真机安装前定稿。

---

## 12. 错误处理分层

按 `generated-code-failure-modes.md` 的"系统上下文 / 错误分类"要求，**每个失败路径必须显式建模并各有用例**，禁止 `catch` 吞掉。

```kotlin
// 示例，非指定：错误分类形状说明
sealed interface AppError {
    val cause: Throwable?

    // 网络
    data class Network(override val cause: Throwable?) : AppError          // 无网 / 超时 / DNS
    // 鉴权
    data class AuthFailed(val reason: AuthReason) : AppError { /* 账号密码错误 / 验证码失败 / 会话过期 / 需绑定手机 */ }
    // 解析
    data class Parse(val stage: ParseStage, val detail: String) : AppError  // 周次串非法 / 表格结构不匹配 / 字段缺失
    // 权限
    data class Permission(val which: PermissionKind) : AppError            // 通知 / 精确闹钟
    // 存储
    data class Storage(val cause: Throwable?) : AppError                   // DB 读写 / 文件读写
    // 数据
    data class NotFound(val what: String) : AppError                       // 无当前学期 / 无课表
    // 未知
    data class Unknown(override val cause: Throwable?) : AppError
}
```

分层映射规则：

| 层 | 约定 |
|----|------|
| `data` | 把底层异常（`IOException` / `SQLiteException` / `JsonParseException` / `SecurityException`）**翻译**为 `AppError`，**不向上抛原始异常**，不打印后吞掉 |
| `repository` | 返回 `AppResult<T>`；写操作失败时保证事务回滚 |
| `domain` | 纯函数，**不抛异常**用于控制流；`WeekExpressionParser` 返回 `Result<WeekSet>` 承载解析失败（第 6.4 节），失败信息必须可读 |
| `feature` | `ViewModel` 把 `AppError` 映射为 `UiState.Error(AppError, 可重试) `；**每个 `UiState` 分支都要有对应 UI**，不出现"错误了但界面没反应" |

`UiState` 模式（统一形状）：

```kotlin
sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NotImported : TodayUiState                 // 尚未导入 -> 引导去导入
    data object OutsideSemester : TodayUiState             // 假期 / 学期未开始
    data object NoClassToday : TodayUiState                // 今天没课
    data class Content(val items: List<SessionOccurrence>, val week: Int, val date: LocalDate) : TodayUiState
    data class Error(val error: AppError, val retryable: Boolean) : TodayUiState
}
```

> 注意：`NotImported` / `OutsideSemester` / `NoClassToday` 是**三个不同的分支**而不是一个 `Empty`——直接对应 PRD 第 10 节"四种空状态文案须可区分"。

---

## 13. 依赖注入方案

**选型：手写轻量容器 `AppContainer`（不引入 Hilt / Dagger）。** 理由见 `ADR-008`。

```kotlin
// 示例，非指定：容器形状说明
class AppContainer(context: Context) {
    private val db: KebiaoDatabase by lazy { KebiaoDatabase.create(context) }
    val timeProvider: TimeProvider = SystemTimeProvider()
    val semesterRepository: SemesterRepository by lazy { SemesterRepository(db.semesterDao(), db.importBatchDao()) }
    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepository(db.classSessionDao(), db.courseDao()) }
    val importMapper: ImportMapper by lazy { ImportMapper(WeekExpressionParser()) }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(context, timeProvider) }
    val widgetUpdater: WidgetUpdater by lazy { WidgetUpdater(context) }
}
```

ViewModel 通过 `viewModelFactory { initializer { ... } }` 从容器取依赖，`ViewModel` 构造函数只接收接口，便于单测注入假实现。

**取舍说明**：

- 本项目规模是"约 30-40 个类 + 单模块 + 单用户"，Dagger/Hilt 的编译期图复杂度收益不抵其成本。
- 而且：Hilt 的 Gradle 插件在 Dagger 2.59 起**要求 AGP 9 与 Gradle 9.1+**（来源：Dagger release notes），引入后会把构建环境的版本耦合再加深一层——对一个"要让大一学生能顺利构建"的项目，这是负面项。
- 若 P1 后功能膨胀到 80+ 类，再评估 Hilt；`AppContainer` 的接口化设计使迁移成本可控。

---

## 14. 测试策略

### 14.1 分层测试目标

| 层 | 测试类型 | 运行环境 | 本机可跑？ |
|----|----------|----------|-----------|
| `domain`（`ScheduleCalculator` / `WeekExpressionParser` / `SemesterResolver`） | 纯 JVM 单元测试 | JDK | 工具链就绪后可跑；**逻辑可先用 Python 双实现交叉验证** |
| `data`（Room Dao / 迁移 / `ImportMapper`） | JVM 测试（Room `inMemoryDatabaseBuilder` / Robolectric 视需要） | JDK + Android 运行库 | 需工具链 |
| `feature`（UiState 映射） | JVM 单测（假 TimeProvider + 假 Repository） | JDK | 需工具链 |
| UI 动画 / 滚动帧率 | Macrobenchmark | 真机或模拟器 | 需设备 |
| 端到端（导入 → 今日 → 换学期 → 提醒） | Manual + 关键路径自动化 | 真机 | 需设备 |

### 14.2 针对"沉默逻辑错误"的定向加固

`WeekExpressionParser` 与 `ScheduleCalculator` 是**本项目唯一"错了也不会报错、只会算错"的地方**（正是 `generated-code-failure-modes.md` 第 2 节点名的最贵一类），因此：

1. **变异定向**：手工注入最怕的算错方式（奇偶判反、区间端点 off-by-one、`(days/7)+1` 写成 `days/7`、周次夹逼方向写反），要求必须有测试变红。
2. **配对断言**：单双周测试不只断言"单周命中"，还要断言"同一天双周课**不**出现"，防止过滤器恒真。
3. **真实数据夹具**：用第 7.5 节脚本导出的真实课表 JSON 作为夹具，逐门课核对周次集合。
4. **空集禁令**：解析失败必须走显式错误路径（第 6.4 节纪律），测试断言 `Result.isFailure`，防止"课程静默消失"。

### 14.3 导入路径的端到端验证

1. 桌面脚本实跑 → 得到真实 JSON → 断言课程数（PRD：14 门课 + 2 门备注课）与关键课的周次集合。
2. 把该 JSON 喂给 App 的 `JsonFileImportSource` → `ImportMapper` → 断言生成的 `ClassSession` 数量与周次集合。
3. 用 `FixedTimeProvider` 设定到具体日期（如第 3 周周一、第 12 周周三）→ 断言今日课程与教室（对齐 PRD 第 9.1 节的 5 个精确期望）。

### 14.4 本机（工具链缺失期）的可用验证手段

- **Python / Node 双实现交叉验证**：把 `WeekExpressionParser` 的算法用 Python 再实现一份，与本机可运行的脚本一起，对 PRD 第 4.2 节的全部真实周次串跑穷举比对。这不是替代 Kotlin 测试，而是在工具链就绪前**先锁定算法正确性**。
- 一旦 JDK + Android SDK 就位，`./gradlew :app:testDebugUnitTest` 才成为正式回归入口。

---

## 15. 构建、交付与工作量

### 15.1 交付路径（依赖第 2 章结论）

| 路径 | 前提 | 产出 |
|------|------|------|
| P-A（推荐） | 用户安装 Android Studio（路线 A） | `./gradlew assembleDebug` 出 APK；`adb install` 到手机 |
| P-B | 用户装 JDK 17 + cmdline-tools（路线 B）+ 有实体机 | 同上，但无模拟器、无 IDE 调试 |
| P-C | 在具备工具链的机器 / CI 构建 | APK 制品；本机只做代码评审 |
| **P-D（本机现在就能做）** | Python 3.13.14（已在位） | 导入脚本 + 真实 JSON 夹具 + 算法交叉验证 |

### 15.2 工作量估算（对照 `development-costs.md`）

`development-costs.md` 无"Android 原生"条目，最接近的是"桌面端工具类 4-6 周 / 2-3 人月"。本项目为单一开发者 + AI 辅助，且范围为单端单模块，按该表"AI 辅助提速 2-3x"折算：

| 工作项 | 估算（人日） | 备注 |
|--------|--------------|------|
| 环境搭建（路线 A） | 0.5 - 1 | 下载 + 首次构建依赖拉取 |
| 骨架（工程 / 主题 / 导航 / 容器 / Room） | 1 - 2 | |
| 领域层（模型 + 周次解析 + 计算器 + 测试） | 1.5 - 2.5 | 核心，测试占比高 |
| 导入（WebView 中继 + 脚本 + 映射 + 核对视图） | 2 - 4 | **风险最高，估时最宽** |
| 今日课程主界面 + 动画 | 1 - 1.5 | |
| 周视图网格 | 1.5 - 2 | |
| 学期管理与切换 | 0.5 - 1 | |
| 桌面小组件 | 1 - 2 | Glance 约束需试错 |
| 上课提醒 | 1 - 2 | 权限与降级分支多 |
| 手动编辑（P1 兜底） | 1 | |
| 真机联调 + 国产 ROM 提醒验证 | 1 - 2 | 依赖用户设备 |
| **合计** | **约 13 - 24 人日**（AI 辅助下） | 若纯人工，按 `development-costs.md` 的 2-3x 折算约 4-7 周 |

### 15.3 隐性成本（用户必须知情）

| 项 | 成本 | 说明 |
|----|------|------|
| 下载体积 / 磁盘 | 路线 A 约 1.3 - 1.8 GB 下载 / 4 - 5 GB 磁盘（含模拟器 3 - 4 GB / 8 - 10 GB） | 第 2.3 节 |
| 国内网络 | Gradle / Google Maven / SDK 源可能慢 | 建议配置镜像（阿里云 / 腾讯云 Maven 镜像、`GRADLE_DISTRIBUTION_URL` 指向镜像） |
| 云服务 | **0 元** | 无后端、无服务器、无域名、无 SSL、无应用商店开发者账号 |
| 调试设备 | 0 元（用户自有 Android 手机） | 若无手机需模拟器，占用磁盘较大 |

> 相比 `development-costs.md` 列的"服务器 ¥100-500/月、域名 ¥100-300/年、小程序认证 ¥300/年、App Store $99/年"等常规隐性成本，本项目**全部为零**——这是"个人自用、无后端"定位带来的直接财务优势。

---

## 16. 端到端验证步骤（完成定义）

> 依 `spec-as-contract.md` 第 4 节：规格以"怎么证明它对"收尾。**注意：以下步骤 1 需要工具链（当前不具备），步骤 0 与步骤 5 的一部分本机今天即可执行。**

**步骤 0（今天可执行）— 算法与数据先行验证**

```bash
# 在本机（Python 3.13.14 已在位）
# 1) 运行周次解析器交叉验证：对 PRD 4.2 节全部真实串比对 Python 与规范期望
python tools/verify_week_parser.py

# 2) 运行导入脚本（需用户交互完成 SSO 登录 + 短信码），产出真实课表 JSON
python tools/export_schedule.py --out out/schedule-2026-2027-1.json

# 3) 断言课程数与关键课的周次集合
python tools/assert_fixture.py out/schedule-2026-2027-1.json
```

期望：解析器全绿；夹具断言"14 门课 + 2 门备注课"；大学物理B(1) 出现两条 session（周次 {2} 与 {3..16}）。

**步骤 1（需工具链）— 构建与安装**

```bash
export JAVA_HOME="<JDK17 安装路径>"
export ANDROID_HOME="%LOCALAPPDATA%\Android\Sdk"
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**步骤 2（需设备）— 核心成功流**

1. 冷启动 → 3 秒内看到"今天课程"（或明确的空状态）。
2. 导入：App 内 WebView 登录 → 核对视图 → 确认 → 今日课程出现今日课次。
3. 用真实课表核对 5 个精确期望（第 6.8 节表格）。

**步骤 3（需设备）— 关键错误流（至少一条）**

1. SSO 密码错误 → 明确提示"账号或密码错误" + 可重试，**DB 无半截数据**。
2. 导入中途断开网络 → 提示网络错误 + 重试，**已入库数据未被破坏**。

**步骤 4（需设备）— 换学期刷新（PRD 关键验收）**

1. 新增并切换到第二个学期 → 今日课程在 3 秒内刷新为新学期数据，**无上学期残留**。
2. 切回原学期 → 今日课程、周视图均按原学期渲染。

**步骤 5（需设备）— 提醒与小组件**

1. 设定某课开始前 15 分钟 → 通知按时到达（含课程名 + 时间 + 地点）。
2. 重启手机 → 提醒仍按计划触发（`BOOT_COMPLETED` 重排生效）。
3. 桌面小组件显示今日课次；切换学期后小组件内容更新。

**完成定义**：步骤 0 全绿 + 步骤 1 构建成功 + 步骤 2/4 通过 + 步骤 3 至少一条错误流通过 + 步骤 5 在本机实测达标（提醒送达率见 OD-008）。

---

## 17. 明确不做（架构层 Out-of-Scope）

产品级 Out-of-Scope 以 `docs/PRD.md` 第 7 节为准。**架构层额外明确不做**（防止镀金）：

| 不做项 | 原因 | 何时再考虑 |
|--------|------|-----------|
| 多 Gradle 模块拆分 | 单模块 30-40 个类，拆模块的收益为负 | 代码量超过约 150 个类，或需要复用为库 |
| Hilt / Dagger | 规模不需要；且其 Gradle 插件会加深 AGP/Gradle 版本耦合 | 依赖关系复杂到手工容器难以维护时 |
| 仓储层之外的额外抽象（UseCase 层、Mapper 工厂、Repository 接口 + 实现双层） | 个人自用规模下属过度设计 | 出现第二个数据源或需要替换实现时 |
| 网络层通用框架（Retrofit + 拦截器栈 + 统一错误映射） | 本项目的"网络"只有一个低频导入动作 | 若引入第二个网络数据源时 |
| 后端 / 云同步 / 多人 | PRD 已排除 | 换机迁移数据、或分享给同学时（届时最小实现为"导出 / 导入 JSON"） |
| OCR 拍照导入 | PRD 已排除（识别错误风险高，竞品差评前车之鉴） | 不计划 |
| CI / CD 流水线 | 单开发者、单设备、无仓库托管前提 | 若代码托管到 GitHub 并需要自动出包 |
| 数据库加密（如 SQLCipher） | 需评估收益与成本；SSO 凭据走 Keystore 已覆盖主要风险面 | OD-007 待裁决 |
| 多语言 / 国际化 | PRD 已排除 | 不计划 |

---

## 18. 版本锚定来源清单（可复查）

| 结论 | 来源 |
|------|------|
| AGP 9.3.x 兼容性（Gradle 9.5.0 / JDK 17 / Build Tools 36.0.0 / max API 37） | `developer.android.com/build/releases/agp-9-3-0-release-notes` |
| AGP 9.4.0 要求 Gradle 9.6.0 | `developer.android.com/build/releases/gradle-plugin` |
| Kotlin 2.4.20（2026-09-07）与 2.4 支持窗口 | `kotlinlang.org/docs/releases.html` |
| KSP 2.3.11 与 Kotlin 2.4.20 搭配；KSP1 与 Kotlin ≥2.3 / AGP ≥9 不兼容 | KotlinPoet changelog；`github.com/google/ksp` README |
| Compose BOM 2026.09.00；Compose 1.12.1；Material3 1.4.0；Compose 1.12 起要求 compileSdk 37 + AGP 9 | `developer.android.com/develop/ui/compose/bom`；`releases/compose-material3`；jetc.dev BOM 记录 |
| Room 2.8.5 稳定；Room 3.0 为 Alpha | `developer.android.com/jetpack/androidx/releases/room` |
| WorkManager 2.11.2（minSdk 23 / compileSdk ≥33） | `developer.android.com/jetpack/androidx/releases/work` |
| Glance 1.2.0 稳定（2026-08-26） | `developer.android.com/jetpack/androidx/releases/glance` |
| Navigation Compose 2.10.1 / Nav3 1.2.0-rc01；Lifecycle 2.11.0；DataStore 1.2.1；Paging 3.5.1 | `developer.android.com/kotlin/multiplatform` 支持矩阵 |
| Core 1.19.0；core-ktx 已并入 core | `developer.android.com/jetpack/androidx/releases/core` |
| androidx.hilt 1.4.0（要求 KGP ≥2.2.0）；Dagger 2.59 起 Hilt Gradle 插件要求 AGP 9 | `developer.android.com/jetpack/androidx/releases/hilt`；Dagger release notes |
| SCHEDULE_EXACT_ALARM 在 Android 14+ 默认拒绝；`canScheduleExactAlarms()`；USE_EXACT_ALARM 仅限闹钟/日历类 | `developer.android.com/about/versions/14/changes/schedule-exact-alarms`；`developer.android.com/training/scheduling` |
| `androidx.compose.material.icons` 不再推荐，已从 Material3 最新 release 移除，改用 Material Symbols | `developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary` |
| Play target API：2026-08-31 起新应用/更新须 API 36+ | `developer.android.com/google/play/requirements/target-sdk` |
| OkHttp 5.5.0 / jsoup 1.23.2 / Ktor 3.5.2 | Maven Central；`jsoup.org`；`ktor.io` |
| SDJU 统一身份认证为 CAS 系（`/authserver/login`）；2026 级新生"首次登录需绑定手机号"、涉及短信验证码 | `authserver.sdju.edu.cn`；`www.sdju.edu.cn/xywl/`；`info.sdju.edu.cn/2026/0810/c6623a154020/page.htm` |
| Apereo CAS 登录表单需 `execution` 等隐藏域、`service` 参数与 TGC/TGT 语义 | Apereo CAS Protocol 3.0 Specification |
