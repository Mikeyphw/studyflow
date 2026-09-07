package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId

enum class StudyActivityLaunchDestination {
    FOCUS_SESSION,
    WRITING_WORKSPACE,
    QUESTIONS,
    REVIEWS,
    MOCK_EXAM_STUDIO,
}

data class StudyActivityLaunchIntent(
    val destination: StudyActivityLaunchDestination,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
)

/**
 * Canonical launch contract for planned activities.
 *
 * This deliberately depends on the typed activity field, never title heuristics. Study/reading/video/custom
 * activities keep the focus-session path, writing gets the dedicated focus workspace, and practice-shaped
 * activities open the existing practice/review/mock surfaces without creating a throw-away generic session.
 */
object StudyActivityLaunchPlanner {
    fun plan(activity: PlannedActivity): StudyActivityLaunchIntent = StudyActivityLaunchIntent(
        destination = when (activity.type) {
            StudyActivityType.QUESTIONS,
            StudyActivityType.FLASHCARDS,
            -> StudyActivityLaunchDestination.QUESTIONS
            StudyActivityType.REVIEW -> StudyActivityLaunchDestination.REVIEWS
            StudyActivityType.MOCK_EXAM -> StudyActivityLaunchDestination.MOCK_EXAM_STUDIO
            StudyActivityType.WRITING -> StudyActivityLaunchDestination.WRITING_WORKSPACE
            StudyActivityType.THEORY,
            StudyActivityType.VIDEO,
            StudyActivityType.READING,
            StudyActivityType.CUSTOM,
            -> StudyActivityLaunchDestination.FOCUS_SESSION
        },
        subjectId = activity.subjectId,
        topicId = activity.topicId,
    )

    fun matchesQuestion(question: Question, subjectId: SubjectId?, topicId: TopicId?): Boolean =
        when {
            topicId != null -> question.topicId == topicId
            subjectId != null -> question.subjectId == subjectId
            else -> true
        }

    fun matchesReview(item: ReviewItem, subjectId: SubjectId?, topicId: TopicId?): Boolean =
        when {
            topicId != null -> item.topicId == topicId
            subjectId != null -> item.subjectId == subjectId
            else -> true
        }
}
