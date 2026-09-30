plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.feedoback.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.feedoback.example"
        minSdk = 24
        targetSdk = 35
        versionCode = 221
        versionName = "1.4.0"
    }

    buildTypes {
        // Signed with the debug key so `gradle installDebug` just works.
        getByName("debug") { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":feedoback"))
    implementation("androidx.appcompat:appcompat:1.7.0")
}
