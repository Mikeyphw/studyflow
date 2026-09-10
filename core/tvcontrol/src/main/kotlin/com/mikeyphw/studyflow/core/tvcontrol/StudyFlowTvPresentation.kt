package com.mikeyphw.studyflow.core.tvcontrol

import kotlinx.serialization.Serializable

/**
 * Typed, transport-neutral StudyFlow presentation snapshot introduced in TVOV21.
 *
 * The data owner projects canonical StudyFlow domain state into this schema. The TV consumes only
 * this presentation-safe representation; database rows, file paths, Room entities, and Android UI
 * objects never cross the TV boundary.
 */
object StudyFlowTvPresentationProtocol {
    const val version = 1
    const val maxQuestionChoices = 4
    const val maxTvQuestions = 20
    const val maxTvReviewItems = 20
    const val maxTvScheduleItems = 40
    const val maxTvLibraryReferences = 20
    const val maxTvGoals = 12
    const val maxInlineMaterialTextChars = 14_400
    const val rawDatabaseRowsForbidden = true
    const val arbitraryUiTreesForbidden = true
    const val transportStillDeferred = false
}

@Serializable
enum class StudyFlowTvPresentationAvailability {
    WAITING_FOR_PLAN,
    READY,
}

/** Minimal acknowledgement of the presentation currently retained by the TV receiver. */
@Serializable
data class StudyFlowTvPresentationReceipt(
    val snapshotId: String,
    val generatedAtEpochMillis: Long,
) {
    init {
        require(snapshotId.isNotBlank()) { "TV presentation receipt snapshot id must not be blank." }
        require(generatedAtEpochMillis >= 0L) { "TV presentation receipt time cannot be negative." }
    }
}

@Serializable
data class StudyFlowTvPresentationSnapshot(
    val snapshotId: String,
    val protocolVersion: Int = StudyFlowTvPresentationProtocol.version,
    val studyPlanId: String,
    val planTitle: String,
    val generatedAtEpochMillis: Long,
    val availability: StudyFlowTvPresentationAvailability,
    val today: StudyFlowTvTodayPresentation? = null,
    val studySession: StudyFlowTvStudySessionPresentation? = null,
    val questionSet: StudyFlowTvQuestionSetPresentation? = null,
    val mockExam: StudyFlowTvMockExamPresentation? = null,
    val review: StudyFlowTvReviewPresentation? = null,
    val progress: StudyFlowTvProgressPresentation? = null,
    val schedule: StudyFlowTvSchedulePresentation? = null,
    val library: StudyFlowTvLibraryPresentation? = null,
) {
    init {
        require(snapshotId.isNotBlank()) { "TV presentation snapshot id must not be blank." }
        require(protocolVersion == StudyFlowTvPresentationProtocol.version) {
            "Unsupported TV presentation protocol version."
        }
        require(studyPlanId.isNotBlank()) { "TV presentation plan id must not be blank." }
        require(planTitle.isNotBlank()) { "TV presentation plan title must not be blank." }
        require(generatedAtEpochMillis >= 0L) { "TV presentation generation time cannot be negative." }
    }

    val hasRenderableStudyData: Boolean
        get() = availability == StudyFlowTvPresentationAvailability.READY &&
            listOf(today, studySession, questionSet, mockExam, review, progress, schedule, library).any { it != null }

    companion object {
        fun waitingForPlan(studyPlanId: String, generatedAtEpochMillis: Long): StudyFlowTvPresentationSnapshot =
            StudyFlowTvPresentationSnapshot(
                snapshotId = "waiting-$studyPlanId-$generatedAtEpochMillis",
                studyPlanId = studyPlanId,
                planTitle = "StudyFlow",
                generatedAtEpochMillis = generatedAtEpochMillis,
                availability = StudyFlowTvPresentationAvailability.WAITING_FOR_PLAN,
            )
    }
}

@Serializable
data class StudyFlowTvTodayPresentation(
    val planTitle: String,
    val nextActivity: StudyFlowTvActivityPresentation?,
    val dueReviewCount: Int,
    val availableQuestionCount: Int,
    val weeklyProgressPercent: Int,
    val mockExamScheduled: Boolean,
) {
    init {
        require(planTitle.isNotBlank())
        require(dueReviewCount >= 0)
        require(availableQuestionCount >= 0)
        require(weeklyProgressPercent in 0..100)
    }
}

@Serializable
data class StudyFlowTvActivityPresentation(
    val activityId: String,
    val title: String,
    val subjectLabel: String,
    val activityType: String,
    val plannedDurationMinutes: Int,
    val scheduledStartEpochMillis: Long?,
    val status: String,
    val targetSurface: StudyFlowTvSurface,
) {
    init {
        require(activityId.isNotBlank())
        require(title.isNotBlank())
        require(subjectLabel.isNotBlank())
        require(activityType.isNotBlank())
        require(plannedDurationMinutes > 0)
        require(status.isNotBlank())
    }
}

