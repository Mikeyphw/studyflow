package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockExamStudioPlannerTest {
    private val planId = StudyPlanId("plan")
    private val subjectA = SubjectId("constitutional")
    private val subjectB = SubjectId("administrative")

    @Test
    fun studioBuildsDeterministicTimedBlueprint() {
        val questions = listOf(
            question("low", subjectA, difficulty = 1, updated = 1),
            question("board", subjectB, difficulty = 4, updated = 2, board = "CESPE"),
            question("favorite", subjectA, difficulty = 5, updated = 3, favorite = true),
        )

        val blueprint = MockExamStudioPlanner.preview(
            questions = questions,
            request = MockExamStudioRequest(
                title = "Final sprint mock",
                questionCount = 2,
                timeLimitMinutes = 90,
                minimumScorePercent = 70,
            ),
        )

        assertTrue(blueprint.executable)
        assertEquals("Final sprint mock", blueprint.title)
        assertEquals(2, blueprint.questionCount)
        assertEquals(5_400L, blueprint.timeLimitSeconds)
        assertEquals(70, blueprint.minimumScorePercent)
        assertEquals(listOf("favorite", "board"), blueprint.selectedQuestions.map { it.id.value })
        assertEquals(2, blueprint.sections.sumOf(MockExamSection::questionCount))
    }

    @Test
    fun studioWarnsInsteadOfInventingQuestions() {
        val blueprint = MockExamStudioPlanner.preview(
            questions = listOf(question("essay", subjectA, QuestionType.ESSAY)),
            request = MockExamStudioRequest(questionCount = 10),
        )

        assertFalse(blueprint.executable)
        assertTrue(MockExamStudioWarning.INELIGIBLE_QUESTIONS_SKIPPED in blueprint.warnings)
        assertTrue(MockExamStudioWarning.NOT_ENOUGH_MATCHING_QUESTIONS in blueprint.warnings)
    }

    @Test
    fun studioFiltersBoardAndTags() {
        val keep = question("keep", subjectA, board = "FGV", tags = listOf("administrativo"))
        val wrongBoard = question("wrong-board", subjectA, board = "CESPE", tags = listOf("administrativo"))
        val wrongTag = question("wrong-tag", subjectB, board = "FGV", tags = listOf("tributario"))

        val blueprint = MockExamStudioPlanner.preview(
            questions = listOf(wrongBoard, wrongTag, keep),
            request = MockExamStudioRequest(
                questionCount = 5,
                examBoard = "FGV",
                tags = listOf("administrativo"),
            ),
        )

        assertEquals(listOf("keep"), blueprint.selectedQuestions.map { it.id.value })
        assertTrue(MockExamStudioWarning.NOT_ENOUGH_MATCHING_QUESTIONS in blueprint.warnings)
    }

    @Test
    fun studioExcludesDisputedUnlessRequestedAndFiltersSource() {
        val keep = question("keep", subjectA, source = "official")
        val disputed = question("disputed", subjectB, source = "official", disputed = true)
        val wrongSource = question("wrong-source", subjectA, source = "course")

        val defaultBlueprint = MockExamStudioPlanner.preview(
            questions = listOf(disputed, wrongSource, keep),
            request = MockExamStudioRequest(questionCount = 5, source = "official"),
        )
        val inclusiveBlueprint = MockExamStudioPlanner.preview(
            questions = listOf(disputed, wrongSource, keep),
            request = MockExamStudioRequest(
                questionCount = 5,
                source = "official",
                includeDisputed = true,
            ),
        )

        assertEquals(listOf("keep"), defaultBlueprint.selectedQuestions.map { it.id.value })
        assertEquals(listOf("disputed", "keep"), inclusiveBlueprint.selectedQuestions.map { it.id.value })
    }

    private fun question(
        id: String,
        subjectId: SubjectId?,
        type: QuestionType = QuestionType.MULTIPLE_CHOICE,
        difficulty: Int = 3,
        updated: Long = 1,
        favorite: Boolean = false,
        board: String? = null,
        source: String? = null,
        disputed: Boolean = false,
        tags: List<String> = emptyList(),
    ) = Question(
        id = QuestionId(id),
        studyPlanId = planId,
        subjectId = subjectId,
        topicId = null,
        type = type,
        statement = "Question $id",
        options = if (type == QuestionType.MULTIPLE_CHOICE) listOf("A", "B", "C", "D") else emptyList(),
        correctAnswer = "A",
        explanation = "Because",
        examBoard = board,
        source = source,
        year = 2026,
        difficulty = difficulty,
        favorite = favorite,
        disputed = disputed,
        tags = tags,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = updated,
    )
}
