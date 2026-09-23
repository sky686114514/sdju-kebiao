import org.jetbrains.kotlin.gradle.dsl.JvmTarget
// 必须显式 import：Kotlin DSL 里裸写 `java.util.Properties` 会被解析成
// JavaPluginExtension 访问器 `java` 加 `.util`，报 Unresolved reference 'util'。
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9.0 起内置 Kotlin，禁止再应用 org.jetbrains.kotlin.android（AGP 会拒绝）。
    // Compose 编译器插件仍需显式应用（AGP 内置 Kotlin 不替代它）。
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---- 正式签名（可选）----
// 密钥库与口令放在仓库外的 F:/AndroidDev/keystores/ 下，仓库内只留一个被 .gitignore
// 挡住的 keystore.properties 指过去。这份 properties **刻意不随仓库分发**：
// 签名私钥泄露后任何人都能伪造同包名的"升级包"，所以它不能进公开仓库。
//
// 别人 clone 后没有这个文件是**正常状态**，此时 release 构建退化为未签名产物，
// 但 debug 构建与单测必须照常可用 —— 所以这里全程判空，绝不让缺失把构建拖死。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystorePropsFile.exists() &&
    !keystoreProps.getProperty("storeFile").isNullOrBlank()

android {
    namespace = "com.kebiao.app"
    compileSdk = 37 // 由 Compose 1.12 强制（Spec 第 4.2 节），非自由选择

    defaultConfig {
        applicationId = "com.kebiao.app"
        minSdk = 26 // Android 8.0：java.time 原生可用，日期数学无需 desugaring（ADR-006）
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false // 自用不上架，不做混淆（Out-of-Scope）
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 缺 keystore.properties 时保持未签名，不报错（详见文件上方注释）
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // jsoup 1.23.2 在 Android 上依赖 NIO 能力，必须开启 core library desugaring
        // （Spec 第 11 节内嵌已知坑）。
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    // 刻意不声明 composeOptions.kotlinCompilerExtensionVersion：
    // Kotlin 2.0+ 起 Compose 编译器已并入 KGP，该配置已废弃（Spec 第 11 节）。

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true // 纯 JVM 单测不触碰 Android 框架默认值
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// Room schema 导出（exportSchema = true，Spec 第 6 节）。禁止 fallbackToDestructiveMigration。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    // ---- 平台与生命周期（版本见 gradle/libs.versions.toml，全部锚定）----
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    // 预测式返回要按起手边缘决定滑出方向，直接读 NavigationEvent.EDGE_* 常量；
    // 显式声明，不靠 navigation-compose 的传递依赖（避免版本漂移后静默编译失败）。
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.datastore.preferences)

    // ---- Compose（版本由 BOM 统一决定）----
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // ---- 存储 ----
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ---- 后台与小组件 ----
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // ---- 导入（会话抓取 + HTML 解析 + JSON 序列化）----
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)

    // ---- core library desugaring 运行时 ----
    // 未锚定项：Spec 第 4.2 节未对 desugar_jdk_libs 做版本锚定。2.1.5 为参考值，
    // 首次具备工具链时必须由 ./gradlew :app:dependencies 核验解析成功后再固化。
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // ---- 纯 JVM 单测 ----
    // 必须是 kotlin-test 的 **JUnit4 变体**，不能用 kotlin("test")：
    // AGP 9 内置 Kotlin 下 kotlin("test") 只落到通用 artifact，不提供
    // `kotlin.test.Test` 的 JVM 实际化（actual typealias -> org.junit.Test），
    // 会直接报 "Unresolved reference 'Test'"，整个单测源集编译不过。
    // kotlin("test-junit") 明确锁定 JUnit4 变体，版本随 KGP（Kotlin 2.4.20，已锚定）。
    testImplementation(kotlin("test-junit"))
    // 未锚定项：Spec 第 4.2 节将 androidx.test.* 列为未锚定并禁止凭印象填写；
    // junit:junit 未在锁定表内，4.13.2 为参考值 —— 已核验：解析成功（见构建日志 dependencyInsight）。
    testImplementation("junit:junit:4.13.2")
    // 真实 org.json（**仅测试运行期**）：JVM 单测里 android.jar 的 org.json 是空壳，
    // `isReturnDefaultValues` 会让 `optString` 静默返回 null，任何 JSON 解析类单测
    // 都会拿到"解析不出来"的假结果。**勿删**：删掉它 ImportEntryClickDescribeTest
    // 会 4 红（实测反证：`_counterfactual.log` -> "5 tests completed, 4 failed"），
    // 而且失败原因看起来像业务代码写错，极易误导后来人。
    // 不引入任何新测试框架，主工程运行时依赖一行未改。
    testImplementation("org.json:json:20240303")
}
