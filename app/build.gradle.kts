plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Номер запуска GitHub Actions как versionCode: каждая сборка новее предыдущей.
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "").toIntOrNull() ?: 0
val buildVersionCode = if (runNumber > 0) runNumber else 1

// Постоянный ключ подписи: если он есть, обновления ставятся поверх без удаления.
val keystoreFile = rootProject.file("keystore/mogged.jks")
val ksPassword = System.getenv("KEYSTORE_PASSWORD").orEmpty()
val canSign = keystoreFile.exists() && ksPassword.isNotEmpty()

android {
    namespace = "com.mogged.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mogged.keyboard"
        minSdk = 26
        targetSdk = 34
        versionCode = buildVersionCode
        versionName = "1.$buildVersionCode"
    }

    signingConfigs {
        if (canSign) {
            create("app") {
                storeFile = keystoreFile
                storePassword = ksPassword
                keyAlias = System.getenv("KEY_ALIAS") ?: "mogged"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ksPassword
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            if (canSign) signingConfig = signingConfigs.getByName("app")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("app")
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
}
