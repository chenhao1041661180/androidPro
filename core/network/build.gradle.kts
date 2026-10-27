plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.androidpro.core.network"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    buildTypes { release { isMinifyEnabled = false } }
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
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)
    testImplementation(libs.junit)
}
