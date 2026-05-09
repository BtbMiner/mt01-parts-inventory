@file:Suppress("UnstableApiUsage")

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kemtdm.mt01"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kemtdm.mt01"
        minSdk = 31
        targetSdk = 37
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

androidComponents {
    onVariants { variant ->
        val appName = project.rootProject.name
        val date = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
        val buildTypeName = variant.buildType ?: variant.name

        // Set the output file name for the APK
        variant.outputs.forEach { output ->
            output.outputFileName.set("${appName}-${date}.apk")
        }

        // Keep the copy task for convenience (moves APK to a predictable folder)
        val capitalizedVariantName = variant.name.replaceFirstChar { it.uppercase() }
        val renameTask = tasks.register<Copy>("copy${capitalizedVariantName}Apk") {
            val apkFolder = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK)
            from(apkFolder)
            include("*.apk")
            destinationDir = file("${project.layout.buildDirectory.get()}/outputs/custom-apk")
            
            doLast {
                println("APK also copied to: ${destinationDir.absolutePath}")
            }
        }

        tasks.matching { it.name == "assemble$capitalizedVariantName" }.configureEach {
            finalizedBy(renameTask)
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.jtds)
    implementation(libs.okhttp)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing)
}
