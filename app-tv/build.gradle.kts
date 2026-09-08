val tvReleaseStoreFilePath = providers.environmentVariable("STUDYFLOW_RELEASE_STORE_FILE").orNull
val tvReleaseStorePassword = providers.environmentVariable("STUDYFLOW_RELEASE_STORE_PASSWORD").orNull
val tvReleaseKeyAlias = providers.environmentVariable("STUDYFLOW_RELEASE_KEY_ALIAS").orNull
val tvReleaseKeyPassword = providers.environmentVariable("STUDYFLOW_RELEASE_KEY_PASSWORD").orNull
val tvReleaseSigningInputs = listOf(
    tvReleaseStoreFilePath,
    tvReleaseStorePassword,
    tvReleaseKeyAlias,
    tvReleaseKeyPassword,
)
val tvReleaseSigningConfigured = tvReleaseSigningInputs.all { value -> !value.isNullOrBlank() }
val tvReleaseSigningPartiallyConfigured = tvReleaseSigningInputs.any { value -> !value.isNullOrBlank() } &&
    !tvReleaseSigningConfigured

require(!tvReleaseSigningPartiallyConfigured) {
    "StudyFlow TV release signing is only accepted when all STUDYFLOW_RELEASE_* variables are configured."
}

plugins {
    id("studyflow.android.application")
}

android {
    namespace = "com.mikeyphw.studyflow.tv"

    defaultConfig {
        applicationId = "com.mikeyphw.studyflow.tv"
        versionCode = 25
        versionName = "0.1.0-tv25"
        buildConfigField("boolean", "TV_RELEASE_SIGNING_CONFIGURED", tvReleaseSigningConfigured.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    if (tvReleaseSigningConfigured) {
        signingConfigs {
            create("studyFlowTvRelease") {
                storeFile = file(requireNotNull(tvReleaseStoreFilePath))
                storePassword = requireNotNull(tvReleaseStorePassword)
                keyAlias = requireNotNull(tvReleaseKeyAlias)
                keyPassword = requireNotNull(tvReleaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (tvReleaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("studyFlowTvRelease")
            }
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

// TVOV19/20 keep the TV APK isolated while making package identity, optional production signing,
// optimized Release packaging, and real-device acceptance independently verifiable.
dependencies {
    implementation(project(":core:tvcontrol"))
    implementation(project(":core:tvlink"))
    testImplementation(libs.junit4)
}
