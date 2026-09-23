# 课刻

给上海电机学院在读学生用的一款 Android 课表 App：从学校教务系统直接导入课表，落在本地数据库里，打开就看今天上什么课、几点、在哪。无广告、无账号、无服务器。

---

## 当前适配范围（请先读这一节）

**本项目目前只适配上海电机学院教务系统。** 具体就是这两处：

- 教务门户：`jwgl.sdju.edu.cn`
- 统一身份认证（SSO）：`authserver.sdju.edu.cn`

登录入口写死在 `app/src/main/java/com/kebiao/app/feature/import/ImportWebView.kt` 的 `LOGIN_URL` 常量里，课表页面的表格结构与单元格格式也是按这一套页面写的。

**如果你不在上海电机学院，clone 下来直接编译是不能用的**，需要自己适配本校教务系统。改动集中在导入链路，不需要动架构：

在 `app/src/main/java/com/kebiao/app/feature/import/` 下：

| 文件 | 适配时改什么 |
|------|--------------|
| `ImportWebView.kt` | 教务系统入口地址（`LOGIN_URL`）与轻量级"这一页像不像课表"探针脚本 |
| `ImportExtractionScript.kt` | 课表表格到内部 schema JSON 的抽取脚本（换校主要改这里） |
| `ImportTableScan.kt` | 跨 iframe 扫描、选哪张表是课表、星期列怎么映射 |
| `ImportCellParser.kt` | 单元格解析：以课程号切块、块内按（周次）（节次 时间）窗口扫描 |
| `ImportWeekMerge.kt` | 同一时间槽被拆成多个周次段时合并成并集 |
| `ImportEntryClick.kt` | 自动点开菜单进课表的点击脚本（里面的中文关键词：我的课表 / 菜单 / 选课） |
| `ImportAutoNav.kt` | 自动导航驱动循环（与入口点击配套） |
| `LoginWebViewSupport.kt` | `isPortalUrl()`：判断当前地址是否已进入教务门户（未到门户就绝不开点） |
| `ImportStructureDump.kt` | 页面结构转储，取证用 |
| `ImportDiagnosticsText.kt` | 导入失败时的诊断文案 |

在 `app/src/main/java/com/kebiao/app/data/import/parser/` 下：

| 文件 | 适配时改什么 |
|------|--------------|
| `ScheduleHtmlParser.kt` | HTML 解析路径的表头关键字与字段正则，全部集中在 `FieldPatterns` |
| `WeekExpressionParser.kt` | 周次表达式语法（如 `2-16 双周`、`3-15 单周`） |

**抽出来的 JSON schema 不要改**，它是抽取脚本与本地入库之间的契约（`data/import/ScheduleJsonCodec.kt`）。

---

## 功能

- **今天视图**（启动默认页）：每张课程卡含课程名、时间、地点三要素；顶栏可一键切到「明天」；区分"今天没课 / 假期 / 学期未开始 / 还没导入"四种空状态
- **周视图**：7 列（周一至周日）× 节次网格；单双周与跨周换教室按周正确渲染；线上课与无固定时间地点的课进「线上 / 不排座」独立分区，不硬塞网格
- **教务系统导入**：WebView 中继登录（真人在系统 WebView 里完成 SSO，含短信验证码），页面内 JS 抽取课表，导入后先给核对视图确认课程数 / 周次 / 教室，再单事务提交
- **多学期管理**：新增、命名、切换学期；切换后今天视图按新学期数据刷新，不残留旧学期课程
- **上课提醒**：AlarmManager 精确闹钟为主、WorkManager 兜底降级；提前量默认 15 分钟，可选 5 / 10 / 15 / 20 / 30 分钟；通知内容含课程名 + 时间 + 教室；开机、改时间、改时区、精确闹钟权限变化后自动重排
- **桌面小组件**：4×2 与 4×4（单 provider 双向可缩放），显示当天课程；换学期或改课后刷新
- **手动编辑课程**：导入失败或教务改版时的兜底，可增 / 改 / 删单条课程（含周次规则），与导入课程并存
- **主题与动效**：Material 3，浅色 / 深色双套 token；页面切换、列表更新、学期切换走统一动效契约
- **本地事件日志**：导入成功 / 失败、学期切换、提醒送达等自检事件写在本机文件里，不联网上报

---

## 技术栈与版本

语言 Kotlin，UI 全量 Jetpack Compose，单模块 `:app`，package-by-feature。

版本号全部取自 `gradle/libs.versions.toml` 与 `app/build.gradle.kts` 的实际内容：

| 项 | 版本 | 来源 |
|----|------|------|
| Kotlin | 2.4.20 | `libs.versions.toml` -> `kotlin` |
| AGP | 9.3.3 | `libs.versions.toml` -> `agp` |
| KSP | 2.3.11 | `libs.versions.toml` -> `ksp` |
| Gradle（Wrapper） | 9.5.0 | `gradle/wrapper/gradle-wrapper.properties` |
| JDK | 17 | `app/build.gradle.kts` -> `JvmTarget.JVM_17` |
| Compose BOM | 2026.09.00 | `libs.versions.toml` -> `composeBom` |
| Material3 | 1.4.0（由 BOM 决定） | `libs.versions.toml` 注释 |
| Room | 2.8.5 | `libs.versions.toml` -> `room` |
| DataStore Preferences | 1.2.1 | `libs.versions.toml` -> `datastore` |
| Navigation Compose | 2.10.1 | `libs.versions.toml` -> `navigationCompose` |
| NavigationEvent | 1.1.2 | `libs.versions.toml` -> `navigationEvent` |
| Lifecycle | 2.11.0 | `libs.versions.toml` -> `lifecycle` |
| Activity Compose | 1.13.0 | `libs.versions.toml` -> `activityCompose` |
| AndroidX Core | 1.19.0 | `libs.versions.toml` -> `androidxCore` |
| WorkManager | 2.11.2 | `libs.versions.toml` -> `workManager` |
| Glance（小组件） | 1.2.0 | `libs.versions.toml` -> `glance` |
| OkHttp | 5.5.0 | `libs.versions.toml` -> `okhttp` |
| jsoup | 1.23.2 | `libs.versions.toml` -> `jsoup` |
| kotlinx-serialization-json | 1.11.0 | `libs.versions.toml` -> `kotlinxSerialization` |

