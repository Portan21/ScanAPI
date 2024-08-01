plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlin-kapt")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.example.scanapi"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.scanapi"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures{
        buildConfig = true
    }
    buildTypes {
        release {
            // Set to true when release
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "SUPABASE_URL", "\"https://ovlwakimblgemkcvoivj.supabase.co\"")
            buildConfigField("String", "SUPABASE_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im92bHdha2ltYmxnZW1rY3ZvaXZqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MjIyMzkzNDEsImV4cCI6MjAzNzgxNTM0MX0.2ftfAcp-vprOsaH92vPhO5iSsYME0nSA9fOoH-RT-tE\"")
            buildConfigField("String", "ROBOFLOW_API_KEY", "\"4LF1NTVpUpZP66V6YLKr\"")
        }
        debug{
            buildConfigField("String", "SUPABASE_URL", "\"https://ovlwakimblgemkcvoivj.supabase.co\"")
            buildConfigField("String", "SUPABASE_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im92bHdha2ltYmxnZW1rY3ZvaXZqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MjIyMzkzNDEsImV4cCI6MjAzNzgxNTM0MX0.2ftfAcp-vprOsaH92vPhO5iSsYME0nSA9fOoH-RT-tE\"")
            buildConfigField("String", "ROBOFLOW_API_KEY", "\"4LF1NTVpUpZP66V6YLKr\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    // Retrofit for network requests
    implementation (libs.retrofit)
    implementation (libs.converter.gson)
    implementation (libs.okhttp)
    implementation (libs.logging.interceptor)


    // CameraX dependencies
    implementation (libs.androidx.camera.core)
    implementation (libs.androidx.camera.camera2)
    implementation (libs.androidx.camera.lifecycle)
    implementation (libs.androidx.camera.view)

    implementation (libs.androidx.camera.camera.core.v110alpha08.x3)
    implementation (libs.androidx.camera.camera2.v110alpha08)
    implementation (libs.androidx.camera.lifecycle.v110alpha08)
    implementation (libs.androidx.camera.view.v100alpha23)
    implementation (libs.androidx.camera.extensions)

    // Activity Result API
    implementation (libs.androidx.activity.ktx) // Check for the latest version on [Maven Central](https://search.maven.org/)
    implementation (libs.androidx.fragment.ktx) // Also check for the latest version

    implementation (libs.gson)

    implementation (libs.squareup.picasso)

    // text-to-speech
    implementation (libs.androidx.core.ktx.v170)

    // Database
    implementation ("androidx.room:room-runtime:2.6.1")
    kapt ("androidx.room:room-compiler:2.6.1")

    // Optional - Kotlin Extensions and Coroutines support for Room
    implementation ("androidx.room:room-ktx:2.6.1")

    // Coroutines
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    // Supabase
    implementation (platform("io.github.jan-tennert.supabase:bom:2.5.4"))
    implementation ("io.github.jan-tennert.supabase:postgrest-kt")
    implementation ("io.ktor:ktor-client-android:2.3.12")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}