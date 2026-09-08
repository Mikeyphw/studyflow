package com.mikeyphw.studyflow.core.tvcontrol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvPresentationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun typedPresentationSnapshotRoundTripsWithoutDatabaseOrUiPayloads() {
        val snapshot = StudyFlowTvPresentationSnapshot(
            snapshotId = "snapshot-42",
            studyPlanId = "plan-1",
            planTitle = "My plan",
            generatedAtEpochMillis = 42L,
            availability = StudyFlowTvPresentationAvailability.READY,
            today = StudyFlowTvTodayPresentation(
                planTitle = "My plan",
                nextActivity = StudyFlowTvActivityPresentation(
                    activityId = "activity-1",
                    title = "Review chapter",
                    subjectLabel = "Administrative Law",
                    activityType = "REVIEW",
                    plannedDurationMinutes = 30,
                    scheduledStartEpochMillis = 100L,
                    status = "PLANNED",
                    targetSurface = StudyFlowTvSurface.REVIEW,
                ),
                dueReviewCount = 3,
                availableQuestionCount = 12,
                weeklyProgressPercent = 50,
                mockExamScheduled = false,
            ),
            questionSet = StudyFlowTvQuestionSetPresentation(
                questionSetId = "questions-plan-1",
                title = "Question practice",
                subjectLabel = "Mixed subjects",
                questions = listOf(
                    StudyFlowTvQuestionPresentation(
                        questionId = "q-1",
                        prompt = "Prompt",
                        choices = listOf(
                            StudyFlowTvQuestionChoicePresentation("0", "A"),
                            StudyFlowTvQuestionChoicePresentation("1", "B"),
                        ),
                        correctOptionId = "1",
                        explanation = "Explanation",
                        subjectLabel = "General",
                        sourceLabel = "Official",
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(StudyFlowTvPresentationSnapshot.serializer(), snapshot)
        val decoded = json.decodeFromString(StudyFlowTvPresentationSnapshot.serializer(), encoded)

        assertEquals(snapshot, decoded)
        assertTrue(decoded.hasRenderableStudyData)
        assertFalse(encoded.contains("RoomEntity"))
        assertFalse(encoded.contains("android.view"))
    }

    @Test
    fun waitingSnapshotIsExplicitAndContainsNoFakeStudyContent() {
        val snapshot = StudyFlowTvPresentationSnapshot.waitingForPlan("plan-missing", 50L)

        assertEquals(StudyFlowTvPresentationAvailability.WAITING_FOR_PLAN, snapshot.availability)
        assertFalse(snapshot.hasRenderableStudyData)
        assertEquals(null, snapshot.questionSet)
        assertEquals(null, snapshot.schedule)
        assertEquals(null, snapshot.library)
    }
}
