// 根构建脚本：只声明插件，不配置任何模块（入口只装配，Spec 第 10 节 / code-organization 第 4 条）。
//
// AGP 9.0 起内置 Kotlin 支持（默认开启）：**不得再应用 org.jetbrains.kotlin.android**，
// 否则 AGP 会主动拒绝并报 "no longer required for Kotlin support since AGP 9.0"。
// 官方来源：https://kotl.in/gradle/agp-built-in-kotlin
//
// AGP 9.3.3 内置的 KGP 版本（约 2.2.10）低于 Spec 第 4.2 节版本锁定表的 2.4.20，
// 按官方"升级到更高 KGP 版本"的说明，在 buildscript classpath 上覆盖。
// 注意：version catalog 在 buildscript 块内不可用，故此处写常量，需与 libs.versions.toml 的 kotlin 保持一致。
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    // org.jetbrains.kotlin.plugin.compose 仍需显式应用：AGP 9 内置 Kotlin 只替代
    // kotlin-android，不替代 Compose 编译器插件（官方 Compose 编译器迁移指南）。
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
