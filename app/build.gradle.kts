plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.tvairmouse"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.tvairmouse"
        minSdk = 28 // Android 9.0+ required for BluetoothHidDevice API
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    // Uses zero external libraries so builds succeed rapidly and reliably
}
