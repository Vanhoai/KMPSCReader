import org.gradle.kotlin.dsl.resolver.buildSrcSourceRootsFilePath
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true

            export(libs.mvvm.core)
            export(libs.mvvm.flow)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.mvvm.core)
            implementation(libs.mvvm.flow)
        }

        androidMain.dependencies {
            api(libs.mvvm.core)
            api(libs.mvvm.flow)
            api(libs.mvvm.flow.compose)

            api(libs.arrow.core)
            api(libs.arrow.fx.coroutines)

            api(libs.jmrtd)
            api(libs.prov)
        }

        iosMain.dependencies {
            api(libs.mvvm.core)
            api(libs.mvvm.flow)
        }
    }
}

android {
    namespace = "org.ic.tech.main.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
