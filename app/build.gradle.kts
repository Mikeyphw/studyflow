import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Properties

abstract class StudyFlowSigningStatusTask : DefaultTask() {
    @get:Input abstract val configured: Property<Boolean>
    @get:Input abstract val propertiesPath: Property<String>
    @get:Input abstract val keystorePath: Property<String>
    @get:Input abstract val keyAlias: Property<String>

    @TaskAction fun reportStatus() {
        if (configured.get()) {
            println("StudyFlow personal signing: configured")
            println("Properties: ${propertiesPath.get()}")
            println("Keystore: ${keystorePath.get()}")
            println("Alias: ${keyAlias.get()}")
            println("Phone debug and release APKs use the same permanent certificate.")
        } else {
            println("StudyFlow personal signing: NOT configured")
            println("Expected properties: ${propertiesPath.get()}")
            println("Run bash tools/setup-personal-signing.sh before installing permanent StudyFlow builds.")
        }
    }
}

abstract class VerifyStudyFlowPersonalSigningConfiguredTask : DefaultTask() {
    @get:Input abstract val configured: Property<Boolean>
    @get:Input abstract val keystorePath: Property<String>

    @TaskAction fun verifyConfigured() {
        check(configured.get() && File(keystorePath.get()).isFile) {
            "StudyFlow permanent personal signing is not configured. Run bash tools/setup-personal-signing.sh."
        }
        println("StudyFlow permanent personal signing configuration: PASS")
    }
}

val studyFlowManifestText = file("src/main/AndroidManifest.xml").readText()

fun manifestLinksXml(attribute: String, resourceName: String): Boolean = Regex(
    "android:$attribute\\s*=\\s*\"@xml/$resourceName\"",
).containsMatchIn(studyFlowManifestText)

val legacyBackupRuleLinked = manifestLinksXml("fullBackupContent", "backup_rules") &&
    file("src/main/res/xml/backup_rules.xml").isFile
val modernBackupRuleLinked = manifestLinksXml("dataExtractionRules", "data_extraction_rules") &&
    file("src/main/res/xml/data_extraction_rules.xml").isFile

val studyFlowRepositoryRoot = rootProject.projectDir.canonicalFile
val studyFlowSigningPropertiesFile = File(
    providers.environmentVariable("STUDYFLOW_ANDROID_SIGNING_PROPERTIES").orNull
        ?: File(System.getProperty("user.home"), ".config/studyflow/android-signing.properties").absolutePath,
).absoluteFile
val studyFlowPersonalSigningConfigured = studyFlowSigningPropertiesFile.isFile
val studyFlowSigningProperties = Properties().apply {
    if (studyFlowPersonalSigningConfigured) studyFlowSigningPropertiesFile.inputStream().use { load(it) }
}
fun studyFlowSigningValue(name: String): String {
    val value = studyFlowSigningProperties.getProperty(name).orEmpty()
    check(value.isNotEmpty() && value.trim() != "REPLACE_ME") {
        "StudyFlow personal signing property '$name' is missing in ${studyFlowSigningPropertiesFile.absolutePath}"
    }
    return value
}
val studyFlowPersonalSigningStoreFile = if (studyFlowPersonalSigningConfigured) {
    check(!studyFlowSigningPropertiesFile.canonicalFile.toPath().startsWith(studyFlowRepositoryRoot.toPath())) {
        "StudyFlow personal signing properties must live outside the repository: ${studyFlowSigningPropertiesFile.absolutePath}"
    }
    File(studyFlowSigningValue("storeFile")).canonicalFile.also { store ->
        check(store.isAbsolute && store.isFile) { "StudyFlow personal signing keystore does not exist: ${store.absolutePath}" }
        check(!store.toPath().startsWith(studyFlowRepositoryRoot.toPath())) {
            "StudyFlow personal signing keystore must live outside the repository: ${store.absolutePath}"
        }
    }
} else null

