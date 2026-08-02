package com.mikeyphw.studyflow.feature.practice

import com.mikeyphw.studyflow.core.domain.PracticeSummary
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyTest

internal data class QuestionEditorState(
    val statement: String = "",
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val correctAnswer: String = "",
    val explanation: String = "",
    val tags: String = "",
    val difficulty: Int = 3,
    val type: QuestionType = QuestionType.MULTIPLE_CHOICE,
) {
    val options: List<String>
        get() = listOf(optionA, optionB, optionC, optionD).map(String::trim).filter(String::isNotBlank)

    val tagList: List<String>
        get() = tags.split(',', ';').map(String::trim).filter(String::isNotBlank).distinct()

    val canSave: Boolean
        get() = statement.isNotBlank() && correctAnswer.isNotBlank() && difficulty in 1..5 &&
            (type != QuestionType.MULTIPLE_CHOICE || options.size >= 2)
}

internal data class PracticeScreenState(
    val questions: List<Question>,
    val tests: List<StudyTest>,
    val mocks: List<MockExam>,
    val summary: PracticeSummary,
    val favoriteOnly: Boolean,
    val disputedOnly: Boolean,
) {
    val filteredQuestions: List<Question> = questions
        .filter { question -> !favoriteOnly || question.favorite }
        .filter { question -> !disputedOnly || question.disputed }
}
