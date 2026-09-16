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

plugins {
    id("studyflow.android.application")
}

android {
    namespace = "com.mikeyphw.studyflow.tv"

    defaultConfig {
        applicationId = "com.mikeyphw.studyflow.tv"
        versionCode = 31
        versionName = "1.0.0-tv31"
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
        checkDependencies = true
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
    implementation(project(":core:link"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit4)
    testImplementation(kotlin("test-junit"))
}

// Release signing is a packaging-time requirement, not a configuration-time requirement.
// This keeps ordinary debug/source verification usable when a shell has only some release
// variables exported, while still preventing unsigned or partially configured TV releases.
tasks.configureEach {
    val releasePackagingTask =
        name == "assembleRelease" ||
            name == "bundleRelease" ||
            name.startsWith("packageRelease")
    if (releasePackagingTask) {
        doFirst {
            require(tvReleaseSigningConfigured) {
                if (tvReleaseSigningPartiallyConfigured) {
                    "StudyFlow TV release signing is only accepted when all STUDYFLOW_RELEASE_* variables are configured."
                } else {
                    "StudyFlow TV release packaging requires all STUDYFLOW_RELEASE_* variables."
                }
            }
        }
    }
}

// ATT12 v4 — Termux final-seal test stability.
tasks.withType<Test>().configureEach {
    maxParallelForks = 1
}
