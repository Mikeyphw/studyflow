package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TestMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PublicExamMockBlueprintTest {
    @Test
    fun `preview reports exact section shortage before creation`() {
        val planId = StudyPlanId("plan")
        val questions = listOf(question("q1", planId, "law"), question("q2", planId, "law"))
        val blueprint = ExamBlueprint(
            title = "Board mock",
            timeLimitMinutes = 120,
            sections = listOf(ExamBlueprintSection("law", 3, subjectId = SubjectId("law"))),
        )

        val preview = ExamBlueprintPlanner.preview(questions, blueprint)

        assertFalse(preview.executable)
        assertEquals(1, preview.missingQuestionCount)
        assertEquals(2, preview.selectedQuestions.size)
    }

    @Test
    fun `frozen blueprint codec preserves exact section distribution and policy`() {
        val planId = StudyPlanId("plan")
        val questions = listOf(question("q1", planId, "law"), question("q2", planId, "math"))
        val blueprint = ExamBlueprint(
            title = "Cebraspe",
            timeLimitMinutes = 90,
            minimumOverallScorePercent = 50,
            scoringPolicy = ExamScoringPolicy(ExamScoringMode.RIGHT_MINUS_WRONG),
            sections = listOf(
                ExamBlueprintSection("law", 1, weight = 2.0, subjectId = SubjectId("law")),
                ExamBlueprintSection("math", 1, weight = 1.0, subjectId = SubjectId("math")),
            ),
        )
        val preview = ExamBlueprintPlanner.preview(questions, blueprint)

        val frozen = ExamBlueprintCodec.decode(ExamBlueprintCodec.encodeConfiguration(preview))

        assertNotNull(frozen)
        assertEquals(2, frozen.questionCount)
        assertEquals(ExamScoringMode.RIGHT_MINUS_WRONG, frozen.scoringPolicy.mode)
        assertEquals(listOf("law", "math"), frozen.sections.map(FrozenExamBlueprintSection::key))
    }

    @Test
    fun `right minus wrong and blanks use frozen scoring policy`() {
        val frozen = FrozenExamBlueprint(
            version = 1,
            title = "Cebraspe",
            timeLimitMinutes = 60,
            minimumOverallScorePercent = 40,
            strictExamMode = true,
            scoringPolicy = ExamScoringPolicy(ExamScoringMode.RIGHT_MINUS_WRONG),
            sections = listOf(FrozenExamBlueprintSection("general", 3, 3.0, null)),
        )
        val planId = StudyPlanId("plan")
        val questions = listOf(
            question("q1", planId, "s"),
            question("q2", planId, "s"),
            question("q3", planId, "s"),
        )
        val state = state(
            questions,
            answers = listOf(
                answer(questions[0], correct = true),
                answer(questions[1], correct = false),
            ),
        )

        val score = ExamBlueprintScorer.score(state, frozen)

        assertEquals(0, score.scorePercent)
        assertFalse(score.passed == true)
    }

    @Test
    fun `fixed negative and weighted sections score independently`() {
        val frozen = FrozenExamBlueprint(
            version = 1,
            title = "Weighted",
            timeLimitMinutes = 60,
            minimumOverallScorePercent = 50,
            strictExamMode = true,
            scoringPolicy = ExamScoringPolicy(
                mode = ExamScoringMode.FIXED_NEGATIVE,
                wrongAnswerPenalty = 0.5,
            ),
            sections = listOf(
                FrozenExamBlueprintSection("heavy", 2, 4.0, 50),
                FrozenExamBlueprintSection("light", 1, 1.0, 100),
            ),
        )
        val planId = StudyPlanId("plan")
        val questions = listOf(
            question("q1", planId, "s"),
            question("q2", planId, "s"),
            question("q3", planId, "s"),
        )
        val state = state(
            questions,
            answers = listOf(
                answer(questions[0], correct = true),
                answer(questions[1], correct = true),
                answer(questions[2], correct = true),
            ),
        )

        val score = ExamBlueprintScorer.score(state, frozen)

        assertEquals(100, score.scorePercent)
        assertTrue(score.passed == true)
        assertTrue(score.sectionScores.all { it.scorePercent == 100 })
    }

    private fun question(id: String, planId: StudyPlanId, subject: String) = Question(
        id = QuestionId(id),
        studyPlanId = planId,
        subjectId = SubjectId(subject),
        topicId = null,
        type = QuestionType.TRUE_FALSE,
        statement = "Statement $id",
        options = emptyList(),
        correctAnswer = "true",
        explanation = "Explanation",
        examBoard = "Board",
        source = "Official",
        year = 2026,
        difficulty = 3,
        favorite = false,
        disputed = false,
        tags = emptyList(),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun answer(question: Question, correct: Boolean) = PracticeAnswerRecord(
        questionId = question.id,
        chosenAnswer = if (correct) question.correctAnswer else "false",
        correctAnswer = question.correctAnswer,
        correct = correct,
        elapsedSeconds = 10,
        answeredAtEpochMillis = 100L,
    )

    private fun state(questions: List<Question>, answers: List<PracticeAnswerRecord>) = PracticeExecutionState(
        attemptId = "mock",
        title = "Mock",
        mode = TestMode.EXAM,
        questionIds = questions.map(Question::id),
        questions = questions,
        answers = answers,
        startedAtEpochMillis = 1L,
        submittedAtEpochMillis = null,
        timeLimitSeconds = 3600L,
        negativeMarking = 0.0,
        minimumScorePercent = null,
    )
}
