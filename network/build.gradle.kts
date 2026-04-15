import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bonial.network"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            val releaseProperties = Properties()
            val releasePropertiesFile = rootProject.file("release.properties")
            if (releasePropertiesFile.exists()) {
                releaseProperties.load(FileInputStream(releasePropertiesFile))
                buildConfigField("String", "BASE_URL", "\"${releaseProperties.getProperty("BASE_URL")}\"")
                buildConfigField("String", "ENVIRONMENT", "\"${releaseProperties.getProperty("ENVIRONMENT")}\"")
            } else {
                buildConfigField("String", "BASE_URL", "\"https://rickandmortyapi.com/api/\"")
                buildConfigField("String", "ENVIRONMENT", "\"production\"")
            }
        }
        getByName("debug") {
            val debugProperties = Properties()
            val debugPropertiesFile = rootProject.file("staging.properties")
            if (debugPropertiesFile.exists()) {
                debugProperties.load(FileInputStream(debugPropertiesFile))
                buildConfigField("String", "BASE_URL", "\"${debugProperties.getProperty("BASE_URL")}\"")
                buildConfigField("String", "ENVIRONMENT", "\"${debugProperties.getProperty("ENVIRONMENT")}\"")
            } else {
                buildConfigField("String", "BASE_URL", "\"https://rickandmortyapi.com/api/\"")
                buildConfigField("String", "ENVIRONMENT", "\"staging\"")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}

dependencies {
    implementation(project(":core"))

    api(libs.retrofit.core)
    api(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}
