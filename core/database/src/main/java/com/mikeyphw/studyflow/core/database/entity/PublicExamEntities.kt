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
