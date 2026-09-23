# Spec — 课刻（Kebiao）v1.0

> 生成日期：2026-09-20
> 基于：PRD v1.0 + 架构文档 v1.0 + UIUX v1.0
> 状态：**已确认**（用户于 2026-09-20 三文档门禁拍板通过）
> 规格即契约：本文档是 Spec，锁定范围 / 功能 / 模块契约 / 数据表 / 页面 / 设计 Token。后续所有开发、测试、验收以本文档为唯一依据。
> 参考：`references/01-standards/spec-as-contract.md`

---

## 0. 用户裁决与总监裁决（已定，不再讨论）

### 0.1 用户在门禁拍板的四项

| # | 议题 | 用户裁决 | 影响 |
|---|------|----------|------|
| U-1 | 三文档确认 | **全部确认，推进** | 进入 Spec / Phase 2，后续自动推进 |
| U-2 | 手动编辑课程兜底 | **保留为 P1** | 场景 E（教务改版兜底）进入第一版范围 |
| U-3 | 本机构建工具链 | **现在装 Android Studio** | 见 §4.4 与 §12；安装包已下载至 F 盘 |
| U-4 | App 命名 | **课刻** | 应用名 = 课刻；包名 `com.kebiao.app` |

### 0.2 项目总监裁决（专家建议口径裁定）

| # | 争议项 | 裁决 | 依据 |
|---|--------|------|------|
| D-1 | 「禁止硬编码颜色」是否涵盖 Token 定义文件 | **Token 定义文件（`design-tokens.json` / `design-tokens.kt`）豁免；组件与页面代码禁止硬编码** | 调色板必须给出色值，否则 Token 无从定义。P0-3 的立法意图是防止颜色散落在组件里失去统一管控，不是禁止定义色值 |
| D-2 | 课程识别色第 8 号为纯紫 `#6E4FD8` 是否触红线 | **保留，不违规** | P0-2 红线针对的是 `Indigo→Pink 渐变 + 发光边框 + 毛玻璃` 三位一体套路。**纯色不触发**，架构文档已确认 |
| D-3 | 是否打包 Inter 拉丁子集 | **打包**（约 30KB） | 时间轴 `08:10 / 10:00 / 12:30 / 14:20 / 15:55 / 17:30` 六行若用比例宽度数字会左右参差，视觉上是"歪的"。成本可忽略 |
| D-4 | 构建权威路径 | **以 Gradle Wrapper 命令行构建为唯一权威路径；Android Studio 仅作可选 IDE**，不作为构建前置 | 见 §4.4 代差风险。`./gradlew assembleDebug` 不依赖 IDE，可规避 AS↔AGP 版本耦合 |
| D-5 | 术语漂移 `CourseOccurrence` vs `ClassSession` | **统一采用架构术语**：L3 = `ClassSession`，L4 = `SessionOccurrence`。全项目禁用 `CourseOccurrence` | 架构文档 §6.1 已给映射表，以架构为准，不各自另起一套 |
| D-6 | PRD §14 开放问题 2：小组件尺寸 | **4×2 为主（显示当天全部课程）+ 4×4（同版本内）**；1×1 不做 | 4×2 能覆盖"当天课程三要素"验收要求 |
| D-7 | PRD §14 开放问题 3：提醒默认提前时长 | **15 分钟**，且用户可改（5/10/15/20/30 分钟档位） | 与 PRD §6.3 验收要点一致 |

---

## 1. 产品定义

- **一句话描述**：给上海电机学院在读学生（当前即用户本人）的一款"零广告、教务系统直连导入、以当天课程为核心"的 Android 课表工具，让"今天上什么课、几点、在哪"在开机一瞥与一次启动内被解决。
- **目标用户**：用户本人，上海电机学院 自动化专业 2026 级大一，临港校区。**唯一真实用户**，无次要用户画像。
- **核心问题**：教务系统是 PC 网页，手机端体验差；同一门课不同周换教室换教师，靠记忆必错；无提醒，早八靠自律。
- **真实诉求（问题而非方案）**："我每天出门前，要能 3 秒内确认今天要上什么课、几点、在哪个教室；换学期时不用重来；早八不迟到。"
- **差异化定位**：竞品把课表当流量入口，我们只把它当工具——不塞广告、不逼注册、不搞社交，导入一次，整个学期不操心。

---

## 2. MVP 范围（锁定 —— 不在此列表的功能一律不做）

> **范围口径**：用户已全选的功能范围**全部纳入第一版**，不删减。P0/P1/P2 仅表示**实现顺序**，不代表"做 / 不做"。真正的"不做"见 §3。

| 优先级 | 功能 | 验收标准摘要 | RICE |
|--------|------|-------------|------|
| P0 | 当天课程主界面 | 默认进入即"今天"；每张卡含课程名 + 时间 + 地点三要素 | 7.5 |
| P0 | 教务系统导入（含周次 / 单双周 / 多教师多教室解析） | 覆盖 §2.1 五种反直觉数据形状 | 6.0 |
| P0 | 多学期课表管理 | 切换后当天课程 3 秒内刷新，旧数据残留 = 0 | 5.33 |
| P0 | 上课提醒通知 | 内容含课程名 + 时间 + 教室；抗国产 ROM 后台清理 | 4.8 |
| P0 | 全局过渡动画 | 页面切换 / 内容更新 / 学期切换均有顺滑过渡，无跳变 | 2.67 |
| P1 | 手动编辑课程（兜底） | 可增 / 改 / 删单条 `ClassSession`，与导入课程并存不冲突 | 3.0 |
| P1 | 周视图 / 完整课表网格 | 正确体现单双周与换教室；线上 / 不排座课进独立分区 | 2.8 |
| P1 | 桌面小组件 Widget | 4×2 与 4×4；换学期或改课后刷新 | 2.4 |
| P2 | 深色模式 / 主题美化 | 浅深两套 Token 均已定义，切换可用 | 1.0 |

### 2.1 必须容纳的五种反直觉数据形状（导入即崩的临界点）

1. **同一门课不同周换教室 / 换教师**——「大学物理B(1)」第 2 周 `E教305`；第 3-16 周 `B105`。「自动化专业导论与职业生涯规划」第 2 周 `D教203` 陈国初 / 3-4 周 `B203` 蒋璐峥 / 5、8 周 `B203` 陈国初 / 6-7 周 `B203` 于妍。
2. **每周实验室不同**——「大学物理实验B(1)」9-11 周 `204(实验室2)` / 12 周 `202(实验室1)` / 13 周 `309(实验室11)` / 14 周 `305(实验室7)` / 15-16 周 `308(实验室10)`。
3. **单双周规则**——`2-16 双周`、`3-15 单周`、`9-16`（每周）。
4. **纯线上课程**——尔雅通识课《航空与航天》，5-16 周，无实体教室。
5. **无固定时间地点**——「军事技能」，`weekday` / 节次 / 时间 / 教室 全部可空。

