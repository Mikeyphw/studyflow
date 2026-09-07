package com.mikeyphw.studyflow.feature.sessions

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov11WritingWorkspaceContractTest {
    @Test
    fun `writing workspace exposes editor rubric checklist history and protects completion`() {
        val workspace = repoFile(
            "feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/WritingWorkspaceSection.kt",
        )
        val screen = repoFile(
            "feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionScreen.kt",
        )
        val domain = repoFile(
            "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/WritingWorkspace.kt",
        )
        val manager = repoFile(
            "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySession.kt",
        )
        val en = repoFile("feature/sessions/src/main/res/values/strings.xml")
        val pt = repoFile("feature/sessions/src/main/res/values-pt-rBR/strings.xml")

        assertTrue("writing_editor" in workspace)
        assertTrue("WritingRubric" in workspace)
        assertTrue("WritingCompletionChecklist" in workspace)
        assertTrue("WritingWorkspaceAnalytics.history" in workspace)
        assertTrue("WritingWorkspaceAnalytics.weeklyProgress" in workspace)
        assertTrue("if (session.type != StudyActivityType.WRITING)" in screen)
        assertTrue("saveWritingDraft" in manager)
        assertTrue("completeWriting" in manager)
        assertTrue("require(checklist.complete)" in manager)
        assertTrue("studyflowWritingV1=" in domain)
        assertTrue("Writing workspace" in en)
        assertTrue("Workspace de Redação" in pt)
        assertTrue("Norma-padrão" in pt)
        assertFalse("androidx" in domain)
    }

    private fun repoFile(path: String): String {
        var current = File(requireNotNull(System.getProperty("user.dir")))
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Could not resolve repository file: $path")
    }
}
