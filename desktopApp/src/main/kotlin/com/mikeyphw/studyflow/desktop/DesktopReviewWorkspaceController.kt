package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.ReviewDuePolicy
import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.FlashcardId
import com.mikeyphw.studyflow.core.model.FlashcardType
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import java.util.UUID

data class DesktopReviewWorkspaceState(
    val due: List<ReviewItem>,
    val upcoming: List<ReviewItem>,
    val completed: List<ReviewItem>,
    val flashcards: List<Flashcard>,
    val errors: List<ErrorEntry>,
)

class DesktopReviewWorkspaceController(
    private val services: DesktopStudyFlowServices,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun state(): DesktopReviewWorkspaceState {
        val planId = services.defaultPlanId ?: return DesktopReviewWorkspaceState(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        )
        val now = clock()
        val reviews = services.reviewRepository.currentReviews(planId)
        return DesktopReviewWorkspaceState(
            due = reviews.filter { ReviewDuePolicy.isDue(it, now) },
            upcoming = reviews.filter { ReviewDuePolicy.isActive(it) && !ReviewDuePolicy.isDue(it, now) },
            completed = reviews.filter { it.status == ReviewStatus.COMPLETED },
            flashcards = services.reviewRepository.currentFlashcards(planId).filterNot(Flashcard::suspended),
            errors = services.reviewRepository.currentErrors(planId),
        )
    }

    suspend fun createFlashcard(front: String, back: String): Flashcard {
        val planId = requireActivePlan()
        val structure = services.planContentController.state()
        val subject = structure.subjects.firstOrNull { !it.archived }
        val topic = structure.topics.firstOrNull { !it.archived && it.subjectId == subject?.id }
        return services.manageStudyReview.createFlashcard(
            id = FlashcardId(UUID.randomUUID().toString()),
            studyPlanId = planId,
            subjectId = subject?.id,
            topicId = topic?.id,
            type = FlashcardType.BASIC,
            front = front,
            back = back,
            clozeText = null,
            tags = listOf("desktop"),
        )
    }

    suspend fun gradeFirstDue(grade: Int): ReviewItem {
        val planId = requireActivePlan()
        val item = requireNotNull(state().due.firstOrNull()) { "No review is currently due." }
        return services.manageStudyReview.gradeReview(planId, item.id, grade)
    }

    suspend fun skipFirstDue(): ReviewItem {
        val planId = requireActivePlan()
        val item = requireNotNull(state().due.firstOrNull()) { "No review is currently due." }
        return services.manageStudyReview.skipReview(planId, item.id)
    }

    suspend fun suspendFirstFlashcard(): Flashcard {
        val planId = requireActivePlan()
        val card = requireNotNull(state().flashcards.firstOrNull()) { "No active Flashcard exists." }
        return services.manageStudyReview.suspendFlashcard(planId, card.id)
    }

    suspend fun reclassifyFirstError(category: ErrorCategory): ErrorEntry {
        val planId = requireActivePlan()
        val error = requireNotNull(state().errors.firstOrNull()) { "Error Notebook is empty." }
        return services.manageStudyReview.reclassifyErrorEntry(planId, error.id, category)
    }

    suspend fun resolveFirstError() {
        val planId = requireActivePlan()
        val error = requireNotNull(state().errors.firstOrNull()) { "Error Notebook is empty." }
        services.manageStudyReview.resolveErrorEntry(planId, error.id)
    }

    private fun requireActivePlan() = requireNotNull(services.currentDefaultPlanForDesktop()) {
        "No Study Plan is selected."
    }.also { plan ->
        require(plan.status == StudyPlanStatus.ACTIVE) { "Only active Study Plans can change reviews." }
    }.id
}
