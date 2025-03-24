plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.thirfir.exoplayersample"
    compileSdk = 35

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/gradle/incremental.annotation.processors"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
    defaultConfig {
        applicationId = "com.thirfir.exoplayersample"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.google.cloud.speech)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.stub)
    implementation(libs.arthenica.ffmpeg.kit.full)

    implementation(libs.glide)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.ui.text.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}