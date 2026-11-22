plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.islandpro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.islandpro"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.5"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
