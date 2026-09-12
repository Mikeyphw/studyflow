val qualitySourceRoots = listOf(
    "app",
    "app-tv",
    "core",
    "feature",
    "desktopApp",
    "build-logic",
)

val excludedDirectoryNames = setOf("build", ".gradle", ".idea")

fun File.isQualitySource(): Boolean =
    extension in setOf("kt", "kts", "xml") &&
        toPath().none { pathSegment -> pathSegment.toString() in excludedDirectoryNames }

tasks.register("verifyStableToolchainBaseline") {
    group = "verification"
    description = "Checks the shared stable Gradle, Android, Kotlin, JVM, SDK, and dependency baseline."

    doLast {
        val failures = mutableListOf<String>()
        val versionsText = file("gradle/libs.versions.toml").readText()
        val buildLogicText = file("build-logic/convention/build.gradle.kts").readText()
        val androidConventionText =
            file("build-logic/convention/src/main/kotlin/AndroidConvention.kt").readText()
        val kotlinConventionText =
            file("build-logic/convention/src/main/kotlin/KotlinLibraryConventionPlugin.kt").readText()
        val desktopBuildText = file("desktopApp/build.gradle.kts").readText()
        val wrapperText = file("gradle/wrapper/gradle-wrapper.properties").readText()

        val requiredCatalogVersions = mapOf(
            "agp" to "9.3.1",
            "kotlin" to "2.4.10",
            "composeBom" to "2026.06.01",
            "ksp" to "2.3.11",
        )
        requiredCatalogVersions.forEach { (name, version) ->
            if ("$name = \"$version\"" !in versionsText) {
                failures += "gradle/libs.versions.toml: $name must be $version"
            }
        }

        val requiredBuildLogicTokens = listOf(
            "JavaLanguageVersion.of(21)",
            "com.android.tools.build:gradle:9.3.1",
            "org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10",
            "com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.11",
        )
        requiredBuildLogicTokens.forEach { token ->
            if (token !in buildLogicText) {
                failures += "build-logic/convention/build.gradle.kts: missing $token"
            }
        }

        val requiredAndroidTokenCounts = mapOf(
            "compileSdk = 36" to 2,
            "targetSdk = 36" to 1,
            "buildToolsVersion = \"36.0.0\"" to 2,
            "sourceCompatibility = JavaVersion.VERSION_21" to 2,
            "targetCompatibility = JavaVersion.VERSION_21" to 2,
        )
        requiredAndroidTokenCounts.forEach { (token, minimumCount) ->
            val count = Regex(Regex.escape(token)).findAll(androidConventionText).count()
            if (count < minimumCount) {
                failures += "AndroidConvention.kt: expected at least $minimumCount occurrences of $token"
            }
        }

        if ("jvmToolchain(21)" !in kotlinConventionText) {
            failures += "Kotlin library convention must use JVM toolchain 21"
        }
        if ("jvmToolchain(21)" !in desktopBuildText) {
            failures += "desktopApp must use JVM toolchain 21"
        }
        val gradleVersionMatch =
            Regex("gradle-([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)-bin\\.zip").find(wrapperText)
        val wrapperGradleVersion = gradleVersionMatch?.groupValues?.get(1)
        fun stableGradleTuple(version: String): Triple<Int, Int, Int>? {
            val match = Regex("^([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?$").matchEntire(version)
                ?: return null
            return Triple(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].ifEmpty { "0" }.toInt(),
            )
        }
        fun isAtLeastGradle970(version: String): Boolean {
            val parsed = stableGradleTuple(version) ?: return false
            return parsed.first > 9 ||
                (parsed.first == 9 && parsed.second > 7) ||
                (parsed.first == 9 && parsed.second == 7 && parsed.third >= 0)
        }
        if (wrapperGradleVersion == null || !isAtLeastGradle970(wrapperGradleVersion)) {
            failures += "Gradle wrapper must use a stable Gradle version >= 9.7.0"
        }
        val wrapperChecksum =
            Regex("(?m)^distributionSha256Sum=([0-9a-f]{64})$")
                .find(wrapperText)
                ?.groupValues
                ?.get(1)
        if (wrapperChecksum == null) {
            failures += "Gradle wrapper must pin a lowercase 64-character SHA-256 checksum"
        }
        if (
            wrapperGradleVersion == "9.7.1" &&
            wrapperChecksum != "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"
        ) {
            failures += "Gradle 9.7.1 wrapper checksum is missing or unexpected"
        }

        val devtoolText = file(".devtool.toml").readText()
        val devtoolGradleVersions =
            Regex("(?m)^version = \"([^\"]+)\"$")
                .findAll(devtoolText)
                .map { it.groupValues[1] }
                .toList()
        if (devtoolGradleVersions.size < 2 || devtoolGradleVersions.any { !isAtLeastGradle970(it) }) {
            failures += "Devtool Gradle targets must use stable Gradle >= 9.7.0"
        }
        if (wrapperGradleVersion != null && devtoolGradleVersions.any { it != wrapperGradleVersion }) {
            failures += "Devtool Gradle targets must match the wrapper Gradle version"
        }
        if ("ndk_version = \"29.0.14206865\"" !in devtoolText) {
            failures += "Devtool Android target must pin NDK 29.0.14206865"
        }
        if ("internal const val STUDYFLOW_NDK_VERSION = \"29.0.14206865\"" !in androidConventionText) {
            failures += "AndroidConvention.kt must define the canonical NDK 29.0.14206865 version"
        }
        if (Regex("ndkVersion = STUDYFLOW_NDK_VERSION").findAll(androidConventionText).count() != 2) {
            failures += "Application and library Android conventions must both use the canonical NDK version"
        }
        if ("VERSION_17" in androidConventionText || "jvmToolchain(17)" in kotlinConventionText) {
            failures += "Java 17 remnants are forbidden in the standardized JVM lane"
        }
        if ("jvmToolchain(17)" in desktopBuildText || "JavaLanguageVersion.of(17)" in buildLogicText) {
            failures += "Java 17 remnants are forbidden in desktop/build-logic toolchains"
        }

        val prereleasePattern =
            Regex(
                "(?i)(?:^|[-.+_])" +
                    "(alpha|beta|rc|preview|canary|eap|snapshot|nightly|milestone|dev|m\\d+)" +
                    "(?:[-.+_]?\\d*)",
            )
        var inVersions = false
        versionsText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed == "[versions]" -> inVersions = true
                inVersions && trimmed.startsWith("[") -> inVersions = false
                inVersions -> {
                    val match = Regex("^([A-Za-z0-9_.-]+)\\s*=\\s*\"([^\"]+)\"$").matchEntire(trimmed)
                    if (match != null) {
                        val name = match.groupValues[1]
                        val version = match.groupValues[2]
                        if (prereleasePattern.containsMatchIn(version)) {
                            failures += "gradle/libs.versions.toml: prerelease $name=$version is forbidden"
                        }
                    }
                }
            }
        }

        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString(prefix = "Stable toolchain violations:\n", separator = "\n"))
        }
    }
}

tasks.register("verifyFormatting") {
    group = "verification"
    description =
        "Checks source files for tabs, trailing whitespace, missing final newlines, and overlong Kotlin lines."

    doLast {
        val failures = mutableListOf<String>()
        qualitySourceRoots
            .map(::file)
            .filter(File::exists)
            .flatMap { sourceRoot -> sourceRoot.walkTopDown().filter(File::isQualitySource).toList() }
            .forEach { sourceFile ->
                val text = sourceFile.readText()
                if (!text.endsWith("\n")) {
                    failures += "${sourceFile.relativeTo(rootDir)}: missing final newline"
                }
                text.lineSequence().forEachIndexed { index, line ->
                    val lineNumber = index + 1
                    if ('\t' in line) {
                        failures += "${sourceFile.relativeTo(rootDir)}:$lineNumber: tab character"
                    }
                    if (line != line.trimEnd()) {
                        failures += "${sourceFile.relativeTo(rootDir)}:$lineNumber: trailing whitespace"
                    }
                    if (sourceFile.extension in setOf("kt", "kts") && line.length > 120) {
                        failures += "${sourceFile.relativeTo(rootDir)}:$lineNumber: ${line.length} characters"
                    }
                }
            }

        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString(prefix = "Formatting violations:\n", separator = "\n"))
        }
    }
}

tasks.register<Exec>("verifyCpov00Toolchain") {
    group = "verification"
    description = "Checks CPOV00 Gradle >= 9.7 and canonical NDK r29 toolchain contracts."
    commandLine("python3", "scripts/verify_cpov00_toolchain.py")
}

tasks.register<Exec>("verifyCpov01PublicExamProfile") {
    group = "verification"
    description = "Checks CPOV01 public-exam profile shell, ownership, onboarding and locale contracts."
    commandLine("python3", "scripts/verify_cpov01_public_exam_profile.py")
}

tasks.register<Exec>("verifyCpov02EditalImport") {
    group = "verification"
    description = "Checks CPOV02 edital parsing, preview-first apply, local document lanes and locale contracts."
    commandLine("python3", "scripts/verify_cpov02_edital_import.py")
}

tasks.register<Exec>("verifyCpov03Cpov04PublicExamProgress") {
    group = "verification"
    description = "Checks CPOV03 edital revisions and CPOV04 persistent study-cycle contracts."
    commandLine("python3", "scripts/verify_cpov03_04_public_exam_progress.py")
}

