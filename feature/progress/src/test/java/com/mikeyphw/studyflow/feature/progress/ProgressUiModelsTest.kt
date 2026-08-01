package com.mikeyphw.studyflow.feature.progress

import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyGoalId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressUiModelsTest {
    @Test
    fun goalRowShowsProgress() {
        val row = StudyGoal(
            id = StudyGoalId("g1"),
            studyPlanId = StudyPlanId("p1"),
            subjectId = null,
            topicId = null,
            type = GoalType.QUESTION_COUNT,
            period = GoalPeriod.WEEKLY,
            title = "Questions",
            targetValue = 100.0,
            currentValue = 25.0,
            startsOnIso = "2026-08-01",
            endsOnIso = null,
            completedAtEpochMillis = null,
            archived = false,
        ).asRowState()

        assertEquals(25, row.progressPercent)
    }
}