@Serializable
data class StudyFlowTvStudySessionPresentation(
    val sessionId: String,
    val title: String,
    val subjectLabel: String,
    val status: String,
    val elapsedMinutes: Int,
    val targetMinutes: Int,
    val questionsAnswered: Int,
    val correctAnswers: Int,
) {
    init {
        require(sessionId.isNotBlank())
        require(title.isNotBlank())
        require(subjectLabel.isNotBlank())
        require(status.isNotBlank())
        require(elapsedMinutes >= 0)
        require(targetMinutes > 0)
        require(questionsAnswered >= 0)
        require(correctAnswers in 0..questionsAnswered)
    }
}

@Serializable
data class StudyFlowTvQuestionSetPresentation(
    val questionSetId: String,
    val title: String,
    val subjectLabel: String,
    val questions: List<StudyFlowTvQuestionPresentation>,
    val started: Boolean = false,
    val submitted: Boolean = false,
    val currentQuestionId: String? = null,
    val answers: List<StudyFlowTvQuestionAnswerPresentation> = emptyList(),
) {
    init {
        require(questionSetId.isNotBlank())
        require(title.isNotBlank())
        require(subjectLabel.isNotBlank())
        require(questions.size <= StudyFlowTvPresentationProtocol.maxTvQuestions)
        require(currentQuestionId == null || questions.any { it.questionId == currentQuestionId })
        require(answers.map { it.questionId }.distinct().size == answers.size)
        require(answers.all { answer -> questions.any { it.questionId == answer.questionId } })
    }
}

@Serializable
data class StudyFlowTvQuestionAnswerPresentation(
    val questionId: String,
    val chosenOptionId: String,
    val correct: Boolean,
    val answeredAtEpochMillis: Long,
) {
    init {
        require(questionId.isNotBlank())
        require(chosenOptionId.isNotBlank())
        require(answeredAtEpochMillis >= 0L)
    }
}

@Serializable
data class StudyFlowTvQuestionPresentation(
    val questionId: String,
    val prompt: String,
    val choices: List<StudyFlowTvQuestionChoicePresentation>,
    val correctOptionId: String,
    val explanation: String,
    val subjectLabel: String,
    val sourceLabel: String?,
) {
    init {
        require(questionId.isNotBlank())
        require(prompt.isNotBlank())
        require(choices.size in 2..StudyFlowTvPresentationProtocol.maxQuestionChoices)
        require(choices.map { it.optionId }.toSet().size == choices.size)
        require(choices.any { it.optionId == correctOptionId })
        require(subjectLabel.isNotBlank())
    }
}

@Serializable
data class StudyFlowTvQuestionChoicePresentation(
    val optionId: String,
    val label: String,
) {
    init {
        require(optionId.isNotBlank())
        require(label.isNotBlank())
    }
}

@Serializable
data class StudyFlowTvMockExamPresentation(
    val mockExamId: String,
    val title: String,
    val status: String,
    val totalMinutes: Int,
    val elapsedMinutes: Int,
    val questionCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val scorePercent: Int?,
) {
    init {
        require(mockExamId.isNotBlank())
        require(title.isNotBlank())
        require(status.isNotBlank())
        require(totalMinutes > 0)
        require(elapsedMinutes in 0..totalMinutes)
        require(questionCount > 0)
        require(answeredCount in 0..questionCount)
        require(correctCount in 0..answeredCount)
        require(scorePercent == null || scorePercent in 0..100)
    }
}

@Serializable
data class StudyFlowTvReviewPresentation(
    val queueId: String,
    val title: String,
    val items: List<StudyFlowTvReviewItemPresentation>,
) {
    init {
        require(queueId.isNotBlank())
        require(title.isNotBlank())
        require(items.size <= StudyFlowTvPresentationProtocol.maxTvReviewItems)
    }
}

@Serializable
data class StudyFlowTvReviewItemPresentation(
    val reviewItemId: String,
    val questionId: String? = null,
    val title: String,
    val subjectLabel: String,
    val prompt: String,
    val explanation: String,
    val reason: String,
    val confidencePercent: Int,
    val dueAtEpochMillis: Long,
    val status: String,
) {
    init {
        require(reviewItemId.isNotBlank())
        require(title.isNotBlank())
        require(subjectLabel.isNotBlank())
        require(prompt.isNotBlank())
        require(reason.isNotBlank())
        require(confidencePercent in 0..100)
        require(dueAtEpochMillis >= 0L)
        require(status.isNotBlank())
    }
}