> **建模铁律**：形状 1 与 2 **不能按"课程表行"简单存一行**。必须落到 L3 `ClassSession` 的**互斥周次行**上——这是唯一能表达"第 2 周在 A、第 3-16 周在 B"的建模方式。

---

## 3. 明确不做（Out-of-Scope —— 锁定）

> 每条带原因。开发中若有人提出下列功能，**直接拒绝**，走 §13 变更流程。

| 不做的功能 | 原因 | 何时考虑 |
|------------|------|----------|
| 上架应用商店 / 对外分发 | 自用项目，无分发需求；签名、隐私政策、审核成本投入产出比为零 | 想分享给同学或作为简历项目时 |
| 产品级账号体系 / 登录注册 | 只有一个使用者，本地存储即可；竞品"强制手机号注册"正是差评来源 | 变为多人使用时 |
| 云同步 / 后端服务器 | 无服务器是既定约束；单设备无需同步 | 换手机想保留数据或多设备同步时 |
| 社交 / 分享 / 找同课同学 | 竞品臃肿与差评主因之一，自用零需求 | 帮室友导入同一套课表时，以"导出文件"最小实现，仍不做社交 |
| 广告 / 付费 / 会员 / 皮肤商城 | 自用无需变现；"无广告"是本产品第一卖点 | **永不考虑** |
| 空教室查询 | 依赖全校用户打卡数据，单人无法实现 | 基本不考虑 |
| 成绩 / 绩点 / 考试倒计时 | 超出"看课表"范围 | 用户明确要求时单独评估 |
| 校花校草 / 小纸条 / 下课聊 | 娱乐社交，与核心完全无关 | **不考虑** |
| 拍照 / OCR 导入 | 教务直连已够用；OCR 识别错风险高（竞品"识别错排课"即前车之鉴） | 教务改版且无任何可解析途径时，用 P1 手动编辑兜底，**仍不做 OCR** |
| iOS / 跨平台 / 桌面端 | 用户是 Android，技术栈锁定 Kotlin + Compose | 更换 iPhone 或需要 PC 端时 |
| 课程笔记 / 作业 / DDL / 待办 | 属"任务管理"范畴，超纲 | 不考虑 |
| 多语言 / 国际化 | 单一中文用户 | 不考虑 |
| App 内静默抓取教务系统（无人工登录） | 学校 SSO 首次登录需绑定手机号 + 短信验证码；为此申请 SMS 读取权限触碰"不采集隐私"红线 | **不考虑**，改用 WebView 中继登录（§4.3） |

---

## 4. 技术架构（锁定 —— 含版本锚定）

### 4.1 分层与依赖方向

```
ui (Compose)  →  feature (Screen/ViewModel)  →  domain (纯 Kotlin，无 Android 依赖)
                                                        ↑
                                          data (Room / import / repository)
```

**依赖只向下，禁止反向**。`domain` 层禁止 import 任何 `android.*`、`androidx.*`、Room 注解。

### 4.2 技术栈版本锁定表

> 全部版本已于 2026-09-20 由项目总监**独立核验存在性**（Google Maven / Maven Central 元数据），**19 / 19 项全部 EXISTS，零幻觉版本**。

| 层 | 技术 | 实际版本 | 锁定原因 |
|----|------|----------|----------|
| 语言 | Kotlin | **2.4.20** | 当前稳定版 |
| 构建 | Gradle (Wrapper) | **9.5.0** | AGP 9.3 默认值 |
| 构建 | AGP | **9.3.3** | 已吃到三个补丁，比 9.4.x 更稳；支持 compileSdk 最高 37。（注：AGP 最新稳定为 9.4.1，本项为刻意保守选择） |
| 注解处理 | KSP | **2.3.11** | **必须 KSP2**；KSP1 与 Kotlin ≥2.3 / AGP ≥9 不兼容 |
| JDK | Temurin JDK | **17 (LTS)** | AGP 9.x 硬性最低要求 |
| UI | Compose BOM | **2026.09.00** | 一次对齐全部 Compose 版本 → UI 1.12.1 + Material3 1.4.0 |
| UI | Material3 | **1.4.0**（由 BOM 决定） | 稳定版；1.5.0-alpha 不引入 |
| SDK | compileSdk | **37** | **由 Compose 1.12 强制**，非自由选择 |
| SDK | targetSdk | **36** | 自用不上架；取 36 规避 Android 17 首日行为变更未知项 |
| SDK | minSdk | **26**（Android 8.0） | `java.time` 原生可用，日期数学无需 desugaring |
| SDK | Build Tools | **36.0.0** | AGP 9.3 默认值 |
| 存储 | Room | **2.8.5** | 需要"按周次查询某天课程"，是关系型查询；Room 3.0 仍 Alpha |
| 存储 | DataStore (Preferences) | **1.2.1** | 仅存标量设置（提醒提前量、主题偏好） |
| 导航 | Navigation Compose (Nav2) | **2.10.1** | Nav3 仍 RC，不用于稳定交付 |
| 生命周期 | Lifecycle Runtime Compose | **2.11.0** | `collectAsStateWithLifecycle()` 来源 |
| Activity | Activity Compose | **1.13.0** | `enableEdgeToEdge()` |
| AndroidX | Core | **1.19.0** | 注意：1.19.0 起 `core-ktx` 已并入 `core` |
| 后台 | WorkManager | **2.11.2** | 兜底提醒 + 定周期刷新 |
| 小组件 | Glance AppWidget | **1.2.0** | 稳定版；1.3.0 仍 Alpha |
| 网络 | OkHttp | **5.5.0** | 会话抓取（CookieJar / 重定向 / TLS） |
| 解析 | jsoup | **1.23.2** | HTML 表格解析；Android 侧需 core library desugaring |
| 序列化 | kotlinx-serialization-json | **1.11.0** | 导入 JSON 兜底路径 |
| 日期 | `java.time`（优先） | JDK 17 内置 | minSdk 26 已原生支持，不引入 kotlinx-datetime |
| 图标 | **Material Symbols** | Google Fonts, Apache-2.0 | 见 §4.5 |
| 依赖注入 | 手写 `AppContainer` | — | 规模不需要 Hilt；且 Hilt 的 Gradle 插件会堵死版本回退退路 |

