package com.mikeyphw.studyflow

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowShellFoundationContractTest {
    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(8) {
            if (current.resolve("settings.gradle.kts").exists()) return current
            current = current.parent ?: return@repeat
        }
        error("Could not locate StudyFlow repository root")
    }

    private fun source(relative: String): String = repoRoot().resolve(relative).toFile().readText()

    @Test
    fun `root navigation uses StudyFlow shell instead of Material navigation suite`() {
        val navigation = source("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
        assertTrue("StudyFlowShell(" in navigation)
        assertFalse("NavigationSuiteScaffold" in navigation)
        assertTrue("StudyFlowWorkspaceRoot.TODAY" in navigation)
        assertTrue("StudyFlowWorkspaceRoot.PLAN" in navigation)
        assertTrue("StudyFlowWorkspaceRoot.PRACTICE" in navigation)
        assertTrue("StudyFlowWorkspaceRoot.PROGRESS" in navigation)
    }

    @Test
    fun `shell owns compact dock and expanded rail without stock navigation bar primitives`() {
        val shell = source("app/src/main/java/com/mikeyphw/studyflow/StudyFlowShell.kt")
        val primitives = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowShellPrimitives.kt",
        )
        assertTrue("StudyFlowCompactDock" in shell)
        assertTrue("StudyFlowExpandedRail" in shell)
        assertTrue("screenWidthDp >= 600" in shell)
        assertFalse("NavigationBar(" in primitives)
        assertFalse("NavigationRail(" in primitives)
        assertTrue("Role.Tab" in primitives)
    }
}