tasks.register<Exec>("verifyRepositoryContracts") {
    group = "verification"
    dependsOn("verifyStableToolchainBaseline", "verifyCpov00Toolchain")
    description =
        "Runs historical contracts through Phase 37, post-V2 features through Phase 64, " +
            "and Debug Workbench D1 through D8."
    commandLine(
        "bash",
        "-lc",
        """
        set -euo pipefail
        find . -path '*/build/reports/lint*' -type f -delete
        find . -path '*/build/reports/lint*' -type d -empty -delete || true
        for phase in {0..37}; do
            python3 "scripts/verify_phase_${'$'}{phase}.py"
        done
        python3 scripts/verify_debug_workbench_d1_r2.py
        python3 scripts/verify_debug_workbench_d2.py
        python3 scripts/verify_debug_workbench_d3.py
        python3 scripts/verify_debug_workbench_d2_d3_corrective_seal.py
        python3 scripts/verify_debug_workbench_d4.py
        python3 scripts/verify_debug_workbench_d5.py
        python3 scripts/verify_debug_workbench_d6.py
        python3 scripts/verify_debug_workbench_d7.py
        python3 scripts/verify_debug_workbench_d8.py
        python3 scripts/verify_ui_ux_v2.py
        python3 scripts/verify_android_focus_shell_r2.py
        python3 scripts/verify_phase_48_49.py
        python3 scripts/verify_phase_50_51.py
        python3 scripts/verify_phase_52_53.py
        python3 scripts/verify_phase_54_55.py
        python3 scripts/verify_phase_56_57.py
        python3 scripts/verify_phase_58_59.py
        python3 scripts/verify_phase_60_61.py
        python3 scripts/verify_phase_62.py
        python3 scripts/verify_phase_63_64.py
        python3 scripts/verify_post_phase_64_final_validation.py
        python3 scripts/verify_full_app_final_user_readiness.py
        python3 scripts/verify_final_app_acceptance.py
        python3 scripts/verify_remediation_phase_1_2.py
        python3 scripts/verify_remediation_ov_13.py
        python3 scripts/verify_final_release_verdict_contract.py
        """.trimIndent(),
    )
}

tasks.register("verifyPhase56_57ReleaseReadiness") {
    group = "verification"
    description =
        "Checks release-facing StudyFlow source gates that do not require signing credentials."

    doLast {
        val failures = mutableListOf<String>()
        val appBuild = file("app/build.gradle.kts").readText()
        val manifest = file("app/src/main/AndroidManifest.xml").readText()
        val widgetLayout = file("app/src/main/res/layout/widget_today.xml").readText()
        val reminderStrings = listOf(
            file("feature/reminders/src/main/res/values/strings.xml"),
            file("feature/reminders/src/main/res/values-pt-rBR/strings.xml"),
        ).joinToString("\n") { resourceFile -> resourceFile.readText() }

        if ("release" !in appBuild) {
            failures += "app/build.gradle.kts: missing release build-type configuration"
        }
        if ("applicationId" !in appBuild || "versionCode" !in appBuild || "versionName" !in appBuild) {
            failures += "app/build.gradle.kts: release identity fields are not visible"
        }
        if ("android.intent.action.MAIN" !in manifest || "android.intent.category.LAUNCHER" !in manifest) {
            failures += "AndroidManifest.xml: launcher entry point is missing"
        }
        if ("android:tint" in widgetLayout) {
            failures += "widget_today.xml: android:tint is forbidden; use app:tint"
        }
        listOf(
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values-pt-rBR/strings.xml",
            "feature/settings/src/main/res/values/strings.xml",
            "feature/settings/src/main/res/values-pt-rBR/strings.xml",
            "feature/workspace/src/main/res/values/strings.xml",
            "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
        ).forEach { resourcePath ->
            val firstLines = file(resourcePath).readLines().take(3).joinToString("\n")
            if ("tools:ignore=\"PluralsCandidate,UnusedResources\"" in firstLines ||
                "tools:ignore=\"UnusedResources\"" in firstLines) {
                failures += "$resourcePath: must not suppress warning cleanup at file scope"
            }
        }

        if ("\\u" in reminderStrings || "app's safe" in reminderStrings || "plan's quiet" in reminderStrings) {
            failures += "reminder strings contain known release resource-flattening risks"
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString(prefix = "Release-readiness violations:\n", separator = "\n"))
        }
    }
}


tasks.register("verifyPhase58_59WarningHygiene") {
    group = "verification"
    description =
        "Checks Phase 58/59 warning cleanup boundaries before Android lint runs."

    doLast {
        val failures = mutableListOf<String>()
        val routes = file("core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt")
            .readText()
        val design = file(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/" +
                "StudyFlowWorkspaceComponents.kt",
        ).readText()
        val workspaceStrings = file("feature/workspace/src/main/res/values/strings.xml").readText()
        val reminders = file(
            "feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt",
        ).readText()
        val widgetLayout = file("app/src/main/res/layout/widget_today.xml").readText()

        listOf(
            "StudyFlowWorkspaceRoot",
            "PlanWorkspaceTab",
            "PracticeWorkspaceTab",
            "ProgressWorkspacePeriod",
        ).forEach { enumName ->
            if ("""@Keep
@Serializable
enum class $enumName""" !in routes) {
                failures += "StudyFlowRoutes.kt: $enumName must be annotated with @Keep"
            }
        }
        if ("PrimaryScrollableTabRow(" in design || "ScrollableTabRow(" in design) {
            failures += "StudyFlowWorkspaceComponents.kt: stock Material tab rows must stay retired"
        }
        val focusPrimitives = file(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/" +
                "StudyFlowFocusWorkspacePrimitives.kt",
        ).readText()
        if ("fun <T> StudyFlowTabStrip(" !in focusPrimitives || "Role.Tab" !in focusPrimitives) {
            failures += "StudyFlowFocusWorkspacePrimitives.kt: custom accessible tab strip is missing"
        }
        if ("name=\"in_progress\"" in workspaceStrings) {
            failures += "workspace strings must not override private androidx.compose.ui:in_progress"
        }
        if ("Build.VERSION.SDK_INT < 23" in reminders) {
            failures += "StudyRemindersScreen.kt: obsolete minSdk < 23 branch remains"
        }
        if ("android:tint" in widgetLayout) {
            failures += "widget_today.xml: android:tint is forbidden; use app:tint"
        }
        listOf(
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values-pt-rBR/strings.xml",
            "feature/settings/src/main/res/values/strings.xml",
            "feature/settings/src/main/res/values-pt-rBR/strings.xml",
            "feature/workspace/src/main/res/values/strings.xml",
            "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
        ).forEach { resourcePath ->
            val firstLines = file(resourcePath).readLines().take(3).joinToString("\n")
            if ("tools:ignore=\"PluralsCandidate,UnusedResources\"" in firstLines ||
                "tools:ignore=\"UnusedResources\"" in firstLines) {
                failures += "$resourcePath: must not suppress warning cleanup at file scope"
            }
        }

        if (failures.isNotEmpty()) {
            throw GradleException(
                failures.joinToString(prefix = "Phase 58/59 warning hygiene violations:\n"),
            )
        }
    }
}

tasks.register<Exec>("verifyPhase60_61ContentReportSeal") {
    group = "verification"
    description = "Checks Phase 60 smart cleanup, Phase 61 local report export, and warning cleanup."
    commandLine("python3", "scripts/verify_phase_60_61.py")
}


tasks.register<Exec>("verifyPhase62OnboardingSeal") {
    group = "verification"
    description = "Checks Phase 62 onboarding and local sample plan wizard behavior."
    commandLine("python3", "scripts/verify_phase_62.py")
}


tasks.register<Exec>("verifyPhase63_64ExamRunbookSprintSeal") {
    group = "verification"
    description = "Checks Phase 63 exam runbook and Phase 64 preview-first study sprint behavior."
    commandLine("python3", "scripts/verify_phase_63_64.py")
}


tasks.register<Exec>("verifyPostPhase64FinalValidationRefresh") {
    group = "verification"
    description = "Checks Phase 64 roadmap reconciliation and final signed-journey coverage."
    commandLine("python3", "scripts/verify_post_phase_64_final_validation.py")
}


tasks.register<Exec>("verifyAndroidFocusShellFinalSeal") {
    group = "verification"
    description = "Checks the Android UXOV-01 through UXOV-05-R2 Focus Shell and polish source contracts."
    commandLine("python3", "scripts/verify_android_focus_shell_r2.py")
}

tasks.register<Exec>("verifyCloudOptionalSyncSettings") {
    group = "verification"
    description = "Checks CSOV00/CSOV01 local-first optional cloud sync source contracts."
    commandLine("python3", "scripts/verify_cloud_optional_sync_settings.py")
}


tasks.register<Exec>("verifyCsov02SyncBundle") {
    group = "verification"
    description = "Checks CSOV02 encrypted sync checkpoint bundle source contracts."
    commandLine("python3", "scripts/verify_csov02_sync_bundle.py")
}

tasks.register<Exec>("verifyCsov03ProviderAbstraction") {
    group = "verification"
    description = "Checks CSOV03 optional sync provider abstraction source contracts."
    commandLine("python3", "scripts/verify_csov03_provider_abstraction.py")
}


tasks.register<Exec>("verifyCsov04OptionalSyncOnboarding") {
    group = "verification"
    description = "Checks CSOV04 optional sync onboarding source contracts."
    commandLine("python3", "scripts/verify_csov04_optional_sync_onboarding.py")
}


tasks.register<Exec>("verifyCsov05ManualFolderSync") {
    group = "verification"
    description = "Checks CSOV05 manual folder sync source contracts."
    commandLine("python3", "scripts/verify_csov05_manual_folder_sync.py")
}


tasks.register<Exec>("verifyCsov06SyncLedger") {
    group = "verification"
    description = "Checks CSOV06 sync device identity and ledger source contracts."
    commandLine("python3", "scripts/verify_csov06_sync_ledger.py")
}


tasks.register<Exec>("verifyCsov07MergeEngine") {
    group = "verification"
    description = "Checks CSOV07 conservative sync merge engine source contracts."
    commandLine("python3", "scripts/verify_csov07_merge_engine.py")
}


tasks.register<Exec>("verifyCsov08ConflictReview") {
    group = "verification"
    description = "Checks CSOV08 conflict review UI and presenter source contracts."
    commandLine("python3", "scripts/verify_csov08_conflict_review.py")
}


tasks.register<Exec>("verifyCsov09BackgroundSyncModes") {
    group = "verification"
    description = "Checks CSOV09 background sync mode source contracts."
    commandLine("python3", "scripts/verify_csov09_background_sync_modes.py")
}

tasks.register<Exec>("verifyCsov10RealProviders") {
    group = "verification"
    description = "Checks CSOV10 WebDAV / Nextcloud real provider lane source contracts."
    commandLine("python3", "scripts/verify_csov10_real_providers.py")
}

