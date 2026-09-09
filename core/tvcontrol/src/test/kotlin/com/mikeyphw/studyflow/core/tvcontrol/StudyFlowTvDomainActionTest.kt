package com.mikeyphw.studyflow.core.tvcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvDomainActionTest {
    @Test
    fun mapperUsesCanonicalIdsAndAnswerLabelsFromPresentation() {
        val snapshot = readySnapshot()
        val answer = StudyFlowTvDomainActionMapper.fromCommand(
            command = command(StudyFlowTvSurface.QUESTION_SET, StudyFlowTvControlAction.SELECT_ANSWER, "q-1", "B"),
            snapshot = snapshot,
            origin = StudyFlowTvControlOrigin.PAIRED_PHONE,
        )
        val session = StudyFlowTvDomainActionMapper.fromCommand(
            command = command(StudyFlowTvSurface.STUDY_SESSION, StudyFlowTvControlAction.PAUSE),
            snapshot = snapshot,
            origin = StudyFlowTvControlOrigin.LOCAL_REMOTE,
        )

        assertEquals(StudyFlowTvDomainActionKind.PRACTICE_ANSWER, answer?.kind)
        assertEquals("test-1", answer?.contextId)
        assertEquals("q-1", answer?.targetId)
        assertEquals("Beta", answer?.answerText)
        assertEquals(StudyFlowTvDomainActionKind.SESSION_PAUSE, session?.kind)
        assertEquals("session-1", session?.targetId)
    }

    @Test
    fun startReviewAndMarkForReviewResolveOnlyFromCanonicalContext() {
        val snapshot = readySnapshot()
        val start = StudyFlowTvDomainActionMapper.fromCommand(
            command = command(StudyFlowTvSurface.STUDY_SESSION, StudyFlowTvControlAction.START),
            snapshot = snapshot,
            origin = StudyFlowTvControlOrigin.LOCAL_REMOTE,
        )
        val mark = StudyFlowTvDomainActionMapper.fromCommand(
            command = command(StudyFlowTvSurface.QUESTION_SET, StudyFlowTvControlAction.MARK_FOR_REVIEW, "q-1"),
            snapshot = snapshot,
            origin = StudyFlowTvControlOrigin.PAIRED_PHONE,
        )
        val error = StudyFlowTvDomainActionMapper.fromCommand(
            command = command(StudyFlowTvSurface.REVIEW, StudyFlowTvControlAction.MARK_FOR_REVIEW, "review-1"),
            snapshot = snapshot,
            origin = StudyFlowTvControlOrigin.LOCAL_REMOTE,
        )

        assertEquals(StudyFlowTvDomainActionKind.SESSION_START, start?.kind)
        assertEquals("activity-1", start?.targetId)
        assertEquals(StudyFlowTvDomainActionKind.PRACTICE_MARK_REVIEW, mark?.kind)
        assertEquals("q-1", mark?.targetId)
        assertEquals(StudyFlowTvDomainActionKind.REVIEW_ERROR_NOTE, error?.kind)
        assertEquals("q-1", error?.contextId)
        assertEquals("review-1", error?.targetId)
        val review = requireNotNull(snapshot.review)
        assertNull(StudyFlowTvDomainActionMapper.fromCommand(
            command = command(StudyFlowTvSurface.REVIEW, StudyFlowTvControlAction.MARK_FOR_REVIEW, "review-1"),
            snapshot = snapshot.copy(
                review = review.copy(items = review.items.map { it.copy(questionId = null) }),
            ),
            origin = StudyFlowTvControlOrigin.LOCAL_REMOTE,
        ))
    }

    @Test
    fun persistencePolicyDoesNotCapturePresentationOnlyNavigation() {
        assertTrue(StudyFlowTvDomainActionProtocol.canonicalDataOwnerRequired)
        assertTrue(StudyFlowTvDomainActionProtocol.tvRepositoryMutationForbidden)
        assertTrue(
            StudyFlowTvDomainActionProtocol.requiresPersistence(
                command(StudyFlowTvSurface.MOCK_EXAM, StudyFlowTvControlAction.SUBMIT),
            ),
        )
        assertTrue(
            !StudyFlowTvDomainActionProtocol.requiresPersistence(
                command(StudyFlowTvSurface.MOCK_EXAM, StudyFlowTvControlAction.NEXT),
            ),
        )
        assertTrue(
            !StudyFlowTvDomainActionProtocol.requiresPersistence(
                command(StudyFlowTvSurface.REVIEW, StudyFlowTvControlAction.SHOW_EXPLANATION),
            ),
        )
    }

    private fun command(
        surface: StudyFlowTvSurface,
        action: StudyFlowTvControlAction,
        targetId: String? = null,
        optionId: String? = null,
    ) = StudyFlowTvControlCommand(
        commandId = "action-${surface.name}-${action.name}-$targetId-$optionId",
        surface = surface,
        action = action,
        targetId = targetId,
        optionId = optionId,
        issuedAtEpochMillis = 100L,
    )

    private fun readySnapshot() = StudyFlowTvPresentationSnapshot(
        snapshotId = "snapshot-1",
        studyPlanId = "plan-1",
        planTitle = "Plan",
        generatedAtEpochMillis = 100L,
        availability = StudyFlowTvPresentationAvailability.READY,
        today = StudyFlowTvTodayPresentation(
            planTitle = "Plan",
            nextActivity = StudyFlowTvActivityPresentation(
                activityId = "activity-1",
                title = "Next block",
                subjectLabel = "Law",
                activityType = "READING",
                plannedDurationMinutes = 30,
                scheduledStartEpochMillis = null,
                status = "PLANNED",
                targetSurface = StudyFlowTvSurface.STUDY_SESSION,
            ),
            dueReviewCount = 1,
            availableQuestionCount = 1,
            weeklyProgressPercent = 10,
            mockExamScheduled = false,
        ),
        studySession = StudyFlowTvStudySessionPresentation(
            sessionId = "session-1",
            title = "Session",
            subjectLabel = "Law",
            status = "RUNNING",
            elapsedMinutes = 5,
            targetMinutes = 30,
            questionsAnswered = 0,
            correctAnswers = 0,
        ),
        questionSet = StudyFlowTvQuestionSetPresentation(
            questionSetId = "test-1",
            title = "Practice",
            subjectLabel = "Law",
            questions = listOf(
                StudyFlowTvQuestionPresentation(
                    questionId = "q-1",
                    prompt = "Prompt",
                    choices = listOf(
                        StudyFlowTvQuestionChoicePresentation("0", "Alpha"),
                        StudyFlowTvQuestionChoicePresentation("1", "Beta"),
                    ),
                    correctOptionId = "1",
                    explanation = "Because",
                    subjectLabel = "Law",
                    sourceLabel = null,
                ),
            ),
            started = true,
            currentQuestionId = "q-1",
        ),
        review = StudyFlowTvReviewPresentation(
            queueId = "review-queue",
            title = "Reviews",
            items = listOf(
                StudyFlowTvReviewItemPresentation(
                    reviewItemId = "review-1",
                    questionId = "q-1",
                    title = "Review",
                    subjectLabel = "Law",
                    prompt = "Prompt",
                    explanation = "Explanation",
                    reason = "due",
                    confidencePercent = 50,
                    dueAtEpochMillis = 0L,
                    status = "DUE",
                ),
            ),
        ),
    )
}
