package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.ExamNoticeRevisionEntity
import com.mikeyphw.studyflow.core.database.entity.LegislationProvisionEntity
import com.mikeyphw.studyflow.core.database.entity.LegislationEvidenceLinkEntity
import com.mikeyphw.studyflow.core.database.entity.LegislationDocumentEntity
import com.mikeyphw.studyflow.core.database.entity.StudyCycleEntity
import com.mikeyphw.studyflow.core.database.entity.StudyCycleItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PublicExamDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExamNoticeRevision(revision: ExamNoticeRevisionEntity)

    @Query(
        "SELECT * FROM exam_notice_revisions " +
            "WHERE studyPlanId = :planId ORDER BY importedAtEpochMillis DESC, id DESC",
    )
    fun observeExamNoticeRevisions(planId: String): Flow<List<ExamNoticeRevisionEntity>>

    @Query(
        "SELECT * FROM exam_notice_revisions " +
            "WHERE studyPlanId = :planId ORDER BY importedAtEpochMillis DESC, id DESC LIMIT 1",
    )
    suspend fun findLatestExamNoticeRevision(planId: String): ExamNoticeRevisionEntity?

    @Query(
        "SELECT * FROM exam_notice_revisions " +
            "WHERE studyPlanId = :planId AND normalizedContentSha256 = :sha256 " +
            "ORDER BY importedAtEpochMillis DESC LIMIT 1",
    )
    suspend fun findExamNoticeRevisionBySha256(planId: String, sha256: String): ExamNoticeRevisionEntity?

    @Query("SELECT * FROM exam_notice_revisions WHERE studyPlanId = :planId AND id = :revisionId LIMIT 1")
    suspend fun findExamNoticeRevision(planId: String, revisionId: String): ExamNoticeRevisionEntity?


    @Query("SELECT * FROM legislation_documents WHERE studyPlanId = :planId ORDER BY title, id")
    fun observeLegislationDocuments(planId: String): Flow<List<LegislationDocumentEntity>>

    @Query(
        "SELECT * FROM legislation_provisions WHERE studyPlanId = :planId " +
            "ORDER BY legislationDocumentId, position, id",
    )
    fun observeLegislationProvisions(planId: String): Flow<List<LegislationProvisionEntity>>

    @Query("SELECT * FROM legislation_evidence_links WHERE studyPlanId = :planId ORDER BY provisionId, id")
    fun observeLegislationEvidenceLinks(planId: String): Flow<List<LegislationEvidenceLinkEntity>>


    @Query("SELECT * FROM legislation_documents WHERE studyPlanId = :planId ORDER BY title, id")
    suspend fun findLegislationDocuments(planId: String): List<LegislationDocumentEntity>

    @Query(
        "SELECT * FROM legislation_provisions WHERE studyPlanId = :planId " +
            "ORDER BY legislationDocumentId, position, id",
    )
    suspend fun findLegislationProvisionsForPlan(planId: String): List<LegislationProvisionEntity>

    @Query("SELECT * FROM legislation_evidence_links WHERE studyPlanId = :planId ORDER BY provisionId, id")
    suspend fun findLegislationEvidenceLinks(planId: String): List<LegislationEvidenceLinkEntity>

    @Query("SELECT * FROM legislation_documents WHERE studyPlanId = :planId AND id = :documentId LIMIT 1")
    suspend fun findLegislationDocument(planId: String, documentId: String): LegislationDocumentEntity?

    @Query("SELECT * FROM legislation_provisions WHERE studyPlanId = :planId AND id = :provisionId LIMIT 1")
    suspend fun findLegislationProvision(planId: String, provisionId: String): LegislationProvisionEntity?

    @Query(
        "SELECT * FROM legislation_provisions WHERE studyPlanId = :planId " +
            "AND legislationDocumentId = :documentId ORDER BY position, id",
    )
    suspend fun findLegislationProvisions(planId: String, documentId: String): List<LegislationProvisionEntity>

    @Upsert
    suspend fun upsertLegislationDocument(document: LegislationDocumentEntity)

    @Upsert
    suspend fun upsertLegislationProvisions(provisions: List<LegislationProvisionEntity>)

    @Upsert
    suspend fun upsertLegislationEvidenceLink(link: LegislationEvidenceLinkEntity)

    @Query(
        "UPDATE legislation_provisions SET position = position + :offset " +
            "WHERE studyPlanId = :planId AND legislationDocumentId = :documentId",
    )
    suspend fun shiftLegislationProvisionPositions(planId: String, documentId: String, offset: Int): Int

    @Query(
        "DELETE FROM legislation_provisions " +
            "WHERE studyPlanId = :planId AND legislationDocumentId = :documentId AND id = :provisionId",
    )
    suspend fun deleteLegislationProvision(planId: String, documentId: String, provisionId: String): Int

    @Query(
        "UPDATE legislation_provisions SET read = :read, updatedAtEpochMillis = :updatedAt " +
            "WHERE studyPlanId = :planId AND id = :provisionId",
    )
    suspend fun setLegislationProvisionRead(planId: String, provisionId: String, read: Boolean, updatedAt: Long): Int

    @Query(
        "UPDATE legislation_provisions SET important = :important, updatedAtEpochMillis = :updatedAt " +
            "WHERE studyPlanId = :planId AND id = :provisionId",
    )
    suspend fun setLegislationProvisionImportant(
        planId: String,
        provisionId: String,
        important: Boolean,
        updatedAt: Long,
    ): Int

    @Upsert
    suspend fun upsertStudyCycle(cycle: StudyCycleEntity)

    @Upsert
    suspend fun upsertStudyCycleItems(items: List<StudyCycleItemEntity>)

    @Query("DELETE FROM study_cycle_items WHERE studyCycleId = :cycleId AND studyPlanId = :planId")
    suspend fun deleteStudyCycleItems(planId: String, cycleId: String): Int

    @Query("SELECT * FROM study_cycles WHERE studyPlanId = :planId LIMIT 1")
    fun observeStudyCycle(planId: String): Flow<StudyCycleEntity?>

    @Query("SELECT * FROM study_cycles WHERE studyPlanId = :planId LIMIT 1")
    suspend fun findStudyCycle(planId: String): StudyCycleEntity?

    @Query(
        "SELECT * FROM study_cycle_items " +
            "WHERE studyPlanId = :planId AND studyCycleId = :cycleId ORDER BY position, id",
    )
    fun observeStudyCycleItems(planId: String, cycleId: String): Flow<List<StudyCycleItemEntity>>

    @Query(
        "SELECT * FROM study_cycle_items " +
            "WHERE studyPlanId = :planId AND studyCycleId = :cycleId ORDER BY position, id",
    )
    suspend fun findStudyCycleItems(planId: String, cycleId: String): List<StudyCycleItemEntity>

    @Query(
        "SELECT item.* FROM study_cycle_items AS item " +
            "JOIN subjects AS subject ON subject.id = item.subjectId " +
            "LEFT JOIN topics AS topic ON topic.id = item.topicId " +
            "WHERE item.studyPlanId = :planId AND item.studyCycleId = :cycleId " +
            "AND item.enabled = 1 AND subject.studyPlanId = :planId AND subject.archived = 0 " +
            "AND (item.topicId IS NULL OR (topic.studyPlanId = :planId AND topic.archived = 0)) " +
            "ORDER BY item.position, item.id",
    )
    suspend fun findEligibleStudyCycleItems(planId: String, cycleId: String): List<StudyCycleItemEntity>

    @Query(
        "UPDATE study_cycles SET currentItemId = :itemId, updatedAtEpochMillis = :updatedAt " +
            "WHERE studyPlanId = :planId AND id = :cycleId",
    )
    suspend fun setCurrentStudyCycleItem(
        planId: String,
        cycleId: String,
        itemId: String?,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE study_cycles SET enabled = :enabled, updatedAtEpochMillis = :updatedAt " +
            "WHERE studyPlanId = :planId AND id = :cycleId",
    )
    suspend fun setStudyCycleEnabled(
        planId: String,
        cycleId: String,
        enabled: Boolean,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE study_cycle_items SET enabled = :enabled " +
            "WHERE studyPlanId = :planId AND studyCycleId = :cycleId AND id = :itemId",
    )
    suspend fun setStudyCycleItemEnabled(
        planId: String,
        cycleId: String,
        itemId: String,
        enabled: Boolean,
    ): Int

    @Query(
        "UPDATE study_cycle_items SET position = :position " +
            "WHERE studyPlanId = :planId AND studyCycleId = :cycleId AND id = :itemId",
    )
    suspend fun updateStudyCycleItemPosition(
        planId: String,
        cycleId: String,
        itemId: String,
        position: Int,
    ): Int

    @Query(
        "UPDATE subjects SET name = :name " +
            "WHERE studyPlanId = :planId AND id = :subjectId",
    )
    suspend fun renameSubject(planId: String, subjectId: String, name: String): Int

    @Query(
        "UPDATE topics SET name = :name " +
            "WHERE studyPlanId = :planId AND id = :topicId",
    )
    suspend fun renameTopic(planId: String, topicId: String, name: String): Int

    @Query(
        "UPDATE subjects SET archived = 1, status = 'ARCHIVED' " +
            "WHERE studyPlanId = :planId AND id = :subjectId",
    )
    suspend fun archiveSubject(planId: String, subjectId: String): Int

    @Query(
        "UPDATE topics SET archived = 1, status = 'ARCHIVED' " +
            "WHERE studyPlanId = :planId AND subjectId = :subjectId",
    )
    suspend fun archiveTopicsForSubject(planId: String, subjectId: String): Int

    @Query(
        "UPDATE topics SET archived = 1, status = 'ARCHIVED' " +
            "WHERE studyPlanId = :planId AND id = :topicId",
    )
    suspend fun archiveTopic(planId: String, topicId: String): Int
}