SDK 等级（`app/build.gradle.kts` 实测）：

- `minSdk = 26`（Android 8.0，为使 `java.time` 原生可用）
- `targetSdk = 36`
- `compileSdk = 37`

两点版本纪律，改之前先看：

- **不要加回 `org.jetbrains.kotlin.android` 插件**。AGP 9.0 起内置 Kotlin，再显式应用会被 AGP 直接拒绝。Compose 编译器插件仍需显式应用。
- **不要写 `composeOptions.kotlinCompilerExtensionVersion`**，Kotlin 2.0 起已废弃。

---

## 构建

前置：JDK 17、Android SDK（platform android-37、build-tools 36.0.0）。

构建需要一份根目录 `local.properties` 指向本机 Android SDK（这个文件是本机环境配置，不进版本库）：

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

然后：

```bash
./gradlew :app:assembleDebug          # 出 debug APK
./gradlew :app:testDebugUnitTest      # 跑纯 JVM 单测
```

用 Android Studio 打开也可以，但本项目以 Gradle Wrapper 命令行为准：AGP 9.3.3 与当前 Android Studio 稳定版存在代差，IDE 可能提示版本超出支持范围，这不影响命令行构建结果。

---

## 目录结构

单模块 `:app`，按特性分包（`app/src/main/java/com/kebiao/app/` 下）：

```text
core/        AppResult / AppError / 本地事件日志 / 时间源抽象
data/
  local/     Room：KebiaoDatabase + Entity + Dao + 迁移
  repository/  SemesterRepository / ScheduleRepository / 映射层
  import/    ImportSource 接口 -> WebViewImportSource / JsonFileImportSource
             parser/  ScheduleHtmlParser / WeekExpressionParser
             session/ CookieStore
domain/      纯 Kotlin，无 Android 依赖：模型 + 排课求值 + 学期解析
feature/
  today/     今天视图
  week/      周视图
  semester/  多学期管理
  import/    教务导入（WebView 中继 + 抽取脚本 + 核对 + 手动编辑）
  settings/  设置
ui/          theme / components / icons / navigation
widget/      Glance 桌面小组件
reminder/    提醒调度：AlarmManager + WorkManager + 各类广播接收器
di/          手写依赖容器 AppContainer
```

依赖方向只向下：`feature` -> `domain` -> `data`；`domain` 不得 import 任何 `android.*` / `androidx.*`。

---

## 数据来源与隐私

课表数据只有一个来源：用户登录自己的教务系统，App 在系统 WebView 里读取课表页面并解析。除此之外没有任何数据进出。

- **不上传任何数据**。没有后端服务器、没有账号体系、没有第三方 SDK、没有埋点上报。自检事件写在 App 私有目录的文件里。
- **不读取密码字段**。登录由真人在系统 WebView 内完成，App 不查询、不注入、不缓存任何输入控件内容。WebView 侧不注册 `@JavascriptInterface`，抽取结果只通过 `evaluateJavascript` 的回调单向取回。
- **不建立回传通道**。`allowFileAccess` 与 `allowContentAccess` 全关，`mixedContentMode` 为 `NEVER_ALLOW`；`res/xml/network_security_config.xml` 全局 `cleartextTrafficPermitted=false`，不放宽证书校验。
- **课表只存本地**。落在 Room 数据库（`KebiaoDatabase`），离线可看。
- **不需要 App 自己的账号**。账号就是学号 + 统一身份认证密码，只在系统 WebView 里输入。
- **课程信息不出本应用**：提醒与小组件的广播接收器 `exported=false`（桌面小组件 provider 除外，它必须被系统 launcher 唤起）。

---

## 已知限制

- 只适配上海电机学院，见开头一节。
- 抽取脚本是按当前教务页面形态写的。学校改版页面结构后，导入会失败并给出诊断信息，此时改的是脚本常量，不是架构。
- 提醒可靠性受国产 ROM 后台策略影响。项目做了精确闹钟 + WorkManager 兜底 + 开机/改时间重排，但需要精确闹钟权限；被拒绝时会降级，不能保证 100% 准点。
- 自用项目，未上架，未做混淆与签名配置。

---

## 许可证

MIT License。见仓库根目录下的 `LICENSE` 文件。

---

## 文档

完整设计文档在 `docs/` 下：

- `docs/PRD.md` 产品需求（问题定义、竞品分析、RICE 排序、验收标准）
- `docs/Spec.md` 规格（MVP 范围、版本锁定表、技术架构）
- `docs/ARCHITECTURE.md` 架构（分层、数据模型、导入与提醒设计、目录约束）
- `docs/UIUX.md` 与 `docs/design-tokens.json` 设计规范与设计令牌
- `docs/BUILD-ENV.md` 本机构建环境记录与踩坑
- `docs/decisions/ADR-001..008` 架构决策记录，`docs/decisions/OPEN-DECISIONS.md` 未决问题
