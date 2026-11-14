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
        versionCode = 7
        versionName = "2.5"
    }

    signingConfigs {
        create("soundmate") {
            storeFile = file("soundmate.jks")
            storeType = "jks"
            storePassword = "soundmate969"
            keyAlias = "soundmate"
            keyPassword = "soundmate969"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("soundmate")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("soundmate")
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
