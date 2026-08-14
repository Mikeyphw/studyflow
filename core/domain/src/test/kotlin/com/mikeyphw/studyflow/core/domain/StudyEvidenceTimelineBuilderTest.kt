package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyGoalId
import com.mikeyphw.studyflow.core.model.StudyNote
import com.mikeyphw.studyflow.core.model.StudyNoteId
import com.mikeyphw.studyflow.core.model.StudyNoteType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.TestMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyEvidenceTimelineBuilderTest {
    private val planId = StudyPlanId("plan")

    @Test
    fun evidenceTimelineCombinesStudyEventsNewestFirst() {
        val events = StudyEvidenceTimelineBuilder.build(
            StudyEvidenceTimelineInput(
                studyPlanId = planId,
                plannedActivities = listOf(activity()),
                sessions = listOf(session()),
                reviewItems = listOf(review()),
                questions = listOf(question()),
                tests = listOf(test()),
                mockExams = listOf(mockExam()),
                goals = listOf(goal()),
                notes = listOf(note()),
                nowEpochMillis = 10_000L,
                limit = 12,
            ),
        )

        assertEquals(StudyEvidenceEventKind.NOTE_PINNED, events.first().kind)
        assertTrue(events.any { event -> event.kind == StudyEvidenceEventKind.SESSION_COMPLETED })
        assertTrue(events.any { event -> event.kind == StudyEvidenceEventKind.REVIEW_DUE })
        assertTrue(events.any { event -> event.kind == StudyEvidenceEventKind.MOCK_EXAM_SUBMITTED })
        assertEquals(events, events.sortedByDescending(StudyEvidenceEvent::occurredAtEpochMillis))
    }

    @Test
    fun evidenceTimelineHonorsBoundedLimit() {
        val events = StudyEvidenceTimelineBuilder.build(
            StudyEvidenceTimelineInput(
                studyPlanId = planId,
                plannedActivities = emptyList(),
                sessions = listOf(session("a", 100), session("b", 200), session("c", 300)),
                reviewItems = emptyList(),
                questions = emptyList(),
                tests = emptyList(),
                mockExams = emptyList(),
                goals = emptyList(),
                notes = emptyList(),
                nowEpochMillis = 1_000L,
                limit = 2,
            ),
        )

        assertEquals(listOf("session:c", "session:b"), events.map(StudyEvidenceEvent::id))
    }


    @Test
    fun evidenceTimelineFiltersEventsToRequestedPlan() {
        val otherPlanId = StudyPlanId("other")
        val events = StudyEvidenceTimelineBuilder.build(
            StudyEvidenceTimelineInput(
                studyPlanId = planId,
                plannedActivities = listOf(activity(), activity("other-activity", otherPlanId)),
                sessions = listOf(session("own", 2_000L), session("other", 9_000L, otherPlanId)),
                reviewItems = emptyList(),
                questions = listOf(question("own-question", planId), question("other-question", otherPlanId)),
                tests = emptyList(),
                mockExams = emptyList(),
                goals = listOf(goal(), goal("other-goal", otherPlanId)),
                notes = listOf(note(), note("other-note", otherPlanId)),
                nowEpochMillis = 10_000L,
                limit = 12,
            ),
        )

        assertTrue(events.isNotEmpty())
        assertTrue(events.none { event -> event.id.contains("other") })
    }

    @Test
    fun evidenceTimelineSkipsUntimedPracticeAndMockEvents() {
        val events = StudyEvidenceTimelineBuilder.build(
            StudyEvidenceTimelineInput(
                studyPlanId = planId,
                plannedActivities = emptyList(),
                sessions = emptyList(),
                reviewItems = emptyList(),
                questions = emptyList(),
                tests = listOf(test(startedAt = null, submittedAt = null)),
                mockExams = listOf(mockExam(startedAt = null, submittedAt = null)),
                goals = emptyList(),
                notes = emptyList(),
                nowEpochMillis = 10_000L,
                limit = 12,
            ),
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun linkedCompletedActivityAndSessionCountAsOneEvidenceEvent() {
        val linkedActivity = activity("linked").copy(
            status = PlannedActivityStatus.COMPLETED,
            completedAtEpochMillis = 2_000L,
        )
        val linkedSession = session("linked-session", 2_000L).copy(plannedActivityId = linkedActivity.id)

        val events = StudyEvidenceTimelineBuilder.buildAll(
            StudyEvidenceTimelineInput(
                studyPlanId = planId,
                plannedActivities = listOf(linkedActivity),
                sessions = listOf(linkedSession),
                reviewItems = emptyList(),
                questions = emptyList(),
                tests = emptyList(),
                mockExams = emptyList(),
                goals = emptyList(),
                notes = emptyList(),
                nowEpochMillis = 3_000L,
            ),
        )

        assertEquals(listOf("session:linked-session"), events.map(StudyEvidenceEvent::id))
    }

    private fun session(
        id: String = "session",
        endedAt: Long = 2_000L,
        studyPlanId: StudyPlanId = planId,
    ) = StudySession(
        id = StudySessionId(id),
        studyPlanId = studyPlanId,
        plannedActivityId = null,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.QUESTIONS,
        status = StudySessionStatus.COMPLETED,
        startedAtEpochMillis = endedAt - 1_800_000L,
        endedAtEpochMillis = endedAt,
        grossDurationSeconds = 1_800L,
        pauseDurationSeconds = 0L,
        pagesCompleted = 0,
        questionsAnswered = 10,
        correctAnswers = 8,
        concentrationRating = null,
        difficultyRating = null,
        notes = "",
        targetDurationMinutes = 30,
    )

    private fun activity(
        id: String = "activity",
        studyPlanId: StudyPlanId = planId,
    ) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = studyPlanId,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.REVIEW,
        title = "Review block",
        scheduledStartEpochMillis = 1_500L,
        plannedDurationMinutes = 25,
        status = PlannedActivityStatus.MISSED,
        locked = false,
        completedAtEpochMillis = null,
    )

    private fun review() = ReviewItem(
        id = ReviewItemId("review"),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.SPACED_REPETITION,
        status = ReviewStatus.DUE,
        dueAtEpochMillis = 3_000L,
        intervalDays = 1,
        repetitionCount = 2,
        easeFactor = 2.4,
        completedAtEpochMillis = null,
    )

    private fun question(
        id: String = "question",
        studyPlanId: StudyPlanId = planId,
    ) = Question(
        id = QuestionId(id),
        studyPlanId = studyPlanId,
        subjectId = null,
        topicId = null,
        type = QuestionType.MULTIPLE_CHOICE,
        statement = "Question statement",
        options = listOf("A", "B"),
        correctAnswer = "A",
        explanation = "Because",
        examBoard = null,
        source = null,
        year = null,
        difficulty = 3,
        favorite = false,
        disputed = false,
        tags = listOf("tag"),
        createdAtEpochMillis = 4_000L,
        updatedAtEpochMillis = 4_000L,
    )

    private fun test(
        id: String = "test",
        startedAt: Long? = 5_000L,
        submittedAt: Long? = null,
    ) = StudyTest(
        id = StudyTestId(id),
        studyPlanId = planId,
        title = "Drill",
        mode = TestMode.PRACTICE,
        timeLimitSeconds = null,
        questionCount = 5,
        answeredCount = 0,
        correctCount = 0,
        startedAtEpochMillis = startedAt,
        submittedAtEpochMillis = submittedAt,
        configurationJson = "questionIds=question",
    )

    private fun mockExam(
        id: String = "mock",
        startedAt: Long? = 5_500L,
        submittedAt: Long? = 6_000L,
    ) = MockExam(
        id = MockExamId(id),
        studyPlanId = planId,
        title = "Mock",
        mode = TestMode.EXAM,
        timeLimitSeconds = 3_600L,
        questionCount = 10,
        answeredCount = 10,
        correctCount = 7,
        scorePercent = 70.0,
        negativeMarking = 0.0,
        minimumScorePercent = 60,
        startedAtEpochMillis = startedAt,
        submittedAtEpochMillis = submittedAt,
        configurationJson = "questionIds=question",
    )

    private fun goal(
        id: String = "goal",
        studyPlanId: StudyPlanId = planId,
    ) = StudyGoal(
        id = StudyGoalId(id),
        studyPlanId = studyPlanId,
        subjectId = null,
        topicId = null,
        type = com.mikeyphw.studyflow.core.model.GoalType.STUDY_TIME,
        period = com.mikeyphw.studyflow.core.model.GoalPeriod.WEEKLY,
        title = "Weekly target",
        targetValue = 100.0,
        currentValue = 100.0,
        startsOnIso = "2026-08-01",
        endsOnIso = null,
        completedAtEpochMillis = 7_000L,
        archived = false,
    )

    private fun note(
        id: String = "note",
        studyPlanId: StudyPlanId = planId,
    ) = StudyNote(
        id = StudyNoteId(id),
        studyPlanId = studyPlanId,
        subjectId = null,
        topicId = null,
        materialId = null,
        type = StudyNoteType.SUMMARY,
        title = "Pinned evidence",
        content = "Evidence",
        pinned = true,
        tags = emptyList(),
        createdAtEpochMillis = 7_500L,
        updatedAtEpochMillis = 8_000L,
    )

    @Test
    fun submittedPracticeScoreUsesTotalQuestionCount() {
        val test = StudyTest(
            id = StudyTestId("test-score"),
            studyPlanId = StudyPlanId("plan"),
            title = "Ten questions",
            mode = TestMode.EXAM,
            questionCount = 10,
            answeredCount = 1,
            correctCount = 1,
            startedAtEpochMillis = 1_000L,
            submittedAtEpochMillis = 2_000L,
            timeLimitSeconds = 600L,
            configurationJson = "questionIds=q1,q2,q3,q4,q5,q6,q7,q8,q9,q10",
        )
        val event = StudyEvidenceTimelineBuilder.buildAll(
            StudyEvidenceTimelineInput(
                studyPlanId = StudyPlanId("plan"),
                plannedActivities = emptyList(),
                sessions = emptyList(),
                reviewItems = emptyList(),
                questions = emptyList(),
                tests = listOf(test),
                mockExams = emptyList(),
                goals = emptyList(),
                notes = emptyList(),
                nowEpochMillis = 3_000L,
            ),
        ).single { it.kind == StudyEvidenceEventKind.PRACTICE_TEST_SUBMITTED }

        assertEquals(10, event.percent)
    }

}
