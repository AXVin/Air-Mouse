plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val baseVersionName = "6.0"
val resolvedVersionName = (project.findProperty("appVersionName") as? String)?.ifBlank { null } ?: baseVersionName
val resolvedVersionCode = (project.findProperty("appVersionCode") as? String)?.toIntOrNull()
    ?: System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100)
    ?: 100

// Read signing keystore strictly from environment variables (populated via GitHub Actions Secrets)
val envKeystorePath = System.getenv("KEYSTORE_FILE")
val envKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
val envKeyAlias = System.getenv("KEY_ALIAS")
val envKeyPassword = System.getenv("KEY_PASSWORD")
val hasSecretKeystore = !envKeystorePath.isNullOrBlank() &&
    !envKeystorePassword.isNullOrBlank() &&
    !envKeyAlias.isNullOrBlank() &&
    !envKeyPassword.isNullOrBlank() &&
    file(envKeystorePath).exists()

android {
    namespace = "com.example.airmouse"
    compileSdk = 34

    signingConfigs {
        if (hasSecretKeystore) {
            create("release") {
                storeFile = file(envKeystorePath!!)
                storePassword = envKeystorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.axvin.airmouse"
        minSdk = 28 // Android 9.0+ required for BluetoothHidDevice API
        targetSdk = 34
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        base.archivesName.set("Air Mouse")
    }

    buildTypes {
        debug {
            if (hasSecretKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            if (hasSecretKeystore) {
                signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    // Zero external dependencies for fast, reliable compilation
}