### 4.3 教务系统导入（本项目最大技术风险）

**目标系统**：`https://jwgl.sdju.edu.cn/home` → CAS SSO `authserver.sdju.edu.cn/authserver/login?service=...`。
**已实测**：教务系统返回 302、SSO 登录页返回 200，**公网可达，本机无需校园网/VPN**（手机流量下可达性待实测，见 OD-002）。
**已查证**：2026 级新生公开信明示统一身份认证**首次登录需绑定手机号**并涉及短信验证码。

**选定路径（三选一裁定）**：**App 内 WebView 中继登录（真人在系统 WebView 完成登录，含短信码）+ 同源 JS 抽取为主；JSON 文件导入为兜底。**

三条路径对比结论：

| 路径 | 可行性 | 裁定 |
|------|--------|------|
| a) App 内静默直连抓取 | **否**——短信验证码环节无法静默通过；拦截短信需高危权限 | 否决 |
| b) 仅电脑端脚本 → JSON 导入 | 可行，但用户无法脱离电脑换学期 | 作为兜底保留 |
| c) **WebView 中继 + JSON 兜底** | **采纳** | 最大不确定性在"登录"而非"解析"；把登录交给真人，代码里只剩"解析一张课表 HTML"一处风险 |

**强制技术约束**：
1. App **不读取密码字段**、**不注册 `@JavascriptInterface`**。
2. 解析风险压缩到单一 `ScheduleHtmlParser`，教务改版时真人自适应，代码只改这一个类。
3. 导入后必须有**核对视图**（哪些课 / 哪些周 / 哪些教室）——对应竞品"导入识别错 → 旷课"这一头号差评。
4. 解析失败**必须显式报错，禁止静默返回空集**。
5. 密码 / 会话凭证仅本地加密存储（Android Keystore），不上传任何第三方。
6. 全链路 HTTPS；禁止明文 Cookie 落盘。

### 4.4 本机构建环境（实测结论 + 代差风险）

**实测（项目总监独立复核，与架构师结论一致）**：本机 `java` / `javac` / `adb` / `gradle` 全部 NOT FOUND；`JAVA_HOME` / `ANDROID_HOME` / `ANDROID_SDK_ROOT` 全空；`%LOCALAPPDATA%\Android\Sdk`、`Program Files\Android`、`Program Files\Java`、`Eclipse Adoptium`、`~/.gradle`、`~/.android` **全部不存在**。

**结论**：本机当前**无法构建 APK，无法真机/模拟器运行**。

**已定位的代差风险（本项目新增发现）**：Google CDN 上可获取的最新 Android Studio 稳定版为 **2025.3.1.5**，而本 Spec 锁定的 AGP 为 **9.3.3**（2026-07 发布）。IDE 与 AGP 存在代差，AS 内可能提示 AGP 版本超出支持范围、IDE 同步或 Preview 功能受限。

**总监裁决 D-4 的处置**：**以 Gradle Wrapper 命令行构建为唯一权威路径，Android Studio 仅作可选 IDE**。
- 理由：AGP 由 Google Maven 解析，**不依赖 IDE**；`./gradlew assembleDebug` 与 AS 版本无关，可彻底规避代差耦合。
- 若 AGP 9.3.3 出现构建阻塞 → 启用**保守回退组合**：AGP 8.13.x + Gradle 8.13 + Compose BOM 2025.12.00 + compileSdk 36（代价：失去 Compose 1.12 的 `Modifier.onPlaced` 等性能改进）。
- 回退决策权在架构师，触发条件写入 `ADR-007`。

**已下载就位**：
- Android Studio 安装包 → `F:\下载\android-studio-2025.3.1.5-windows.exe`（1,374,985,648 字节，文件头 `MZ` 校验通过）
- JDK 17 → `F:\AndroidDev\installers\`
- SDK / 工具链落位目录 → `F:\AndroidDev\Sdk`（**不占 C 盘**）

### 4.5 图标库锁定（P0 规则 1）

**全项目锁定唯一一套：Material Symbols**（Google Fonts，Apache-2.0）。

**为什么不能沿用 `androidx.compose.material.icons`**：官方已明确"不再推荐"，且**已从 Material3 最新 release 移除**。本 Spec 锁定 Material3 **1.4.0**，代码里写 `Icons.Filled.*` 会**直接编译失败**。这是本项目必须提前告知的编译期陷阱。

**接入方式**：从 Google Fonts 取 **VectorDrawable XML** 放 `app/src/main/res/drawable/`，用 `painterResource` 渲染。**明确不引入 `material-icons-extended`**（数千图标全进 APK）。统一引用点：`ui/icons/KebiaoIcons.kt`。

**尺寸规范**：16dp（与文字同行的小标识）/ 20dp（紧凑行内控件与列表项尾随）/ 24dp（图标按钮、导航项、空状态、FAB）。
**落地档位**：MVP 必做 **18 个**；设计清单完整 **49 项**（含 `_fill` 选中态变体），其余 31 项按页面进度补齐。
**禁止**：任何 emoji 充当功能图标；混用第二套图标库。

---

## 5. 模块接口契约清单

> **为什么没有 HTTP API 章节**：本产品**无后端服务器**（Out-of-Scope 已锁定），不存在 REST 端点。按"规格即契约"的精神，本项目的契约面是**模块接口**——把它锁死，效果等价于锁死 API。

### 5.1 核心纯函数（domain 层，可脱离 Android 单测）

| 签名 | 职责 | 约束 |
|------|------|------|
| `Semester.weekOf(date: LocalDate): Int?` | 给定日期 → 第几教学周（越界返回 null） | **纯函数**；禁止 `LocalDate.now()` |
| `WeekExpressionParser.parse(raw: String): WeekSet` | `"2-16 双周"` / `"2,3-4,5,8,6-7"` → 规范化周次集合 | **解析失败必须抛异常，禁止静默返回空集** |
| `ScheduleCalculator.occurrencesOn(term, date, sessions): List<SessionOccurrence>` | 给定日期 → 当天全部课次（含状态判定） | **纯函数**；`date` / `now` 由参数注入 |
| `SemesterResolver.resolve(semesters, today): Semester?` | 解析"当前学期" | 纯函数 |

**铁律**：`domain` 层禁止出现 `LocalDate.now()` / `System.currentTimeMillis()`，时间一律经 `TimeProvider` 注入。

### 5.2 仓库接口

| 接口 | 关键方法 | 契约 |
|------|----------|------|
| `SemesterRepository` | `observeCurrent(): Flow<Semester?>`、`switchTo(id)`、`upsert(semester)` | **`is_current` 唯一写入口**，必须保证"至多一个 current"不变式 |
| `ScheduleRepository` | `observeToday(): Flow<TodaySchedule>`、`observeWeek(weekIndex): Flow<WeekSchedule>` | UI **只订阅 Flow，不自己算"今天"** |
| `ImportSource` | `suspend fun fetch(): AppResult<RawSchedule>` | 两个实现：`WebViewImportSource` / `JsonFileImportSource` |
| `ImportCommitter` | `suspend fun commit(batch): AppResult<ImportResult>` | **单事务提交 + 失败整体回滚** |

### 5.3 刷新链路（架构核心，ADR-001）

```
用户切换学期
  → SemesterRepository.switchTo(id)（Room 事务，更新 is_current）
  → Room 表变更触发 Flow 重发
  → ScheduleRepository.observeToday() 重新求值
  → TodayViewModel 收到新 UiState
  → Compose 以过渡动画呈现新旧更替
