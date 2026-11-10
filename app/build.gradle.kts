plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.airmouse"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.axvin.airmouse"
        minSdk = 28 // Android 9.0+ required for BluetoothHidDevice API
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"
        setProperty("archivesBaseName", "Air Mouse")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // Zero external dependencies for fast, reliable compilation
}
