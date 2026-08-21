package com.mikeyphw.studyflow.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "study_plans")
data class StudyPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val type: String,
    val status: String,
    val examDateIso: String?,
    val weeklyGoalHours: Int,
    val progressPercent: Int,
    val studiedMinutesThisWeek: Int,
    val questionsAnswered: Int,
    val averageAccuracyPercent: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val trashedAtEpochMillis: Long?,
    val contentMode: String = "EXAM_PLAN",
)

@Entity(
    tableName = "study_targets",
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
data class StudyTargetEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val organization: String,
    val role: String,
    val examBoard: String,
    val noticeUrl: String?,
    val confirmedExamDateIso: String?,
    val expectedExamDateIso: String?,
    val openings: Int?,
    val estimatedCutoffPercent: Int?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "exam_stages",
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
    indices = [Index("studyPlanId"), Index("studyTargetId")],
)
data class ExamStageEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val studyTargetId: String,
    val name: String,
    val type: String,
    val scheduledDateIso: String?,
    val weight: Double,
    val minimumScorePercent: Int?,
    val position: Int,
)

@Entity(
    tableName = "subjects",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("studyPlanId"),
        Index("examStageId"),
        Index(value = ["studyPlanId", "position"]),
    ],
)
data class SubjectEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val name: String,
    val description: String,
    val colorArgb: Long?,
    val priority: Int,
    val examWeight: Double,
    val estimatedMinutes: Int,
    val progressPercent: Int,
    val accuracyPercent: Int,
    val status: String,
    val position: Int,
    val archived: Boolean,
    val examStageId: String? = null,
)

@Entity(
    tableName = "topics",
    foreignKeys = [
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
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTopicId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("studyPlanId"),
        Index("subjectId"),
        Index("parentTopicId"),
        Index("examStageId"),
    ],
)
data class TopicEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String,
    val parentTopicId: String?,
    val name: String,
    val description: String,
    val priority: Int,
    val difficulty: Int,
    val examIncidencePercent: Int?,
    val estimatedMinutes: Int,
    val progressPercent: Int,
    val masteryPercent: Int,
    val status: String,
    val position: Int,
    val archived: Boolean,
    val examStageId: String? = null,
)

@Entity(
    tableName = "study_strategies",
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
data class StudyStrategyEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val type: String,
    val minimumSessionMinutes: Int,
    val maximumSessionMinutes: Int,
    val theoryRatioPercent: Int,
    val questionRatioPercent: Int,
    val reviewRatioPercent: Int,
    val adaptivePlanningEnabled: Boolean,
    val configurationJson: String,
)

@Entity(
    tableName = "schedule_rules",
    foreignKeys = [
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
    ],
    indices = [Index("studyPlanId"), Index("subjectId")],
)
data class ScheduleRuleEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val type: String,
    val dayOfWeek: Int?,
    val startMinuteOfDay: Int?,
    val durationMinutes: Int,
    val recurrenceRule: String?,
    val locked: Boolean,
    val enabled: Boolean,
    val position: Int,
)

@Entity(
    tableName = "study_materials",
    foreignKeys = [
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
    indices = [Index("studyPlanId"), Index("subjectId"), Index("topicId")],
)
data class StudyMaterialEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val type: String,
    val title: String,
    val description: String,
    val sourceUri: String?,
    val author: String?,
    val totalUnits: Int?,
    val completedUnits: Int,
    val primary: Boolean,
    val favorite: Boolean,
    val archived: Boolean,
    val tagsJson: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "video_resources",
    foreignKeys = [
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
    indices = [Index("studyPlanId"), Index("subjectId"), Index("topicId")],
)
data class VideoResourceEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val title: String,
    val sourceType: String,
    val sourceUri: String,
    val canonicalSourceId: String?,
    val thumbnailUri: String?,
    val durationMillis: Long?,
    val playbackPositionMillis: Long,
    val completed: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "planned_activities",
    foreignKeys = [
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
        ForeignKey(
            entity = StudyMaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = VideoResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoResourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("studyPlanId"),
        Index("subjectId"),
        Index("topicId"),
        Index("materialId"),
        Index("videoResourceId"),
    ],
)
data class PlannedActivityEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val materialId: String?,
    val videoResourceId: String?,
    val type: String,
    val title: String,
    val scheduledStartEpochMillis: Long?,
    val plannedDurationMinutes: Int,
    val status: String,
    val locked: Boolean,
    val completedAtEpochMillis: Long?,
)