```

**这条链路的意义**：UI **结构上不存在"忘了刷新"的代码路径**——刷新不是一次主动调用，而是数据流的必然结果。

---

## 6. 数据库表清单（锁定）

Room 配置：`exportSchema = true`；迁移用 `Migration` 显式声明；**禁止 `fallbackToDestructiveMigration`**（个人项目丢了课表比崩溃更痛）。

| 表名 | 核心字段 | 索引 | 关联 |
|------|----------|------|------|
| `semesters` | `id` PK、`name`、`academic_year`、`term_index`、`start_date`(ISO-8601 TEXT)、`total_weeks`、`is_current`、`created_at`、`updated_at` | **UNIQUE** `(academic_year, term_index)` | 被下列各表 `ON DELETE CASCADE` 引用 |
| `courses` | `id` PK、`semester_id` FK、`name`、`code`、`total_hours`、`is_online`、时间戳 | `idx_courses_semester(semester_id)` | → `semesters`；← `class_sessions` |
| `class_sessions`（**核心表**） | `id` PK、`course_id` FK、`weekday`(1-7, **可为 NULL**)、`period_start`/`period_end`、`start_time`/`end_time`、`campus`、`room`、`teacher`、`weeks_raw`（原始串，审计用）、`week_numbers`（规范化 `",2,4,6,8,"`）、`source`(0=IMPORT 1=MANUAL)、`import_batch_id`、`remark`、时间戳 | `idx_sessions_course(course_id)`、`idx_sessions_weekday(weekday)` | → `courses`；→ `import_batches` |
| `import_batches` | `id` PK、`semester_id` FK、`source`(0=WEBVIEW 1=JSON_FILE)、`status`(0 RUNNING/1 SUCCESS/2 FAILED/3 ROLLED_BACK)、`payload_ref`、`course_count`、`session_count`、`message`、`started_at`、`finished_at` | `idx_batches_semester(semester_id)` | → `semesters` |

**索引策略**：单学期 `class_sessions` 仅数十行量级，"某天有哪些课"采用**内存过滤**（一次 SELECT 出该学期全部 L3，在 `ScheduleCalculator` 内过滤），复杂度 O(n) 且 n 极小。`week_numbers` **刻意不建索引**——避免过早优化。

**四层概念分层（ADR-001，本项目架构核心）**：

| 层 | 实体 | 是否落库 |
|----|------|----------|
| L1 | `Semester` | 落库 |
| L2 | `Course` | 落库 |
| L3 | `ClassSession`（一条 = 一组"周次集合 + 星期 + 节次 + 地点 + 教师"） | 落库 |
| L4 | `SessionOccurrence`（某天课次） | **不落库，每次现算** |

---

## 7. 页面清单（锁定）

| 页面 | 路由 | 核心组件 | 数据来源 | Token 主题 |
|------|------|----------|----------|-----------|
| **今日课程**（核心） | `today` | `CourseCard` ×N、`SectionHeader`（待定分组）、`EmptyState`、`LoadingSkeleton` | `ScheduleRepository.observeToday()` | 浅/深双套 |
| **周视图** | `week` | `WeekGrid`（逐日列 + Pager，时间轴 64dp 固定）、`WeekDayColumn` | `observeWeek(weekIndex)` | 浅/深双套 |
| **多学期管理** | `semesters` | 学期列表、`进行中` 标记、切换动作 | `SemesterRepository` | 浅/深双套 |
| **导入 / WebView 登录** | `import` | `WebViewLoginScreen`、导入进度、**核对视图**、`CourseEditDialog`（P1 手动编辑共用） | `ImportViewModel` | 浅/深双套 |
| **设置** | `settings` | 提醒开关 + 提前时长（5/10/15/20/30）、主题、本地事件日志入口 | DataStore | 浅/深双套 |
| **桌面小组件** | — | Glance，**4×2**（当天全部课程）+ **4×4** | 只读 `observeToday()` | Widget 专用精简 Token（8 色 + 4 档字号，从 `design-tokens.json` 派生） |
| **上课提醒通知** | — | 通知渠道、含课程名 + 时间 + 教室 | `ReminderScheduler` | 系统通知样式 |

**今日课程 8 种课程卡状态机**：已结束 / 正在进行中 / 即将开始 / 稍后 / 待定（无时间地点）/ 线上 / 今日已上完 / 今日无课。
**空状态文案**（区分四种，禁止写"暂无数据"）：

```
今天没有课程
下节课：周四 08:10 大学物理B(1) · B105 · 袁艳红
[查看本周课表]
```

其余三种：假期中 / 学期未开始 / 尚未导入课表（提供"去导入"引导）。

**周视图约束**：手机竖屏**不硬塞 7 列**，改逐日列 + Pager；时间轴 64dp 固定不随滑动。

---

## 8. 设计 Token（锁定）

> 完整定义见 `docs/design-tokens.json`（528 行）与 `docs/design-tokens.kt`（750 行，Compose 可直接 import）。前端必须通过 import 引用，**禁止在组件内硬编码色值**（口径见 §0.2 D-1）。

| 项 | 取值 |
|----|------|
| 设计方向 | Material 3 底子上的**仪表盘精密风**：冷色中性底 + 单一强调色 + 8 色课程识别环 + 克制的功能性动效 |
| 明确排除 | Glassmorphism（低端 Android 掉帧 + 吞掉课程色识别度）、Aurora 紫渐变（命中红线）、Claymorphism（K12 儿童语言）、纯黑 OLED（1dp 边框不可见） |
| 对标品牌 | Linear（中性底 + 单一强调 + 严格 4dp 网格）、Notion Calendar（时间轴语言）、Things 3（入退场节奏）、Apple Calendar（颜色标记节制）、TimeTree（同色语义换明度） |
| **强调色** | 电控蓝 **`#1D6FE8`**（色相 216，是"蓝"不是"青紫"，对白底 4.67:1，过 WCAG AA） |
| 浅色底 / 卡片 | `#F4F6FA` / `#FFFFFF` |
| 深色底 / 卡片 | `#0E1116` / `#161A21`（**均不用纯黑纯白**） |
| **8 色课程识别环** | 蓝 `#1F63D6` / 青 `#0B7C8C` / 绿 `#0F7A48` / 橄榄 `#6B7A0F` / 琥珀 `#9A6206` / 橙红 `#B94A1A` / 品红 `#AE3A78` / 紫 `#6E4FD8`（浅色下对白底均 ≥4.5:1） |
| 课程配色铁律 | ① 一色一课，**课程代码稳定哈希取模 8**，全 App 与 Widget 永远同色 ② 色只出现在 **8dp 圆点 + 约 10% 面积的低饱和 tint 底**，**绝不整格铺满** ③ **课程名文字永远中性**，对比度恒定 ④ **禁止 `border-left` 色条**（项目违规写法） |
| 字体（中文） | **不打包**，走 `FontFamily.Default` 交给系统（MiSans / HarmonyOS Sans / OPPO Sans / Noto Sans CJK）。理由：中文子集化仍 300-600KB，是纯体积浪费；硬塞会绕过系统无障碍字号缩放 |
| 字体（数字） | **Inter 拉丁子集**（约 30KB，**总监裁决 D-3 批准打包**），时间 / 节次 / 教室编号开启 `tnum` 等宽数字。降级链 `Inter → Monospace → Default` |
| 字号阶梯 | 32 / 22 / 18 / 16 / 14 / 13 / 12 / 11 sp |
| 字重 | 意图值 400 / 510 / 590；落地 400 / 500 / 600 |
| 间距 | 4dp 网格 |
| 圆角 / 层级 | `KebiaoShapes` / `KebiaoElevation`（见 Token 文件） |
| 图标库 | **Material Symbols**（§4.5） |
| 主题 | 浅色 + 深色**双套完整 Token** |

