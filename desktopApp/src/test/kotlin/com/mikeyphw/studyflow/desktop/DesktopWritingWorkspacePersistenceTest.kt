package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.WritingCompletionChecklist
import com.mikeyphw.studyflow.core.domain.WritingRubric
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopWritingWorkspacePersistenceTest {
    @Test
    fun writingDraftRubricAndHistoryPersistAcrossRestart() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov16-")
            try {
                val seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create())
                val first = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val started = first.writingWorkspaceController.start("Explain why spaced review works.")
                first.writingWorkspaceController.saveDraft(
                    prompt = "Explain why spaced review works.",
                    draftText = "Spacing retrieval changes when recall happens.",
                )

                val reopened = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val restored = reopened.writingWorkspaceController.state()
                assertEquals(started.id, restored.activeSession?.id)
                assertEquals("Spacing retrieval changes when recall happens.", restored.record?.draftText)

                reopened.writingWorkspaceController.complete(
                    prompt = restored.record!!.prompt,
                    draftText = restored.record.draftText,
                    rubric = WritingRubric(16, 17, 18, 15, 19),
                    checklist = WritingCompletionChecklist(true, true, true, true),
                )

                val finalServices = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val finalState = finalServices.writingWorkspaceController.state()
                assertTrue(finalState.activeSession == null)
                assertTrue(finalState.history.isNotEmpty())
                assertNotNull(finalState.history.first().record)
                assertEquals(85, finalState.history.first().record?.scorePercent)
                assertTrue(Files.isRegularFile(root.resolve("workspace-v1.properties")))
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }
}
