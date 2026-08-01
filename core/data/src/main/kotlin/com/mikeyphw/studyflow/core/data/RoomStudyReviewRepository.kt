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
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomStudyReviewRepository(
    private val database: StudyFlowDatabase,
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

    override suspend fun findErrorEntryForQuestion(
        planId: StudyPlanId,
        questionId: com.mikeyphw.studyflow.core.model.QuestionId,
    ): ErrorEntry? = retentionDao.findErrorEntryForQuestion(planId.value, questionId.value)?.asExternalModel()

    override suspend fun upsertReviewItem(item: ReviewItem): ReviewItem = database.withTransaction {
        requirePlan(item.studyPlanId)
        item.subjectId?.let { subjectId -> requireSubject(item.studyPlanId, subjectId.value) }
        item.topicId?.let { topicId -> requireTopic(item.studyPlanId, topicId.value) }
        retentionDao.upsertReviewItem(item.asEntity())
        requireNotNull(retentionDao.findReviewItem(item.studyPlanId.value, item.id.value)).asExternalModel()
    }

    override suspend fun upsertFlashcard(flashcard: Flashcard): Flashcard = database.withTransaction {
        requirePlan(flashcard.studyPlanId)
        flashcard.subjectId?.let { subjectId -> requireSubject(flashcard.studyPlanId, subjectId.value) }
        flashcard.topicId?.let { topicId -> requireTopic(flashcard.studyPlanId, topicId.value) }
        retentionDao.upsertFlashcard(flashcard.asEntity())
        requireNotNull(retentionDao.findFlashcard(flashcard.studyPlanId.value, flashcard.id.value)).asExternalModel()
    }

    override suspend fun upsertErrorEntry(entry: ErrorEntry): ErrorEntry = database.withTransaction {
        requirePlan(entry.studyPlanId)
        entry.subjectId?.let { subjectId -> requireSubject(entry.studyPlanId, subjectId.value) }
        entry.topicId?.let { topicId -> requireTopic(entry.studyPlanId, topicId.value) }
        retentionDao.upsertErrorEntry(entry.asEntity())
        requireNotNull(retentionDao.findErrorEntry(entry.studyPlanId.value, entry.id.value)).asExternalModel()
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
