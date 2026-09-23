# 悬而未决登记册（Open Decisions Register）

> 规范来源：`references/01-standards/open-decisions-register.md`（第三持久记忆通道）。
> 纪律：**只追加、不重写、不删除**；解决时**就地翻 `RESOLVED` 并补 `Resolution`**（决定了什么 + 为什么 + 日期）；`Resolves when` 必须是**可判定的具体条件**，禁止写"以后再说"。
> 类别 slug（固定三类，不新增）：`waiting-on-external-condition` / `design-decision-to-evaluate` / `existing-design-boundary`
> 上游文档：`docs/ARCHITECTURE.md` v1.0、`docs/PRD.md` v1.0

**当前汇总：1 RESOLVED + 10 OPEN**

> 追加记录（2026-09-20，team-lead）：本节以下条目按"只追加"纪律增补，未改动任何既有条目的原始判断。
> 新增 OD-010（AGP 9 构建验证）、OD-011（动画配方 ⑨ 接受缺口）；OD-005 与 OD-009 各追加一条进度注记。

---

## OPEN — waiting-on-external-condition — OD-001 教务导入主路径在真机上的可行性未验证

- **Date**: 2026-09-20
- **Source**: ADR-003；`docs/ARCHITECTURE.md` §7
- **Open Item**: 已定"WebView 中继登录 + JS 抽取"为主路径，但该路径**尚未在真机 + 真实校园环境跑通一次**。未验证点：WebView 能否正常访问 `authserver.sdju.edu.cn`；登录后能否同源读取课表 iframe；教务系统是否对 WebView UA 有反制。
- **Related Constraints**: 手机绑定 + 短信验证码（OD-003）；iframe 跨域可能性（OD-005）；本机无 Android 工具链（ARCHITECTURE §2），当前无法验证。
- **Current Leaning**: 主路径按 ADR-003 实现；同时**优先实现桌面脚本 + JSON 文件导入**（本机可实跑），确保兜底先可用。
- **Blocked By**: 需要一台 Android 设备 + 可构建的工程（工具链安装）。
- **Resolves When**: 真机上 WebView 成功登录并拿到课表 HTML/JSON，或确认 WebView 路径不可行从而正式切换主路径为文件导入。
- **Status**: OPEN

---

## OPEN — waiting-on-external-condition — OD-002 手机流量下教务系统 / SSO 是否可达、是否需校园网或 VPN

- **Date**: 2026-09-20
- **Source**: `docs/ARCHITECTURE.md` §2.5 实测
- **Open Item**: 本机（当前网络）实测 `jwgl.sdju.edu.cn` 返回 HTTP 302、`authserver.sdju.edu.cn` 返回 HTTP 200，**说明从本机网络公网可达**。但**未验证**：(a) 手机 4G/5G 流量下是否同样可达；(b) 课表的 iframe 数据接口是否要求校内 IP；(c) 是否需要学校 VPN（新生信提到 VPN 已对接统一身份认证）。
- **Related Constraints**: 学校提供校园网 `i-dianji` / `i-dianji.1x` 与 VPN；若必须校内或 VPN，则"App 内导入"的使用场景受限（用户需在校园网内或先连 VPN）。
- **Current Leaning**: 假定公网可达（实测支持），在导入失败的错误分支中显式区分"网络不可达"与"鉴权失败"，并在文案中提示"可能需要连接校园网或 VPN"。
- **Blocked By**: 需要用户在手机流量 / 校园网两种网络下各试一次。
- **Resolves When**: 用户在手机 4G/5G 下成功打开 `authserver.sdju.edu.cn/authserver/login` 并成功跳转课表页（或确认必须 VPN，从而在引导文案与错误分类中固化该前提）。
- **Status**: OPEN

---

## OPEN — waiting-on-external-condition — OD-003 SSO 登录是否需要短信验证码 / 手机绑定（影响主路径可行性）

