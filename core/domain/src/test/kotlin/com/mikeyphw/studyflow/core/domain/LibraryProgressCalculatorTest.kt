package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryProgressCalculatorTest {
    @Test
    fun materialProgressClampsToTotalUnits() {
        assertEquals(100, LibraryProgressCalculator.materialProgressPercent(material(completed = 12, total = 10)))
    }

    @Test
    fun overallProgressAveragesActiveResources() {
        val progress = LibraryProgressCalculator.overallProgressPercent(
            materials = listOf(material(completed = 5, total = 10), material(completed = 0, total = 10)),
            videos = emptyList(),
        )
        assertEquals(25, progress)
    }

    private fun material(completed: Int, total: Int): StudyMaterial = StudyMaterial(
        id = StudyMaterialId("material-$completed"),
        studyPlanId = StudyPlanId("plan-1"),
        subjectId = null,
        topicId = null,
        type = StudyMaterialType.BOOK,
        title = "Book",
        description = "",
        sourceUri = null,
        author = null,
        totalUnits = total,
        completedUnits = completed,
        primary = false,
        favorite = false,
        archived = false,
        tags = emptyList(),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
}
