package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRecommendationEngineTest {
    private val planId = StudyPlanId("plan")

    @Test
    fun recommendationsAreDeterministicStructuredAndExplainEveryChoice() {
        val input = StudyRecommendationInput(
            studyPlanId = planId,
            questionAccuracyPercent = 58,
            dueReviewCount = 4,
            scheduleAdherencePercent = 60,
            topics = listOf(
                TopicRecommendationInput("b", "Budget", 35, 3, 20, 4),
                TopicRecommendationInput("a", "Administrative Law", 35, 3, 20, 4),
            ),
            nextActivity = null,
            overdueResource = ResourceRecommendationInput("m", "Course notes", 2, 10, 1L),
            nearestGoal = GoalDeadlineInput("Finish syllabus", "2026-09-01"),
        )

        val first = StudyRecommendationEngine.recommend(input)
        val second = StudyRecommendationEngine.recommend(input)

        assertEquals(first, second)
        assertEquals(4, first.size)
        assertTrue(first.all { it.reasons.isNotEmpty() })
        assertTrue(first.any { it.id == "weak-topic:a" })
        assertTrue(first.any { it.type == StudyRecommendationType.PRACTICE_DECK })
        assertTrue(first.any { it.type == StudyRecommendationType.OVERDUE_RESOURCE })
        assertTrue(
            first.flatMap(StudyRecommendation::reasons)
                .any { it.type == StudyRecommendationReasonType.GOAL_DEADLINE },
        )
    }

    @Test
    fun nextActivityIsUsedWhenThereAreNoDueReviews() {
        val recommendations = StudyRecommendationEngine.recommend(
            StudyRecommendationInput(
                studyPlanId = planId,
                questionAccuracyPercent = 80,
                dueReviewCount = 0,
                scheduleAdherencePercent = 90,
                topics = emptyList(),
                nextActivity = ActivityRecommendationInput("activity", "Read chapter", 1L, true),
                overdueResource = null,
                nearestGoal = null,
            ),
        )
        assertEquals("next-activity:activity", recommendations.single().id)
        assertEquals(StudyRecommendationPresentation.PLANNED_ACTIVITY, recommendations.single().presentation)
        assertTrue(
            recommendations.single().reasons.any {
                it.type == StudyRecommendationReasonType.MISSED_ACTIVITY
            },
        )
    }

    @Test
    fun masteredTopicsDoNotProduceFalseWeakTopicRecommendations() {
        val recommendations = StudyRecommendationEngine.recommend(
            StudyRecommendationInput(
                studyPlanId = planId,
                questionAccuracyPercent = 92,
                dueReviewCount = 0,
                scheduleAdherencePercent = 100,
                topics = listOf(TopicRecommendationInput("topic", "Ready", 100, 1, 8, 0)),
                nextActivity = null,
                overdueResource = null,
                nearestGoal = null,
            ),
        )

        assertFalse(recommendations.any { it.type == StudyRecommendationType.WEAK_TOPIC_REVIEW })
        assertFalse(recommendations.any { it.type == StudyRecommendationType.PRACTICE_DECK })
    }

    @Test
    fun dueReviewsTakePriorityOverTheNextActivity() {
        val recommendations = StudyRecommendationEngine.recommend(
            StudyRecommendationInput(
                studyPlanId = planId,
                questionAccuracyPercent = 70,
                dueReviewCount = 2,
                scheduleAdherencePercent = 40,
                topics = emptyList(),
                nextActivity = ActivityRecommendationInput("activity", "Read", 1L, true),
                overdueResource = null,
                nearestGoal = null,
            ),
        )

        assertEquals("next-review:plan", recommendations.single().id)
        assertEquals(StudyRecommendationDestination.REVIEWS, recommendations.single().destination)
    }
}