- **Date**: 2026-09-20
- **Source**: `docs/ARCHITECTURE.md` §7.1；ADR-003
- **Open Item**: 2026 级新生信原文写明"首次登录时需绑定手机号""获取短信验证码时，请勿连续点击'发送验证码'按钮"。**未确认**：这是"仅首次绑定"环节，还是"每次登录/陌生设备登录"都要短信验证码。若后者，对 App 内导入的影响是：真人必须在 WebView 里能收到并填入验证码（WebView 方案天然支持，但 App 内静默直连方案彻底不可行）。
- **Related Constraints**: P0 隐私约束（PRD §11 "不采集隐私"）→ **不得**申请 SMS 读取权限来拦截验证码；ADR-003 已因此排除"App 内静默直连"主路径。
- **Current Leaning**: 主路径按"真人在 WebView 中完成登录（含短信码）"设计，天然兼容两种情形；不依赖短信必不出现或必出现。
- **Blocked By**: 需要用户说明其登录实际流程，或在真机上试跑一次导入。
- **Resolves When**: 确认登录是否每次需短信码；若需要，确认 WebView 方案在该情形下可完成（预期可完成）。
- **Status**: OPEN

---

## OPEN — design-decision-to-evaluate — OD-004 学期第 1 周周一（`startDate`）的获取方式

- **Date**: 2026-09-20
- **Source**: ADR-001；`docs/ARCHITECTURE.md` §6.6
- **Open Item**: `Semester.startDate`（第 1 周周一）是"今天是第几周"的唯一输入，取错会导致整个学期偏移一周（PRD §5.3 记载竞品 WakeUp 曾识别错开学周）。**未定**获取方式三选一：(a) 从教务系统页面解析（需确认页面是否提供学期起始日 / 当前第几周）；(b) 由"导入当日 = 第 N 周"反推 `startDate = 导入日 - (N-1)*7` 再归一化到周一；(c) 首次导入时让用户校准（给出默认值 + 可修改）。
- **Related Constraints**: PRD §4 已给出本学期真实值 `2026-09-14`（经校验为周一）——可作首个学期的已知输入与测试金标准，但不能泛化到未来学期。
- **Current Leaning**: (b) + (c) 组合兜底：优先从页面解析；解析不到则用导入日反推；两者都不可信时让用户一次校准并持久化。
- **Blocked By**: 需确认教务课表页/首页是否暴露"学期起始日期"或"当前第 N 周"文本（依赖 OD-005 的页面结构确认）。
- **Resolves When**: 拿到课表页原始 HTML，确认是否存在学期起始日 / 当前周次字段；据此定稿 `SemesterResolver` 的校准流程。
- **Status**: OPEN

---

## OPEN — design-decision-to-evaluate — OD-005 课表 iframe 的数据接口形态（URL / POST 参数 / 学期 id 枚举）

- **Date**: 2026-09-20
- **Source**: ADR-003；`docs/ARCHITECTURE.md` §7.4
- **Open Item**: 课表在一个 iframe 内，**未确认**：(a) iframe 是否与主页面同源（决定能否 `contentDocument` 直读，还是必须把 iframe 的 `src` 提为 WebView 主文档）；(b) 课表是服务端渲染的 HTML 表格，还是通过一次 AJAX/POST 拉 JSON/HTML（若是后者，注入脚本需复现该请求参数）；(c) 多学期切换是如何传参的（学期 id / 学年学期字符串），这决定"切换学期"时能否按学期枚举导入。
- **Related Constraints**: 用户已实测可抓到 iframe 内的完整表格数据，说明数据可达；不确定的是"如何用脚本稳定拿到"。
- **Current Leaning**: 先用 `evaluateJavascript` 尝试同源读取；若跨域，则改为在 WebView 内直接打开 iframe 的 `src` 使其成为主文档后再抽取（无需处理跨域）。
- **Blocked By**: 需要课表页的 DOM / Network 抓包记录（用户可在电脑浏览器 DevTools 中导出 HAR 或保存 HTML）。
- **Resolves When**: 拿到 iframe 的 `src` 与请求形态（HAR 或 HTML），据此定稿 `ScheduleHtmlParser` 与抽取脚本。
- **Status**: OPEN
- **追加注记（2026-09-20，team-lead）**: 前端交付的 `feature/import/ImportWebView.kt` 中 `EXTRACTION_SCRIPT` 已按"先同源 `contentDocument` 直读、跨域则把 iframe `src` 提为主文档"的 Current Leaning 实现（对应 ADR-003）。但该脚本**依赖真实教务页面的 DOM 结构，从未在真站验证过成功路径**。经总监裁决（advisory 5），处置为：**真机跑通后把命中的选择器固化进脚本，并增加一版 DOM 变更告警**（页面结构变化时显式报错而非静默返回空集，与 AC-04 同口径）。本条在真机导入成功前不关闭。

