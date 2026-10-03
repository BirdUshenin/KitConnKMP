plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kitconn.android"
    compileSdk = 37

    defaultConfig {
        // Отдельный id, чтобы KMP-версию можно было ставить рядом с текущим com.kitconnvpn.
        // Когда она заменит старое приложение, вернуть "com.kitconnvpn" (и тот же ключ подписи).
        applicationId = "com.kitconnvpn.kmp"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "4.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // Ядро Xray для Android (gomobile-сборка); на iOS будет своя
    implementation(files("libs/libv2ray.aar"))
}
