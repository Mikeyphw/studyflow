package com.mikeyphw.studyflow.feature.practice

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionType

internal enum class QuestionEditorValidationIssue {
    BLANK_STATEMENT,
    BLANK_ANSWER,
    INVALID_DIFFICULTY,
    MULTIPLE_CHOICE_NEEDS_TWO_OPTIONS,
    MULTIPLE_CHOICE_ANSWER_NOT_IN_OPTIONS,
    TRUE_FALSE_ANSWER_NOT_BOOLEAN,
}

internal data class QuestionEditorValidation(
    val issues: Set<QuestionEditorValidationIssue>,
) {
    val canSave: Boolean = issues.isEmpty()
}

internal fun QuestionEditorState.validation(): QuestionEditorValidation {
    val issues = linkedSetOf<QuestionEditorValidationIssue>()
    if (statement.isBlank()) issues += QuestionEditorValidationIssue.BLANK_STATEMENT
    if (correctAnswer.isBlank()) issues += QuestionEditorValidationIssue.BLANK_ANSWER
    if (difficulty !in 1..5) issues += QuestionEditorValidationIssue.INVALID_DIFFICULTY
    when (type) {
        QuestionType.MULTIPLE_CHOICE -> {
            if (options.size < 2) issues += QuestionEditorValidationIssue.MULTIPLE_CHOICE_NEEDS_TWO_OPTIONS
            if (correctAnswer.isNotBlank() && options.none { option ->
                    option.equals(correctAnswer.trim(), ignoreCase = true)
                }
            ) {
                issues += QuestionEditorValidationIssue.MULTIPLE_CHOICE_ANSWER_NOT_IN_OPTIONS
            }
        }
        QuestionType.TRUE_FALSE -> {
            if (correctAnswer.isNotBlank() && !correctAnswer.trim().isBooleanAnswer()) {
                issues += QuestionEditorValidationIssue.TRUE_FALSE_ANSWER_NOT_BOOLEAN
            }
        }
        QuestionType.SHORT_ANSWER,
        QuestionType.ESSAY,
        -> Unit
    }
    return QuestionEditorValidation(issues)
}

internal fun QuestionEditorState.domainOptions(): List<String> = when (type) {
    QuestionType.MULTIPLE_CHOICE -> options
    QuestionType.TRUE_FALSE -> listOf("True", "False")
    QuestionType.SHORT_ANSWER,
    QuestionType.ESSAY,
    -> emptyList()
}

internal fun QuestionEditorState.domainCorrectAnswer(): String = when (type) {
    QuestionType.TRUE_FALSE -> if (correctAnswer.trim().equals("true", ignoreCase = true)) "True" else "False"
    else -> correctAnswer.trim()
}

internal fun questionAnswerChoices(question: Question): List<String> = when (question.type) {
    QuestionType.MULTIPLE_CHOICE -> question.options
    QuestionType.TRUE_FALSE -> question.options.ifEmpty { listOf("True", "False") }
    QuestionType.SHORT_ANSWER,
    QuestionType.ESSAY,
    -> emptyList()
}

internal fun questionRequiresTextAnswer(question: Question): Boolean = questionAnswerChoices(question).isEmpty()

private fun String.isBooleanAnswer(): Boolean = equals("true", ignoreCase = true) || equals("false", ignoreCase = true)