---

## OPEN — existing-design-boundary — OD-006 minSdk 26 的取舍（若用户设备低于 Android 8.0）

- **Date**: 2026-09-20
- **Source**: ADR-006
- **Open Item**: 已定 minSdk 26（Android 8.0），理由是 `java.time` 原生可用可省去 desugaring。但**尚未确认用户实际设备 API 级别**。若低于 26，需下调至 24 并开启 core library desugaring，带来额外构建配置与一类潜在行为差异。
- **Related Constraints**: PRD §11 已写"Android 8.0 及以上"；本项目单用户自用，覆盖面不是选择依据。
- **Current Leaning**: 保持 minSdk 26；若确认设备 < 26，则改 24 + desugaring（改动局部，只影响 Gradle 配置与少量 API 使用）。
- **Blocked By**: 需要用户告知手机型号 / Android 版本（或真机 `adb shell getprop ro.build.version.sdk`）。
- **Resolves When**: 确认用户设备 API ≥ 26（则本条就地关闭为"维持 minSdk 26"）。
- **Status**: OPEN

---

## OPEN — design-decision-to-evaluate — OD-007 SSO 凭据与会话的本地存储强度

- **Date**: 2026-09-20
- **Source**: ADR-003；PRD §11「安全」
- **Open Item**: PRD 要求"SSO 密码 / 会话凭证仅本地加密存储（Android Keystore），不上传第三方"。**未定**：(a) 是否提供"记住密码 / 免重复登录"（若提供，凭据必须走 Keystore 加密，且默认关闭）；(b) WebView 的 Cookie / 会话是否需要在进程重启后持久化（持久化会延长会话有效期，提升便利但也扩大暴露面）；(c) 是否需要 Room + SQLCipher 加密整个数据库（课表本身敏感度低，主要风险面是凭据）。
- **Related Constraints**: P0 隐私红线（不上传、不采集）；ADR-003 已强制"App 不读取密码字段"。
- **Current Leaning**: 最小暴露原则——**默认不缓存密码**；会话仅在 WebView 生命周期内有效（登录一次导入一次）；数据库**不加密**（课表数据无敏感身份信息，且无后端、无外传路径）。凭据缓存作为可选开关，仅 Keystore 加密实现。
- **Blocked By**: 需要用户对"每次导入都要重新登录"的接受度反馈（若不可接受，则必须实现凭据缓存）。
- **Resolves When**: 用户确认是否接受"每次导入重新登录"；若接受，本条就地关闭为"不实现凭据缓存"。
- **Status**: OPEN

---

## OPEN — existing-design-boundary — OD-008 国产 ROM 后台清理下的提醒送达率无实测样本

- **Date**: 2026-09-20
- **Source**: ADR-004；PRD §9.4 / §13
- **Open Item**: PRD 设定"提醒准点送达率 ≥ 95%"，但**本机无 Android 设备、无工具链**（ARCHITECTURE §2），当前**无任何实测样本**。国产 ROM（MIUI / ColorOS / HarmonyOS / MagicOS）的后台清理策略各有差异，`setExactAndAllowWhileIdle` + 自愈机制能覆盖"重启后"与"权限变更"类失效，但**"被杀且未重启"窗口内的精确提醒仍可能丢失**。
- **Related Constraints**: 不申请 `SCHEDULE_EXACT_ALARM` 之外的特殊权限；不强弹"电池优化豁免"（只做说明 + 跳转引导）。
- **Current Leaning**: 机制正确 + 可测量（`remind_scheduled` / `remind_delivered` / `remind_missed` 三个本地事件），把"送达率"作为**真机实测指标**而非当前可承诺值；在 PRD/验收中标注"待实测"。
- **Blocked By**: 需要用户真机 + 一个学期的实际使用样本。
- **Resolves When**: 真机运行一个学期后，`remind_delivered / remind_scheduled` ≥ 95%（或低于则需追加前台服务 / 更强引导等增强手段）。
- **Status**: OPEN