// Legacy release-only environment variables remain a compatibility fallback. Personal signing wins.
val releaseStoreFilePath = providers.environmentVariable("STUDYFLOW_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("STUDYFLOW_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("STUDYFLOW_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("STUDYFLOW_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(releaseStoreFilePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { value -> !value.isNullOrBlank() }

plugins {
    id("studyflow.android.application")
}

android {
    namespace = "com.mikeyphw.studyflow"

    defaultConfig {
        applicationId = "com.mikeyphw.studyflow"
        versionCode = 37
        versionName = "1.0.0-rc1"
        buildConfigField("boolean", "BACKUP_LEGACY_RULE_LINKED", legacyBackupRuleLinked.toString())
        buildConfigField("boolean", "BACKUP_MODERN_RULE_LINKED", modernBackupRuleLinked.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (studyFlowPersonalSigningConfigured) {
            create("studyFlowPersonal") {
                storeFile = studyFlowPersonalSigningStoreFile
                storePassword = studyFlowSigningValue("storePassword")
                keyAlias = studyFlowSigningValue("keyAlias")
                keyPassword = studyFlowSigningValue("keyPassword")
            }
        } else if (releaseSigningConfigured) {
            create("studyFlowRelease") {
                storeFile = file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (studyFlowPersonalSigningConfigured) signingConfig = signingConfigs.getByName("studyFlowPersonal")
        }
        release {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
            if (studyFlowPersonalSigningConfigured) {
                signingConfig = signingConfigs.getByName("studyFlowPersonal")
            } else if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("studyFlowRelease")
            }
        }
    }

    testBuildType = "release"

    lint {
        checkDependencies = true
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
        )
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:i18n"))
    implementation(project(":core:link"))
    implementation(project(":core:model"))
    implementation(project(":core:navigation"))
    implementation(project(":core:tvcontrol"))
    implementation(project(":core:tvlink"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:analytics"))
    implementation(project(":feature:library"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:privacy"))
    implementation(project(":feature:plans"))
    implementation(project(":feature:practice"))
    implementation(project(":feature:reminders"))
    implementation(project(":feature:reviews"))
    implementation(project(":feature:search"))
    implementation(project(":feature:schedule"))
    implementation(project(":feature:sessions"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:subjects"))
    implementation(project(":feature:workspace"))

    compileOnly(libs.androidx.annotation)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(kotlin("test-junit"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}



// OV01: fail release packaging on missing/partial signing credentials before release build work.
// `mustRunAfter` has no effect for ordinary Release unit tests/lint unless the preflight is also
// in the graph, so source validation can still run without production signing secrets.
val verifyPhoneReleaseSigningPreflight = tasks.register<Exec>("verifyPhoneReleaseSigningPreflight") {
    group = "verification"
    description = "Fails fast when StudyFlow phone Release signing inputs are incomplete or invalid."
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/ov01_release_gate.py", "signing", "--surface", "phone")
}

// Bridge the permanent properties into the existing release gate without exposing them in the repository.
if (studyFlowPersonalSigningConfigured) {
    verifyPhoneReleaseSigningPreflight.configure {
        environment("STUDYFLOW_RELEASE_STORE_FILE", studyFlowPersonalSigningStoreFile!!.absolutePath)
        environment("STUDYFLOW_RELEASE_STORE_PASSWORD", studyFlowSigningValue("storePassword"))
        environment("STUDYFLOW_RELEASE_KEY_ALIAS", studyFlowSigningValue("keyAlias"))
        environment("STUDYFLOW_RELEASE_KEY_PASSWORD", studyFlowSigningValue("keyPassword"))
    }
}

tasks.register<StudyFlowSigningStatusTask>("studyFlowSigningStatus") {
    group = "help"
    description = "Show whether StudyFlow phone debug and release use the permanent personal certificate."
    configured.set(studyFlowPersonalSigningConfigured)
    propertiesPath.set(studyFlowSigningPropertiesFile.absolutePath)
    keystorePath.set(studyFlowPersonalSigningStoreFile?.absolutePath.orEmpty())
    keyAlias.set(if (studyFlowPersonalSigningConfigured) studyFlowSigningValue("keyAlias") else "")
}

tasks.register<VerifyStudyFlowPersonalSigningConfiguredTask>("verifyStudyFlowPersonalSigningConfigured") {
    group = "verification"
    description = "Fail unless permanent StudyFlow personal signing is configured outside the repository."
    configured.set(studyFlowPersonalSigningConfigured)
    keystorePath.set(studyFlowPersonalSigningStoreFile?.absolutePath.orEmpty())
}

tasks.register("assemblePersonalDebug") {
    group = "build"
    description = "Build the phone debug APK with the permanent StudyFlow personal certificate."
    dependsOn("verifyStudyFlowPersonalSigningConfigured", "assembleDebug")
}

tasks.register("assemblePersonalRelease") {
    group = "build"
    description = "Build the phone release APK with the permanent StudyFlow personal certificate."
    dependsOn("verifyStudyFlowPersonalSigningConfigured", "assembleRelease")
}

tasks.configureEach {
    if (name != "verifyPhoneReleaseSigningPreflight" && "Release" in name) {
        mustRunAfter(verifyPhoneReleaseSigningPreflight)
    }
    val releasePackagingTask =
        name == "assembleRelease" ||
            name == "bundleRelease" ||
            (name.startsWith("packageRelease") && name != "packageReleaseResources") ||
            name.startsWith("signRelease")
    if (releasePackagingTask) {
        dependsOn(verifyPhoneReleaseSigningPreflight)
    }
}

// ATT12 v4 — Termux final-seal test stability.
tasks.withType<Test>().configureEach {
    maxParallelForks = 1
}
