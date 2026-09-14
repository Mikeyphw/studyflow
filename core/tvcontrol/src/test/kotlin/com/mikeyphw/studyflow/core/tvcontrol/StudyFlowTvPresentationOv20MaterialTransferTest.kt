package com.mikeyphw.studyflow.core.tvcontrol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StudyFlowTvPresentationOv20MaterialTransferTest {
    @Test
    fun phoneLocalMaterialUrisAreRejectedAtPresentationBoundary() {
        assertFailsWith<IllegalArgumentException> {
            StudyFlowTvMaterialViewerPresentation(
                kind = StudyFlowTvMaterialViewerKind.PDF,
                sourceUri = "content://phone/material.pdf",
                mimeType = "application/pdf",
            )
        }
    }

    @Test
    fun transferIdCreatesTvResolvableSourceUri() {
        val presentation = StudyFlowTvMaterialViewerPresentation(
            kind = StudyFlowTvMaterialViewerKind.PDF,
            mimeType = "application/pdf",
            transferId = "plan-p-material-m",
        )

        assertEquals("studyflow-transfer://material/plan-p-material-m", presentation.tvResolvableSourceUri)
        assertTrue(presentation.hasTransferableSource)
    }
}