---

## RESOLVED — waiting-on-external-condition — OD-009 本机 Android 构建工具链缺失（阻塞一切构建/测试断言）

- **Date**: 2026-09-20
- **Source**: `docs/ARCHITECTURE.md` §2（实机探测结论）
- **Open Item**: 本机（Windows，用户名 `28766`）经逐项实测确认：**JDK / Android SDK / Android Studio / Gradle / adb / sdkmanager / kotlinc 全部不存在**；`%LOCALAPPDATA%\Android`、`~/.gradle`、`~/.android` 均不存在（证明从未构建过 Android 工程）。因此**本机无法产出 APK、无法运行单元测试、无法验证 UI/动画/提醒**。
- **Related Constraints**: AGP 9.3 要求 JDK 17；compileSdk 37 需 `platforms;android-37`；Build Tools 需 36.0.0。安装路线与体积见 ARCHITECTURE §2.3（路线 A：Android Studio，约 1.3-1.8 GB 下载 / 4-5 GB 磁盘；路线 B：JDK 17 + cmdline-tools，约 600-800 MB 下载 / 1.3-1.8 GB 磁盘）。
- **Current Leaning**: 推荐路线 A（Android Studio 一站式，含 JBR JDK + SDK Manager，对非专业开发用户最省事）；**在工具链就绪前，所有"构建通过 / 测试通过"的断言一律不得作出**；同时优先交付本机可实跑的桌面导入脚本与算法交叉验证（ARCHITECTURE §14.4）。
- **Blocked By**: 需要用户安装 Android Studio（或 JDK 17 + Android command-line tools）并接受 SDK 许可（`sdkmanager --licenses`）。
- **Resolves When**: 本机 `./gradlew assembleDebug` 成功产出 APK（此时本条就地关闭，并补 `Resolution` 记录实际使用的 JDK / AGP / Gradle 版本）。
- **Status**: **RESOLVED (2026-09-20)**
- **Resolution（2026-09-20，team-lead 实测复核）**：判定条件已满足，本条关闭。
  - **实际使用版本**（即主锁定组合，未启用回退）：Temurin **JDK 17.0.20.1** / **Gradle 9.5.0** / **AGP 9.3.3** / **Kotlin 2.4.20** / **KSP 2.3.11** / **Compose BOM 2026.09.00** / **compileSdk 37**（平台目录 `platforms/android-37.0`）/ **targetSdk 36** / **minSdk 26** / Build Tools **36.0.0** / **Room 2.8.5** / **Glance 1.2.0**。
  - **构建证据**：`gradlew :app:clean :app:assembleDebug :app:testDebugUnitTest --rerun-tasks --no-build-cache --console=plain` → `BUILD SUCCESSFUL in 3m 13s` / `46 actionable tasks: 46 executed`（全量重编译，零 UP-TO-DATE、零 FROM-CACHE）。
  - **APK 产物**：`app/build/outputs/apk/debug/app-debug.apk`，**16,537,081 字节（15.77 MB）**，**SHA-256 `EE537D0C0156C03617D59ADA7BB82018AE0A0BECF3C624A10F18CB45094148A1`**（由 team-lead 独立 `sha256sum` 复核，与执行者自报逐字符一致）。
  - **包清单实测**（`aapt2 dump badging`，team-lead 独立执行）：`package: com.kebiao.app`、`versionCode='1'`、`versionName='1.0'`、`compileSdkVersion='37'`、`minSdkVersion:'26'`、`targetSdkVersion:'36'`、**`application-label:'课刻'`**、18 个 dex、native-code 覆盖 `arm64-v8a` / `armeabi-v7a` / `x86` / `x86_64`。
  - **单测证据**：4 suite / **38 用例 / 0 失败 / 0 错误 / 0 跳过**（JUnit XML 实测）。
  - **权限清单**（复核隐私红线）：仅 `INTERNET`、`ACCESS_NETWORK_STATE`、`POST_NOTIFICATIONS`、`SCHEDULE_EXACT_ALARM`、`RECEIVE_BOOT_COMPLETED`、`WAKE_LOCK`、`FOREGROUND_SERVICE`。**无短信读取、无定位**——P0 隐私约束成立。
  - **仍不声称**：真机/模拟器安装与运行、冷启动行为、WebView 真实校方登录抓取、Room 迁移在真 DB 执行、小组件渲染。这些尚未验证。
  - **过程记录**：工具链安装于 F 盘（用户要求不占 C 盘）；期间处置三个环境级问题（JVM 出网须走会话级代理而 Gradle wrapper 绕过它、SDK 包名改斜杠记法且 API 37 以次要版本发布、`gradle wrapper` 需 `--offline` 生成）；并修复一处 SDK 平台残缺目录（`android-37.0` 缺 `package.xml`，完整版曾在 `android-37.0-2`）。完整复现步骤见 `docs/BUILD-ENV.md`。
  - **残留项**：主锁定组合的**首个破坏性变更**已单独立案于 **OD-010**（AGP 9 内置 Kotlin），其版本组合的进一步验证随 OD-010 跟踪。
