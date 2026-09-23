# ADR-007: 锁定 Kotlin / AGP / Gradle / Compose BOM 的兼容组合

## Status
Accepted (2026-09-20)

## Background

Kotlin、AGP、Gradle、Compose BOM 四者版本**互相约束**，任意一个"按通用印象写"都可能得到无法解析或无法编译的组合。依 `spec-as-contract.md` 第 3 节与 `generated-code-failure-modes.md` 第 3 节，版本必须**先确认实际存在的版本号，再按该版本写 API**，禁止凭空指定。

联网核对（2026-09-20）得到的关键约束（来源见 ARCHITECTURE 第 18 章）：

- AGP 9.3 要求 Gradle ≥ 9.5.0（默认 9.5.0）、JDK 17、Build Tools 36.0.0，最高支持 API 37。
- AGP 9.4.0 要求 Gradle ≥ 9.6.0。
- Kotlin 2.4.20（2026-09-07）支持 Gradle 至 9.7.0。
- KSP1 与 Kotlin ≥ 2.3 / AGP ≥ 9 **不兼容**，必须用 KSP2。
- Compose BOM 2026.09.00 → Compose 1.12.1 + Material3 1.4.0；**Compose 1.12.0 起要求 compileSdk 37 与 AGP 9**。
- Hilt 的 Gradle 插件（Dagger 2.59 起）**要求 AGP 9 与 Gradle 9.1+**。
- androidx.hilt 1.4.0 要求 KGP ≥ 2.2.0。

## Decision

**锁定以下组合（互相校验通过）：**

| 组件 | 版本 | 备注 |
|------|------|------|
| JDK | **17 (LTS)** | AGP 9 最低要求；Temurin 或 Microsoft OpenJDK |
| Gradle（Wrapper） | **9.5.0** | 与 AGP 9.3 的默认值严格一致 |
| AGP | **9.3.3** | 已含三个补丁；支持 compileSdk 37 |
| Kotlin | **2.4.20** | 当前稳定版；Compose 编译器插件随 Kotlin（`org.jetbrains.kotlin.plugin.compose`） |
| KSP | **2.3.11** | **KSP2**（KSP1 与 Kotlin≥2.3 / AGP≥9 不兼容） |
| Compose BOM | **2026.09.00** | → Compose 1.12.1 + Material3 1.4.0 |
| compileSdk | **37** | 由 Compose 1.12 强制 |
| targetSdk | **36** | 见 ADR-006 |
| minSdk | **26** | 见 ADR-006 |
| Build Tools | **36.0.0** | AGP 9.3 默认 |

**依赖注入不引入 Hilt**（见 ADR-008），因此规避了 Hilt Gradle 插件对 AGP/Gradle 的额外版本耦合。

**版本管理方式**：单一版本目录 `gradle/libs.versions.toml` 作为版本的**唯一真相源**（符合 `context-engineering.md` 的"单一来源、避免多处漂移"）；禁止在模块 `build.gradle.kts` 里散写版本号。

**未锚定项**（诚实标注，构建时校验）：`kotlinx-coroutines-*`、`material3.adaptive-*`、各测试库版本在本次调研中未取得官方一手确认，**不写入锁定值**；写入 `libs.versions.toml` 前必须由构建环境解析成功（存在性核验）。

**保守回退组合**（若 AGP 9.x 出现阻塞）：

| 组件 | 回退值 |
|------|--------|
| AGP | 8.13.x |
| Gradle | 8.13 |
| Compose BOM | 2025.12.00（Compose 1.10 / Material3 1.4.0） |
| compileSdk | 36 |
| 其余 | 同主组合（Kotlin 2.4.20 与 AGP 8.13 的 KGP 兼容性需自行确认） |

回退代价：失去 Compose 1.12 的性能改进（pausable composition 默认启用、`Modifier.onPlaced` 优化等），对"页面切换与内容更新必须有过渡动画 + 性能预算"有轻微不利。

**另一处必须锁定的版本敏感项**：图标方案。Compose 官方明确 `androidx.compose.material.icons` **已从 Material3 最新 release 中移除**、不再推荐，替代品为 **Material Symbols**（VectorDrawable XML + `painterResource()`）。由于本项目锁定 Material3 1.4.0，代码中若出现 `Icons.Filled.*` 会**直接编译失败**。故 ADR-007 同时锁定：**全项目图标来源 = Material Symbols，单一来源、不得混用、不得用 emoji 充当功能图标**。

## Consequences

**正面**

- 四者版本经交叉校验，无"版本互斥"的构建期阻塞。
- Kotlin 2.4 + Compose BOM 2026.09 带来 Compose 1.12 的性能改进，直接服务动画与性能预算要求。
- 单一版本目录便于日后整体升级，也便于审计"某个依赖从哪来"。
- KSP2 + 无 Kapt，编译速度与增量构建更好。

**负面 / 风险**

- **整条链路很新**（AGP 9.3.3 / Kotlin 2.4.20 / Compose 1.12 均为 2026 年年中至 9 月发布）。对本机而言"新"还有一个放大效应：本机**没有 Android 工具链**（见 ARCHITECTURE 第 2 章），因此这套组合**尚未在本机验证过一次真实构建**。第一次 `assembleDebug` 的真正风险落在这里，而非代码逻辑。
- compileSdk 37 需额外下载 SDK Platform 37。
- 所有"未锚定项"必须在构建时补锚，不可凭印象填写。

**缓解**：优先按第 2.3 节安装工具链并执行一次"空工程构建冒烟"（模板工程 `assembleDebug` 成功）后，再引入全部依赖，避免把环境问题与依赖问题混在一起排查。

## Related ADRs
ADR-002（Room 2.8.5 + KSP 2.3.11）、ADR-005（Glance 1.2.0 需 AGP ≥9.2 / compileSdk 37）、ADR-006（SDK 级别）、ADR-008（不引入 Hilt 以避免额外版本耦合）
