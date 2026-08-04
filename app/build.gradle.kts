val studyFlowManifestText = file("src/main/AndroidManifest.xml").readText()

fun manifestLinksXml(attribute: String, resourceName: String): Boolean = Regex(
    "android:$attribute\\s*=\\s*\"@xml/$resourceName\"",
).containsMatchIn(studyFlowManifestText)

val legacyBackupRuleLinked = manifestLinksXml("fullBackupContent", "backup_rules") &&
    file("src/main/res/xml/backup_rules.xml").isFile
val modernBackupRuleLinked = manifestLinksXml("dataExtractionRules", "data_extraction_rules") &&
    file("src/main/res/xml/data_extraction_rules.xml").isFile

plugins {
    id("studyflow.android.application")
}

android {
    namespace = "com.mikeyphw.studyflow"

    defaultConfig {
        applicationId = "com.mikeyphw.studyflow"
        versionCode = 35
        versionName = "0.35.0-phase35"
        buildConfigField("boolean", "BACKUP_LEGACY_RULE_LINKED", legacyBackupRuleLinked.toString())
        buildConfigField("boolean", "BACKUP_MODERN_RULE_LINKED", modernBackupRuleLinked.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:i18n"))
    implementation(project(":core:model"))
    implementation(project(":core:navigation"))
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
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