tasks.register<Exec>("verifyCsov11FinalSyncSeal") {
    group = "verification"
    description = "Checks CSOV11 local-first optional cloud final acceptance source contracts."
    commandLine("python3", "scripts/verify_csov11_final_sync_seal.py")
}

tasks.register<Exec>("verifyContentModeFoundation") {
    group = "verification"
    description = "Checks CMOV-01 content mode persistence, UI, backup and capability contracts."
    commandLine("python3", "scripts/verify_content_mode_foundation.py")
}




tasks.register<Exec>("verifyProgrammingCourseModel") {
    group = "verification"
    description = "Checks programming-course data model, persistence, and Termux preference contracts."
    commandLine("python3", "scripts/verify_programming_course_model.py")
}



tasks.register<Exec>("verifyCodeBlocksSyntax") {
    group = "verification"
    description = "Checks CMOV-03 reusable code block and syntax-highlighting contracts."
    commandLine("python3", "scripts/verify_code_blocks_syntax.py")
}

tasks.register<Exec>("verifyTermuxRuntimeFoundation") {
    group = "verification"
    description = "Checks CMOV-04 safe Termux runtime request/result and command-template contracts."
    commandLine("python3", "scripts/verify_termux_runtime_foundation.py")
}

tasks.register<Exec>("verifyProgrammingCourseUi") {
    group = "verification"
    description = "Checks CMOV-05 programming-course UI labels, workspace panel and code-block integration."
    commandLine("python3", "scripts/verify_programming_course_ui.py")
}

tasks.register<Exec>("verifyCodingExerciseTermuxExecution") {
    group = "verification"
    description = "Checks CMOV-06 coding-exercise Termux execution planning and result contracts."
    commandLine("python3", "scripts/verify_coding_exercise_termux_execution.py")
}

tasks.register<Exec>("verifyLanguageDeckIdiomModel") {
    group = "verification"
    description = "Checks CMOV-07 language deck, expression, idiom and vocabulary model contracts."
    commandLine("python3", "scripts/verify_language_deck_idiom_model.py")
}

tasks.register<Exec>("verifyLanguageReviewModes") {
    group = "verification"
    description = "Checks CMOV-08 language review prompts, evaluation and spaced-review bridge contracts."
    commandLine("python3", "scripts/verify_language_review_modes.py")
}

tasks.register<Exec>("verifyImportTemplates") {
    group = "verification"
    description = "Checks CMOV-09 programming-course and language idiom import template contracts."
    commandLine("python3", "scripts/verify_import_templates.py")
}


tasks.register<Exec>("verifyModeAwareAnalytics") {
    group = "verification"
    description = "Checks CMOV-10 mode-aware analytics profiles, labels and UI routing."
    commandLine("python3", "scripts/verify_mode_aware_analytics.py")
}


tasks.register<Exec>("verifyContentModesFinalIntegrationSeal") {
    group = "verification"
    description = "Checks the audited CMOV-01..CMOV-10 integration seal, including Android Termux bridge readiness."
    commandLine("python3", "scripts/verify_content_modes_final_integration.py")
}

tasks.register<Exec>("verifyFinalSourceAcceptance") {
    group = "verification"
    description = "Checks source/static final acceptance only; it does not claim release artifact or device acceptance."
    commandLine("python3", "scripts/verify_final_app_acceptance.py")
}

val studyFlowReleaseSigningVariables = listOf(
    "STUDYFLOW_RELEASE_STORE_FILE",
    "STUDYFLOW_RELEASE_STORE_PASSWORD",
    "STUDYFLOW_RELEASE_KEY_ALIAS",
    "STUDYFLOW_RELEASE_KEY_PASSWORD",
)

tasks.register<Delete>("prepareDevtoolValidationArtifacts") {
    group = "verification"
    description =
        "Removes stale unsigned Release outputs before ordinary Devtool validation when signing is unavailable."
    onlyIf { studyFlowReleaseSigningVariables.any { name -> System.getenv(name).isNullOrBlank() } }
    delete(
        layout.projectDirectory.dir("app/build/outputs/apk/release"),
        layout.projectDirectory.dir("app/build/outputs/bundle/release"),
    )
}

tasks.register<Exec>("verifyOv19PromiseAudit") {
    group = "verification"
    description = "Checks the OV19 public-exam promise audit and backlog-closure contracts."
    commandLine("python3", "scripts/verify_ov19_promise_audit.py")
}

tasks.register<Exec>("verifyOv20FinalIntegration") {
    group = "verification"
    description = "Runs the fail-closed OV20 public-exam integration/source seal."
    commandLine("python3", "scripts/verify_ov20_final_integration.py")
}


tasks.register<Exec>("verifyStudyFlowTvArchitecture") {
    group = "verification"
    description = "Checks TVOV00 shared-core, app-tv boundary, and Devtool TV target contracts."
    commandLine("python3", "scripts/verify_tvov00_architecture.py")
}

tasks.register<Exec>("verifyStudyFlowTvSizeBudget") {
    group = "verification"
    description = "Checks TV size-budget, dependency-boundary, and placeholder target contracts."
    commandLine("python3", "scripts/verify_tv_size_budget.py")
}

tasks.register<Exec>("verifyStudyFlowTvControlCast") {
    group = "verification"
    description = "Checks phone-to-TV control and cast boundary contracts."
    commandLine("python3", "scripts/verify_tv_control_cast_contract.py")
}


tasks.register<Exec>("verifyStudyFlowTvAppModule") {
    group = "verification"
    description = "Checks TVOV01 minimal app-tv module, leanback manifest, and size-first dependency boundary."
    commandLine("python3", "scripts/verify_tvov01_app_module.py")
}

tasks.register<Exec>("verifyStudyFlowTvDependencyBudget") {
    group = "verification"
    description = "Checks TVOV02 app-tv dependency budget, lint-clean manifest, and size-safe resources."
    commandLine("python3", "scripts/verify_tvov02_dependency_budget.py")
}

tasks.register<Exec>("verifyStudyFlowTvPairingControlCastProtocol") {
    group = "verification"
    description = "Checks TVOV03 shared phone-to-TV pairing, control, and cast protocol contracts."
    commandLine("python3", "scripts/verify_tvov03_pairing_control_cast_protocol.py")
}

tasks.register<Exec>("verifyStudyFlowTvFocusPrimitives") {
    group = "verification"
    description = "Checks TVOV04 native Android TV D-pad/focus primitive contracts."
    commandLine("python3", "scripts/verify_tvov04_focus_primitives.py")
}


tasks.register<Exec>("verifyStudyFlowTvNavigationShell") {
    group = "verification"
    description = "Checks TVOV05 Android TV navigation shell, destination model, and D-pad-aware rail contracts."
    commandLine("python3", "scripts/verify_tvov05_navigation_shell.py")
}

tasks.register<Exec>("verifyStudyFlowTvHomeSurface") {
    group = "verification"
    description = "Checks TVOV06 Android TV Home surface, warning cleanup, and paired-phone entry contracts."
    commandLine("python3", "scripts/verify_tvov06_home_surface.py")
}


tasks.register<Exec>("verifyStudyFlowTvTodayWorkspace") {
    group = "verification"
    description = "Checks TVOV07 Android TV Today workspace, controls, focus items, and shell rendering contracts."
    commandLine("python3", "scripts/verify_tvov07_today_workspace.py")
}

tasks.register<Exec>("verifyStudyFlowTvStudySessionExperience") {
    group = "verification"
    description = "Checks TVOV08 Android TV study-session experience, remote/phone controls, and cast-state contracts."
    commandLine("python3", "scripts/verify_tvov08_study_session.py")
}

tasks.register<Exec>("verifyFinalAppAcceptanceSeal") {
    group = "verification"
    description = "Compatibility alias for the final source/static acceptance contract."
    commandLine("python3", "scripts/verify_final_app_acceptance.py")
}

tasks.register<Exec>("verifyRemediationOv13") {
    group = "verification"
    description = "Checks OV-13 release-seal source contracts without synthesizing runtime evidence."
    commandLine("python3", "scripts/verify_remediation_ov_13.py")
}

tasks.register("verifyFinalReleaseHostGate") {
    group = "verification"
    description = "Runs the signed host-side Release regression gate and records APK/AAB evidence."
    dependsOn(
        "checkAll",
        ":app:compileReleaseAndroidTestKotlin",
        ":app:processReleaseResources",
        ":app:assembleRelease",
        ":app:bundleRelease",
    )
    doLast {
        fun sha256(source: File): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            source.inputStream().buffered().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

        val signingVariables = listOf(
            "STUDYFLOW_RELEASE_STORE_FILE",
            "STUDYFLOW_RELEASE_STORE_PASSWORD",
            "STUDYFLOW_RELEASE_KEY_ALIAS",
            "STUDYFLOW_RELEASE_KEY_PASSWORD",
        )
        val missingSigningVariables = signingVariables.filter { name -> System.getenv(name).isNullOrBlank() }
        if (missingSigningVariables.isNotEmpty()) {
            throw GradleException(
                "Final Release validation requires signing credentials: ${missingSigningVariables.joinToString()}",
            )
        }
        val releaseApk = file("app/build/outputs/apk/release/app-release.apk")
        val releaseAab = file("app/build/outputs/bundle/release/app-release.aab")
        if (!releaseApk.isFile) {
            throw GradleException("Signed Release APK output is missing: ${releaseApk.absolutePath}")
        }
        if (!releaseAab.isFile) {
            throw GradleException("Signed Release AAB output is missing: ${releaseAab.absolutePath}")
        }
        val validationSession = System.getenv("STUDYFLOW_RELEASE_VALIDATION_SESSION")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "host-${java.util.UUID.randomUUID()}"
        val report = file("build/reports/studyflow/release-host-gates.json")
        report.parentFile.mkdirs()
        val generatedAtUtc = java.time.Instant.now().toString()
        report.writeText(
            """
            {
              "schemaVersion": 2,
              "status": "PASS",
              "validationSession": "$validationSession",
              "generatedAtUtc": "$generatedAtUtc",
              "gates": [
                {"name": "gradle_configuration", "status": "PASS"},
                {"name": "source_contracts", "status": "PASS"},
                {"name": "main_compilation", "status": "PASS"},
                {"name": "test_compilation", "status": "PASS"},
                {"name": "unit_tests", "status": "PASS"},
                {"name": "resource_merge", "status": "PASS"},
                {"name": "lint", "status": "PASS"},
                {"name": "release_assemble", "status": "PASS"},
                {"name": "release_bundle", "status": "PASS"}
              ],
              "artifacts": {
                "releaseApk": {"path": "${releaseApk.absolutePath}", "sha256": "${sha256(releaseApk)}"},
                "releaseAab": {"path": "${releaseAab.absolutePath}", "sha256": "${sha256(releaseAab)}"}
              },
              "scope": "signed host Release gate; connected Android evidence is recorded separately"
            }
            """.trimIndent() + "\n",
        )
        println("FINAL_RELEASE_HOST_GATES: PASS")
        println("Evidence: ${report.absolutePath}")
    }
}

