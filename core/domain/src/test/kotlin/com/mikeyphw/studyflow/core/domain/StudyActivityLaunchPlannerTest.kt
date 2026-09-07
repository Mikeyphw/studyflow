package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionAuthorship
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyActivityLaunchPlannerTest {
    private val planId = StudyPlanId("plan")
    private val subjectId = SubjectId("subject")
    private val topicId = TopicId("topic")

    @Test
    fun `activity types route to their canonical workspaces`() {
        val expectations = mapOf(
            StudyActivityType.MOCK_EXAM to StudyActivityLaunchDestination.MOCK_EXAM_STUDIO,
            StudyActivityType.WRITING to StudyActivityLaunchDestination.WRITING_WORKSPACE,
            StudyActivityType.QUESTIONS to StudyActivityLaunchDestination.QUESTIONS,
            StudyActivityType.FLASHCARDS to StudyActivityLaunchDestination.QUESTIONS,
            StudyActivityType.REVIEW to StudyActivityLaunchDestination.REVIEWS,
            StudyActivityType.THEORY to StudyActivityLaunchDestination.FOCUS_SESSION,
            StudyActivityType.VIDEO to StudyActivityLaunchDestination.FOCUS_SESSION,
            StudyActivityType.READING to StudyActivityLaunchDestination.FOCUS_SESSION,
            StudyActivityType.CUSTOM to StudyActivityLaunchDestination.FOCUS_SESSION,
        )
        expectations.forEach { (type, expected) ->
            val intent = StudyActivityLaunchPlanner.plan(activity(type))
            assertEquals(expected, intent.destination)
            assertEquals(subjectId, intent.subjectId)
            assertEquals(topicId, intent.topicId)
        }
    }

    @Test
    fun `practice filters prefer topic then subject and never broaden silently`() {
        val exactQuestion = question("exact", subjectId, topicId)
        val siblingQuestion = question("sibling", subjectId, TopicId("other-topic"))
        val otherQuestion = question("other", SubjectId("other-subject"), TopicId("other-topic"))
        assertTrue(StudyActivityLaunchPlanner.matchesQuestion(exactQuestion, subjectId, topicId))
        assertFalse(StudyActivityLaunchPlanner.matchesQuestion(siblingQuestion, subjectId, topicId))
        assertTrue(StudyActivityLaunchPlanner.matchesQuestion(siblingQuestion, subjectId, null))
        assertFalse(StudyActivityLaunchPlanner.matchesQuestion(otherQuestion, subjectId, null))

        val exactReview = review("exact", subjectId, topicId)
        val siblingReview = review("sibling", subjectId, TopicId("other-topic"))
        assertTrue(StudyActivityLaunchPlanner.matchesReview(exactReview, subjectId, topicId))
        assertFalse(StudyActivityLaunchPlanner.matchesReview(siblingReview, subjectId, topicId))
        assertTrue(StudyActivityLaunchPlanner.matchesReview(siblingReview, subjectId, null))
    }

    private fun activity(type: StudyActivityType) = PlannedActivity(
        id = PlannedActivityId(type.name), studyPlanId = planId, subjectId = subjectId, topicId = topicId,
        materialId = null, videoResourceId = null, type = type, title = type.name,
        scheduledStartEpochMillis = null, plannedDurationMinutes = 30, status = PlannedActivityStatus.PLANNED,
        locked = false, completedAtEpochMillis = null,
    )

    private fun question(id: String, subject: SubjectId?, topic: TopicId?) = Question(
        id = QuestionId(id), studyPlanId = planId, subjectId = subject, topicId = topic,
        type = QuestionType.TRUE_FALSE, statement = "Statement $id", options = emptyList(),
        correctAnswer = "True", explanation = "", examBoard = null, source = null, year = null,
        difficulty = 3, favorite = false, disputed = false, tags = emptyList(),
        createdAtEpochMillis = 1L, updatedAtEpochMillis = 1L, authorship = QuestionAuthorship.UNKNOWN,
    )

    private fun review(id: String, subject: SubjectId?, topic: TopicId?) = ReviewItem(
        id = ReviewItemId(id), studyPlanId = planId, subjectId = subject, topicId = topic,
        questionId = null, flashcardId = null, errorEntryId = null, method = ReviewMethod.SPACED_REPETITION,
        status = ReviewStatus.DUE, dueAtEpochMillis = 1L, intervalDays = 1, repetitionCount = 0,
        easeFactor = 2.5, completedAtEpochMillis = null,
    )
}