- **追加注记（2026-09-20，team-lead）**: **工具链部分已解决，但本条不关闭**（因为 `Resolves When` 的判定条件是"产出 APK"，而非"工具链就位"）。
  - 已实际安装并验证（全部落位 **F 盘**，用户明确要求不占 C 盘）：Temurin JDK **17.0.20.1**（`java -version` 通过）、Gradle **9.5.0**、platform-tools **37.0.1**（`adb version` 通过）、**`platforms/android-37.0`**（`android.jar` 43MB 在位）、build-tools **36.0.0**（aapt2/d8/zipalign/apksigner 全在位）。
  - `./gradlew --version` 已实测通过（Gradle 9.5.0 / Launcher JVM 17.0.20.1）。
  - **依赖解析已实证可用**：隔离探针项目解析 AGP 9.3.3 / Kotlin KGP 2.4.20 / Room 2.8.5 后，Gradle 缓存落盘 **186MB**，含 `com.android.tools.build`、`androidx.compose`、`androidx.room`、`androidx.sqlite`。
  - 过程中发现并已处置的三个环境级问题（详见 `docs/BUILD-ENV.md`）：① JVM 出网必须走会话级代理，而 **Gradle wrapper 的下载器不走系统代理** → 已改 wrapper 为本地 `file:` 分发；② SDK 包名改为斜杠记法且 **API 37 以次要版本发布，包 ID 是 `platforms/android-37.0`，不存在 `platforms/android-37`**；③ `gradle wrapper` 任务会联网校验分发 URL，需 `--offline` 才能生成。
  - 原始建议的"路线 A（Android Studio 一站式）"仍已下载就位（`F:\下载\android-studio-2025.3.1.5-windows.exe`），但按 `Spec.md` §4.4 裁决 D-4，**Android Studio 仅作可选 IDE，不作为构建前置**——因为它与锁定 AGP 存在版本代差。
  - **下一步判定条件不变**：`./gradlew :app:assembleDebug` 产出 `app-debug.apk`。

---

## OPEN — waiting-on-external-condition — OD-010 主锁定版本组合尚未经真实构建验证（已发现首例破坏性变更）

- **Date**: 2026-09-20
- **Source**: `docs/ARCHITECTURE.md` §4.1 / §4.3；`ADR-007`；`docs/Spec.md` §4.2 / §4.4；架构师 blocking 第 2 条
- **Open Item**: 主锁定组合（JDK 17 / Gradle 9.5.0 / AGP 9.3.3 / Kotlin 2.4.20 / KSP 2.3.11 / Compose BOM 2026.09.00 / compileSdk 37 / Material3 1.4.0）**各版本号已逐一核验存在**（19/19 在 Google Maven 与 Maven Central 命中，零幻觉版本），但**组合是否能真实构建通过，尚未验证**。
  **已发现首例破坏性变更**（第一次真实构建即暴露）：
  ```
  An exception occurred applying plugin request [id: 'org.jetbrains.kotlin.android', version: '2.4.20']
  > The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
    Solution: Remove the 'org.jetbrains.kotlin.android' plugin from this project's build file.
    See https://kotl.in/gradle/agp-built-in-kotlin
  ```
  **AGP 9.0 起内置 Kotlin 支持，再显式应用 `org.jetbrains.kotlin.android` 会被 AGP 主动拒绝。** 该变更不在任何 release notes 摘要中，**只有在真实构建时才会暴露**——`ARCHITECTURE.md` 与 `Spec.md` 均未覆盖，属调研盲区（非执行者失误）。