@Entity(
    tableName = "study_sessions",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlannedActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannedActivityId"],
            onDelete = ForeignKey.SET_NULL,
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
        ForeignKey(
            entity = StudyMaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = VideoResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoResourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("studyPlanId"),
        Index("plannedActivityId"),
        Index("subjectId"),
        Index("topicId"),
        Index("materialId"),
        Index("videoResourceId"),
    ],
)
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val plannedActivityId: String?,
    val subjectId: String?,
    val topicId: String?,
    val materialId: String?,
    val videoResourceId: String?,
    val type: String,
    val status: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val grossDurationSeconds: Long,
    val pauseDurationSeconds: Long,
    val pagesCompleted: Int,
    val questionsAnswered: Int,
    val correctAnswers: Int,
    val concentrationRating: Int?,
    val difficultyRating: Int?,
    val notes: String,
    @ColumnInfo(defaultValue = "NULL")
    val targetDurationMinutes: Int? = null,
)

@Entity(
    tableName = "study_goals",
    foreignKeys = [
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
    indices = [Index("studyPlanId"), Index("subjectId"), Index("topicId")],
)
data class StudyGoalEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val type: String,
    val period: String,
    val title: String,
    val targetValue: Double,
    val currentValue: Double,
    val startsOnIso: String,
    val endsOnIso: String?,
    val completedAtEpochMillis: Long?,
    val archived: Boolean,
)

@Entity(
    tableName = "annexes",
    foreignKeys = [
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
        ForeignKey(
            entity = StudyMaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("studyPlanId"), Index("subjectId"), Index("topicId"), Index("materialId")],
)
data class AnnexEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val materialId: String?,
    val displayName: String,
    val mimeType: String,
    val byteSize: Long,
    val checksumSha256: String?,
    val storageMode: String,
    val storageReference: String,
    val persistedPermission: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "questions",
    foreignKeys = [
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
    indices = [Index("studyPlanId"), Index("subjectId"), Index("topicId")],
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val type: String,
    val statement: String,
    val optionsJson: String,
    val correctAnswer: String,
    val explanation: String,
    val examBoard: String?,
    val source: String?,
    val year: Int?,
    val difficulty: Int,
    val favorite: Boolean,
    val disputed: Boolean,
    val tagsJson: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "study_tests",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("studyPlanId")],
)
data class StudyTestEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val title: String,
    val mode: String,
    val timeLimitSeconds: Long?,
    val questionCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val startedAtEpochMillis: Long?,
    val submittedAtEpochMillis: Long?,
    val configurationJson: String,
)

@Entity(
    tableName = "mock_exams",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("studyPlanId")],
)
data class MockExamEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val title: String,
    val mode: String,
    val timeLimitSeconds: Long,
    val questionCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val scorePercent: Double?,
    val negativeMarking: Double,
    val minimumScorePercent: Int?,
    val startedAtEpochMillis: Long?,
    val submittedAtEpochMillis: Long?,
    val configurationJson: String,
)

@Entity(
    tableName = "flashcards",
    foreignKeys = [
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
    indices = [Index("studyPlanId"), Index("subjectId"), Index("topicId")],
)
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val type: String,
    val front: String,
    val back: String,
    val clozeText: String?,
    val tagsJson: String,
    val suspended: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "error_entries",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.SET_NULL,
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
    indices = [Index("studyPlanId"), Index("questionId"), Index("subjectId"), Index("topicId")],
)
data class ErrorEntryEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val questionId: String?,
    val subjectId: String?,
    val topicId: String?,
    val category: String,
    val chosenAnswer: String?,
    val correctAnswer: String?,
    val explanation: String,
    val recurrenceCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "review_items",
    foreignKeys = [
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
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = FlashcardEntity::class,
            parentColumns = ["id"],
            childColumns = ["flashcardId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ErrorEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["errorEntryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("studyPlanId"),
        Index("subjectId"),
        Index("topicId"),
        Index("questionId"),
        Index("flashcardId"),
        Index("errorEntryId"),
    ],
)
data class ReviewItemEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val questionId: String?,
    val flashcardId: String?,
    val errorEntryId: String?,
    val method: String,
    val status: String,
    val dueAtEpochMillis: Long,
    val intervalDays: Int,
    val repetitionCount: Int,
    val easeFactor: Double,
    val completedAtEpochMillis: Long?,
)

@Entity(
    tableName = "study_notes",
    foreignKeys = [
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
        ForeignKey(
            entity = StudyMaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("studyPlanId"), Index("subjectId"), Index("topicId"), Index("materialId")],
)
data class StudyNoteEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val materialId: String?,
    val type: String,
    val title: String,
    val content: String,
    val pinned: Boolean,
    val tagsJson: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "plan_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("studyPlanId")],
)
data class PlanSnapshotEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val kind: String,
    val label: String,
    val schemaVersion: Int,
    val payloadReference: String,
    val checksumSha256: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "study_plan_settings",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StudyPlanSettingsEntity(
    @PrimaryKey val studyPlanId: String,
    val adaptivePlanningEnabled: Boolean,
    val reviewNotificationsEnabled: Boolean,
    val strictExamModeEnabled: Boolean,
    val preferredSessionMinutes: Int,
    val quietHoursStartMinute: Int?,
    val quietHoursEndMinute: Int?,
    val configurationJson: String,
)

@Entity(tableName = "database_metadata")
data class DatabaseMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)
