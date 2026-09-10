package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.ReviewStatus
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopReviewWorkspacePersistenceTest {
    @Test
    fun reviewFlashcardAndWrongAnswerEvidencePersistAcrossRestart() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov14-")
            val sample = DesktopStudyFlowSampleData.create()
            val firstWorkspace = DesktopWorkspacePersistence.open(root, DesktopWorkspaceSnapshot.seed(sample))
            val first = createDesktopStudyFlowServices(workspacePersistence = firstWorkspace)
            first.reviewWorkspaceController.createFlashcard("Coroutine scope", "Structured concurrency boundary")
            first.reviewWorkspaceController.gradeFirstDue(4)

            val attempt = first.practiceExecutionController.createAttempt("Wrong-answer capture")
            first.practiceExecutionController.start(attempt.id)
            val current = requireNotNull(first.practiceExecutionController.state().execution?.currentQuestion)
            val wrongAnswer = if (current.correctAnswer == "definitely incorrect") {
                "__wrong__"
            } else {
                "definitely incorrect"
            }
            first.practiceExecutionController.answerCurrent(wrongAnswer, 8L)
            first.practiceExecutionController.finish()

            val secondWorkspace = DesktopWorkspacePersistence.open(root, DesktopWorkspaceSnapshot.seed(sample))
            val second = createDesktopStudyFlowServices(workspacePersistence = secondWorkspace)
            val planId = second.defaultPlanId!!
            val cards = second.reviewRepository.currentFlashcards(planId)
            val errors = second.reviewRepository.currentErrors(planId)
            val reviews = second.reviewRepository.currentReviews(planId)

            assertEquals(1, cards.size)
            assertTrue(errors.isNotEmpty())
            assertTrue(reviews.any { it.status == ReviewStatus.COMPLETED })
            assertTrue(reviews.any { it.status == ReviewStatus.SCHEDULED || it.status == ReviewStatus.DUE })
            assertTrue(Files.isRegularFile(root.resolve("reviews-v1.properties")))
        }
    }
}
