# ADR-006: 最低支持 Android 8.0（minSdk 26），targetSdk 36，compileSdk 37

## Status
Accepted (2026-09-20)

## Background

三个 SDK 级别各自被不同因素约束，不能混为一谈：

- **compileSdk**：由依赖强制。Compose 官方文档明确"自 Compose 1.12.0 起，项目必须使用 compileSdk 37 与 AGP 9"。我们锁定的 Compose BOM 2026.09.00 指向 Compose 1.12.1，因此 compileSdk 事实上已被锁死。
- **targetSdk**：决定系统对 App 应用的**行为变更**范围。Google Play 自 2026-08-31 起要求新应用/更新 target API 36+；但本项目**不上架**，因此该要求不构成约束，只是"跟进行为标准"的参考。
- **minSdk**：决定覆盖的设备范围与可用 API。PRD 第 11 节已写入"Android 8.0 及以上"，需要架构侧确认其技术后果并锚定。

minSdk 候选：

| 候选 | 覆盖 | 关键技术影响 |
|------|------|-------------|
| API 24（Android 7.0） | 更广 | `java.time` **不可用**，需开启 core library desugaring；通知渠道不存在，需兼容分支 |
| **API 26（Android 8.0）** | 2026 年几乎全部在用设备 | **`java.time` 原生可用**；通知渠道（`NotificationChannel`）为强制模型；自适应图标 |
| API 29（Android 10） | 略窄 | 分区存储；收益不明显 |
| API 31（Android 12） | 更窄 | Material You 动态取色原生；但无谓牺牲覆盖 |

## Decision

| 参数 | 取值 | 依据 |
|------|------|------|
| `compileSdk` | **37** | Compose 1.12 强制（不可协商） |
| `targetSdk` | **36** | 与 Play 当前要求一致，但不上架；取 36 而非 37 以规避 Android 17 首日行为变更中的未知项 |
| `minSdk` | **26**（Android 8.0） | 与 PRD 第 11 节一致；`java.time` 原生可用（见下） |
| SDK Build Tools | **36.0.0** | AGP 9.3 的默认值 |

**minSdk 26 的决定性技术理由**：本项目"数据与今天解耦"的核心是日期数学（`LocalDate` / `DayOfWeek` / `ChronoUnit` / `LocalTime`），即 `java.time` 包。**`java.time` 在 API 26 起原生可用**；若把 minSdk 压到 24 或 21，则必须启用 core library desugaring 来向后移植 `java.time` —— 这会引入额外的构建配置、APK 体积与一类潜在的 desugaring 行为差异，对"早八不迟到"这类依赖日期计算正确的场景是**净负面**。

同时 API 26 起 `NotificationChannel` 成为强制模型，提醒功能（ADR-004）本就只会跑在 26+ 的模型上，不存在兼容分支；PRD 的"国产 ROM 后台策略"适配也集中在较新系统上。

**关于 minSdk 26 的覆盖判断**：本项目是**单用户自用**，用户设备（用户本人的 Android 手机）几乎必然为 Android 8.0 以上。因此"覆盖面"不是选择依据，**"技术简化的收益"才是**——这与商业产品 minSdk 决策的权衡方向不同，属于本项目性质带来的合理简化。

## Consequences

**正面**

- 用 `java.time` 直接实现 `Semester.weekOf(date)` 等纯函数，无需 desugaring，无跨 API 行为差异风险。
- 无 `NotificationChannel` 兼容分支，提醒代码路径唯一。
- 无 `java.time` 的 desugaring 相关潜在坑（例如 `ChronoUnit` / `DayOfWeek` 在旧 API 上的边缘行为）。

**负面 / 风险**

- 若用户的实际设备低于 Android 8.0（极小概率），需下调 minSdk 至 24 并开启 core library desugaring。**此为可判定条件，已登记 OD-006**（触发条件：确认用户设备 API < 26）。
- `targetSdk 36` 意味着需遵守 Android 16 的行为变更（如 edge-to-edge 强制、通知样式等）。ARCHITECTURE 10.3 已按"默认 edge-to-edge + token 化主题"设计以承接该要求。
- compileSdk 37 要求安装 `platforms;android-37`（约 60-90 MB，见第 2.3 节安装清单）。

## Related ADRs
ADR-001（`weekOf` 纯函数依赖 `java.time`）、ADR-002（Room 与 minSdk 无冲突）、ADR-004（精确闹钟行为随 targetSdk 变化）、ADR-007（版本锁定组合）
