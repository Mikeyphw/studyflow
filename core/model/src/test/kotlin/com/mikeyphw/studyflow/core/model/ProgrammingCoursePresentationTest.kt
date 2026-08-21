package com.mikeyphw.studyflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgrammingCoursePresentationTest {
    @Test
    fun `programming courses expose programming labels`() {
        val labels = labelsForContentMode(StudyContentMode.PROGRAMMING_COURSE)

        assertEquals("Modules", labels?.moduleLabel)
        assertEquals("Exercises", labels?.exerciseLabel)
        assertEquals("Projects", labels?.projectLabel)
        assertEquals("Skill progress", labels?.progressLabel)
    }

    @Test
    fun `exam plans do not receive programming labels`() {
        assertNull(labelsForContentMode(StudyContentMode.EXAM_PLAN))
    }

    @Test
    fun `dashboard counts cannot be negative`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProgrammingCourseDashboardSummary(
                moduleCount = -1,
                exerciseCount = 0,
                projectCount = 0,
                weakConceptCount = 0,
                termuxRuntimeReady = false,
            )
        }
    }
}
