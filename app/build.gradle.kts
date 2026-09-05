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

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
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

    // Coil (image loading)
    implementation(libs.coil.compose)

    // ML Kit (barcode scanning)
    implementation(libs.mlkit.barcode.scanning)

    // Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    // Background work
    implementation(libs.androidx.work.runtime)

    // Glance app widgets
    implementation(libs.glance.appwidget)

    // Play Billing
    implementation(libs.billing.ktx)

    // Health Connect
    implementation(libs.health.connect.client)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.health.connect.testing)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