tasks.register<Exec>("verifyFinalReleaseArtifact") {
    group = "verification"
    description = "Verifies exact signed APK/AAB identity, hashes, and signatures for this host session."
    dependsOn("verifyFinalReleaseHostGate")
    mustRunAfter(
        ":core:data:connectedDebugAndroidTest",
        ":core:database:connectedDebugAndroidTest",
        ":feature:plans:connectedDebugAndroidTest",
        ":feature:practice:connectedDebugAndroidTest",
        ":feature:schedule:connectedDebugAndroidTest",
        ":feature:workspace:connectedDebugAndroidTest",
        ":app:connectedReleaseAndroidTest",
    )
    commandLine("python3", "scripts/verify_final_release_artifact.py")
}

tasks.register("recordConnectedReleaseTestGates") {
    group = "verification"
    description = "Runs required connected Android suites and records PASS only after every suite succeeds."
    dependsOn(
        "verifyFinalReleaseArtifact",
        ":core:data:connectedDebugAndroidTest",
        ":core:database:connectedDebugAndroidTest",
        ":feature:plans:connectedDebugAndroidTest",
        ":feature:practice:connectedDebugAndroidTest",
        ":feature:schedule:connectedDebugAndroidTest",
        ":feature:workspace:connectedDebugAndroidTest",
        ":app:connectedReleaseAndroidTest",
    )
    doLast {
        val host = file("build/reports/studyflow/release-host-gates.json").readText()
        val artifact = file("build/reports/studyflow/release-artifact-identity.json").readText()
        fun requiredMatch(text: String, pattern: Regex, label: String): String =
            pattern.find(text)?.groupValues?.get(1)
                ?: throw GradleException("Connected test evidence cannot resolve $label")

        val validationSession = requiredMatch(
            host,
            Regex("\\\"validationSession\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""),
            "validation session",
        )
        val artifactSession = requiredMatch(
            artifact,
            Regex("\\\"validationSession\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""),
            "artifact validation session",
        )
        if (artifactSession != validationSession) {
            throw GradleException("Connected test prerequisites came from mixed validation sessions")
        }
        val artifactSha = requiredMatch(
            artifact,
            Regex("\\\"artifact\\\"\\s*:\\s*\\{[\\s\\S]*?\\\"sha256\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\""),
            "signed APK SHA-256",
        )
        val report = file("build/reports/studyflow/connected-test-gates.json")
        report.parentFile.mkdirs()
        report.writeText(
            """
            {
              "schemaVersion": 1,
              "status": "PASS",
              "validationSession": "$validationSession",
              "generatedAtUtc": "${java.time.Instant.now()}",
              "artifactSha256": "$artifactSha",
              "gates": [
                {"name": "core_data", "status": "PASS"},
                {"name": "core_database_migrations", "status": "PASS"},
                {"name": "feature_plans", "status": "PASS"},
                {"name": "feature_practice", "status": "PASS"},
                {"name": "feature_schedule", "status": "PASS"},
                {"name": "feature_workspace", "status": "PASS"},
                {"name": "app_release_suite", "status": "PASS"}
              ]
            }
            """.trimIndent() + "\n",
        )
        println("CONNECTED_RELEASE_TEST_GATES: PASS")
        println("Evidence: ${report.absolutePath}")
    }
}

tasks.register<Exec>("recordFullJourneyEvidence") {
    group = "verification"
    description = "Runs the exact signed Release restore/restart journey and records bound Android evidence."
    dependsOn("recordConnectedReleaseTestGates")
    commandLine("python3", "scripts/record_full_journey_evidence.py")
}

tasks.register("verifyFinalReleaseAutomatedGate") {
    group = "verification"
    description = "Mandatory automated final gate: signed artifacts, connected suites, and restore/restart journey."
    dependsOn("recordFullJourneyEvidence")
    doLast {
        val journey = file("build/reports/studyflow/full-journey.json")
        if (!journey.isFile || !Regex("\\\"status\\\"\\s*:\\s*\\\"PASS\\\"").containsMatchIn(journey.readText())) {
            throw GradleException("Full restore/restart journey evidence is missing or not PASS")
        }
        println("FINAL_RELEASE_AUTOMATED_GATE: PASS")
        println("Manual real-device observations remain required before productSealed=true.")
    }
}

tasks.register<Exec>("verifyFinalReleaseVerdict") {
    group = "verification"
    description = "Fails closed until automated and manual final Release evidence all PASS."
    commandLine("python3", "scripts/verify_final_release_verdict.py")
}

tasks.register<Exec>("verifyStudyFlowDesktopFoundation") {
    group = "verification"
    description = "Checks StudyFlow desktop foundation wiring before the desktop app shell exists."
    commandLine("python3", "scripts/verify_studyflow_desktop_foundation.py")
}


tasks.register<Exec>("verifyStudyFlowDesktopShellContracts") {
    group = "verification"
    description = "Checks the Compose Desktop shell and shared-module boundary contracts."
    commandLine("python3", "scripts/verify_studyflow_desktop_shell_contracts.py")
}


tasks.register<Exec>("verifyStudyFlowDesktopBootstrapScreens") {
    group = "verification"
    description = "Checks desktop bootstrap, domain wiring, dashboard, and library screens."
    commandLine("python3", "scripts/verify_studyflow_desktop_bootstrap_screens.py")
}


tasks.register<Exec>("verifyStudyFlowDesktopWorkflows") {
    group = "verification"
    description = "Checks desktop planner, practice, analytics, search, and settings workflow contracts."
    commandLine("python3", "scripts/verify_studyflow_desktop_workflows.py")
}


tasks.register<Exec>("verifyDesktopDov08PersistentWorkspace") {
    group = "verification"
    description = "Checks DOV08 durable Desktop workspace persistence and production bootstrap wiring."
    commandLine("python3", "scripts/verify_desktop_dov08_persistent_workspace.py")
}


tasks.register<Exec>("verifyDesktopDov09PlanContentLifecycle") {
    group = "verification"
    description = "Checks DOV09 persistent Study Plan, Subject, and Topic lifecycle contracts."
    commandLine("python3", "scripts/verify_desktop_dov09_plan_content_lifecycle.py")
}


tasks.register<Exec>("verifyDesktopDov10PlannerAdaptiveWorkflows") {
    group = "verification"
    description = "Checks DOV10 persistent planner authoring and adaptive schedule workflows."
    commandLine("python3", "scripts/verify_desktop_dov10_planner_adaptive_workflows.py")
}


tasks.register<Exec>("verifyDesktopDov11SessionRuntime") {
    group = "verification"
    description = "Checks DOV11 durable Study Session runtime and linked planned-activity transitions."
    commandLine("python3", "scripts/verify_desktop_dov11_session_runtime.py")
}


tasks.register<Exec>("verifyDesktopDov12QuestionPracticeExecution") {
    group = "verification"
    description = "Checks DOV12 persistent Desktop question practice execution contracts."
    commandLine("python3", "scripts/verify_desktop_dov12_question_practice_execution.py")
}


tasks.register<Exec>("verifyDesktopDov13MockExamStudio") {
    group = "verification"
    description = "Checks DOV13 restart-safe Desktop Mock Exam Studio contracts."
    commandLine("python3", "scripts/verify_desktop_dov13_mock_exam_studio.py")
}


tasks.register<Exec>("verifyDesktopDov14ReviewsSrsErrorNotebook") {
    group = "verification"
    description = "Checks DOV14 persistent Desktop reviews, SRS, flashcards, and Error Notebook contracts."
    commandLine("python3", "scripts/verify_desktop_dov14_reviews_srs_error_notebook.py")
}


tasks.register<Exec>("verifyDesktopDov15LibraryFilesMaterialViewers") {
    group = "verification"
    description = "Checks DOV15 persistent Desktop library files and material viewer contracts."
    commandLine("python3", "scripts/verify_desktop_dov15_library_files_material_viewers.py")
}

tasks.register<Exec>("verifyDesktopDov16WritingWorkspace") {
    group = "verification"
    description = "Checks DOV16 persistent Desktop Writing Workspace contracts."
    commandLine("python3", "scripts/verify_desktop_dov16_writing_workspace.py")
}


tasks.register<Exec>("verifyDesktopDov17ContentModesExecution") {
    group = "verification"
    description = "Checks DOV17 persisted Desktop content modes and native execution contracts."
    commandLine("python3", "scripts/verify_desktop_dov17_content_modes_execution.py")
}

tasks.register<Exec>("verifyDesktopDov18PublicExamParity") {
    group = "verification"
    description = "Verify Desktop DOV18 Concurso Publico parity"
    commandLine("python3", "scripts/verify_desktop_dov18_public_exam_parity.py")
}

tasks.register<Exec>("verifyDesktopDov19ReadinessCoachingReports") {
    group = "verification"
    description = "Verify Desktop DOV19 readiness, coaching, cleanup, reports, runbooks, and sprints"
    commandLine("python3", "scripts/verify_desktop_dov19_readiness_coaching_reports.py")
}


tasks.register<Exec>("verifyDesktopDov20PortabilityBackupMigration") {
    group = "verification"
    description = "Verify Desktop DOV20 portable export/import, encrypted backup/restore, and migration"
    commandLine("python3", "scripts/verify_desktop_dov20_portability_backup_migration.py")
}

tasks.register<Exec>("verifyDesktopDov21EnPtBrLocalization") {
    group = "verification"
    description = "Verify Desktop DOV21 English and pt-BR localization"
    commandLine("python3", "scripts/verify_desktop_dov21_en_ptbr_localization.py")
}

tasks.register<Exec>("verifyDesktopDov22SystemLightDarkAppearance") {
    group = "verification"
    description = "Verify Desktop DOV22 System, Light, and Dark appearance"
    commandLine("python3", "scripts/verify_desktop_dov22_system_light_dark_appearance.py")
}

tasks.register<Exec>("verifyDesktopDov23NativeDesktopIntegration") {
    group = "verification"
    description = "Verify Desktop DOV23 native file, browser, notification, and deep-link integration"
    commandLine("python3", "scripts/verify_desktop_dov23_native_desktop_integration.py")
}

tasks.register<Exec>("verifyDesktopDov24FullApplicableParitySeal") {
    group = "verification"
    description = "Verify Desktop DOV24 full applicable parity acceptance seal"
    commandLine("python3", "scripts/verify_desktop_dov24_full_applicable_parity_seal.py")
}

tasks.register("verifyDesktopDov20Dov24Batch") {
    group = "verification"
    description = "Run the terminal Desktop DOV20-DOV24 portability, presentation, native, and parity seal."
    dependsOn(
        "verifyDesktopDov20PortabilityBackupMigration",
        "verifyDesktopDov21EnPtBrLocalization",
        "verifyDesktopDov22SystemLightDarkAppearance",
        "verifyDesktopDov23NativeDesktopIntegration",
        "verifyDesktopDov24FullApplicableParitySeal",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
    )
}

tasks.register("verifyDesktopDov16Dov19Batch") {
    group = "verification"
    description = "Verify Desktop DOV16-DOV19 writing, modes, public exam, and progress batch"
    dependsOn(
        "verifyDesktopDov16WritingWorkspace",
        "verifyDesktopDov17ContentModesExecution",
        "verifyDesktopDov18PublicExamParity",
        "verifyDesktopDov19ReadinessCoachingReports",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
    )
}


tasks.register("verifyDesktopDov08Dov11Batch") {
    group = "verification"
    description = "Runs the DOV08-DOV11 Desktop persistence, structure, planner, and session batch seal."
    dependsOn(
        "verifyDesktopDov08PersistentWorkspace",
        "verifyDesktopDov09PlanContentLifecycle",
        "verifyDesktopDov10PlannerAdaptiveWorkflows",
        "verifyDesktopDov11SessionRuntime",
        ":desktopApp:test",
        ":desktopApp:compileKotlin",
    )
}


tasks.register<Exec>("verifyCpov05Cpov06PriorityBancaQuestions") {
    group = "verification"
    description = "Checks CPOV05 explainable priority and CPOV06 banca question contracts."
    commandLine("python3", "scripts/verify_cpov05_06_priority_banca_questions.py")
}

tasks.register<Exec>("verifyCpov07Cpov08BancaErrorNotebook") {
    group = "verification"
    description = "Checks CPOV07 banca blueprint scoring and CPOV08 error-notebook review-policy contracts."
    commandLine("python3", "scripts/verify_cpov07_08_banca_error_notebook.py")
}

tasks.register("checkAll") {
    group = "verification"
    description = "Runs repository contracts, formatting, unit tests, and Android lint."
    dependsOn(
        "verifyRepositoryContracts",
        "verifyCpov00Toolchain",
        "verifyCpov01PublicExamProfile",
        "verifyCpov02EditalImport",
        "verifyCpov03Cpov04PublicExamProgress",
        "verifyCpov05Cpov06PriorityBancaQuestions",
        "verifyCpov07Cpov08BancaErrorNotebook",
        "verifyCpov09Cpov10LegislationDashboard",
        "verifyCpov11Cpov12FinalStretchOnboarding",
        "verifyCpov13PublicExamReleaseSeal",
        "verifyCpovRoadmapReconciliation",
        "verifyPhase56_57ReleaseReadiness",
        "verifyPhase58_59WarningHygiene",
        "verifyPhase60_61ContentReportSeal",
        "verifyPhase62OnboardingSeal",
        "verifyPhase63_64ExamRunbookSprintSeal",
        "verifyPostPhase64FinalValidationRefresh",
        "verifyAndroidFocusShellFinalSeal",
        "verifyCloudOptionalSyncSettings",
        "verifyCsov02SyncBundle",
        "verifyCsov03ProviderAbstraction",
        "verifyCsov04OptionalSyncOnboarding",
        "verifyCsov05ManualFolderSync",
        "verifyCsov06SyncLedger",
        "verifyCsov07MergeEngine",
        "verifyCsov08ConflictReview",
        "verifyCsov09BackgroundSyncModes",
        "verifyCsov10RealProviders",
        "verifyCsov11FinalSyncSeal",
        "verifyCsov12ProviderSetupSecurity",
        "verifyCsov13SyncRunbook",
        "verifyCsov14OptionalSyncDeliveryAudit",
        "verifyCsov15OptionalInstrumentationValidation",
        "verifyContentModeFoundation",
        "verifyProgrammingCourseModel",
        "verifyCodeBlocksSyntax",
        "verifyContentModesFinalIntegrationSeal",
        "verifyOv19PromiseAudit",
        "verifyOv20FinalIntegration",
        "verifyFinalSourceAcceptance",
        "verifyRemediationOv13",
        "verifyFormatting",
        "verifySflp01ProtocolBaseline",
        "verifySflp02Sflp03JournalReconciliation",
        "verifySflp04Sflp05IdentityTransport",
        "verifySflp06Sflp07TopologyOfflineQueue",
        "verifySflp08Sflp09AndroidDesktopIntegration",
        "verifySflp10Sflp11WebTvGeneralization",
        "verifySflp12Sflp13WebTvHandoff",
        "verifySflp14Sflp15LiveSessionProjection",
        "verifySflp16Sflp17ControlInputDelegation",
        "verifySflp18Sflp19NotificationMediaSync",
        "verifySflp20Sflp21ShareImportConflict",
        "verifySflp22Sflp23AccountBackupValidation",
        "verifySflp24FinalReleaseSeal",
        "verifyRel01TargetReleaseValidationRunner",
        "verifyRel02ReleaseEvidenceTriage",
        "verifyRel03Rel04ArtifactInstallRollout",
        "verifyRel05Rel06ReleaseNotesRollback",
        "verifyRel07Rel08FinalReleaseOpsSeal",
        ":core:diagnostics:test",
        ":core:data:testDebugUnitTest",
        ":core:database:testDebugUnitTest",
        ":core:domain:test",
        ":core:model:test",
        ":core:navigation:test",
        ":core:portability:test",
        ":core:testing:test",
        ":app:testReleaseUnitTest",
        ":feature:dashboard:testDebugUnitTest",
        ":feature:analytics:testDebugUnitTest",
        ":feature:library:testDebugUnitTest",
        ":feature:progress:testDebugUnitTest",
        ":feature:privacy:testDebugUnitTest",
        ":feature:plans:testDebugUnitTest",
        ":feature:practice:testDebugUnitTest",
        ":feature:reminders:testDebugUnitTest",
        ":feature:reviews:testDebugUnitTest",
        ":feature:search:testDebugUnitTest",
        ":feature:schedule:testDebugUnitTest",
        ":feature:sessions:testDebugUnitTest",
        ":feature:settings:testDebugUnitTest",
        ":feature:subjects:testDebugUnitTest",
        ":feature:workspace:testDebugUnitTest",
        ":feature:workspace:lintDebug",
        ":app:lintDebug",
        ":app:compileReleaseKotlin",
        ":app:lintRelease",
        ":app:assembleRelease",
        ":app:bundleRelease",
    )
}


tasks.register<Exec>("verifyCsov12ProviderSetupSecurity") {
    group = "verification"
    description = "Checks CSOV12 provider setup and credential-redaction source contracts."
    commandLine("python3", "scripts/verify_csov12_provider_setup_security.py")
}


tasks.register<Exec>("verifyCsov13SyncRunbook") {
    group = "verification"
    description = "Checks CSOV13 manual and background sync runbook source contracts."
    commandLine("python3", "scripts/verify_csov13_sync_runbook.py")
}


tasks.register<Exec>("verifyCsov14OptionalSyncDeliveryAudit") {
    group = "verification"
    description = "Runs the cumulative CSOV00-CSOV13 optional sync delivery audit."
    commandLine("python3", "scripts/verify_csov14_optional_sync_delivery_audit.py")
}


tasks.register<Exec>("verifyCsov15OptionalInstrumentationValidation") {
    group = "verification"
    description = "Checks CSOV15 optional adb/root instrumentation validation source contracts."
    commandLine("python3", "scripts/verify_csov15_optional_instrumentation_validation.py")
}


tasks.register<Exec>("recordOptionalInstrumentationValidation") {
    group = "verification"
    description = "Optionally runs connected Android instrumentation through adb and records skip/pass/fail evidence."
    commandLine("python3", "scripts/run_optional_instrumentation_validation.py")
}

tasks.register<Exec>("verifyCpov09Cpov10LegislationDashboard") {
    group = "verification"
    description = "Verifies CPOV09 structured legislation and CPOV10 derived concurso dashboard contracts."
    commandLine("python3", "scripts/verify_cpov09_10_legislation_dashboard.py")
}


tasks.register<Exec>("verifyCpov11Cpov12FinalStretchOnboarding") {
    group = "verification"
    description = "Verifies CPOV11 final-stretch strategy and CPOV12 public-exam onboarding contracts."
    commandLine("python3", "scripts/verify_cpov11_12_final_stretch_onboarding.py")
}

tasks.register<Exec>("verifyCpov13PublicExamReleaseSeal") {
    group = "verification"
    description = "Verifies the complete CPOV migration, portability, connected-journey and release-seal contracts."
    commandLine("python3", "scripts/verify_cpov13_public_exam_release_seal.py")
}

tasks.register<Exec>("verifyCpovRoadmapReconciliation") {
    group = "verification"
    description = "Verifies reconciled CPOV00-CPOV13 roadmap promises missed by the original source seals."
    commandLine("python3", "scripts/verify_cpov_roadmap_reconciliation.py")
}



tasks.register<Exec>("verifyStudyFlowTvQuestionPractice") {
    group = "verification"
    description =
        "Checks TVOV09 Android TV question-practice source, control, focus, shell, test, and" +
        "documentation contracts."
    commandLine("python3", "scripts/verify_tvov09_question_practice.py")
}

tasks.register<Exec>("verifyStudyFlowTvMockExamStudio") {
    group = "verification"
    description =
        "Checks TVOV10 Android TV Mock Exam Studio source, control, focus, shell, test, and" +
        "documentation contracts."
    commandLine("python3", "scripts/verify_tvov10_mock_exam_studio.py")
}

tasks.register<Exec>("verifyStudyFlowTvReviewNotebook") {
    group = "verification"
    description =
        "Checks TVOV11 Android TV Review and error-notebook source, control, focus, shell," +
        "test, and documentation contracts."
    commandLine("python3", "scripts/verify_tvov11_review_notebook.py")
}

tasks.register<Exec>("verifyStudyFlowTvProgressAnalytics") {
    group = "verification"
    description =
        "Checks TVOV12 Android TV Progress and analytics source, control, focus, shell, test," +
        "and documentation contracts."
    commandLine("python3", "scripts/verify_tvov12_progress_analytics.py")
}


tasks.register<Exec>("verifyStudyFlowTvReadOnlySchedule") {
    group = "verification"
    description =
        "Checks TVOV13 Android TV read-only Schedule source, control, focus, shell, test, and" +
        "documentation contracts."
    commandLine("python3", "scripts/verify_tvov13_read_only_schedule.py")
}

tasks.register<Exec>("verifyStudyFlowTvLibraryReference") {
    group = "verification"
    description =
        "Checks TVOV14 Android TV limited Library/reference source, control, focus, shell," +
        "test, and documentation contracts."
    commandLine("python3", "scripts/verify_tvov14_library_reference.py")
}


tasks.register<Exec>("verifyStudyFlowTvSettings") {
    group = "verification"
    description = "Checks TVOV15 Android TV minimal Settings source, control, shell, test, and documentation contracts."
    commandLine("python3", "scripts/verify_tvov15_settings.py")
}

tasks.register<Exec>("verifyStudyFlowTvControlStateContinuity") {
    group = "verification"
    description = "Checks TVOV16 Android TV local/paired control and state-continuity contracts."
    commandLine("python3", "scripts/verify_tvov16_control_state_continuity.py")
}


tasks.register<Exec>("verifyStudyFlowTvAccessibilityTenFootUi") {
    group = "verification"
    description =
        "Checks TVOV17 Android TV accessibility, 10-foot typography/layout, focus visibility," +
        "tests, and shell contracts."
    commandLine("python3", "scripts/verify_tvov17_accessibility_10foot_ui.py")
}

tasks.register<Exec>("verifyStudyFlowTvSizeOptimization") {
    group = "verification"
    description =
        "Checks TVOV18 Android TV R8, resource shrinking, release budget policy, tests, and" +
        "Devtool contracts."
    commandLine("python3", "scripts/verify_tvov18_size_optimization.py")
}

tasks.register<Exec>("verifyStudyFlowTvOptimizedReleaseArtifact") {
    group = "verification"
    description =
        "Builds and verifies the optimized TV Release APK, R8 mapping evidence, ZIP" +
        "integrity, and 25 MiB target."
    dependsOn(":app-tv:assembleRelease")
    commandLine("python3", "scripts/verify_tvov18_size_optimization.py", "--require-release-artifact")
}

tasks.register<Exec>("verifyStudyFlowTvManifestPackaging") {
    group = "verification"
    description =
        "Checks TVOV19 Leanback manifest, package identity, backup/data-extraction hardening," +
        "signing contract, and banner resources."
    commandLine("python3", "scripts/verify_tvov19_manifest_packaging.py")
}

tasks.register<Exec>("verifyStudyFlowTvTargetIsolation") {
    group = "verification"
    description =
        "Checks that the studyflow_tv Devtool target contains only TV/shared-control tasks" +
        "and never phone :app or feature-module work."
    commandLine("python3", "scripts/verify_tvov19_manifest_packaging.py", "--target-isolation")
}

tasks.register<Exec>("verifyStudyFlowTvPackagedArtifacts") {
    group = "verification"
    description =
        "Builds and inspects Debug/Release TV APK packaging, Leanback identity, ZIP" +
        "integrity, and optional production signing."
    dependsOn(":app-tv:assembleDebug", ":app-tv:assembleRelease")
    commandLine("python3", "scripts/verify_tvov19_manifest_packaging.py", "--require-packaged-artifacts")
}

tasks.register<Exec>("verifyStudyFlowTvFinalAcceptanceContracts") {
    group = "verification"
    description =
        "Checks TVOV20 final release/real-device acceptance policy, tests, docs, and" +
        "validation wiring without requiring a connected device."
    commandLine("python3", "scripts/verify_tvov20_real_device_release_seal.py")
}

tasks.register<Exec>("verifyStudyFlowTvRealDeviceAcceptance") {
    group = "verification"
    description =
        "Runs TVOV20 install/upgrade, launch, Leanback-device, and D-pad smoke checks when" +
        "exactly one usable TV ADB device is connected; otherwise skips cleanly."
    dependsOn(":app-tv:assembleDebug", "verifyStudyFlowTvManifestPackaging")
    commandLine("python3", "scripts/verify_tvov20_real_device_release_seal.py", "--device-acceptance")
}

tasks.register<Exec>("verifyStudyFlowTvFinalReleaseSeal") {
    group = "verification"
    description =
        "Seals TVOV20 Release criteria from optimized artifact, package hardening, unit" +
        "tests, lint, and optional production signing evidence."
    dependsOn(
        "verifyStudyFlowTvOptimizedReleaseArtifact",
        "verifyStudyFlowTvPackagedArtifacts",
        ":core:tvcontrol:test",
        ":core:tvlink:testDebugUnitTest",
        ":core:tvlink:lintDebug",
        ":app-tv:testDebugUnitTest",
        ":app-tv:lintDebug",
        ":app-tv:lintRelease",
    )
    commandLine("python3", "scripts/verify_tvov20_real_device_release_seal.py", "--require-release-artifact")
}


tasks.register<Exec>("verifySflp01ProtocolBaseline") {
    group = "verification"
    description =
        "Checks SFLP01 platform-neutral protocol identities, capabilities, envelope/schema/golden vectors, " +
            "and the pure JVM core:link contract tests."
    dependsOn(":core:link:test")
    commandLine("python3", "scripts/verify_sflp01_protocol_baseline.py")
}


tasks.register<Exec>("verifySflp02Sflp03JournalReconciliation") {
    group = "verification"
    description =
        "Checks SFLP02/SFLP03 journal, vector-frontier reconciliation, conflicts, sync wire vectors, " +
            "and pure JVM core:link tests."
    dependsOn("verifySflp01ProtocolBaseline", ":core:link:test")
    commandLine("python3", "scripts/verify_sflp02_03_journal_reconciliation.py")
}

tasks.register<Exec>("verifySflp04Sflp05IdentityTransport") {
    group = "verification"
    description =
        "Checks SFLP04/SFLP05 generic identity/trust/pairing/reconnect plus transport/discovery contracts."
    dependsOn("verifySflp02Sflp03JournalReconciliation", ":core:link:test")
    commandLine("python3", "scripts/verify_sflp04_05_identity_transport.py")
}


tasks.register<Exec>("verifySflp06Sflp07TopologyOfflineQueue") {
    group = "verification"
    description =
        "Checks SFLP06/SFLP07 multi-peer routing, loop prevention, durable offline queue, and reconnect recovery."
    dependsOn("verifySflp04Sflp05IdentityTransport", ":core:link:test")
    commandLine("python3", "scripts/verify_sflp06_07_topology_offline_queue.py")
}


tasks.register<Exec>("verifySflp08Sflp09AndroidDesktopIntegration") {
    group = "verification"
    description =
        "Checks SFLP08/SFLP09 Android + Desktop generic peer bindings, persistence, discovery, " +
            "trust UI, and direct sync."
    dependsOn(
        "verifySflp06Sflp07TopologyOfflineQueue",
        ":core:link:test",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp08_09_android_desktop_integration.py")
}


tasks.register<Exec>("verifySflp10Sflp11WebTvGeneralization") {
    group = "verification"
    description =
        "Checks SFLP10/SFLP11 Web/Web-TV peer integration and Android TV generic peer generalization."
    dependsOn(
        "verifySflp08Sflp09AndroidDesktopIntegration",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp10_11_web_tv_generalization.py")
}



tasks.register<Exec>("verifySflp12Sflp13WebTvHandoff") {
    group = "verification"
    description =
        "Checks SFLP12 Web TV receiver connectivity and SFLP13 cross-device activity handoff."
    dependsOn(
        "verifySflp10Sflp11WebTvGeneralization",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp12_13_web_tv_handoff.py")
}



tasks.register<Exec>("verifySflp14Sflp15LiveSessionProjection") {
    group = "verification"
    description =
        "Checks SFLP14 live session continuity and SFLP15 universal presentation projection."
    dependsOn(
        "verifySflp12Sflp13WebTvHandoff",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp14_15_live_session_projection.py")
}



tasks.register<Exec>("verifySflp16Sflp17ControlInputDelegation") {
    group = "verification"
    description = "Checks SFLP16 control protocol and SFLP17 input delegation."
    dependsOn(
        "verifySflp14Sflp15LiveSessionProjection",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp16_17_control_input_delegation.py")
}


tasks.register<Exec>("verifySflp18Sflp19NotificationMediaSync") {
    group = "verification"
    description = "Checks SFLP18 notification/schedule sync and SFLP19 material streaming handoff."
    dependsOn(
        "verifySflp16Sflp17ControlInputDelegation",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp18_19_notification_media_sync.py")
}


tasks.register<Exec>("verifySflp20Sflp21ShareImportConflict") {
    group = "verification"
    description = "Checks SFLP20 clipboard/share/import bridge and SFLP21 conflict-review UX."
    dependsOn(
        "verifySflp18Sflp19NotificationMediaSync",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp20_21_share_import_conflict.py")
}


tasks.register<Exec>("verifyCrossPlatformParityBaseline") {
    group = "verification"
    description =
        "Checks PARITY01-PARITY03 cross-platform capability truth, documentation reconciliation, " +
            "and the restored OV20 integration seal."
    commandLine("python3", "scripts/verify_parity01_03_cross_platform_baseline.py")
}


tasks.register<Exec>("verifyStudyFlowTvPresentationProjection") {
    group = "verification"
    description =
        "Checks TVOV21 typed presentation snapshots, canonical phone-side projection, " +
            "fixture-free TV runtime wiring, and source-ID continuity."
    commandLine("python3", "scripts/verify_tvov21_presentation_projection.py")
}

tasks.register<Exec>("verifyStudyFlowTvLocalPairingTransport") {
    group = "verification"
    description =
        "Checks TVOV22 explicit local pairing, Android NSD discovery, framed TCP transport, " +
            "revocable sessions, and production phone/TV wiring."
    commandLine("python3", "scripts/verify_tvov22_local_pairing_transport.py")
}

tasks.register<Exec>("verifyStudyFlowTvPhoneController") {
    group = "verification"
    description =
        "Checks TVOV23 Settings-accessible Android phone TV controller, explicit pairing UI, " +
            "persisted-session resume, and shared remote-command coverage."
    commandLine("python3", "scripts/verify_tvov23_phone_controller.py")
}

tasks.register<Exec>("verifyStudyFlowTvCastStateSynchronization") {
    group = "verification"
    description =
        "Checks TVOV24 canonical presentation streaming, exact TV receipt acknowledgement, " +
            "reconnect resync, and explicit STOP_CAST lifecycle."
    commandLine("python3", "scripts/verify_tvov24_cast_state_synchronization.py")
}

tasks.register<Exec>("verifyStudyFlowTvDomainPersistenceConvergence") {
    group = "verification"
    description =
        "Checks TVOV25 canonical phone-owned mutations, TV outbox/ack flow, idempotent persistence, " +
            "and fixture-free projected results."
    commandLine("python3", "scripts/verify_tvov25_domain_persistence_convergence.py")
}



tasks.register<Exec>("verifyStudyFlowTvEndToEndPhysicalSealContracts") {
    group = "verification"
    description =
        "Checks TVOV26 fail-closed two-device Release acceptance, restart-resync, exact-artifact " +
            "evidence, and final seal wiring without claiming physical execution."
    commandLine("python3", "scripts/verify_tvov26_end_to_end_physical_seal.py")
}

tasks.register<Exec>("verifyStudyFlowTvPhysicalTwoDeviceAcceptance") {
    group = "verification"
    description =
        "Runs fail-closed TVOV26 physical acceptance on one real Android phone and one real " +
            "Leanback TV; missing devices, signing, or data are failures, never skips."
    dependsOn(
        ":app:assembleRelease",
        ":app-tv:assembleRelease",
        "verifyStudyFlowTvEndToEndPhysicalSealContracts",
        "verifyStudyFlowTvOptimizedReleaseArtifact",
        "verifyStudyFlowTvPackagedArtifacts",
    )
    commandLine("python3", "scripts/run_tvov26_two_device_acceptance.py")
}

tasks.register<Exec>("verifyStudyFlowTvFinalProductSeal") {
    group = "verification"
    description =
        "Consumes real TVOV26 two-device evidence fail-closed and seals the complete " +
            "TVOV00-TVOV26 product track."
    dependsOn(
        "verifyStudyFlowTvPhysicalTwoDeviceAcceptance",
        "verifyStudyFlowTvFinalReleaseSeal",
        ":core:tvcontrol:test",
        ":core:tvlink:testDebugUnitTest",
        ":app-tv:testDebugUnitTest",
        ":app-tv:lintRelease",
    )
    commandLine("python3", "scripts/verify_tvov26_end_to_end_physical_seal.py", "--require-evidence")
}

tasks.register<Exec>("verifyStudyFlowTvFullNativeMaterialViewer") {
    group = "verification"
    description = "Checks TVOV27 native text/image/PDF/media material viewing and presentation projection."
    commandLine("python3", "scripts/verify_tvov27_full_native_material_viewer.py")
}

tasks.register<Exec>("verifyStudyFlowTvGoalProjectionActions") {
    group = "verification"
    description = "Checks TVOV28 canonical goal projection and complete/pause/resume actions."
    commandLine("python3", "scripts/verify_tvov28_goal_projection_actions.py")
}

tasks.register<Exec>("verifyStudyFlowTvErrorNotebookMutationParity") {
    group = "verification"
    description = "Checks TVOV29 Error Notebook reclassify/resolve/reopen parity."
    commandLine("python3", "scripts/verify_tvov29_error_notebook_mutation_parity.py")
}

tasks.register<Exec>("verifyStudyFlowTvDpadSflpSearch") {
    group = "verification"
    description = "Checks TVOV30 D-pad search and SFLP delegated text input."
    commandLine("python3", "scripts/verify_tvov30_dpad_sflp_search.py")
}

tasks.register<Exec>("verifyStudyFlowTvAndroidWebSemanticParity") {
    group = "verification"
    description = "Checks TVOV31 Android-TV/Web-TV common semantic fixture parity."
    commandLine("python3", "scripts/verify_tvov31_android_web_tv_semantic_parity.py")
}

tasks.register("verifyStudyFlowTvTvov27Tvov31Batch") {
    group = "verification"
    description = "Runs the terminal TVOV27-TVOV31 convergence seal."
    dependsOn(
        "verifyStudyFlowTvFullNativeMaterialViewer",
        "verifyStudyFlowTvGoalProjectionActions",
        "verifyStudyFlowTvErrorNotebookMutationParity",
        "verifyStudyFlowTvDpadSflpSearch",
        "verifyStudyFlowTvAndroidWebSemanticParity",
    )
}

tasks.register<Exec>("verifyStudyFlowTvRoadmapInstructions") {
    group = "verification"
    description =
        "Checks the full Android TV roadmap, Devtool target rules, and structured-overlay " +
            "implementation preferences."
    commandLine("python3", "scripts/verify_tv_roadmap_instructions.py")
}

tasks.register<Exec>("verifySflp22Sflp23AccountBackupValidation") {
    group = "verification"
    description = "Checks SFLP22 account/backup handoff and SFLP23 release validation consolidation."
    dependsOn(
        "verifySflp20Sflp21ShareImportConflict",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp22_23_account_backup_validation.py")
}

tasks.register<Exec>("verifySflp24FinalReleaseSeal") {
    group = "verification"
    description = "Checks the final SFLP01-SFLP23 consolidated release validation seal."
    dependsOn(
        "verifySflp22Sflp23AccountBackupValidation",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp24_final_release_seal.py")
}

tasks.register<Exec>("verifyRel01TargetReleaseValidationRunner") {
    group = "verification"
    description = "Checks the post-SFLP target-machine release validation runner and evidence plan."
    commandLine("python3", "scripts/verify_rel01_target_release_validation_runner.py")
}

tasks.register<Exec>("verifyRel02ReleaseEvidenceTriage") {
    group = "verification"
    description = "Checks the REL02 release evidence exporter and failure triage bundle."
    dependsOn("verifyRel01TargetReleaseValidationRunner")
    commandLine("python3", "scripts/verify_rel02_release_evidence_triage.py")
}


tasks.register<Exec>("verifyRel03Rel04ArtifactInstallRollout") {
    group = "verification"
    description = "Checks REL03 signed artifact/install-smoke evidence and REL04 rollout manifest gating."
    dependsOn("verifyRel02ReleaseEvidenceTriage")
    commandLine("python3", "scripts/verify_rel03_04_artifact_install_rollout.py")
}


tasks.register<Exec>("verifyRel05Rel06ReleaseNotesRollback") {
    group = "verification"
    description = "Checks REL05 release-notes export and REL06 rollback package manifest generation."
    dependsOn("verifyRel03Rel04ArtifactInstallRollout")
    commandLine("python3", "scripts/verify_rel05_06_release_notes_rollback.py")
}


tasks.register<Exec>("verifyRel07Rel08FinalReleaseOpsSeal") {
    group = "verification"
    description = "Checks REL07 release checklist gate and REL08 final release-ops evidence seal."
    dependsOn("verifyRel05Rel06ReleaseNotesRollback")
    commandLine("python3", "scripts/verify_rel07_08_final_release_ops_seal.py")
}

tasks.register<Exec>("verifyRel09KotlinTestClasspathHotfix") {
    group = "verification"
    description = "Checks REL09 Kotlin test classpath hotfix wiring for core/link and desktopApp."
    commandLine("python3", "scripts/verify_rel09_kotlin_test_classpath_hotfix.py")
}



tasks.register<Exec>("verifyWeb20SharedSemanticGoldenVectors") {
    group = "verification"
    description = "Run the WEB20 language-neutral semantic golden-vector contract."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_web20_shared_semantic_golden_vectors.py")
}

tasks.register<Exec>("verifyWeb20RoadmapTruth") {
    group = "verification"
    description =
        "Verify WEB20 closes only the canonical roadmap/ledger track and leaves Presence/Parity ordering intact."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_roadmap01_cross_platform_truth_reconciliation.py")
}

tasks.register<Exec>("verifyWeb20WebLint") {
    group = "verification"
    description = "Type-check Web semantics and verify committed runtime parity."
    workingDir = file("web")
    commandLine("npm", "run", "lint")
}

tasks.register<Exec>("verifyWeb20WebTests") {
    group = "verification"
    description = "Run the complete Web regression suite after WEB20 semantic convergence."
    workingDir = file("web")
    commandLine("npm", "test")
}

tasks.register("verifyWeb20WebRegression") {
    group = "verification"
    description = "Run WEB20 Web type/runtime and regression checks."
    dependsOn("verifyWeb20WebLint", "verifyWeb20WebTests")
}

tasks.register<Exec>("verifyWeb20WebRelease") {
    group = "verification"
    description = "Check the committed self-contained Web release after semantic convergence."
    workingDir = file("web")
    commandLine("npm", "run", "release:check")
}

tasks.register("verifyWeb20TerminalSeal") {
    group = "verification"
    description = "Terminal WEB20 seal across Kotlin/core, Desktop, and TypeScript/Web."
    dependsOn(
        "verifyWeb20SharedSemanticGoldenVectors",
        "verifyWeb20RoadmapTruth",
        "verifyWeb20WebRegression",
        "verifyWeb20WebRelease",
        ":core:domain:test",
        ":core:link:test",
        ":core:portability:test",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
    )
}

tasks.register<Exec>("verifyPresence01PeerActivityPresence") {
    group = "verification"
    description =
        "Checks PRESENCE01 ephemeral multi-client peer/activity presence, capability visibility, " +
            "expiry, reconnect, and loop safety."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_presence01_peer_activity_presence.py")
}

tasks.register<Exec>("verifyPresence01WebLint") {
    group = "verification"
    description = "Type-check Web/Web-TV presence and verify committed runtime parity."
    workingDir = file("web")
    commandLine("npm", "run", "lint")
}

tasks.register<Exec>("verifyPresence01WebTests") {
    group = "verification"
    description = "Run the complete Web regression suite with PRESENCE01."
    workingDir = file("web")
    commandLine("npm", "test")
}

tasks.register<Exec>("verifyPresence01LanRelayStability") {
    group = "verification"
    description = "Exercise the browser-TV LAN relay with load-tolerant startup/readiness handling."
    workingDir = file("web")
    commandLine(
        "python3",
        "-m",
        "unittest",
        "tests.test_wtv06_07_history_control_relay." +
            "Wtv0607HistoryControlRelayTest.test_lan_relay_pair_command_cast_revoke",
    )
}

tasks.register("verifyPresence01WebRegression") {
    group = "verification"
    description = "Run PRESENCE01 Web/Web-TV type/runtime and regression checks."
    dependsOn("verifyPresence01WebLint", "verifyPresence01WebTests", "verifyPresence01LanRelayStability")
}

tasks.register<Exec>("verifyPresence01WebRelease") {
    group = "verification"
    description = "Check the self-contained Web release contains the PRESENCE01 runtime."
    workingDir = file("web")
    commandLine("npm", "run", "release:check")
}

tasks.register("verifyPresence01TerminalSeal") {
    group = "verification"
    description = "Terminal PRESENCE01 seal across shared link runtime and all five StudyFlow client surfaces."
    dependsOn(
        "verifyPresence01PeerActivityPresence",
        "verifyWeb20SharedSemanticGoldenVectors",
        "verifyWeb20RoadmapTruth",
        "verifyPresence01WebRegression",
        "verifyPresence01WebRelease",
        ":core:link:test",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
        ":app:compileDebugKotlin",
        ":app:testReleaseUnitTest",
        ":app:assembleDebug",
        ":app-tv:compileDebugKotlin",
        ":app-tv:testDebugUnitTest",
        ":app-tv:assembleDebug",
    )
}

tasks.register<Exec>("verifyParity04FiveClientFullMesh") {
    group = "verification"
    description =
        "Checks PARITY04 five-client full-mesh, convergence, exactly-once, capability, semantic, " +
            "and portability contracts."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_parity04_five_client_full_mesh.py")
}

tasks.register<Exec>("verifyParity04WebAcceptance") {
    group = "verification"
    description = "Runs the Web/Web-TV PARITY04 acceptance corpus against production runtimes."
    workingDir = file("web")
    commandLine("node", "tests/parity04_runner.mjs")
}

tasks.register("verifyParity04TerminalSeal") {
    group = "verification"
    description =
        "Terminal five-client PARITY04 acceptance seal across shared core, Desktop, Web, Android, and Android TV."
    dependsOn(
        "verifyParity04FiveClientFullMesh",
        "verifyParity04WebAcceptance",
        "verifyWeb20SharedSemanticGoldenVectors",
        "verifyPresence01PeerActivityPresence",
        "verifyPresence01WebRegression",
        "verifyPresence01WebRelease",
        ":core:domain:test",
        ":core:link:test",
        ":core:portability:test",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
        ":app:compileDebugKotlin",
        ":app:testReleaseUnitTest",
        ":app:assembleDebug",
        ":app-tv:compileDebugKotlin",
        ":app-tv:testDebugUnitTest",
        ":app-tv:assembleDebug",
    )
}

tasks.register<Exec>("verifyRelFinalRoadmapPromiseAudit") {
    group = "verification"
    description = "Checks the permanent REL-FINAL 213-verifier promise ledger and repaired product truth."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_rel_final_roadmap_promise_audit.py")
}

tasks.register<Exec>("runRelFinalExhaustiveRoadmapAudit") {
    group = "verification"
    description =
        "Standalone REL-FINAL exhaustive roadmap audit; run outside concurrent Gradle compilation."
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        "scripts/run_rel_final_roadmap_audit.py",
        "--timeout-seconds",
        "180",
        "--workers",
        "4",
    )
}

tasks.register<Exec>("verifyRelFinalDevtoolReleaseRunner") {
    group = "verification"
    description =
        "Checks that TV and REL-FINAL Android builds route through Devtool's Termux-safe Android runner."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_rel_final_devtool_release_runner.py")
}

tasks.register<Exec>("prepareRelFinalEvidencePlan") {
    group = "verification"
    description = "Prepares REL-FINAL target-host/device command templates without claiming release readiness."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/run_rel_final_release_campaign.py", "--mode", "plan")
}

tasks.register<Exec>("runRelFinalReleaseCampaign") {
    group = "verification"
    description = "Executes the real REL-FINAL target-host evidence campaign; fails until all evidence closes."
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        "scripts/run_rel_final_release_campaign.py",
        "--mode",
        "execute",
        "--channel",
        "production",
    )
}

tasks.register<Exec>("finalizeRelFinalReleaseCampaign") {
    group = "verification"
    description = "Rebuilds REL05-REL08 outputs and final verdict after real device/operator evidence is recorded."
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        "scripts/run_rel_final_release_campaign.py",
        "--mode",
        "finalize",
        "--channel",
        "production",
    )
}

tasks.register("verifyRelFinalTerminalSeal") {
    group = "verification"
    description = "Terminal product/static seal before the separate real REL-FINAL evidence execution campaign."
    dependsOn(
        "verifyRelFinalRoadmapPromiseAudit",
        "verifyRelFinalDevtoolReleaseRunner",
        "prepareRelFinalEvidencePlan",
        "verifyParity04TerminalSeal",
        "verifySflp24FinalReleaseSeal",
        "verifyRel07Rel08FinalReleaseOpsSeal",
        "verifyFinalSourceAcceptance",
    )
}


tasks.register<Exec>("verifyStudyFlowTvDevtoolAndroidRunner") {
    group = "verification"
    description = "Checks that the StudyFlow TV Devtool target uses the Android runner and TV-only debug tasks."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_tv_devtool_android_runner.py")
}

// ATT01 — Unified Attention Domain + SFLP Correctness Foundation
tasks.register<Exec>("verifyAtt01AttentionContracts") {
    group = "verification"
    description = "Checks ATT01 attention-domain, SFLP compatibility, persistence, migration, diagnostics, and documentation contracts."
    commandLine("python3", "scripts/verify_att01_unified_attention_foundation.py")
}

tasks.register<Exec>("verifyAtt01WebAttentionRuntime") {
    group = "verification"
    description = "Checks ATT01 Web attention TypeScript parity and durable runtime behavior."
    workingDir(rootProject.file("web"))
    commandLine(
        "bash",
        "-lc",
        "npx tsc -p tsconfig.json --noEmit --pretty false && " +
            "node scripts/compile_runtime.mjs && " +
            "python3 -m unittest tests.test_att01_attention_runtime tests.test_sflp18_19_notification_media",
    )
}

tasks.register("verifyAtt01UnifiedAttentionFoundation") {
    group = "verification"
    description = "Focused ATT01 production gate across shared, Android, Desktop, Web, and Android TV attention foundations."
    dependsOn(
        "verifyAtt01AttentionContracts",
        "verifyAtt01WebAttentionRuntime",
        ":core:link:test",
        ":core:diagnostics:test",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
        ":app-tv:testDebugUnitTest",
    )
}

// ATT02 — Android Attention System 2.0
tasks.register<Exec>("verifyAtt02AndroidAttentionContracts") {
    group = "verification"
    description = "Checks ATT02 Android policy, scheduling, channels, actions, recovery, reliability, diagnostics, tests and documentation contracts."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att02_android_attention_system.py")
}

tasks.register("verifyAtt02AndroidAttentionSystem") {
    group = "verification"
    description = "Focused ATT02 production gate for the complete Android attention system slice."
    dependsOn(
        "verifyAtt02AndroidAttentionContracts",
        ":core:domain:test",
        ":feature:reminders:testDebugUnitTest",
        ":feature:settings:testDebugUnitTest",
        ":app:testReleaseUnitTest",
    )
}

// ATT03 — Android Widget Suite 2.0
tasks.register<Exec>("verifyAtt03AndroidWidgetContracts") {
    group = "verification"
    description = "Checks ATT03 widget modes, per-instance configuration, freshness scheduling, actions, diagnostics, migration, tests and documentation contracts."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att03_android_widget_suite.py")
}

tasks.register("verifyAtt03AndroidWidgetSuite") {
    group = "verification"
    description = "Focused ATT03 production gate for the complete Android Widget Suite 2.0 slice."
    dependsOn(
        "verifyAtt03AndroidWidgetContracts",
        ":app:testReleaseUnitTest",
    )
}
