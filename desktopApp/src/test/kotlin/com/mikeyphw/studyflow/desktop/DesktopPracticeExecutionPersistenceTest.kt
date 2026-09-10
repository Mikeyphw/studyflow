package com.mikeyphw.studyflow.desktop

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPracticeExecutionPersistenceTest {
    @Test
    fun practiceAttemptAnswersAndQuestionFlagsPersistAcrossRestart() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov12-")
            val sample = DesktopStudyFlowSampleData.create()
            val firstWorkspace = DesktopWorkspacePersistence.open(root, DesktopWorkspaceSnapshot.seed(sample))
            val first = createDesktopStudyFlowServices(workspacePersistence = firstWorkspace)
            val controller = first.practiceExecutionController
            val question = first.practiceRepository.currentQuestions(first.defaultPlanId!!).first()

            controller.toggleFavorite(question.id)
            val created = controller.createAttempt("Persistent desktop practice")
            controller.start(created.id)
            val current = requireNotNull(controller.state().execution?.currentQuestion)
            controller.answerCurrent(current.correctAnswer, elapsedSeconds = 12L)

            val secondWorkspace = DesktopWorkspacePersistence.open(root, DesktopWorkspaceSnapshot.seed(sample))
            val second = createDesktopStudyFlowServices(workspacePersistence = secondWorkspace)
            val restoredAttempt = second.practiceRepository.currentTests(second.defaultPlanId!!)
                .first { it.id == created.id }
            val restoredQuestion = second.practiceRepository.currentQuestions(second.defaultPlanId!!)
                .first { it.id == question.id }

            assertEquals(1, restoredAttempt.answeredCount)
            assertTrue(restoredAttempt.configurationJson.contains("answer="))
            assertEquals(!question.favorite, restoredQuestion.favorite)
            assertNotNull(second.practiceExecutionController.state().execution)
            assertTrue(Files.isRegularFile(root.resolve("practice-v1.properties")))
        }
    }
}
