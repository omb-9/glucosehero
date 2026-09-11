import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.omb9.glucosehero"
    compileSdk = 37 // Updated from 35

    defaultConfig {
        applicationId = "com.omb9.glucosehero"
        minSdk = 26
        targetSdk = 37 // Updated from 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Replaces the removed `android.kotlinOptions { jvmTarget = "17" }` block:
// Kotlin Gradle Plugin 2.4 turned the kotlinOptions/jvmTarget deprecation into
// a hard error, and compilerOptions is the supported equivalent.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Kotlin 2.0.20+ Compose compiler: Strong Skipping mode is enabled by default,
// which (together with @Immutable state + kotlinx immutable collections)
// keeps the 90-day log list recomposition-cheap.
// Compose compiler reports are disabled: on Windows, writing the report files
// throws java.io.IOException: Invalid file path (ModuleMetricsImpl.saveReportsTo),
// which fails compileDebugKotlin outright. Re-enable only when building on
// macOS/Linux/WSL, or once the upstream Kotlin plugin fixes Windows path handling.
// composeCompiler {
//     reportsDestination = layout.buildDirectory.dir("compose_reports")
// }

dependencies {
    // Core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.markdown.core)
    implementation(libs.markdown.m3)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // FEATURE: sqlcipher-migrations
    // net.zetetic:sqlcipher-android + SupportOpenHelperFactory. Room 2.8.4
    // already pulls androidx.sqlite:sqlite:2.6.2; do not force sqlite-ktx.
    implementation(libs.sqlcipher.android)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)

    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.play.services)

    // FEATURE: meal-nlp-food-swaps
    // Meal-photo JSON, NL/voice quick-log, and food swaps reuse kotlinx.serialization
    // plus the existing OpenAI-compatible AiApi client. Speech uses the platform
    // RecognizerIntent. No extra Gradle dependencies.

    // Coil (image loading)
    implementation(libs.coil.compose)

    // Google Play services code scanner (out-of-process barcode scanning)
    implementation(libs.play.services.code.scanner)

    // Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    // FEATURE: lttb-chart-perf
    // CGM downsampling (LTTB + SQLite 15 min / 1 hour buckets) is pure Kotlin and Room.
    // Vico still renders the reduced series. No extra chart dependency.

    // Background work
    implementation(libs.androidx.work.runtime)

    // Glance app widgets
    implementation(libs.glance.appwidget)

    // Play Billing
    implementation(libs.billing.ktx)

    // Health Connect
    implementation(libs.health.connect.client)

    // FEATURE: wear-os-companion
    implementation(libs.play.services.wearable)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // connect-testing 1.0.0-alpha04 transitively pulls connect-client 1.2.0-alpha05,
    // which changes ExerciseSessionRecord's default constructor. Main compiles
    // against 1.1.0, so unit tests then throw NoSuchMethodError. Keep the
    // test classpath on the same client as production.
    testImplementation(libs.health.connect.testing) {
        exclude(group = "androidx.health.connect", module = "connect-client")
        exclude(group = "androidx.health.connect", module = "connect-client-proto")
        exclude(group = "androidx.health.connect", module = "connect-client-external-protobuf")
    }
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)

    // FEATURE: agp-pdf-encrypted-backup
    // AGP PDFs use android.graphics.pdf.PdfDocument + Canvas (already on minSdk 26).
    // Encrypted backups use javax.crypto AES-GCM plus Android Keystore; WebDAV/Drive
    // upload uses the existing OkHttp client. No extra PDF or Drive SDK dependency.

    // FEATURE: widget-iob-backup-sanitize
    // Widget mini-trend is a fixed 120x36 PNG (java.util.zip Deflater). IOB uses
    // the Walsh activity triangle in Kotlin math. Backup restore uses kotlinx
    // serialization with unknown keys rejected. No extra Gradle dependencies.
}
