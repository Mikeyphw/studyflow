package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionAuthorship
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicExamQuestionFiltersTest {
    private val plan = StudyPlanId("plan")

    private fun question(
        id: String,
        planId: StudyPlanId = plan,
        board: String? = "Cebraspe",
        organization: String? = "TCU",
        role: String? = "Auditor",
        year: Int? = 2025,
        authorship: QuestionAuthorship = QuestionAuthorship.OFFICIAL,
    ) = Question(
        id = QuestionId(id),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        type = QuestionType.TRUE_FALSE,
        statement = "Statement $id",
        options = emptyList(),
        correctAnswer = "True",
        explanation = "",
        examBoard = board,
        source = null,
        year = year,
        difficulty = 3,
        favorite = false,
        disputed = false,
        tags = emptyList(),
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        organization = organization,
        role = role,
        authorship = authorship,
    )

    @Test
    fun `combined filters preserve plan isolation`() {
        val other = StudyPlanId("other")
        val filter = PublicExamQuestionFilter(
            examBoard = "cebraspe",
            organization = "tcu",
            role = "auditor",
            yearFrom = 2024,
            authorship = QuestionAuthorship.OFFICIAL,
        )
        val questions = listOf(
            question("ok"),
            question("wrong-board", board = "FGV"),
            question("cross", planId = other),
        )

        val filtered = PublicExamQuestionFilters.apply(plan, questions, filter)

        assertEquals(listOf("ok"), filtered.map { it.id.value })
    }

    @Test
    fun `attempt outcome supports unseen correctly`() {
        val first = question("first")
        val second = question("second")
        val filter = PublicExamQuestionFilter(outcome = QuestionAttemptOutcome.WRONG)
        val outcomes = mapOf(second.id to QuestionAttemptOutcome.WRONG)

        val filtered = PublicExamQuestionFilters.apply(plan, listOf(first, second), filter, outcomes)

        assertEquals(listOf("second"), filtered.map { it.id.value })
    }
}
