plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
    signing
}

// What the coordinate reads as: com.feedoback:feedoback-android:0.1.0. The
// React Native and Flutter packages depend on this by coordinate rather than
// by path, because that is what a customer's build resolves.
group = "com.feedoback"
version = "0.1.0"

android {
    namespace = "com.feedoback"
    compileSdk = 35

    defaultConfig {
        // ~98% of devices, and enough for the APIs the sheet uses without
        // back-ports.
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        // The release variant only. A debug artifact on Maven Central would be
        // a second thing to get wrong and nothing anyone wants to ship.
        // Central refuses a release without both jars beside the aar.
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    testOptions {
        // Left false on purpose: an Android API this SDK calls without a real
        // implementation behind it should fail loudly in a test rather than
        // quietly answer zero. org.json is the one that matters, and the real
        // one is on the test classpath below.
        unitTests.isReturnDefaultValues = false
    }
}

dependencies {
    // Nothing but the platform and the coroutines the SDK's own API is built
    // on: this ships inside somebody else's app, and every dependency added
    // here is one their build has to resolve and their app has to carry.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
    // android.jar ships org.json as a stub that answers null for everything,
    // so a local unit test needs the real one. Test-only: nothing ships with it.
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "feedoback-android"
            afterEvaluate { from(components["release"]) }
            pom {
                name = "Feedoback Android"
                description = "Native feedback for Android apps."
                url = "https://feedoback.com"
                licenses {
                    license {
                        name = "MIT"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                developers {
                    developer {
                        id = "feedoback"
                        name = "Feedoback"
                        email = "hello@feedoback.com"
                        url = "https://feedoback.com"
                    }
                }
                // The public mirror, not the private repository the code is
                // written in: a POM is read by people who cannot see that one.
                scm {
                    url = "https://github.com/feedoback/feedoback-sdks"
                    connection = "scm:git:https://github.com/feedoback/feedoback-sdks.git"
                    developerConnection = "scm:git:ssh://git@github.com/feedoback/feedoback-sdks.git"
                }
                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/feedoback/feedoback-sdks/issues"
                }
            }
        }
    }
    repositories {
        // A plain directory in Maven layout. Central's Portal takes a zip of
        // exactly this, so the release uploads it rather than pulling in a
        // publishing plugin to do the same thing.
        maven {
            name = "staging"
            url = uri(layout.buildDirectory.dir("staging"))
        }
    }
}

// Signed only when a key is supplied, so a local publishToMavenLocal still
// works on a machine without one. Central refuses anything unsigned, which is
// the check that matters; the release script refuses to upload before it.
val signingKey = providers.environmentVariable("SIGNING_KEY").filter { it.isNotBlank() }
if (signingKey.isPresent) {
    signing {
        useInMemoryPgpKeys(signingKey.get(), providers.environmentVariable("SIGNING_PASSWORD").orNull)
        sign(publishing.publications["release"])
    }
}
