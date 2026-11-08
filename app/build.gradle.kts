plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fontschecker.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fontschecker.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Постоянная тестовая подпись: keystore лежит в репозитории,
    // поэтому каждая сборка подписана одним и тем же ключом.
    signingConfigs {
        create("test") {
            storeFile = file("test-signing.jks")
            storePassword = "fontschecker"
            keyAlias = "test"
            keyPassword = "fontschecker"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("test")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Чтение 7z-архивов (ZIP читается встроенными средствами)
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
}