### 8.1 过渡动画规范（用户硬要求）

> 全部落到 Compose Animation API 参数级，共 **12 个编排配方**（`MotionTokens`）。完整定义见 `UIUX.md` §5。

| 项 | 规格 |
|----|------|
| 时长阶梯 | **100 / 150 / 250 / 350 / 500 ms**，150ms 为收敛基准 |
| 缓动曲线 | `standard(0.2, 0, 0, 1)` / `emphasized(0.05, 0.7, 0.1, 1)` / `decelerate(0, 0, 0.2, 1)` / `accelerate(0.3, 0, 1, 1)` |
| **弹簧** | **所有 `dampingRatio` 恒为 1.0（NoBouncy）**，刚度 1500 / 400 / 200 三档。**数学上不存在回弹** |
| 同级页面切换 | fade-through：入场 `fadeIn + scaleIn` 250ms decelerate（`initialScale 0.94`）；出场 `fadeOut` 90ms accelerate；`sizeTransform` null |
| 层级页推入 | shared-axis X：350ms emphasized，新页从 `width/4` 推入、旧页左移 `width/6`；honor Android 14 预测性返回 |
| **学期切换刷新编排**（全 App 最用心一段，总预算 500ms） | +0ms 旧列表 fadeOut 140ms 上移 / +100ms 学期 Chip 竖向翻页 150ms / +140ms 容器 `animateContentSize` spring(400) / +140ms 新列表 stagger 入场 |
| 列表项入场 | 单卡 180ms decelerate；位移 `initialOffsetY = it/6`；stagger 间隔 60ms；**封顶 6 项**；`rememberSaveable` 只播一次 |
| 状态变"进行中" | 边框色/宽度、底色、状态 pill、脉冲圆环共 5 重变化，300ms standard；脉冲 `infiniteRepeatable(tween(1600))` Reverse；**全屏只允许 1 门**；仅可见且 RESUMED 时运行 |
| 空状态 | 列表 160ms 退出；图标 `scaleIn 0.88` + 文案 260ms 延时 160ms；总 420ms |
| 下拉刷新 | M3 `PullToRefreshBox`；差异项 `animateItem(spring 400)`；新项 180ms |
| 周视图 Pager | 跟手 1:1；吸附 spring(NoBouncy, 400)；`PagerSnapDistance.atMost(1)` 禁止跨多周飞甩 |
| 骨架屏 | shimmer 1200ms 线性 Restart，高光带 24%，3 张卡固定 112dp 防 CLS |

**性能预算（硬约束）**：只动 `alpha` / `translation` / `scale` 走 `graphicsLayer`；**禁止动 `width` / `height` / `top` / `left` / `margin`**；同时动画元素 ≤ 3；**滚动期间动画数恒为 0**；reduced motion 时关闭 stagger 与脉冲。
**特别说明**：课程卡"进行中"状态变化**不含任何位置或尺寸变化**，只有颜色与脉冲——用户正在读的那一行不会跑掉。

---

## 9. 验收标准（锁定 —— QA 测试以此为唯一依据）

> 采用 EARS 格式（While / When / If / Where + 系统 + 必须 / 应该 + 行为）。完整 Given/When/Then 用例见 `PRD.md` §9。

### 9.1 导入与解析

| 编号 | EARS 验收标准 | 优先级 |
|------|---------------|--------|
| AC-01 | **When** 用户完成 SSO 登录并触发导入，**系统必须**入库全部课程，且课程数 / 周次 / 时间 / 教室 / 教师与教务系统逐条一致 | P0 |
| AC-02 | **While** 查看「大学物理B(1)」，**系统必须**在第 2 周显示 `E教305`、第 3 周起显示 `B105` | P0 |
| AC-03 | **While** 查看「自动化专业导论与职业生涯规划」，**系统必须**依次显示 第2周 陈国初(D教203) / 3-4周 蒋璐峥(B203) / 5,8周 陈国初(B203) / 6-7周 于妍(B203) | P0 |
| AC-04 | **When** 解析遇到无法识别的周次表达式，**系统必须**显式报错并保留现场，**不得**静默返回空集 | P0 |
| AC-05 | **If** SSO 密码错误，**系统必须**提示"账号或密码错误"并允许重试，且**不得**产生半截错误数据 | P0 |
| AC-06 | **If** 导入过程中断网，**系统必须**显示网络错误原因并提供重试，且已入库数据**不得**被破坏 | P0 |
| AC-07 | **When** 用户输入乱序 / 重叠周次串（如 `2,3-4,5,8,6-7`），**系统必须**解析为去重后的规范集合 | P0 |

