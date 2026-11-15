plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.skypin.soundmate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.skypin.soundmate"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "2.7"
    }

    // Kunci rilis dibaca dari GitHub Secrets saat build, jadi tidak ada kunci atau password di kode.
    // Kalau variabelnya tidak ada (build di komputer biasa), APK rilis dibuat tanpa tanda tangan.
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("SOUNDMATE_KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storeType = "pkcs12"
                storePassword = System.getenv("SOUNDMATE_KEYSTORE_PASSWORD")
                keyAlias = "soundmate"
                keyPassword = System.getenv("SOUNDMATE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (System.getenv("SOUNDMATE_KEYSTORE_PATH") != null) {
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
