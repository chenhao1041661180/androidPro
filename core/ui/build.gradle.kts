plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.androidpro.core.ui"
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
    api(libs.androidx.core.ktx)
    api(libs.androidx.activity.ktx)
    api(libs.androidx.appcompat)
    api(libs.androidx.fragment.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.recyclerview)
    api(libs.androidx.paging.runtime)
    api(libs.material)
    api(libs.brvah)
    api(libs.shape.view)
    api(libs.state.layout)
}
