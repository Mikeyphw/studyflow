package com.mikeyphw.studyflow.tv.presentation

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMockExamPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMockExamSectionPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationAvailability
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvScheduleItemPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSchedulePresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvPresentationAdapterOv19TruthTest {
    @Test
    fun mockExamProjectionUsesRealSectionsAndPreservesPausedStatus() {
        val state = StudyFlowTvPresentationAdapter.applySnapshot(
            current = StudyFlowTvPresentationAdapter.waitingState(),
            snapshot = snapshot(
                mock = StudyFlowTvMockExamPresentation(
                    mockExamId = "mock-real",
                    title = "Real mock",
                    status = "PAUSED",
                    totalMinutes = 90,
                    elapsedMinutes = 15,
                    questionCount = 20,
                    answeredCount = 4,
                    correctCount = 3,
                    scorePercent = null,
                    sections = listOf(
                        StudyFlowTvMockExamSectionPresentation("laws", "Laws", 10, 45, 4, 3),
                        StudyFlowTvMockExamSectionPresentation("math", "Math", 10, 45, 0, 0),
                    ),
                    currentSectionId = "math",
                    canPersistActions = true,
                ),
            ),
        )

        assertEquals("mock-real", state.mockExam.examId)
        assertEquals(2, state.mockExam.sections.size)
        assertEquals("math", state.mockExam.activeSection.sourceId)
        assertEquals(StudyFlowTvMockExamStatus.PAUSED, state.mockExam.status)
        assertEquals(4, state.mockExam.answeredQuestionCount)
    }

    @Test
    fun scheduleLabelsAreFormattedForTvAndNotRawEpochMillis() {
        val rawEpoch = 1_700_000_000_000L
        val state = StudyFlowTvPresentationAdapter.applySnapshot(
            current = StudyFlowTvPresentationAdapter.waitingState(),
            snapshot = snapshot(
                schedule = StudyFlowTvSchedulePresentation(
                    scheduleId = "schedule-real",
                    title = "Real schedule",
                    items = listOf(
                        StudyFlowTvScheduleItemPresentation(
                            activityId = "activity-real",
                            title = "Real block",
                            subjectLabel = "Math",
                            activityType = "READING",
                            status = "PLANNED",
                            plannedDurationMinutes = 45,
                            scheduledStartEpochMillis = rawEpoch,
                            targetSurface = StudyFlowTvSurface.STUDY_SESSION,
                        ),
                    ),
                ),
            ),
        )
        val label = state.schedule.items.single().timeLabel

        assertFalse(label.contains(rawEpoch.toString()))
        assertTrue(label.contains("45 min"))
    }

    private fun snapshot(
        mock: StudyFlowTvMockExamPresentation? = null,
        schedule: StudyFlowTvSchedulePresentation? = null,
    ) = StudyFlowTvPresentationSnapshot(
        snapshotId = "ov19-snapshot",
        studyPlanId = "plan-real",
        planTitle = "Plan",
        generatedAtEpochMillis = 100_000L,
        availability = StudyFlowTvPresentationAvailability.READY,
        mockExam = mock,
        schedule = schedule,
    )
}
