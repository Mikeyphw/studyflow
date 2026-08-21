package com.mikeyphw.studyflow.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "programming_course_profiles",
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
data class ProgrammingCourseProfileEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val primaryLanguage: String,
    val level: String,
    val repositoryUri: String?,
    val workspaceRelativePath: String,
    val buildSystem: String?,
    val packageManager: String?,
    val tagsJson: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "code_snippets",
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
data class CodeSnippetEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val title: String,
    val language: String,
    val code: String,
    val description: String,
    val sourceUri: String?,
    val tagsJson: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "coding_exercises",
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
            entity = CodeSnippetEntity::class,
            parentColumns = ["id"],
            childColumns = ["snippetId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("studyPlanId"), Index("subjectId"), Index("topicId"), Index("snippetId")],
)
data class CodingExerciseEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val snippetId: String?,
    val type: String,
    val title: String,
    val prompt: String,
    val starterCode: String,
    val expectedOutput: String?,
    val hintsJson: String,
    val solutionNotes: String,
    val language: String,
    val difficulty: Int,
    val status: String,
    val runCommandTemplate: String?,
    val testCommandTemplate: String?,
    val filesJson: String,
    val tagsJson: String,
    val lastResultJson: String?,
    val retryAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "mini_projects",
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
data class MiniProjectEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val subjectId: String?,
    val topicId: String?,
    val title: String,
    val description: String,
    val status: String,
    val language: String,
    val repositoryUri: String?,
    val workspaceRelativePath: String,
    val checkpointsJson: String,
    val deliverablesJson: String,
    val runCommandTemplate: String?,
    val testCommandTemplate: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "termux_runtime_preferences",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TermuxRuntimePreferenceEntity(
    @PrimaryKey val studyPlanId: String,
    val enabled: Boolean,
    val workspaceRoot: String,
    val preferredLanguage: String?,
    val defaultRunCommand: String?,
    val defaultTestCommand: String?,
    val allowedCommandTemplatesJson: String,
    val captureStdout: Boolean,
    val captureStderr: Boolean,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "termux_command_requests",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["studyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CodingExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("studyPlanId"), Index("exerciseId"), Index("state")],
)
data class TermuxCommandRequestEntity(
    @PrimaryKey val id: String,
    val studyPlanId: String,
    val exerciseId: String?,
    val workspaceRoot: String,
    val courseSlug: String,
    val workspaceRelativePath: String,
    val action: String,
    val commandTemplateId: String?,
    val commandTemplateLabel: String?,
    val commandTemplateLanguage: String?,
    val commandLine: String?,
    val workingDirectory: String?,
    val state: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "termux_command_results",
    foreignKeys = [
        ForeignKey(
            entity = TermuxCommandRequestEntity::class,
            parentColumns = ["id"],
            childColumns = ["requestId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("requestId", unique = true), Index("state")],
)
data class TermuxCommandResultEntity(
    @PrimaryKey val id: String,
    val requestId: String,
    val state: String,
    val exitCode: Int?,
    val stdoutText: String,
    val stderrText: String,
    val resultJson: String?,
    val durationMillis: Long?,
    val completedAtEpochMillis: Long,
)
