plugins {
    id("studyflow.android.application")
}

android {
    namespace = "com.mikeyphw.studyflow.tv"

    defaultConfig {
        applicationId = "com.mikeyphw.studyflow.tv"
        versionCode = 1
        versionName = "0.1.0-tv12"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
    }

    lint {
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
        )
    }
}

// TVOV01 intentionally has no feature-module, Cast SDK, MediaRouter, AndroidX TV, or Compose runtime
// dependencies. Later TV overlays must add dependencies through measured size-budget contracts.
dependencies {
    implementation(project(":core:tvcontrol"))
    testImplementation(libs.junit4)
}
