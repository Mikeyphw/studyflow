val qualitySourceRoots = listOf(
    "app",
    "core",
    "feature",
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
        "Runs StudyFlow contracts through Phase 37, UI/UX V2 through Phase 61, and Debug Workbench D1 through D8."
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
        python3 scripts/verify_final_app_acceptance.py
        python3 scripts/verify_remediation_phase_1_2.py
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


tasks.register<Exec>("verifyFinalAppAcceptanceSeal") {
    group = "verification"
    description = "Checks the final StudyFlow V2 through Phase 62 acceptance contract."
    commandLine("python3", "scripts/verify_final_app_acceptance.py")
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
        "verifyFinalAppAcceptanceSeal",
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
    )
}
