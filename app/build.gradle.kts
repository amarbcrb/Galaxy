plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "galaxy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "galaxy.companion"
        minSdk = 26
        targetSdk = 35
        // CI sets the run number so every published APK installs over the previous one.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.$versionCode"
    }

    // Release signing key, provided by CI from repository secrets so it never lives in the repo.
    // Without it, the release is signed with the debug key of the machine that builds it.
    val keystorePath = System.getenv("GALAXY_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("galaxy") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("GALAXY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GALAXY_KEY_ALIAS")
                keyPassword = System.getenv("GALAXY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("galaxy") ?: signingConfigs.getByName("debug")
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
    }
}

dependencies {
    implementation("galaxy:core")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("com.google.mlkit:face-detection:16.1.7")
}
