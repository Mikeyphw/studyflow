package com.mikeyphw.studyflow.tv.presentation

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvActivityPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvLibraryPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvLibraryReferencePresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationAvailability
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvProgressPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvQuestionChoicePresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvQuestionPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvQuestionSetPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvScheduleItemPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSchedulePresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvTodayPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvPresentationAdapterTest {
    @Test
    fun typedSnapshotKeepsCanonicalSourceIdsWhenAdaptedToTvSlots() {
        val snapshot = snapshot("snap-1", 100L)
        val state = StudyFlowTvPresentationAdapter.applySnapshot(
            StudyFlowTvPresentationAdapter.waitingState(),
            snapshot,
        )

        assertEquals("Real plan", state.today.examName)
        assertEquals("question-real", state.questionPractice.questions.single().sourceId)
        assertEquals("activity-real", state.schedule.items.single().sourceId)
        assertEquals("material-real", state.library.references.single().sourceId)
        assertFalse(state.today.nextBlockTitle.contains("Focused study block"))
    }

    @Test
    fun inboxRejectsDuplicateAndOlderPresentationSnapshots() {
        val inbox = StudyFlowTvPresentationInbox()
        assertTrue(inbox.apply(snapshot("snap-1", 100L)))
        assertFalse(inbox.apply(snapshot("snap-1", 100L)))
        assertFalse(inbox.apply(snapshot("snap-old", 99L)))
        assertTrue(inbox.apply(snapshot("snap-2", 101L)))
        assertEquals("snap-2", inbox.current?.snapshotId)
    }

    @Test
    fun waitingStateIsExplicitAndContainsNoHistoricalExamFixture() {
        val state = StudyFlowTvPresentationAdapter.waitingState()
        assertTrue(state.today.nextBlockTitle.contains("Waiting"))
        assertTrue(state.questionPractice.title.contains("Waiting"))
        assertFalse(state.progress.examName.contains("TRT"))
    }

    private fun snapshot(id: String, generatedAt: Long) = StudyFlowTvPresentationSnapshot(
        snapshotId = id,
        studyPlanId = "plan-real",
        planTitle = "Real plan",
        generatedAtEpochMillis = generatedAt,
        availability = StudyFlowTvPresentationAvailability.READY,
        today = StudyFlowTvTodayPresentation(
            planTitle = "Real plan",
            nextActivity = StudyFlowTvActivityPresentation(
                activityId = "activity-real",
                title = "Real activity",
                subjectLabel = "Algorithms",
                activityType = "QUESTIONS",
                plannedDurationMinutes = 30,
                scheduledStartEpochMillis = 1_000L,
                status = "PLANNED",
                targetSurface = StudyFlowTvSurface.QUESTION_SET,
            ),
            dueReviewCount = 2,
            availableQuestionCount = 1,
            weeklyProgressPercent = 50,
            mockExamScheduled = false,
        ),
        questionSet = StudyFlowTvQuestionSetPresentation(
            questionSetId = "set-real",
            title = "Real questions",
            subjectLabel = "Algorithms",
            questions = listOf(
                StudyFlowTvQuestionPresentation(
                    questionId = "question-real",
                    prompt = "Real prompt",
                    choices = listOf(
                        StudyFlowTvQuestionChoicePresentation("0", "A"),
                        StudyFlowTvQuestionChoicePresentation("1", "B"),
                    ),
                    correctOptionId = "0",
                    explanation = "Real explanation",
                    subjectLabel = "Algorithms",
                    sourceLabel = null,
                ),
            ),
        ),
        progress = StudyFlowTvProgressPresentation(
            planTitle = "Real plan",
            studiedMinutesLastSevenDays = 300,
            weeklyGoalMinutes = 600,
            weeklyProgressPercent = 50,
            questionAccuracyPercent = 80,
            answeredQuestions = 20,
            dueReviewCount = 2,
            contentProgressPercent = 45,
            consistencyScorePercent = 70,
            weakAreas = emptyList(),
        ),
        schedule = StudyFlowTvSchedulePresentation(
            scheduleId = "schedule-real",
            title = "Real schedule",
            items = listOf(
                StudyFlowTvScheduleItemPresentation(
                    activityId = "activity-real",
                    title = "Real activity",
                    subjectLabel = "Algorithms",
                    activityType = "QUESTIONS",
                    status = "PLANNED",
                    plannedDurationMinutes = 30,
                    scheduledStartEpochMillis = 1_000L,
                    targetSurface = StudyFlowTvSurface.QUESTION_SET,
                ),
            ),
        ),
        library = StudyFlowTvLibraryPresentation(
            libraryId = "library-real",
            title = "Real library",
            references = listOf(
                StudyFlowTvLibraryReferencePresentation(
                    referenceId = "material-real",
                    title = "Real reference",
                    subjectLabel = "Algorithms",
                    sourceLabel = "My notes",
                    excerpt = "Real excerpt",
                    referenceKind = "NOTE",
                    relatedSurface = StudyFlowTvSurface.LIBRARY,
                ),
            ),
        ),
    )
}
