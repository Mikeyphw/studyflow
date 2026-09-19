import java.io.File
import java.util.Properties

val studyFlowTvRepositoryRoot = rootProject.projectDir.canonicalFile
val studyFlowTvSigningPropertiesFile = File(
    providers.environmentVariable("STUDYFLOW_ANDROID_SIGNING_PROPERTIES").orNull
        ?: File(System.getProperty("user.home"), ".config/studyflow/android-signing.properties").absolutePath,
).absoluteFile
val studyFlowTvPersonalSigningConfigured = studyFlowTvSigningPropertiesFile.isFile
val studyFlowTvSigningProperties = Properties().apply {
    if (studyFlowTvPersonalSigningConfigured) studyFlowTvSigningPropertiesFile.inputStream().use { load(it) }
}
fun studyFlowTvSigningValue(name: String): String {
    val value = studyFlowTvSigningProperties.getProperty(name).orEmpty()
    check(value.isNotEmpty() && value.trim() != "REPLACE_ME") {
        "StudyFlow personal signing property '$name' is missing in ${studyFlowTvSigningPropertiesFile.absolutePath}"
    }
    return value
}
val studyFlowTvPersonalSigningStoreFile = if (studyFlowTvPersonalSigningConfigured) {
    check(!studyFlowTvSigningPropertiesFile.canonicalFile.toPath().startsWith(studyFlowTvRepositoryRoot.toPath())) {
        "StudyFlow personal signing properties must live outside the repository"
    }
    File(studyFlowTvSigningValue("storeFile")).canonicalFile.also { store ->
        check(store.isAbsolute && store.isFile) { "StudyFlow personal signing keystore does not exist: ${store.absolutePath}" }
        check(!store.toPath().startsWith(studyFlowTvRepositoryRoot.toPath())) { "StudyFlow personal signing keystore must live outside the repository" }
    }
} else null

val tvReleaseStoreFilePath = providers.environmentVariable("STUDYFLOW_RELEASE_STORE_FILE").orNull
val tvReleaseStorePassword = providers.environmentVariable("STUDYFLOW_RELEASE_STORE_PASSWORD").orNull
val tvReleaseKeyAlias = providers.environmentVariable("STUDYFLOW_RELEASE_KEY_ALIAS").orNull
val tvReleaseKeyPassword = providers.environmentVariable("STUDYFLOW_RELEASE_KEY_PASSWORD").orNull
val tvReleaseSigningInputs = listOf(tvReleaseStoreFilePath, tvReleaseStorePassword, tvReleaseKeyAlias, tvReleaseKeyPassword)
val tvReleaseSigningConfigured = tvReleaseSigningInputs.all { value -> !value.isNullOrBlank() }
val tvReleaseSigningPartiallyConfigured = tvReleaseSigningInputs.any { value -> !value.isNullOrBlank() } && !tvReleaseSigningConfigured

plugins {
    id("studyflow.android.application")
}

android {
    namespace = "com.mikeyphw.studyflow.tv"

    defaultConfig {
        applicationId = "com.mikeyphw.studyflow.tv"
        versionCode = 31
        versionName = "1.0.0-tv31"
        buildConfigField("boolean", "TV_RELEASE_SIGNING_CONFIGURED", (studyFlowTvPersonalSigningConfigured || tvReleaseSigningConfigured).toString())
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (studyFlowTvPersonalSigningConfigured) {
            create("studyFlowTvPersonal") {
                storeFile = studyFlowTvPersonalSigningStoreFile
                storePassword = studyFlowTvSigningValue("storePassword")
                keyAlias = studyFlowTvSigningValue("keyAlias")
                keyPassword = studyFlowTvSigningValue("keyPassword")
            }
        } else if (tvReleaseSigningConfigured) {
            create("studyFlowTvRelease") {
                storeFile = file(requireNotNull(tvReleaseStoreFilePath))
                storePassword = requireNotNull(tvReleaseStorePassword)
                keyAlias = requireNotNull(tvReleaseKeyAlias)
                keyPassword = requireNotNull(tvReleaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (studyFlowTvPersonalSigningConfigured) signingConfig = signingConfigs.getByName("studyFlowTvPersonal")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (studyFlowTvPersonalSigningConfigured) {
                signingConfig = signingConfigs.getByName("studyFlowTvPersonal")
            } else if (tvReleaseSigningConfigured) {
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
// Permanent personal signing satisfies the release-signing requirement when configured.
tasks.configureEach {
    val releasePackagingTask =
        name == "assembleRelease" ||
            name == "bundleRelease" ||
            name.startsWith("packageRelease")
    if (releasePackagingTask) {
        doFirst {
            require(studyFlowTvPersonalSigningConfigured || tvReleaseSigningConfigured) {
                if (tvReleaseSigningPartiallyConfigured) {
                    "StudyFlow TV release signing is only accepted when all STUDYFLOW_RELEASE_* variables are configured."
                } else {
                    "StudyFlow TV release packaging requires permanent personal signing or all STUDYFLOW_RELEASE_* variables."
                }
            }
        }
    }
}

// ATT12 v4 — Termux final-seal test stability.
tasks.withType<Test>().configureEach {
    maxParallelForks = 1
}

// Permanent personal signing convenience entry points. The same external key is used by phone and TV.
tasks.register("studyFlowSigningStatus") {
    group = "help"
    description = "Show whether StudyFlow TV debug and release use the permanent personal certificate."
    doLast {
        if (studyFlowTvPersonalSigningConfigured) {
            println("StudyFlow TV personal signing: configured")
            println("Properties: ${studyFlowTvSigningPropertiesFile.absolutePath}")
            println("Keystore: ${studyFlowTvPersonalSigningStoreFile!!.absolutePath}")
            println("Alias: ${studyFlowTvSigningValue("keyAlias")}")
            println("TV debug and release APKs use the same permanent certificate.")
        } else {
            println("StudyFlow TV personal signing: NOT configured")
            println("Expected properties: ${studyFlowTvSigningPropertiesFile.absolutePath}")
            println("Run bash tools/setup-personal-signing.sh before installing permanent StudyFlow builds.")
        }
    }
}

tasks.register("verifyStudyFlowTvPersonalSigningConfigured") {
    group = "verification"
    description = "Fail unless permanent StudyFlow personal signing is configured for TV."
    doLast {
        check(studyFlowTvPersonalSigningConfigured && studyFlowTvPersonalSigningStoreFile?.isFile == true) {
            "StudyFlow permanent personal signing is not configured. Run bash tools/setup-personal-signing.sh."
        }
        println("StudyFlow TV permanent personal signing configuration: PASS")
    }
}

tasks.register("assemblePersonalDebug") {
    group = "build"
    description = "Build the TV debug APK with the permanent StudyFlow personal certificate."
    dependsOn("verifyStudyFlowTvPersonalSigningConfigured", "assembleDebug")
}

tasks.register("assemblePersonalRelease") {
    group = "build"
    description = "Build the TV release APK with the permanent StudyFlow personal certificate."
    dependsOn("verifyStudyFlowTvPersonalSigningConfigured", "assembleRelease")
}
