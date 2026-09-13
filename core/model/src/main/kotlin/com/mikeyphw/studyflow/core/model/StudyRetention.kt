package com.mikeyphw.studyflow.core.model

enum class ReviewMethod {
    FIXED_INTERVAL,
    SPACED_REPETITION,
    PERFORMANCE_BASED,
    QUESTION_BASED,
    ERROR_BASED,
    MANUAL,
}

enum class ReviewStatus {
    SCHEDULED,
    DUE,
    COMPLETED,
    SKIPPED,
    CANCELLED,
}

enum class FlashcardType {
    BASIC,
    CLOZE,
}

enum class ErrorCategory {
    CONTENT_GAP,
    FORGOTTEN_CONTENT,
    CONCEPT_CONFUSION,
    INTERPRETATION,
    DISTRACTION,
    CALCULATION,
    TIME_PRESSURE,
    DISPUTED_QUESTION,
    OTHER,
}

enum class StudyNoteType {
    FREEFORM,
    SUMMARY,
    FORMULA,
    LEGISLATION,
    MIND_MAP,
    CHECKLIST,
}

object PlanSnapshotLabels {
    const val PRE_IMPORT = "snapshot.pre_import"
    const val PRE_RESTORE = "snapshot.pre_restore"
}

enum class PlanSnapshotKind {
    AUTOMATIC,
    MANUAL,
    PRE_IMPORT,
    PRE_RECALCULATION,
    PRE_DELETION,
}

data class ReviewItem(
    val id: ReviewItemId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val questionId: QuestionId?,
    val flashcardId: FlashcardId?,
    val errorEntryId: ErrorEntryId?,
    val method: ReviewMethod,
    val status: ReviewStatus,
    val dueAtEpochMillis: Long,
    val intervalDays: Int,
    val repetitionCount: Int,
    val easeFactor: Double,
    val completedAtEpochMillis: Long?,
) {
    init {
        require(intervalDays >= 0) { "Review interval cannot be negative." }
        require(repetitionCount >= 0) { "Review repetition count cannot be negative." }
        require(easeFactor > 0.0) { "Review ease factor must be positive." }
    }
}

data class Flashcard(
    val id: FlashcardId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val type: FlashcardType,
    val front: String,
    val back: String,
    val clozeText: String?,
    val tags: List<String>,
    val suspended: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(front.isNotBlank()) { "Flashcard front cannot be blank." }
        require(back.isNotBlank()) { "Flashcard back cannot be blank." }
    }
}

data class ErrorEntry(
    val id: ErrorEntryId,
    val studyPlanId: StudyPlanId,
    val questionId: QuestionId?,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val category: ErrorCategory,
    val chosenAnswer: String?,
    val correctAnswer: String?,
    val explanation: String,
    val recurrenceCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(recurrenceCount >= 0) { "Error recurrence count cannot be negative." }
    }
}

data class StudyNote(
    val id: StudyNoteId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val materialId: StudyMaterialId?,
    val type: StudyNoteType,
    val title: String,
    val content: String,
    val pinned: Boolean,
    val tags: List<String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(title.isNotBlank()) { "Study note title cannot be blank." }
    }
}

data class PlanSnapshot(
    val id: PlanSnapshotId,
    val studyPlanId: StudyPlanId,
    val kind: PlanSnapshotKind,
    val label: String,
    val schemaVersion: Int,
    val payloadReference: String,
    val checksumSha256: String,
    val createdAtEpochMillis: Long,
) {
    init {
        require(label.isNotBlank()) { "Snapshot label cannot be blank." }
        require(schemaVersion > 0) { "Snapshot schema version must be positive." }
        require(payloadReference.isNotBlank()) { "Snapshot payload reference cannot be blank." }
        require(checksumSha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Snapshot checksum must be SHA-256." }
    }
}

data class StudyPlanSettings(
    val studyPlanId: StudyPlanId,
    val adaptivePlanningEnabled: Boolean,
    val reviewNotificationsEnabled: Boolean,
    val strictExamModeEnabled: Boolean,
    val preferredSessionMinutes: Int,
    val quietHoursStartMinute: Int?,
    val quietHoursEndMinute: Int?,
    val configurationJson: String,
) {
    init {
        require(preferredSessionMinutes in StudyDomainInvariants.preferredSessionMinutes) {
            "Preferred session duration must be between ${StudyDomainInvariants.MIN_SESSION_MINUTES} and " +
                "${StudyDomainInvariants.MAX_PREFERRED_SESSION_MINUTES} minutes."
        }
        require(quietHoursStartMinute == null || quietHoursStartMinute in 0..1439) {
            "Quiet-hours start must be a minute within the day."
        }
        require(quietHoursEndMinute == null || quietHoursEndMinute in 0..1439) {
            "Quiet-hours end must be a minute within the day."
        }
        require((quietHoursStartMinute == null) == (quietHoursEndMinute == null)) {
            "Quiet-hours start and end must both be set or both be absent."
        }
    }

    companion object {
        fun defaults(studyPlanId: StudyPlanId): StudyPlanSettings = StudyPlanSettings(
            studyPlanId = studyPlanId,
            adaptivePlanningEnabled = true,
            reviewNotificationsEnabled = true,
            strictExamModeEnabled = false,
            preferredSessionMinutes = 45,
            quietHoursStartMinute = null,
            quietHoursEndMinute = null,
            configurationJson = "{}",
        )
    }
}
