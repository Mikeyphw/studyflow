package com.mikeyphw.studyflow.tv.library

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMaterialViewerKind
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMaterialViewerPresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFlowTvMaterialViewerOv20ResilienceTest {
    @Test
    fun nativePdfPageCountUnlocksNavigationWithoutProjectionHint() {
        val initial = StudyFlowTvMaterialViewerState(
            title = "PDF",
            presentation = StudyFlowTvMaterialViewerPresentation(
                kind = StudyFlowTvMaterialViewerKind.PDF,
                mimeType = "application/pdf",
                transferId = "pdf-transfer",
            ),
            textPages = listOf("fallback"),
        )
        val resolved = StudyFlowTvMaterialViewer.withResolvedPdfPageCount(initial, 4)
        val next = StudyFlowTvMaterialViewer.reduce(resolved, StudyFlowTvMaterialViewerAction.NEXT_PAGE)

        assertEquals(4, resolved.pageCount)
        assertEquals(1, next.pageIndex)
    }

    @Test
    fun mediaToggleKeepsCapturedPosition() {
        val initial = StudyFlowTvMaterialViewerState(
            title = "Video",
            presentation = StudyFlowTvMaterialViewerPresentation(
                kind = StudyFlowTvMaterialViewerKind.MEDIA,
                sourceUri = "https://example.invalid/video.mp4",
                mimeType = "video/mp4",
                durationMillis = 90_000L,
            ),
            textPages = listOf("video"),
        )

        val captured = StudyFlowTvMaterialViewer.captureMediaPosition(initial, 42_000L)
        val toggled = StudyFlowTvMaterialViewer.reduce(captured, StudyFlowTvMaterialViewerAction.PLAY_PAUSE)

        assertEquals(42_000L, toggled.mediaPositionMillis)
    }
}
