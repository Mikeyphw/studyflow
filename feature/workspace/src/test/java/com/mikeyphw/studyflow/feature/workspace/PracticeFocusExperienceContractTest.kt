package com.mikeyphw.studyflow.feature.workspace

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeFocusExperienceContractTest {
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
    fun `practice root is shell-native and keeps all three practice routes`() {
        val practice = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt",
        )
        assertFalse("Scaffold(" in practice)
        assertFalse("FloatingActionButton(" in practice)
        assertFalse("StudyFlowWorkspaceTabs(" in practice)
        assertTrue("StudyFlowTabStrip(" in practice)
        assertTrue("StudyFlowPracticeHero(" in practice)
        assertTrue("StudyFlowFloatingAction(" in practice)
        assertTrue("PracticeWorkspaceTab.REVIEWS" in practice)
        assertTrue("PracticeWorkspaceTab.QUESTIONS" in practice)
        assertTrue("PracticeWorkspaceTab.ERRORS" in practice)
        assertTrue("onOpenReviewCenter" in practice)
        assertTrue("onOpenQuestionBank" in practice)
        assertTrue("onQuickAdd" in practice)
    }

    @Test
    fun `focus route uses immersive StudyFlow execution chrome`() {
        val focus = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/FocusSessionScreen.kt",
        )
        assertFalse("Scaffold(" in focus)
        assertFalse("TopAppBar(" in focus)
        assertFalse("Card(" in focus)
        assertTrue("StudyFlowImmersiveTimer(" in focus)
        assertTrue("StudyFlowSessionControls(" in focus)
        assertTrue("sessionManager.pause(" in focus)
        assertTrue("sessionManager.resume(" in focus)
        assertTrue("sessionManager.complete(" in focus)
        assertTrue("sessionManager.cancel(" in focus)
        assertTrue("PredictiveBackHandler(" in focus)
    }

    @Test
    fun `active session dock is backed by repository session state`() {
        val navigation = source("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
        val shell = source("app/src/main/java/com/mikeyphw/studyflow/StudyFlowShell.kt")
        val primitives = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowPracticePrimitives.kt",
        )
        assertTrue("observeActiveSessions()" in navigation)
        assertTrue("manageStudySession.liveActiveSeconds" in navigation)
        assertTrue("manageStudySession.pause(" in navigation)
        assertTrue("manageStudySession.resume(" in navigation)
        assertTrue("activeSession = activeSession" in navigation)
        assertTrue("StudyFlowActiveSessionBar(" in shell)
        assertTrue("StudyFlowCompactDock(" in shell)
        assertTrue("fun StudyFlowActiveSessionBar(" in primitives)
    }

    @Test
    fun `quick add uses focused action rows rather than utility scaffold`() {
        val utility = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceUtilityScreens.kt",
        )
        val quickAdd = utility.substringAfter("fun QuickAddScreen(").substringBefore("private fun quickAddSourceLabel")
        assertFalse("WorkspaceUtilityScaffold(" in quickAdd)
        assertTrue("StudyFlowActionRow(" in quickAdd)
        assertTrue("QuickSessionDialog(" in quickAdd)
        assertTrue("onStudyBlock" in quickAdd)
        assertTrue("onQuestion" in quickAdd)
        assertTrue("onGoalOrNote" in quickAdd)
        assertTrue("onMaterial" in quickAdd)
    }
}
