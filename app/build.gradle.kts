plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/*
 * CI injects the release identity so the tag on GitHub Releases, the APK and
 * BuildConfig always agree - the in-app updater compares that tag against
 * BuildConfig.VERSION_NAME, so a drift there means "update available" forever.
 */
val ciVersionName = (project.findProperty("devcheckVersionName") as String?)?.takeIf { it.isNotBlank() }
val ciVersionCode = (project.findProperty("devcheckVersionCode") as String?)?.toIntOrNull()

// Committed CI key by default; overridable per developer/secret without edits.
val keystorePath = (project.findProperty("devcheckKeystore") as String?)?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
    ?: rootProject.file("keystore/devcheck-ci.jks")
val keystoreAvailable = keystorePath.exists()

android {
    namespace = "com.icy.devcheckplus"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.icy.devcheckplus"
        minSdk = 26
        targetSdk = 34
        versionCode = ciVersionCode ?: 1
        versionName = ciVersionName ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        /*
         * Release builds must keep a stable signature: Android refuses to update
         * an install whose signing certificate changed, which would break the
         * in-app updater. See keystore/README.md.
         */
        create("devcheck") {
            if (keystoreAvailable) {
                storeFile = keystorePath
                storePassword = (project.findProperty("devcheckStorePassword") as String?) ?: "devcheckci"
                keyAlias = (project.findProperty("devcheckKeyAlias") as String?) ?: "devcheck"
                keyPassword = (project.findProperty("devcheckKeyPassword") as String?) ?: "devcheckci"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreAvailable) {
                signingConfig = signingConfigs.getByName("devcheck")
            } else {
                logger.warn("No signing keystore at ${'$'}keystorePath - producing an unsigned release APK.")
            }
        }
        debug {
            applicationIdSuffix = ""
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    // Process-wide foreground state (stops every poller when the app backgrounds)
    // plus lifecycle-aware flow collection in Compose.
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose Material 3 & Icons
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Root via libsu
    implementation("com.github.topjohnwu.libsu:core:5.3.0")

    // Shizuku
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // DataStore preferences: pinned dashboard items (1.1.x keeps compileSdk 34 happy)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
