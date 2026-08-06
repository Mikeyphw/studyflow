package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeaknessDrillGeneratorTest {
    private val planId = StudyPlanId("plan")
    private val subjectA = SubjectId("civil")
    private val subjectB = SubjectId("tax")
    private val topicA = TopicId("appeal")

    @Test
    fun drillPrioritizesRepeatedErrorsOverGenericQuestions() {
        val exact = question("exact", subjectA, topicA, updated = 10)
        val sameTopic = question("same-topic", subjectA, topicA, updated = 20)
        val other = question("other", subjectB, null, updated = 30)
        val remediation = remediation(
            question = exact,
            priority = RemediationPriority.CRITICAL,
            recurrenceCount = 5,
        )

        val plan = WeaknessDrillGenerator.build(
            questions = listOf(other, sameTopic, exact),
            remediations = listOf(remediation),
            dueReviews = emptyList(),
            request = WeaknessDrillRequest(limit = 3, minimumQuestions = 2),
        )

        assertTrue(plan.executable)
        assertEquals(listOf("exact", "same-topic"), plan.questions.take(2).map { it.id.value })
    }

    @Test
    fun dueQuestionReviewsCanDriveADrillWithoutErrorNotebook() {
        val due = question("due", subjectA, null, updated = 10)
        val sibling = question("sibling", subjectA, null, updated = 20)

        val plan = WeaknessDrillGenerator.build(
            questions = listOf(sibling, due),
            remediations = emptyList(),
            dueReviews = listOf(review(due.id, subjectA)),
            request = WeaknessDrillRequest(limit = 2, minimumQuestions = 1),
        )

        assertTrue(plan.executable)
        assertEquals("due", plan.questions.first().id.value)
    }

    @Test
    fun emptySignalsAreReportedInsteadOfInventingADrill() {
        val plan = WeaknessDrillGenerator.build(
            questions = listOf(question("q", subjectA, topicA, updated = 1)),
            remediations = emptyList(),
            dueReviews = emptyList(),
        )

        assertEquals(WeaknessDrillEmptyReason.NO_WEAK_SIGNALS, plan.emptyReason)
    }


    @Test
    fun unrelatedQuestionsAreNotPulledInByRecurrenceWeight() {
        val exact = question("exact", subjectA, topicA, updated = 10)
        val unrelated = question("unrelated", subjectB, null, updated = 20)
        val remediation = remediation(
            question = exact,
            priority = RemediationPriority.CRITICAL,
            recurrenceCount = 8,
        )

        val plan = WeaknessDrillGenerator.build(
            questions = listOf(unrelated, exact),
            remediations = listOf(remediation),
            dueReviews = emptyList(),
            request = WeaknessDrillRequest(limit = 5, minimumQuestions = 1),
        )

        assertEquals(listOf("exact"), plan.questions.map { it.id.value })
    }

    private fun remediation(
        question: Question,
        priority: RemediationPriority,
        recurrenceCount: Int,
    ) = WrongAnswerRemediation(
        error = ErrorEntry(
            id = ErrorEntryId("error-${question.id.value}"),
            studyPlanId = planId,
            questionId = question.id,
            subjectId = question.subjectId,
            topicId = question.topicId,
            category = ErrorCategory.CONCEPT_CONFUSION,
            chosenAnswer = "B",
            correctAnswer = question.correctAnswer,
            explanation = question.explanation,
            recurrenceCount = recurrenceCount,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 2,
        ),
        pendingReview = null,
        priority = priority,
        suggestion = RemediationSuggestion.PRACTICE_SIMILAR_QUESTIONS,
    )

    private fun review(questionId: QuestionId, subjectId: SubjectId) = ReviewItem(
        id = ReviewItemId("review-${questionId.value}"),
        studyPlanId = planId,
        subjectId = subjectId,
        topicId = null,
        questionId = questionId,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.QUESTION_BASED,
        status = ReviewStatus.DUE,
        dueAtEpochMillis = 1,
        intervalDays = 0,
        repetitionCount = 2,
        easeFactor = 2.2,
        completedAtEpochMillis = null,
    )

    private fun question(
        id: String,
        subjectId: SubjectId?,
        topicId: TopicId?,
        updated: Long,
    ) = Question(
        id = QuestionId(id),
        studyPlanId = planId,
        subjectId = subjectId,
        topicId = topicId,
        type = QuestionType.MULTIPLE_CHOICE,
        statement = "Statement $id",
        options = listOf("A", "B"),
        correctAnswer = "A",
        explanation = "Explanation",
        examBoard = null,
        source = null,
        year = null,
        difficulty = 3,
        favorite = false,
        disputed = false,
        tags = emptyList(),
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = updated,
    )
}
