val qualitySourceRoots = listOf(
    "app",
    "core",
    "feature",
    "desktopApp",
    "build-logic",
)

val excludedDirectoryNames = setOf("build", ".gradle", ".idea")

fun File.isQualitySource(): Boolean =
    extension in setOf("kt", "kts", "xml") &&
        toPath().none { pathSegment -> pathSegment.toString() in excludedDirectoryNames }

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

tasks.register<Exec>("verifyRepositoryContracts") {
    group = "verification"
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
        if ("import androidx.compose.material3.ScrollableTabRow" in design) {
            failures += "StudyFlowWorkspaceComponents.kt: deprecated ScrollableTabRow remains"
        }
        if ("PrimaryScrollableTabRow(" !in design) {
            failures += "StudyFlowWorkspaceComponents.kt: PrimaryScrollableTabRow is missing"
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


tasks.register<Exec>("verifyFinalSourceAcceptance") {
    group = "verification"
    description = "Checks source/static final acceptance only; it does not claim release artifact or device acceptance."
    commandLine("python3", "scripts/verify_final_app_acceptance.py")
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

tasks.register("checkAll") {
    group = "verification"
    description = "Runs repository contracts, formatting, unit tests, and Android lint."
    dependsOn(
        "verifyRepositoryContracts",
        "verifyPhase56_57ReleaseReadiness",
        "verifyPhase58_59WarningHygiene",
        "verifyPhase60_61ContentReportSeal",
        "verifyPhase62OnboardingSeal",
        "verifyPhase63_64ExamRunbookSprintSeal",
        "verifyPostPhase64FinalValidationRefresh",
        "verifyFinalSourceAcceptance",
        "verifyRemediationOv13",
        "verifyFormatting",
        ":core:diagnostics:test",
        ":core:data:testDebugUnitTest",
        ":core:database:testDebugUnitTest",
        ":core:domain:test",
        ":core:model:test",
        ":core:navigation:test",
        ":core:portability:test",
        ":core:testing:test",
        ":app:testDebugUnitTest",
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
