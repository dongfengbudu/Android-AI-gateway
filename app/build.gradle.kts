import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * release 签名：从 keystore.properties 读，仅在文件存在时装配。
 *
 * debug 包（本地与 CI，见 .github/workflows/android-debug.yml）不需要 keystore.properties；
 * 只有本地手动出 release 包时才提供该文件。文件缺失时不在配置阶段报错，
 * 但 release 构建会因无签名配置而失败——正是期望行为。
 */
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val hasReleaseKeystore = rootProject.file("keystore.properties").exists()

fun keystoreValue(key: String): String =
    keystoreProps.getProperty(key)
        ?: error("缺少签名配置 $key：请检查根目录 keystore.properties")

android {
    namespace = "dev.aigw.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "dev.aigw.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 105
        versionName = "1.1.8"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        // 固定调试密钥（仓库根目录 debug.keystore，PKCS12，标准调试口令，无保密性）：
        // 签名跨次构建稳定，历次 CI debug 包可直接覆盖升级。
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "PKCS12"
        }

        // 只有存在 keystore.properties 时才创建 release 签名；
        // debug 包不依赖该文件。
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreValue("storeFile"))
                storePassword = keystoreValue("storePassword")
                keyAlias = keystoreValue("keyAlias")
                keyPassword = keystoreValue("keyPassword")
            }
        }
    }

    buildTypes {
        // 两个变体都开 R8。注意 AGP 对 debuggable 构建会禁用「优化与混淆」
        // （构建时会提示 “All code optimizations and obfuscation are disabled for
        // debuggable builds”），但**代码裁剪照常生效**——这才是体积的大头。
        //
        // debug = 开发包（本地与 CI）：包名与 release 完全一致（dev.aigw.app），
        // 用仓库固定的调试密钥签名。两者签名不同，不能同机共存，也不能相互覆盖安装（换装需先卸载）。
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // release = 本地备好 keystore.properties 后手动出包；CI 只构建 debug。
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    // :core 用 implementation 引 gson，不传递；app 里要直接构造 JsonObject（如动作 payload）
    implementation("com.google.code.gson:gson:2.11.0")

    // Compose 版本与 miuix 0.8.8 的传递依赖对齐（JB CMP 1.10.3 → androidx 1.10.5）。
    // 不用 BOM：旧 BOM 2024.12.01 会把 ui/foundation 钉在 1.7.6，与 1.10.5 混版本运行。
    implementation("androidx.compose.ui:ui:1.10.5")
    implementation("androidx.compose.foundation:foundation:1.10.5")
    implementation("androidx.core:core-ktx:1.15.0")
    // ≥1.12.0 的 ComponentActivity 才实现 NavigationEventDispatcherOwner 并挂 ViewTree owner；
    // miuix 0.8.8 的弹窗（NavigationBackHandler）依赖它，钉 1.9.3 会让所有 WindowDialog 弹出即崩。
    // 1.13.0 可能要求 compileSdk 37，取 1.12 系最新补丁。
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Miuix（HyperOS 风格 UI 库）；强调色通过 MiuixTheme 自定义 Colors 注入。
    // 0.9.x 要求 compileSdk 37 + AGP 9.1（超出本地工具链），故取 0.8.8：
    // 单构件 miuix-android（basic/theme/utils/extra 组件），extended 图标在 miuix-icons。
    implementation("top.yukonga.miuix.kmp:miuix-android:0.8.8")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.8.8")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    debugImplementation("androidx.compose.ui:ui-tooling:1.10.5")
}
