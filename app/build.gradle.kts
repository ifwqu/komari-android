import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 读取 keystore 配置：仓库内 keystore/keystore.properties（个人测试分发），
// 或环境变量 KEYSTORE_B64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD（GitHub Secrets 迁移用）
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) {
        FileInputStream(f).use { load(it) }
    }
}

fun secret(name: String): String? = System.getenv(name) ?: keystoreProps[name] as String?

val keystoreFile = rootProject.file("keystore/komari-release.jks")
val keystoreEnvB64 = secret("KEYSTORE_B64")
val hasKeystore = keystoreFile.exists() || !keystoreEnvB64.isNullOrBlank()

android {
    namespace = "com.komari.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.komari.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "2.3.0"
    }

    signingConfigs {
        if (hasKeystore) {
            create("releaseSign") {
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                } else {
                    // 从环境变量解码 keystore 到构建目录（CI + GitHub Secrets 场景）
                    val dir = java.io.File(project.buildDir, "signing")
                    dir.mkdirs()
                    val decoded = java.util.Base64.getDecoder().decode(keystoreEnvB64)
                    storeFile = java.io.File(dir, "komari-release.jks").apply { writeBytes(decoded) }
                }
                storePassword = secret("KEYSTORE_PASSWORD") ?: error("缺少 KEYSTORE_PASSWORD")
                keyAlias = secret("KEY_ALIAS") ?: "komari"
                keyPassword = secret("KEY_PASSWORD") ?: error("缺少 KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 正式签名（keystore）：签名一致后可直接覆盖安装；未配置时回退调试签名
            signingConfig = if (hasKeystore) {
                signingConfigs.getByName("releaseSign")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}