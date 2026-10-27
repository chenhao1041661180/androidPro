plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.androidpro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.androidpro"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug { buildConfigField("String", "BASE_URL", "\"https://jsonplaceholder.typicode.com/\"") }
        release {
            isMinifyEnabled = true
            buildConfigField("String", "BASE_URL", "\"https://api.example.com/\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:ui"))
    implementation(project(":core:list"))
    ksp(libs.room.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
}
