plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.androidpro.core.list"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    buildFeatures { viewBinding = true }
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
    api(project(":core:common"))
    api(project(":core:network"))
    api(project(":core:ui"))
    api(libs.androidx.paging.runtime)
    api(libs.androidx.swiperefreshlayout)
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.room.paging)
}
