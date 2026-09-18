plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/*
 * Version single source of truth.
 *
 * CI (GitHub Actions) sets GITHUB_RUN_NUMBER, so every published release gets a
 * strictly increasing versionCode and a matching versionName — which is also what
 * the in-app updater compares the release tag against. Local builds keep the
 * plain "1.0.0"/1 defaults, and `-PappVersionName=` / `-PappVersionCode=` can
 * override both for a one-off build.
 */
val ciRunNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.trim()?.toIntOrNull()
val baseVersionName: String = (project.findProperty("appVersionName") as String?)
    ?: ciRunNumber?.let { "1.0.$it" }
    ?: "1.0.0"
val resolvedVersionCode: Int = (project.findProperty("appVersionCode") as String?)?.toIntOrNull()
    ?: ciRunNumber?.let { 1 + it }
    ?: 1

android {
    namespace = "com.icy.devcheckplus"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.icy.devcheckplus"
        minSdk = 26
        targetSdk = 34
        versionCode = resolvedVersionCode
        versionName = baseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // collectAsStateWithLifecycle: lifecycle-aware flow collection for telemetry
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
