package com.mikeyphw.studyflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyContentModeTest {
    @Test
    fun `existing plans default to exam content mode`() {
        val plan = StudyPlan(
            id = StudyPlanId("existing"),
            name = "Existing",
            description = "Created before content modes",
            type = StudyType.PUBLIC_EXAMINATION,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = null,
            weeklyGoalHours = 10,
            progressPercent = 0,
            studiedMinutesThisWeek = 0,
            questionsAnswered = 0,
            averageAccuracyPercent = 0,
        )

        assertEquals(StudyContentMode.EXAM_PLAN, plan.contentMode)
    }

    @Test
    fun `programming mode exposes future capability flags without enabling runtime yet`() {
        val capabilities = StudyContentMode.PROGRAMMING_COURSE.capabilities

        assertTrue(capabilities.supportsCodeBlocks)
        assertFalse(capabilities.supportsTermuxRuntime)
        assertFalse(capabilities.supportsCodingExercises)
    }

    @Test
    fun `unknown stored content mode falls back to exam plan`() {
        assertEquals(StudyContentMode.EXAM_PLAN, contentModeOrDefault("UNKNOWN"))
    }
}
