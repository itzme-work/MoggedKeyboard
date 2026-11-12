plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// versionName задаётся здесь, versionCode — номер запуска CI, чтобы каждая
// следующая сборка была «новее» предыдущей и обновление ставилось поверх.
val appVersionName = "0.8.1"
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "").toIntOrNull() ?: 0
val buildVersionCode = if (runNumber > 0) runNumber else 1

// Ключ подписи НЕ лежит в репозитории: CI раскладывает его из секрета
// KEYSTORE_BASE64 во временный файл и передаёт путь в KEYSTORE_PATH.
val keystorePath = System.getenv("KEYSTORE_PATH").orEmpty()
val keystoreFile = if (keystorePath.isNotEmpty()) rootProject.file(keystorePath) else null
val ksPassword = System.getenv("KEYSTORE_PASSWORD").orEmpty()
val canSign = keystoreFile != null && keystoreFile.isFile && ksPassword.isNotEmpty()

android {
    namespace = "com.mogged.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mogged.keyboard"
        minSdk = 26
        targetSdk = 34
        versionCode = buildVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (canSign) {
            create("app") {
                storeFile = keystoreFile
                storeType = "PKCS12"
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

    lint {
        abortOnError = true
        // Текстовый отчёт печатается в лог CI при падении.
        textReport = true
        // Сборка не должна падать из-за «устаревших» версий библиотек.
        disable += setOf("GradleDependency", "OldTargetApi", "NewerVersionAvailable")
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
    testImplementation("junit:junit:4.13.2")
    // На устройстве JSON даёт Android, а в юнит-тестах нужна обычная JVM-реализация.
    testImplementation("org.json:json:20240303")
}
