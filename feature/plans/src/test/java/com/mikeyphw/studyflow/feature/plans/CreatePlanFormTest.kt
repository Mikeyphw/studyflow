package com.mikeyphw.studyflow.feature.plans

import com.mikeyphw.studyflow.core.model.StudyType
import org.junit.Assert.assertEquals
import org.junit.Test

class CreatePlanFormTest {
    @Test
    fun `form retains domain values without localized state`() {
        val form = CreatePlanForm(
            name = "TRT 2027",
            description = "Preparation",
            studyType = StudyType.PUBLIC_EXAMINATION,
            examDateIso = "2027-10-06",
            weeklyGoalHours = 20,
        )

        assertEquals(StudyType.PUBLIC_EXAMINATION, form.studyType)
    }
}
