package com.mikeyphw.studyflow.feature.workspace

import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicExamTopicSessionMatchingTest {
    private val subjectA = SubjectId("subject-a")
    private val subjectB = SubjectId("subject-b")
    private val topicA = TopicId("topic-a")
    private val topicB = TopicId("topic-b")

    @Test
    fun `topic-specific session contributes only to its exact topic`() {
        val session = session(subjectId = subjectA, topicId = topicA)

        assertTrue(session.contributesToTopicEvidence(topicA, subjectA))
        assertFalse(session.contributesToTopicEvidence(topicB, subjectA))
    }

    @Test
    fun `subject-only session contributes to topics in that subject`() {
        val session = session(subjectId = subjectA, topicId = null)

        assertTrue(session.contributesToTopicEvidence(topicA, subjectA))
        assertTrue(session.contributesToTopicEvidence(topicB, subjectA))
        assertFalse(session.contributesToTopicEvidence(topicA, subjectB))
    }

    @Test
    fun `topic from another subject never leaks through broad subject matching`() {
        val session = session(subjectId = subjectA, topicId = topicB)

        assertFalse(session.contributesToTopicEvidence(topicA, subjectA))
    }

    private fun session(subjectId: SubjectId?, topicId: TopicId?) = StudySession(
        id = StudySessionId("session-${subjectId?.value}-${topicId?.value}"),
        studyPlanId = StudyPlanId("plan"),
        plannedActivityId = null,
        subjectId = subjectId,
        topicId = topicId,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.QUESTIONS,
        status = StudySessionStatus.COMPLETED,
        startedAtEpochMillis = 1_000L,
        endedAtEpochMillis = 2_000L,
        grossDurationSeconds = 1_000L,
        pauseDurationSeconds = 0L,
        pagesCompleted = 0,
        questionsAnswered = 10,
        correctAnswers = 8,
        concentrationRating = null,
        difficultyRating = null,
        notes = "",
    )
}
