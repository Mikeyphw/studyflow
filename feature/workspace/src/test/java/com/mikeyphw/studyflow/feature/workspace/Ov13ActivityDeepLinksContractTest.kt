package com.mikeyphw.studyflow.feature.workspace

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov13ActivityDeepLinksContractTest {
    private fun repoFile(path: String): String {
        var current = File(requireNotNull(System.getProperty("user.dir")))
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Repository file not found: $path")
    }

    @Test
    fun `today launches typed activities into canonical workspaces`() {
        val today = repoFile(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
        )
        val nav = repoFile("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
        assertTrue("StudyActivityLaunchPlanner.plan(activity)" in today)
        assertTrue("StudyActivityLaunchDestination.MOCK_EXAM_STUDIO" in today)
        assertTrue("onOpenMockExamStudio" in today)
        assertTrue("onOpenQuestions(intent.subjectId, intent.topicId)" in today)
        assertTrue("onOpenReviews(intent.subjectId, intent.topicId)" in today)
        assertTrue("entry = PracticeWorkspaceEntry.MOCK_EXAM_STUDIO" in nav)
        assertFalse("title.contains(\"mock\"" in today.lowercase())
    }

    @Test
    fun `practice route carries activity subject and topic filters`() {
        val routes = repoFile(
            "core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt",
        )
        val practice = repoFile(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt",
        )
        assertTrue("val subjectId: String? = null" in routes)
        assertTrue("val topicId: String? = null" in routes)
        assertTrue("val entry: PracticeWorkspaceEntry = PracticeWorkspaceEntry.DEFAULT" in routes)
        assertTrue("enum class PracticeWorkspaceEntry" in routes)
        assertTrue("StudyActivityLaunchPlanner.matchesQuestion" in practice)
        assertTrue("StudyActivityLaunchPlanner.matchesReview" in practice)
        assertTrue("rememberSaveable(openMockStudio)" in practice)
    }
}
