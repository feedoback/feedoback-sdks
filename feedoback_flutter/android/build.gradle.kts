// The Flutter side of the Android SDK.
//
// Thin on purpose: com.feedoback:feedoback-android is the SDK, resolved by
// coordinate rather than by path because that is what a customer's build
// resolves. Everything here is the bridge between a MethodCall and it.
group = "com.feedoback.feedoback_flutter"
version = "0.1.0"

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// The Android Gradle plugin only. Applying the Kotlin Gradle plugin from a
// Flutter plugin is what future Flutter releases refuse to build, and AGP
// brings its own Kotlin, so there is nothing to apply.
plugins {
    id("com.android.library")
}

android {
    namespace = "com.feedoback.feedoback_flutter"

    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") { java.srcDirs("src/main/kotlin") }
        getByName("test") { java.srcDirs("src/test/kotlin") }
    }

    defaultConfig {
        // The SDK's own floor. A Flutter app that supports less than this
        // already cannot take the dependency.
        minSdk = 24
    }

    testOptions {
        unitTests {
            all { it.useJUnitPlatform() }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("com.feedoback:feedoback-android:0.1.0")

    testImplementation(kotlin("test"))
}