### 9.2 当天课程主界面

| 编号 | EARS 验收标准 | 优先级 |
|------|---------------|--------|
| AC-10 | **When** 打开 App，**系统必须**默认进入"今天"页，且每张课程卡至少含课程名 + 上课时间 + 上课地点 | P0 |
| AC-11 | **While** 今天无课，**系统必须**显示"今天没有课"空状态，并给出下节课提示；文案**必须**与"假期中""学期未开始""尚未导入"三种状态可区分 | P0 |
| AC-12 | **Where** 某课为纯线上课程，**系统必须**显示"线上"而非空白教室 | P0 |
| AC-13 | **Where** 某课为单双周课程，**If** 当前周不命中，**系统不得**将其列入当天课程 | P0 |
| AC-14 | **While** 课程列表渲染，**系统必须**用骨架屏而非空白占位 | P1 |

### 9.3 多学期管理（关键场景）

| 编号 | EARS 验收标准 | 优先级 |
|------|---------------|--------|
| AC-20 | **When** 用户新增并切换到另一学期并完成导入，**系统必须**在 **3 秒内**刷新当天课程，且**不得**出现任何上一学期课程 | P0 |
| AC-21 | **When** 用户从学期 B 切回学期 A，**系统必须**使当天课程与周视图均按学期 A 数据渲染 | P0 |
| AC-22 | **When** 学期切换与数据刷新并发发生，**系统必须**保证不出现新旧数据混合 | P0 |
| AC-23 | **系统必须**始终保证"至多一个 `is_current = 1`"的学期 | P0 |

### 9.4 上课提醒

| 编号 | EARS 验收标准 | 优先级 |
|------|---------------|--------|
| AC-30 | **When** 距某课开始 15 分钟（默认），**系统必须**发出含课程名 + 时间 + 教室的通知 | P0 |
| AC-31 | **While** App 不在前台且设备为激进后台清理的国产 ROM，**系统必须**仍能送达提醒，送达率 ≥ 95% | P0 |
| AC-32 | **If** 用户拒绝通知权限，**系统必须**在 App 内提示"提醒将无法送达"并引导至系统设置 | P0 |
| AC-33 | **If** Android 14+ 拒绝精确闹钟权限，**系统必须**降级为 WorkManager 近似提醒并告知用户 | P0 |

### 9.5 手动编辑（P1 兜底）

| 编号 | EARS 验收标准 | 优先级 |
|------|---------------|--------|
| AC-40 | **When** 用户手动新增一门课（含周次规则），**系统必须**使其出现在当天课程与周视图中，与导入课程并存不冲突 | P1 |
| AC-41 | **系统必须**标记手动录入项（`source = 1`），且**重新导入时不得**静默覆盖 | P1 |

### 9.6 周视图 / 网格

| 编号 | EARS 验收标准 | 优先级 |
|------|---------------|--------|
| AC-50 | **While** 处于第 3 周（单周），**系统必须**仅渲染命中单双周规则的课程，并显示该周对应教室 | P1 |
| AC-51 | **Where** 存在线上 / 无固定时间地点课程，**系统不得**将其硬塞进网格，**必须**在"线上 / 不排座"分区可查 | P1 |

### 9.7 过渡动画

| 编号 | EARS 验收标准 | 优先级 |
|------|---------------|--------|
| AC-60 | **When** 用户在"今天""周视图""设置"间切换，**系统必须**呈现顺滑过渡，无白屏跳变、无可感知卡顿 | P0 |
| AC-61 | **When** 学期切换或数据更新完成，**系统必须**以过渡动画呈现新旧更替，而非瞬时闪烁 | P0 |
| AC-62 | **While** 列表滚动，**系统必须**保持动画数为 0 且 60fps | P0 |

### 9.8 桌面小组件

| 编号 | EARS 验收标准 | 优先级 |
|------|---------------|--------|
| AC-70 | **When** 用户查看小组件，**系统必须**显示当天课程三要素（或"今天没有课"） | P1 |
| AC-71 | **When** 用户切换学期或手动改课，**系统必须**使小组件内容随之更新 | P1 |

---

## 10. 边界与约束

- **平台**：Android 8.0（API 26）及以上；单平台，不做 iOS / 桌面端。
- **网络**：仅"导入 / 更新"需要联网；**已导入数据的查看与提醒必须完全离线可用**。
- **性能**：冷启动到看见"今日课程" < 2s；列表滚动 60fps；导入解析 15 秒内完成。
- **安全**：全链路 HTTPS；SSO 密码与会话凭证**仅本地加密存储（Android Keystore）**，不上传任何第三方；不采集 IP；不存原始教务页面之外的用户隐私。App **不读密码字段**、**不注册 `@JavascriptInterface`**。
- **兼容性**：适配 MIUI / ColorOS / HarmonyOS / MagicOS 后台策略，提醒需引导加入白名单。
- **可访问性**：文字对比度满足 WCAG 2.1 AA；关键交互支持无障碍焦点；系统字号缩放生效。
- **屏幕适配**：小屏手机周视图文字自动缩放不重叠；深色模式对比度达标。
- **代码组织硬约束**：单文件 ≤ 300 行；一个文件一个主角；入口文件只装配零业务；按资源/特性分包不按技术类型横切。
- **包名**：`com.kebiao.app`。**`applicationId` 一旦安装后再改需卸载重装——必须在首次真机安装前定稿。**
- **术语**：L3 = `ClassSession`，L4 = `SessionOccurrence`。**全项目禁用 `CourseOccurrence`**（§0.2 D-5）。

---

## 11. 内嵌已知坑（防重蹈覆辙）

