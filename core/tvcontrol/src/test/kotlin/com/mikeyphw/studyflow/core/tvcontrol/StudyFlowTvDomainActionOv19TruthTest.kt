package com.mikeyphw.studyflow.core.tvcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvDomainActionOv19TruthTest {
    @Test
    fun mockPauseAndResumeRequirePersistentCanonicalMutations() {
        assertTrue(StudyFlowTvDomainActionProtocol.requiresPersistence(command(StudyFlowTvControlAction.PAUSE)))
        assertTrue(StudyFlowTvDomainActionProtocol.requiresPersistence(command(StudyFlowTvControlAction.RESUME)))

        val pause = StudyFlowTvDomainActionMapper.fromCommand(
            command = command(StudyFlowTvControlAction.PAUSE),
            snapshot = snapshot(),
            origin = StudyFlowTvControlOrigin.PAIRED_PHONE,
        )
        val resume = StudyFlowTvDomainActionMapper.fromCommand(
            command = command(StudyFlowTvControlAction.RESUME),
            snapshot = snapshot(),
            origin = StudyFlowTvControlOrigin.PAIRED_PHONE,
        )

        assertEquals(StudyFlowTvDomainActionKind.MOCK_PAUSE, pause?.kind)
        assertEquals("mock-real", pause?.contextId)
        assertEquals("mock-real", pause?.targetId)
        assertEquals(StudyFlowTvDomainActionKind.MOCK_RESUME, resume?.kind)
        assertEquals("mock-real", resume?.targetId)
    }

    @Test
    fun knownAndCompleteReviewCommandsDoNotCollapseToSameGrade() {
        val known = StudyFlowTvDomainActionMapper.fromCommand(
            command = reviewCommand("known"),
            snapshot = snapshot(),
            origin = StudyFlowTvControlOrigin.LOCAL_REMOTE,
        )
        val complete = StudyFlowTvDomainActionMapper.fromCommand(
            command = reviewCommand("complete"),
            snapshot = snapshot(),
            origin = StudyFlowTvControlOrigin.LOCAL_REMOTE,
        )

        assertEquals(StudyFlowTvDomainActionKind.REVIEW_GRADE, known?.kind)
        assertEquals(5, known?.reviewGrade)
        assertEquals(4, complete?.reviewGrade)
        assertNotEquals(known?.reviewGrade, complete?.reviewGrade)
    }

    private fun command(action: StudyFlowTvControlAction) = StudyFlowTvControlCommand(
        commandId = "ov19-mock-${action.name.lowercase()}",
        surface = StudyFlowTvSurface.MOCK_EXAM,
        action = action,
        targetId = "mock-real",
        optionId = action.name.lowercase(),
        issuedAtEpochMillis = 1_000L,
    )

    private fun reviewCommand(optionId: String) = StudyFlowTvControlCommand(
        commandId = "ov19-review-$optionId",
        surface = StudyFlowTvSurface.REVIEW,
        action = StudyFlowTvControlAction.COMPLETE,
        targetId = "review-real",
        optionId = optionId,
        issuedAtEpochMillis = 1_000L,
    )

    private fun snapshot() = StudyFlowTvPresentationSnapshot(
        snapshotId = "ov19-snapshot",
        studyPlanId = "plan-real",
        planTitle = "Plan",
        generatedAtEpochMillis = 1_000L,
        availability = StudyFlowTvPresentationAvailability.READY,
        mockExam = StudyFlowTvMockExamPresentation(
            mockExamId = "mock-real",
            title = "Mock",
            status = "RUNNING",
            totalMinutes = 60,
            elapsedMinutes = 10,
            questionCount = 20,
            answeredCount = 3,
            correctCount = 2,
            scorePercent = null,
        ),
        review = StudyFlowTvReviewPresentation(
            queueId = "review-queue",
            title = "Reviews",
            items = listOf(
                StudyFlowTvReviewItemPresentation(
                    reviewItemId = "review-real",
                    questionId = "question-real",
                    title = "Review",
                    subjectLabel = "Law",
                    prompt = "Prompt",
                    explanation = "Explanation",
                    reason = "Due",
                    confidencePercent = 40,
                    dueAtEpochMillis = 0L,
                    status = "DUE",
                ),
            ),
        ),
    )
}
