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
        "Runs StudyFlow contracts through Phase 37, UI/UX V2 through Phase 51, and Debug Workbench D1 through D8."
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
        """.trimIndent(),
    )
}

tasks.register("checkAll") {
    group = "verification"
    description = "Runs repository contracts, formatting, unit tests, and Android lint."
    dependsOn(
        "verifyRepositoryContracts",
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
    )
}