| 坑 | 技术栈指纹 | 根因 | 修法 |
|----|------------|------|------|
| `Icons.Filled.*` 编译失败 | material3-1.4.0 | `androidx.compose.material.icons` 已从 Material3 最新 release 移除 | 全项目用 Material Symbols VectorDrawable + `painterResource`；图标统一走 `ui/icons/KebiaoIcons.kt` |
| KSP 注解处理报不兼容 | kotlin-2.4.20 / agp-9.3.3 | KSP1 与 Kotlin ≥2.3、AGP ≥9 不兼容 | **必须使用 KSP2**（2.3.11） |
| 写 `composeOptions.kotlinCompilerExtensionVersion` 无效 | kotlin-2.x | Kotlin 2.0+ 起 Compose 编译器已并入 KGP | 改用 `org.jetbrains.kotlin.plugin.compose` 插件，删除旧配置 |
| `core-ktx` 依赖解析为空 | core-1.19.0 | 1.19.0 起 `core-ktx` 已并入 `core`，`core-ktx` 变为空兼容包 | 直接依赖 `androidx.core:core` |
| jsoup 在 Android 上崩溃 | jsoup-1.23.2 / minSdk-26 | jsoup 需要 NIO 能力 | 开启 core library desugaring（官方明示） |
| IDE 提示 AGP 版本超出支持 | android-studio-2025.3.1.5 / agp-9.3.3 | CDN 上 AS 最新稳定版为 2025.3.1.5，与 AGP 9.3.3 存在代差 | **以 Gradle CLI 为权威构建路径**（D-4）；IDE 报错不阻塞构建 |
| 周次解析静默返回空集导致"连续几天没课" | WeekExpressionParser | 解析失败时未显式抛错，被上层 `catch` 吞掉 | 解析失败**必须抛异常**；禁止 `catch` 吞错；穷举边界用例（AC-04 / AC-07） |
| 换学期后仍显示旧课 | Room + Flow | UI 主动缓存了课程列表，未随学期切换失效 | UI 只订阅 `Flow<TodaySchedule>`，**不缓存、不自己算"今天"**（ADR-001） |
| 提醒被国产 ROM 杀后台 | AlarmManager / MIUI 等 | 未加入电池优化白名单 + 仅用非精确闹钟 | `setExactAndAllowWhileIdle` 为主 + WorkManager 兜底 + 三重自愈（日更 Worker / 开机广播 / 权限变更广播）|
| 课程卡用 `border-left` 色条 | 全项目 | 早先 `output/kebiao.html` 的历史写法被误当作范例 | **禁止色条**；一律「8dp 圆点 + 低饱和 tint 底」 |

### 11.1 真实构建才暴露的坑（2026-09-20 首次 `assembleDebug` 实测补充）

> 来源：项目总监首次真实构建的错误原文 + 团队修复记录。**这些坑在纯文档阶段无法发现**——它们只在编译器/链接器拒绝时暴露。每一条都已验证修法。

| 坑（错误原文） | 技术栈指纹 | 根因 | 修法 |
|---------------|------------|------|------|
| `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0` | agp-9.3.3 / kotlin-2.4.20 | **AGP 9.0 起内置 Kotlin 支持**，显式应用 Kotlin 插件会被 AGP 主动拒绝 | 移除 `org.jetbrains.kotlin.android`（根 + app）。`kotlin.plugin.compose` **仍需保留**（内置 Kotlin 不替代 Compose 编译器插件）。Kotlin 版本经根 `buildscript` classpath 覆盖（AGP 9.3.3 内置 KGP ≈2.2.10，低于锁定 2.4.20） |
| `resource attr/colorControlNormal not found` | compose / 无 appcompat | Material Symbols 导出的 VectorDrawable 带 `android:tint="?attr/colorControlNormal"`，**少了 `android:` 前缀**——不带前缀的是 AppCompat 的应用级属性，本项目无该依赖 | **删掉资源级 `android:tint`**（Compose 项目在 Compose 层着色，资源级 tint 冗余且会盖掉 Compose tint）；同时 `fillColor` 从 `@android:color/white` 改为 `#FF000000`，避免"未着色时白图标隐形" |
| `No parameter with name 'sizeTransform' found`（`AnimatedContent` 上） | compose-1.12.1 | **参数不是被删除，而是迁移了**：从 `AnimatedContent` 的构造参数改为挂在 `ContentTransform` 上 | 改用 `(enter togetherWith exit).using(null)`。`NavHost`（Navigation Compose 2.10.1）上的 `sizeTransform` 参数**依然存在**，两者不要混淆 |
| `Unresolved reference 'DampingRatioNoBouncy'` | compose-1.12.1 | 它是 `Spring` 的伴生常量，不是顶层 | 用 `Spring.DampingRatioNoBouncy`（或别名 `ComposeSpring`） |
| `Unresolved reference 'em' on receiver of type 'Double'` | compose | `TextUnit.em` 是扩展属性 | 补 `import androidx.compose.ui.unit.em` |
| `Unresolved reference 'updateAll' on receiver of type 'KebiaoWidget'` | glance-1.2.0 | `updateAll` 是**顶层扩展**，不是 widget 类的成员 | 补 `import androidx.glance.appwidget.updateAll`，调用为 `updateAll<KebiaoWidget>(context)` |
| 单测源集 30+ 条 `Unresolved reference 'Test'` | agp-9.3.3 / kotlin-2.4.20 | AGP 9 内置 Kotlin 下 `testImplementation(kotlin("test"))` 只落到通用 artifact，不提供 JVM 端 `kotlin.test.Test` 注解 | 改用 JUnit4 变体 `testImplementation(kotlin("test-junit"))` |
| `Property delegate must have a getValue/setValue method` | compose | `by` 委托缺 `getValue`/`setValue` 扩展导入 | 补 `androidx.compose.runtime.getValue` / `setValue` |
| `'if' must have both main and 'else' branches when used as an expression` | kotlin | `if` 被当作表达式使用 | 补 `else`，或改用 `buildList { if (...) add(...) }` 等语句形态 |
| `Observed package id ... in inconsistent location 'platforms\android-37.0-2'` | SDK 环境 | SDK 组件安装**在收尾写包元数据时被中断**，目录缺 `package.xml` / `source.properties`，sdkmanager 重装为 `-2` 后缀目录 | 确认完整版（含 `package.xml`）并以重命名扶正到规范路径。**教训：环境搭建里"目录在、jar 在"不等于"安装完成"** |
| 构建日志中 `e:` 行被截断，导致错误定位失准 | Gradle 默认富文本 console | 默认 console 会**硬换行到 120 字符**，把报错行截断并混入续行 | **抓完整错误一律加 `--console=plain`**，配合 `--continue` 收集全部失败任务 |

### 11.2 已登记但刻意不修的技术债

| 项 | 位置 | 决定 | 理由 |
|----|------|------|------|
| `-Xjvm-default` 已废弃，应迁 `-jvm-default` | `app/build.gradle.kts` | **不修** | 新参数语义有变（`all`→`no-compatibility`），会改变接口默认方法字节码形态；对已通过的构建是无收益引入变量 |
| `assets.srcDir(...)` 废弃，应改 `directories` | `app/build.gradle.kts:62` | **不修** | 仅警告；AGP `directories` 迁移有细节差异，不值得为一条 warning 引入构建配置风险 |

