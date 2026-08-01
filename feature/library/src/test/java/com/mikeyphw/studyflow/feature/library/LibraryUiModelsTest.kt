package com.mikeyphw.studyflow.feature.library

import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryUiModelsTest {
    @Test
    fun materialRowShowsProgress() {
        val row = StudyMaterial(
            id = StudyMaterialId("m1"),
            studyPlanId = StudyPlanId("p1"),
            subjectId = null,
            topicId = null,
            type = StudyMaterialType.PDF,
            title = "PDF",
            description = "",
            sourceUri = null,
            author = null,
            totalUnits = 4,
            completedUnits = 1,
            primary = false,
            favorite = true,
            archived = false,
            tags = emptyList(),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        ).asRowState()

        assertEquals(25, row.progressPercent)
        assertEquals(true, row.favorite)
    }
}
