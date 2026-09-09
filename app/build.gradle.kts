val studyFlowManifestText = file("src/main/AndroidManifest.xml").readText()

fun manifestLinksXml(attribute: String, resourceName: String): Boolean = Regex(
    "android:$attribute\\s*=\\s*\"@xml/$resourceName\"",
).containsMatchIn(studyFlowManifestText)

val legacyBackupRuleLinked = manifestLinksXml("fullBackupContent", "backup_rules") &&
    file("src/main/res/xml/backup_rules.xml").isFile
val modernBackupRuleLinked = manifestLinksXml("dataExtractionRules", "data_extraction_rules") &&
    file("src/main/res/xml/data_extraction_rules.xml").isFile

val releaseStoreFilePath = providers.environmentVariable("STUDYFLOW_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("STUDYFLOW_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("STUDYFLOW_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("STUDYFLOW_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { value -> !value.isNullOrBlank() }

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

    if (releaseSigningConfigured) {
        signingConfigs {
            create("studyFlowRelease") {
                storeFile = file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("studyFlowRelease")
            }
        }
    }

    testBuildType = "release"

    lint {
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

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