---

## 12. 端到端验证步骤（Spec 锁定最后一项）

### 12.1 阶段一：工具链未就绪期（本机当前可跑，今天就能验证）

本机已有 Python 3.13.12 与 Node 22.22.2，**周次解析算法可立即交叉验证**——这是当前唯一能产出"可运行证据"的部分，且同时是导入兜底路径与 App 解析器的真实测试夹具。

```bash
# 1) 运行周次解析器交叉验证：对 §2.1 全部真实串比对 Python 与规范期望
python tools/verify_week_parser.py
# 断言：对 "2-16 双周" / "3-15 单周" / "9-16" / "2,3-4,5,8,6-7" / "1-20" 全部命中规范期望

# 2) 运行导入脚本（需人工完成 SSO 登录 + 短信码），产出真实课表 JSON
python tools/fetch_schedule.py --out build/schedule.json

# 3) 断言课程数与关键课的周次集合
python tools/assert_schedule.py build/schedule.json
# 断言：课程数 = 14（+2 备注）；大学物理B(1) 第2周 E教305、第3-16周 B105
```

### 12.2 阶段二：工具链就绪后（APK 构建验证）

```bash
# 0) 环境（全部落位 F 盘，不占 C 盘）
export JAVA_HOME="F:/AndroidDev/jdk-17"
export ANDROID_HOME="F:/AndroidDev/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

# 1) 空工程冒烟（先分离环境问题与依赖问题）
./gradlew --version && ./gradlew assembleDebug
# 断言：BUILD SUCCESSFUL，产出 app/build/outputs/apk/debug/app-debug.apk

# 2) 纯 JVM 单测（domain + parser，无需设备）
./gradlew :app:testDebugUnitTest
# 断言：周次解析边界用例、ScheduleCalculator、Semester.weekOf 全部通过

# 3) Lint
./gradlew :app:lintDebug

# 4) 安装并运行（真机优先，模拟器次之）
./gradlew :app:installDebug
adb shell am start -n com.kebiao.app/.MainActivity

# 5) 核心成功流人工核验
#   - 默认进入"今天"页，课程卡含 课程名 + 时间 + 地点
#   - 导入 2026-2027 学年第 1 学期（学期起始 2026-09-14）
#   - 切到另一学期再切回 → 3 秒内刷新，无旧课残留

# 6) 关键错误流
#   - 输入错误 SSO 密码 → 断言提示"账号或密码错误"，无半截数据
#   - 导入中断网 → 断言显示网络错误 + 重试，已入库数据完好
```

### 12.3 完成定义（Definition of Done）

- [ ] `./gradlew assembleDebug` 成功产出 APK
- [ ] `./gradlew :app:testDebugUnitTest` 全绿（含周次解析穷举用例）
- [ ] AC-01 ~ AC-07、AC-10 ~ AC-14、AC-20 ~ AC-23、AC-30 ~ AC-33、AC-60 ~ AC-62 全部通过
- [ ] P0 缺陷归零
- [ ] P0 红线全量扫描通过：**emoji 零命中 / 紫粉渐变零命中 / 硬编码色值仅出现在 Token 定义文件 / 玩具感缓动零命中**
- [ ] 真机安装并完成核心成功流与关键错误流

> **纪律（硬约束）**：在 §12.1 与 §12.2 的验证步骤真实执行之前，**任何"构建通过 / 测试通过 / 动画流畅 / 提醒已送达"的描述都不成立**，不得写入任何验收结论或交付话术。

---

## 13. 变更记录

| 日期 | 变更内容 | 原因 | 影响范围 |
|------|----------|------|----------|
| 2026-09-20 | Spec v1.0 创建，锁定 13 章节 | 用户三文档门禁确认后按 SOP 自动生成 | 全项目 |
| 2026-09-20 | 增补 U-1~U-4 用户裁决、D-1~D-7 总监裁决 | 门禁拍板 4 项 + 专家顾问建议裁定 | §0 |
| 2026-09-20 | 增补 §4.4 Android Studio 与 AGP 代差风险 + 处置方式 | 项目总监独立核验时新发现 | 构建路径、§12.2 |
| 2026-09-20 | 版本锁定表 19 项经 Google Maven / Maven Central 独立核验存在性 | 履行 `spec-as-contract` 版本锚定门禁 | §4.2 |
| 2026-09-20 | 增补 §11.1「真实构建才暴露的坑」11 条 + §11.2 技术债 2 条 | 首次 `assembleDebug` 实测暴露 AGP 9 内置 Kotlin、`colorControlNormal`、`sizeTransform` 迁移等纯文档阶段无法发现的坑 | §11 |
| 2026-09-20 | **构建门禁达成**：`BUILD SUCCESSFUL in 3m 13s` / 46 任务全量执行 / APK 16,537,081 字节 / SHA-256 `EE537D0C…48A1` / 38 单测全绿 | 工具链就位后首次全量构建通过，OD-009 关闭 | §12.3、`decisions/OPEN-DECISIONS.md` |
| 2026-09-20 | §12.3 完成定义中「构建成功」条目已达成，其余（真机安装与运行）保持未达成 | 严格执行"构建通过 ≠ 真机可用"的边界 | §12.3 |

---

## 14. 关联决策文档

| 文件 | 内容 |
|------|------|
| `docs/decisions/ADR-001-data-today-decoupling.md` | 数据与"今天"解耦（四层模型） |
| `docs/decisions/ADR-002-room-for-local-storage.md` | 选 Room 而非 DataStore / SQLDelight |
| `docs/decisions/ADR-003-schedule-import-path.md` | 教务导入路径（WebView 中继 + JSON 兜底） |
| `docs/decisions/ADR-004-class-reminder-alarmmanager.md` | AlarmManager 精确闹钟 + WorkManager 兜底 |
| `docs/decisions/ADR-005-widget-glance.md` | 小组件选 Glance 1.2.0 |
| `docs/decisions/ADR-006-min-sdk-and-api-levels.md` | minSdk 26 取舍 |
| `docs/decisions/ADR-007-version-lock-kotlin-agp-compose.md` | 版本锁定与保守回退组合 |
| `docs/decisions/ADR-008-manual-di-container.md` | 手写 AppContainer，不引 Hilt |
| `docs/decisions/OPEN-DECISIONS.md` | 悬而未决登记册（OD-001 ~ OD-009） |
