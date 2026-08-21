package com.mikeyphw.studyflow.core.database

import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlanEntityMapperTest {
    @Test
    fun `mapping preserves stable enum names and timestamps`() {
        val plan = StudyPlan(
            id = StudyPlanId("phase-2"),
            name = "Phase 2",
            description = "Persistent plan",
            type = StudyType.PUBLIC_EXAMINATION,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = "2027-10-06",
            weeklyGoalHours = 20,
            progressPercent = 42,
            studiedMinutesThisWeek = 760,
            questionsAnswered = 428,
            averageAccuracyPercent = 74,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 200L,
        )

        assertEquals(plan, plan.asEntity(nowEpochMillis = 999L).asExternalModel())
    }

    @Test
    fun `mapping preserves content mode`() {
        val plan = StudyPlan(
            id = StudyPlanId("programming"),
            name = "Android course",
            description = "Kotlin and Compose",
            type = StudyType.FREE_STUDY,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = null,
            weeklyGoalHours = 12,
            progressPercent = 0,
            studiedMinutesThisWeek = 0,
            questionsAnswered = 0,
            averageAccuracyPercent = 0,
            contentMode = StudyContentMode.PROGRAMMING_COURSE,
        )

        assertEquals(StudyContentMode.PROGRAMMING_COURSE, plan.asEntity(1L).asExternalModel().contentMode)
    }

    @Test
    fun `mapping supplies timestamps for phase 1 plans`() {
        val legacyPlan = StudyPlan(
            id = StudyPlanId("legacy"),
            name = "Legacy",
            description = "Created before Room",
            type = StudyType.FREE_STUDY,
            status = StudyPlanStatus.DRAFT,
            examDateIso = null,
            weeklyGoalHours = 5,
            progressPercent = 0,
            studiedMinutesThisWeek = 0,
            questionsAnswered = 0,
            averageAccuracyPercent = 0,
        )

        val entity = legacyPlan.asEntity(nowEpochMillis = 123L)

        assertEquals(123L, entity.createdAtEpochMillis)
        assertEquals(123L, entity.updatedAtEpochMillis)
    }
}
