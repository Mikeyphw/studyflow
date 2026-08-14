package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudyReviewRepository
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.FlashcardId
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomStudyReviewRepository(
    private val database: StudyFlowDatabase,
    private val diagnosticRecorder: com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder = com
        .mikeyphw.studyflow.core.diagnostics.NoOpDiagnosticRecorder,
) : StudyReviewRepository {
    private val retentionDao = database.studyRetentionDao()
    private val planDao = database.studyPlanDao()

    override fun observeReviewItems(planId: StudyPlanId): Flow<List<ReviewItem>> =
        retentionDao.observeReviewItems(planId.value).map { items -> items.map { it.asExternalModel() } }

    override fun observeFlashcards(planId: StudyPlanId): Flow<List<Flashcard>> =
        retentionDao.observeFlashcards(planId.value).map { cards -> cards.map { it.asExternalModel() } }

    override fun observeErrorEntries(planId: StudyPlanId): Flow<List<ErrorEntry>> =
        retentionDao.observeErrorEntries(planId.value).map { entries -> entries.map { it.asExternalModel() } }

    override suspend fun findReviewItem(planId: StudyPlanId, reviewItemId: ReviewItemId): ReviewItem? =
        retentionDao.findReviewItem(planId.value, reviewItemId.value)?.asExternalModel()

    override suspend fun findFlashcard(planId: StudyPlanId, flashcardId: FlashcardId): Flashcard? =
        retentionDao.findFlashcard(planId.value, flashcardId.value)?.asExternalModel()

    override suspend fun findErrorEntry(planId: StudyPlanId, errorEntryId: ErrorEntryId): ErrorEntry? =
        retentionDao.findErrorEntry(planId.value, errorEntryId.value)?.asExternalModel()

    override suspend fun findErrorEntryForQuestion(planId: StudyPlanId, questionId: QuestionId): ErrorEntry? =
        retentionDao.findErrorEntryForQuestion(planId.value, questionId.value)?.asExternalModel()

    override suspend fun upsertReviewItem(item: ReviewItem): ReviewItem = database.withTransaction {
        requireReviewOwnership(item)
        retentionDao.upsertReviewItem(item.asEntity())
        requireNotNull(retentionDao.findReviewItem(item.studyPlanId.value, item.id.value)).asExternalModel()
    }

    override suspend fun upsertFlashcard(flashcard: Flashcard): Flashcard = database.withTransaction {
        requirePlan(flashcard.studyPlanId)
        flashcard.subjectId?.let { requireSubject(flashcard.studyPlanId, it.value) }
        flashcard.topicId?.let { requireTopic(flashcard.studyPlanId, it.value) }
        retentionDao.upsertFlashcard(flashcard.asEntity())
        requireNotNull(retentionDao.findFlashcard(flashcard.studyPlanId.value, flashcard.id.value)).asExternalModel()
    }

    override suspend fun upsertErrorEntry(entry: ErrorEntry): ErrorEntry = database.withTransaction {
        requireErrorOwnership(entry)
        retentionDao.upsertErrorEntry(entry.asEntity())
        requireNotNull(retentionDao.findErrorEntry(entry.studyPlanId.value, entry.id.value)).asExternalModel()
    }

    override suspend fun suspendFlashcardAndCancelReviews(
        flashcard: Flashcard,
        completedAtEpochMillis: Long,
    ): Flashcard = database.withTransaction {
        requirePlan(flashcard.studyPlanId)
        retentionDao.upsertFlashcard(flashcard.asEntity())
        retentionDao.cancelActiveReviewsForFlashcard(
            flashcard.studyPlanId.value,
            flashcard.id.value,
            completedAtEpochMillis,
        )
        requireNotNull(retentionDao.findFlashcard(flashcard.studyPlanId.value, flashcard.id.value)).asExternalModel()
    }

    override suspend fun completeReviewAndScheduleSuccessor(
        current: ReviewItem,
        completed: ReviewItem,
        successor: ReviewItem,
    ): ReviewItem = diagnosticRecorder.recordRepositoryMutation(
        area = com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea.REVIEWS,
        action = "review-grade",
    ) {
        database.withTransaction {
        require(current.studyPlanId == completed.studyPlanId && current.id == completed.id)
        require(successor.studyPlanId == current.studyPlanId)
        require(completed.status == ReviewStatus.COMPLETED)
        require(successor.status == ReviewStatus.SCHEDULED || successor.status == ReviewStatus.DUE)
        requireReviewOwnership(successor)
        val changed = retentionDao.compareAndSetReviewState(
            planId = current.studyPlanId.value,
            reviewItemId = current.id.value,
            expectedStatus = current.status.name,
            newStatus = completed.status.name,
            newDueAtEpochMillis = completed.dueAtEpochMillis,
            newIntervalDays = completed.intervalDays,
            newRepetitionCount = completed.repetitionCount,
            newEaseFactor = completed.easeFactor,
            completedAtEpochMillis = completed.completedAtEpochMillis,
        )
        require(changed == 1) { "Review changed or was already terminal; refusing stale grade." }
        retentionDao.upsertReviewItem(successor.asEntity())
        requireNotNull(retentionDao.findReviewItem(successor.studyPlanId.value, successor.id.value)).asExternalModel()
    }
    }

    override suspend fun skipReviewIfActive(current: ReviewItem,
        skipped: ReviewItem): ReviewItem = diagnosticRecorder.recordRepositoryMutation(
        area = com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea.REVIEWS,
        action = "review-skip",
    ) {
        database.withTransaction {
            require(current.studyPlanId == skipped.studyPlanId && current.id == skipped.id)
            require(skipped.status == ReviewStatus.SKIPPED)
            val changed = retentionDao.compareAndSetReviewState(
                planId = current.studyPlanId.value,
                reviewItemId = current.id.value,
                expectedStatus = current.status.name,
                newStatus = skipped.status.name,
                newDueAtEpochMillis = skipped.dueAtEpochMillis,
                newIntervalDays = skipped.intervalDays,
                newRepetitionCount = skipped.repetitionCount,
                newEaseFactor = skipped.easeFactor,
                completedAtEpochMillis = skipped.completedAtEpochMillis,
            )
            require(changed == 1) { "Review changed or was already terminal; refusing stale skip." }
            requireNotNull(retentionDao.findReviewItem(current.studyPlanId.value, current.id.value)).asExternalModel()
        }
    }

    override suspend fun recordWrongAnswerAndEnsureReview(
        entry: ErrorEntry,
        dueAtEpochMillis: Long,
    ): ErrorEntry = database.withTransaction {
        requireErrorOwnership(entry)
        val existing = entry.questionId?.let {
            retentionDao.findErrorEntryForQuestion(entry.studyPlanId.value, it.value)?.asExternalModel()
        }
        val merged = existing?.copy(
            subjectId = entry.subjectId,
            topicId = entry.topicId,
            category = entry.category,
            chosenAnswer = entry.chosenAnswer,
            correctAnswer = entry.correctAnswer,
            explanation = entry.explanation,
            recurrenceCount = existing.recurrenceCount + 1,
            updatedAtEpochMillis = entry.updatedAtEpochMillis,
        ) ?: entry
        retentionDao.upsertErrorEntry(merged.asEntity())
        ensurePendingErrorReview(merged, dueAtEpochMillis)
        requireNotNull(retentionDao.findErrorEntry(merged.studyPlanId.value, merged.id.value)).asExternalModel()
    }

    override suspend fun scheduleErrorReviewIfAbsent(
        entry: ErrorEntry,
        dueAtEpochMillis: Long,
    ): ReviewItem = database.withTransaction {
        requireErrorOwnership(entry)
        ensurePendingErrorReview(entry, dueAtEpochMillis)
    }

    override suspend fun deleteReviewItem(planId: StudyPlanId, reviewItemId: ReviewItemId) {
        database.withTransaction {
            require(retentionDao.deleteReviewItem(planId.value, reviewItemId.value) == 1) {
                "Review item does not belong to this Study Plan."
            }
        }
    }

    override suspend fun deleteFlashcard(planId: StudyPlanId, flashcardId: FlashcardId) {
        database.withTransaction {
            require(retentionDao.deleteFlashcard(planId.value, flashcardId.value) == 1) {
                "Flashcard does not belong to this Study Plan."
            }
        }
    }

    override suspend fun deleteErrorEntry(planId: StudyPlanId, errorEntryId: ErrorEntryId) {
        database.withTransaction {
            retentionDao.deleteReviewsForErrorEntry(planId.value, errorEntryId.value)
            require(retentionDao.deleteErrorEntry(planId.value, errorEntryId.value) == 1) {
                "Error entry does not belong to this Study Plan."
            }
        }
    }

    private suspend fun ensurePendingErrorReview(entry: ErrorEntry, dueAtEpochMillis: Long): ReviewItem {
        retentionDao.findPendingReviewForError(entry.studyPlanId.value, entry.id.value)?.let {
            return it.asExternalModel()
        }
        val review = ReviewItem(
            id = ReviewItemId(UUID.randomUUID().toString()),
            studyPlanId = entry.studyPlanId,
            subjectId = entry.subjectId,
            topicId = entry.topicId,
            questionId = entry.questionId,
            flashcardId = null,
            errorEntryId = entry.id,
            method = ReviewMethod.ERROR_BASED,
            status = ReviewStatus.SCHEDULED,
            dueAtEpochMillis = dueAtEpochMillis,
            intervalDays = 0,
            repetitionCount = 0,
            easeFactor = 2.5,
            completedAtEpochMillis = null,
        )
        retentionDao.upsertReviewItem(review.asEntity())
        return review
    }

    private suspend fun requireReviewOwnership(item: ReviewItem) {
        requirePlan(item.studyPlanId)
        item.subjectId?.let { requireSubject(item.studyPlanId, it.value) }
        item.topicId?.let { requireTopic(item.studyPlanId, it.value) }
        item.questionId?.let { id ->
            requireNotNull(database.studyPracticeDao().findQuestion(item.studyPlanId.value, id.value)) {
                "Review question does not belong to this Study Plan."
            }
        }
    }

    private suspend fun requireErrorOwnership(entry: ErrorEntry) {
        requirePlan(entry.studyPlanId)
        entry.subjectId?.let { requireSubject(entry.studyPlanId, it.value) }
        entry.topicId?.let { requireTopic(entry.studyPlanId, it.value) }
        entry.questionId?.let { id ->
            requireNotNull(database.studyPracticeDao().findQuestion(entry.studyPlanId.value, id.value)) {
                "Error question does not belong to this Study Plan."
            }
        }
    }

    private suspend fun requirePlan(planId: StudyPlanId) {
        requireNotNull(planDao.findById(planId.value)) { "Study Plan does not exist." }
    }

    private suspend fun requireSubject(planId: StudyPlanId, subjectId: String) {
        requireNotNull(database.studyStructureDao().findSubject(planId.value, subjectId)) {
            "The selected subject does not belong to this Study Plan."
        }
    }

    private suspend fun requireTopic(planId: StudyPlanId, topicId: String) {
        requireNotNull(database.studyStructureDao().findTopic(planId.value, topicId)) {
            "The selected topic does not belong to this Study Plan."
        }
    }
}