@Serializable
data class StudyFlowTvProgressPresentation(
    val planTitle: String,
    val studiedMinutesLastSevenDays: Int,
    val weeklyGoalMinutes: Int,
    val weeklyProgressPercent: Int,
    val questionAccuracyPercent: Int,
    val answeredQuestions: Int,
    val dueReviewCount: Int,
    val contentProgressPercent: Int,
    val consistencyScorePercent: Int,
    val weakAreas: List<StudyFlowTvWeakAreaPresentation>,
    val goals: List<StudyFlowTvGoalPresentation> = emptyList(),
) {
    init {
        require(planTitle.isNotBlank())
        require(studiedMinutesLastSevenDays >= 0)
        require(weeklyGoalMinutes > 0)
        require(weeklyProgressPercent in 0..100)
        require(questionAccuracyPercent in 0..100)
        require(answeredQuestions >= 0)
        require(dueReviewCount >= 0)
        require(contentProgressPercent in 0..100)
        require(consistencyScorePercent in 0..100)
        require(goals.size <= StudyFlowTvPresentationProtocol.maxTvGoals)
    }
}

@Serializable
data class StudyFlowTvGoalPresentation(
    val goalId: String,
    val title: String,
    val goalType: String,
    val currentValue: Double,
    val targetValue: Double,
    val progressPercent: Int,
    val completed: Boolean,
    val paused: Boolean,
    val canComplete: Boolean,
) {
    init {
        require(goalId.isNotBlank())
        require(title.isNotBlank())
        require(goalType.isNotBlank())
        require(currentValue >= 0.0)
        require(targetValue > 0.0)
        require(progressPercent in 0..100)
        require(!completed || !paused)
    }
}

@Serializable
data class StudyFlowTvWeakAreaPresentation(
    val itemId: String,
    val label: String,
    val progressPercent: Int,
    val masteryPercent: Int,
    val priority: Int,
) {
    init {
        require(itemId.isNotBlank())
        require(label.isNotBlank())
        require(progressPercent in 0..100)
        require(masteryPercent in 0..100)
        require(priority >= 0)
    }
}

@Serializable
data class StudyFlowTvSchedulePresentation(
    val scheduleId: String,
    val title: String,
    val items: List<StudyFlowTvScheduleItemPresentation>,
) {
    init {
        require(scheduleId.isNotBlank())
        require(title.isNotBlank())
        require(items.size <= StudyFlowTvPresentationProtocol.maxTvScheduleItems)
    }
}

@Serializable
data class StudyFlowTvScheduleItemPresentation(
    val activityId: String,
    val title: String,
    val subjectLabel: String,
    val activityType: String,
    val status: String,
    val plannedDurationMinutes: Int,
    val scheduledStartEpochMillis: Long?,
    val targetSurface: StudyFlowTvSurface,
) {
    init {
        require(activityId.isNotBlank())
        require(title.isNotBlank())
        require(subjectLabel.isNotBlank())
        require(activityType.isNotBlank())
        require(status.isNotBlank())
        require(plannedDurationMinutes > 0)
    }
}

@Serializable
data class StudyFlowTvLibraryPresentation(
    val libraryId: String,
    val title: String,
    val references: List<StudyFlowTvLibraryReferencePresentation>,
) {
    init {
        require(libraryId.isNotBlank())
        require(title.isNotBlank())
        require(references.size <= StudyFlowTvPresentationProtocol.maxTvLibraryReferences)
    }
}

@Serializable
enum class StudyFlowTvMaterialViewerKind {
    TEXT,
    IMAGE,
    PDF,
    MEDIA,
    WEB,
    UNAVAILABLE,
}

@Serializable
data class StudyFlowTvMaterialViewerPresentation(
    val kind: StudyFlowTvMaterialViewerKind,
    val sourceUri: String? = null,
    val mimeType: String? = null,
    val inlineText: String? = null,
    val pageCountHint: Int? = null,
    val durationMillis: Long? = null,
    val playbackPositionMillis: Long = 0L,
) {
    init {
        require(sourceUri == null || sourceUri.isNotBlank())
        require(mimeType == null || mimeType.contains('/'))
        require(inlineText == null || inlineText.length <= StudyFlowTvPresentationProtocol.maxInlineMaterialTextChars)
        require(pageCountHint == null || pageCountHint > 0)
        require(durationMillis == null || durationMillis >= 0L)
        require(playbackPositionMillis >= 0L)
    }
}

@Serializable
data class StudyFlowTvLibraryReferencePresentation(
    val referenceId: String,
    val title: String,
    val subjectLabel: String,
    val sourceLabel: String,
    val excerpt: String,
    val referenceKind: String,
    val relatedSurface: StudyFlowTvSurface,
    val viewer: StudyFlowTvMaterialViewerPresentation? = null,
) {
    init {
        require(referenceId.isNotBlank())
        require(title.isNotBlank())
        require(subjectLabel.isNotBlank())
        require(sourceLabel.isNotBlank())
        require(excerpt.isNotBlank())
        require(referenceKind.isNotBlank())
    }
}
