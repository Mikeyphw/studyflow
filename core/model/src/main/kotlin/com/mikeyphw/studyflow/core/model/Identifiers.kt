package com.mikeyphw.studyflow.core.model

@JvmInline
value class StudyPlanId(val value: String) {
    init {
        require(value.isNotBlank()) { "StudyPlanId cannot be blank." }
    }
}

@JvmInline
value class StudyTargetId(val value: String) {
    init {
        require(value.isNotBlank()) { "StudyTargetId cannot be blank." }
    }
}

@JvmInline
value class ExamStageId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExamStageId cannot be blank." }
    }
}

@JvmInline
value class SubjectId(val value: String) {
    init {
        require(value.isNotBlank()) { "SubjectId cannot be blank." }
    }
}

@JvmInline
value class TopicId(val value: String) {
    init {
        require(value.isNotBlank()) { "TopicId cannot be blank." }
    }
}

@JvmInline
value class StudyStrategyId(val value: String) {
    init {
        require(value.isNotBlank()) { "StudyStrategyId cannot be blank." }
    }
}

@JvmInline
value class ScheduleRuleId(val value: String) {
    init {
        require(value.isNotBlank()) { "ScheduleRuleId cannot be blank." }
    }
}

@JvmInline
value class PlannedActivityId(val value: String) {
    init {
        require(value.isNotBlank()) { "PlannedActivityId cannot be blank." }
    }
}

@JvmInline
value class StudySessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "StudySessionId cannot be blank." }
    }
}

@JvmInline
value class StudyGoalId(val value: String) {
    init {
        require(value.isNotBlank()) { "StudyGoalId cannot be blank." }
    }
}

@JvmInline
value class StudyMaterialId(val value: String) {
    init {
        require(value.isNotBlank()) { "StudyMaterialId cannot be blank." }
    }
}

@JvmInline
value class VideoResourceId(val value: String) {
    init {
        require(value.isNotBlank()) { "VideoResourceId cannot be blank." }
    }
}

@JvmInline
value class AnnexId(val value: String) {
    init {
        require(value.isNotBlank()) { "AnnexId cannot be blank." }
    }
}

@JvmInline
value class QuestionId(val value: String) {
    init {
        require(value.isNotBlank()) { "QuestionId cannot be blank." }
    }
}

@JvmInline
value class StudyTestId(val value: String) {
    init {
        require(value.isNotBlank()) { "StudyTestId cannot be blank." }
    }
}

@JvmInline
value class MockExamId(val value: String) {
    init {
        require(value.isNotBlank()) { "MockExamId cannot be blank." }
    }
}

@JvmInline
value class ReviewItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "ReviewItemId cannot be blank." }
    }
}

@JvmInline
value class FlashcardId(val value: String) {
    init {
        require(value.isNotBlank()) { "FlashcardId cannot be blank." }
    }
}

@JvmInline
value class ErrorEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "ErrorEntryId cannot be blank." }
    }
}

@JvmInline
value class StudyNoteId(val value: String) {
    init {
        require(value.isNotBlank()) { "StudyNoteId cannot be blank." }
    }
}

@JvmInline
value class PlanSnapshotId(val value: String) {
    init {
        require(value.isNotBlank()) { "PlanSnapshotId cannot be blank." }
    }
}

@JvmInline
value class ProgrammingCourseProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProgrammingCourseProfileId cannot be blank." }
    }
}

@JvmInline
value class CodeSnippetId(val value: String) {
    init {
        require(value.isNotBlank()) { "CodeSnippetId cannot be blank." }
    }
}

@JvmInline
value class CodingExerciseId(val value: String) {
    init {
        require(value.isNotBlank()) { "CodingExerciseId cannot be blank." }
    }
}

@JvmInline
value class MiniProjectId(val value: String) {
    init {
        require(value.isNotBlank()) { "MiniProjectId cannot be blank." }
    }
}
@JvmInline
value class LanguageDeckId(val value: String) {
    init {
        require(value.isNotBlank()) { "LanguageDeckId cannot be blank." }
    }
}

@JvmInline
value class LanguageExpressionId(val value: String) {
    init {
        require(value.isNotBlank()) { "LanguageExpressionId cannot be blank." }
    }
}

@JvmInline
value class IdiomCardId(val value: String) {
    init {
        require(value.isNotBlank()) { "IdiomCardId cannot be blank." }
    }
}

@JvmInline
value class VocabularyCardId(val value: String) {
    init {
        require(value.isNotBlank()) { "VocabularyCardId cannot be blank." }
    }
}
