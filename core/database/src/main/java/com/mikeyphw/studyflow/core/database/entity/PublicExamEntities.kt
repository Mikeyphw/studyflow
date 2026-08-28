package com.mikeyphw.studyflow.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exam_notice_revisions",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StudyTargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyTargetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("studyPlanId"),
        Index("studyTargetId"),
        Index(value = ["studyPlanId", "importedAtEpochMillis"]),
        Index(value = ["studyPlanId", "normalizedContentSha256"]),
    ],
)
data class ExamNoticeRevisionEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val studyTargetId: String,
    val label: String,
    val sourceReference: String?,
    val importedAtEpochMillis: Long,
    val normalizedContentSha256: String,
    val parserVersion: String,
    val publicationDateIso: String?,
    val addedCount: Int,
    val removedCount: Int,
    val changedCount: Int,
    val unchangedCount: Int,
)

@Entity(
    tableName = "study_cycles",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["studyPlanId"], unique = true)],
)
data class StudyCycleEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val name: String,
    val enabled: Boolean,
    val currentItemId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "study_cycle_items",
    foreignKeys = [
        ForeignKey(
            entity = StudyCycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyCycleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("studyCycleId"),
        Index("studyPlanId"),
        Index("subjectId"),
        Index("topicId"),
        Index(value = ["studyCycleId", "position"], unique = true),
    ],
)
data class StudyCycleItemEntity(
    @PrimaryKey val id: String,
    val studyCycleId: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val position: Int,
    val targetDurationMinutes: Int,
    val enabled: Boolean,
    val activityType: String,
    val completionRule: String?,
)


@Entity(
    tableName = "legislation_documents",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("studyPlanId"), Index(value = ["studyPlanId", "title"])],
)
data class LegislationDocumentEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val title: String,
    val sourceReference: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "legislation_provisions",
    foreignKeys = [
        ForeignKey(
            entity = LegislationDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["legislationDocumentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("legislationDocumentId"),
        Index("studyPlanId"),
        Index(value = ["legislationDocumentId", "position"], unique = true),
        Index(value = ["studyPlanId", "reference"]),
    ],
)
data class LegislationProvisionEntity(
    @PrimaryKey val id: String,
    val legislationDocumentId: String,
    val studyPlanId: String,
    val reference: String,
    val parentReference: String?,
    val kind: String,
    val text: String,
    val position: Int,
    val read: Boolean,
    val important: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "legislation_evidence_links",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LegislationProvisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["provisionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ErrorEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["errorEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReviewItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["reviewItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("studyPlanId"),
        Index("provisionId"),
        Index("questionId"),
        Index("errorEntryId"),
        Index("reviewItemId"),
    ],
)
data class LegislationEvidenceLinkEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val provisionId: String,
    val questionId: String?,
    val errorEntryId: String?,
    val reviewItemId: String?,
)
