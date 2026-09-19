plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.huhst.jiaowu"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.huhst.jiaowu"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.3"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // 注意：这里刻意**不加** versionNameSuffix。
            // 「关于」页直接显示 PackageManager 里的 versionName，
            // 加后缀会显示成 1.0.2-debug，与产品版本号不符。
        }
        release {
            // 先用 debug 签名，保证可覆盖安装；正式发布前替换为自有 keystore。
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
        )
    }

    lint {
        abortOnError = false
    }
}

// AGP 9 内置 Kotlin 支持：无需再应用 kotlin-android 插件，
// jvmTarget 默认取 android.compileOptions.targetCompatibility（此处为 17）。

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