- **Related Constraints**: 需连带确认三项（必须读官方文档原文，不得凭印象推断）：(a) 移除 Kotlin 插件后 `org.jetbrains.kotlin.plugin.compose` 是否仍需显式应用；(b) KSP 在 AGP 9 + Kotlin 2.4.20 下的正确插件写法；(c) Kotlin 版本改由何处配置。
  另有一个待验证项：SDK 平台目录名是 **`android-37.0`**（API 37 按次要版本发布），**不存在 `android-37`**，需确认 `compileSdk = 37` 能否被 AGP 9.3.3 正确解析到该目录。
- **Current Leaning**: 优先按官方文档修正构建脚本（移除 Kotlin 插件 + 按 AGP 9 内置 Kotlin 方式重配），**不轻易放弃现代组合**。
- **Blocked By**: 需要工具链就绪（**已就绪**，见 OD-009 追加注记）。
- **Resolves When**: `./gradlew :app:assembleDebug` 在**不改动主锁定版本号**的前提下产出 APK。若无法达成，则**就地翻 `RESOLVED` 并补 `Resolution`**，记录启用回退组合（AGP 8.13.x + Gradle 8.13 + Compose BOM 2025.12.00 + compileSdk 36）的决定与理由。
- **Status**: OPEN
- **回退触发条件（预先约定，避免临场犹豫）**: 满足任一即由 team-lead 裁定回退——① AGP 9.3.3 与 Gradle 9.5.0 或 Kotlin 2.4.20 组合本身不兼容且无官方迁移路径；② 修正内置 Kotlin 写法后仍出现无法定位的 AGP 内部异常；③ 同一构建阻塞点连续 3 轮修复无进展。
  **回退代价**（`ARCHITECTURE.md` §4.3 已载）：失去 Compose 1.12 的 `Modifier.onPlaced` 等性能改进与默认可暂停组合能力，对"页面切换与内容更新必须有过渡动画 + 性能预算"这一用户硬要求有轻微不利。

---

## OPEN — existing-design-boundary — OD-011 动画配方 ⑨ `refreshedItemEnter` 已定义 token 但无接线点

- **Date**: 2026-09-20
- **Source**: `docs/UIUX.md` §5（12 组配方）；`ui/theme/Motion.kt` L220-228；总监裁决 advisory 2
- **Open Item**: 12 组动画配方中 **11 组已接线，配方 ⑨ `refreshedItemEnter` 未接线**。原因：本 App 的下拉刷新只是"重新订阅同一条 Room 查询"，**几乎不产生新列表项**，该配方的触发条件不成立；列表插入 / 删除 / 重排的真实动效已由 `Modifier.animateItem()` 覆盖。
- **Related Constraints**: 若要启用，需在 `TodayCourseList` 引入**跨 emission 的 `sessionId` 差集**来标记"新出现"的卡片；**严禁在 ViewModel 里缓存原始数据比对**（会制造第二真相源，与"UI 只订阅 Flow"的 ADR-001 冲突）。
- **Current Leaning**: **接受为已知缺口，不实现差集方案。** 依据「过度设计护栏」：评审只标「正确性缺陷 / 需求未满足 / 契约与数据完整性破坏」三类阻断，"未被要求的额外特性"不作为阻断理由；唯一会新增项的场景是手动编辑课程（低频，且可由 `source = MANUAL` 直接识别）。
- **Blocked By**: 无（这是一个已裁决接受的设计边界，非待办）。
- **Resolves When**: 若未来出现"刷新后确实会新增列表项"的真实场景（例如支持导入增量合并），则启用差集方案并在 `Motion.kt` 就地关闭本条。
- **Status**: OPEN
