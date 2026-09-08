plugins {
    id("studyflow.android.application")
}

android {
    namespace = "com.mikeyphw.studyflow.tv"

    defaultConfig {
        applicationId = "com.mikeyphw.studyflow.tv"
        versionCode = 1
        versionName = "0.1.0-tv18"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
        )
    }
}

// TVOV18 keeps the APK dependency-minimal while making the Release shrink path authoritative.
dependencies {
    implementation(project(":core:tvcontrol"))
    testImplementation(libs.junit4)
}
