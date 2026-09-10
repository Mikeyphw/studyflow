package com.mikeyphw.studyflow.desktop

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMockExamStudioPersistenceTest {
    @Test
    fun frozenMockExamAndAnswersPersistAcrossRestart() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov13-")
            val sample = DesktopStudyFlowSampleData.create()
            val firstWorkspace = DesktopWorkspacePersistence.open(root, DesktopWorkspaceSnapshot.seed(sample))
            val first = createDesktopStudyFlowServices(workspacePersistence = firstWorkspace)
            val created = first.mockExamStudioController.create()
            first.mockExamStudioController.start(created.id)
            val current = requireNotNull(first.mockExamStudioController.state().execution?.currentQuestion)
            first.mockExamStudioController.answerCurrent(current.correctAnswer, elapsedSeconds = 21L)

            val secondWorkspace = DesktopWorkspacePersistence.open(root, DesktopWorkspaceSnapshot.seed(sample))
            val second = createDesktopStudyFlowServices(workspacePersistence = secondWorkspace)
            val restored = second.practiceRepository.currentMockExams(second.defaultPlanId!!)
                .first { it.id == created.id }
            val execution = second.mockExamStudioController.state().execution

            assertNotNull(restored.startedAtEpochMillis)
            assertEquals(1, restored.answeredCount)
            assertTrue(restored.configurationJson.contains("questionIds="))
            assertTrue(restored.configurationJson.contains("answer="))
            assertEquals(created.questionCount, execution?.questionIds?.size)
        }
    }
}
