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
        assertTrue("LocalWindowInfo.current.containerSize.width" in shell)
        assertTrue("containerWidthPx >= 600.dp.roundToPx()" in shell)
        assertFalse("NavigationBar(" in primitives)
        assertFalse("NavigationRail(" in primitives)
        assertTrue("Role.Tab" in primitives)
        assertTrue("testTag(\"studyflow.shell.root." in primitives)
        // The dock MUST remain outside the session visibility branch.
        assertTrue("StudyFlowCompactDock(destinations = destinations)" in shell)
        assertTrue("AnimatedVisibility(" in shell)
        assertFalse("studyflow-session-dock-morph" in shell)
        val compactBranch = shell.substringAfter("// Always preserve access to Today/Plan/Practice/Progress")
        assertTrue(compactBranch.indexOf("StudyFlowActiveSessionBar(") >= 0)
        assertTrue(compactBranch.indexOf("StudyFlowCompactDock(destinations = destinations)") >
            compactBranch.indexOf("StudyFlowActiveSessionBar("))
    }
}
