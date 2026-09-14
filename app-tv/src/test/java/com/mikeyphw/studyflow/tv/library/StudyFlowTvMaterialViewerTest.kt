package com.mikeyphw.studyflow.tv.library

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMaterialViewerKind
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMaterialViewerPresentation
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvMaterialViewerTest {
    @Test
    fun textViewerPaginatesAndClampsNavigation() {
        val text = (1..900).joinToString(" ") { "word$it" }
        val reference = reference(StudyFlowTvMaterialViewerPresentation(
            kind = StudyFlowTvMaterialViewerKind.TEXT,
            mimeType = "text/plain",
            inlineText = text,
        ))
        val opened = requireNotNull(StudyFlowTvMaterialViewer.open(reference))
        assertTrue(opened.textPages.size > 1)
        val previous = StudyFlowTvMaterialViewer.reduce(opened, StudyFlowTvMaterialViewerAction.PREVIOUS_PAGE)
        assertEquals(0, previous.pageIndex)
        val next = StudyFlowTvMaterialViewer.reduce(opened, StudyFlowTvMaterialViewerAction.NEXT_PAGE)
        assertEquals(1, next.pageIndex)
    }

    @Test
    fun pdfNavigationUsesPdfPageCountRatherThanTextPageCount() {
        val pdf = requireNotNull(
            StudyFlowTvMaterialViewer.open(
                reference(
                    StudyFlowTvMaterialViewerPresentation(
                        kind = StudyFlowTvMaterialViewerKind.PDF,
                        sourceUri = "studyflow-transfer://material/pdf",
                        mimeType = "application/pdf",
                        pageCountHint = 3,
                    ),
                ),
            ),
        )

        val secondPage = StudyFlowTvMaterialViewer.reduce(
            pdf,
            StudyFlowTvMaterialViewerAction.NEXT_PAGE,
        )
        assertEquals(1, secondPage.pageIndex)
        assertEquals(3, secondPage.pageCount)
    }

    @Test
    fun imagePdfAndMediaExposeOnlyRelevantControls() {
        val image = requireNotNull(StudyFlowTvMaterialViewer.open(reference(
            StudyFlowTvMaterialViewerPresentation(StudyFlowTvMaterialViewerKind.IMAGE, "https://example.invalid/image.png", "image/png"),
        )))
        assertTrue(StudyFlowTvMaterialViewerAction.ZOOM_IN in StudyFlowTvMaterialViewer.availableActions(image))
        assertFalse(StudyFlowTvMaterialViewerAction.PLAY_PAUSE in StudyFlowTvMaterialViewer.availableActions(image))

        val media = requireNotNull(StudyFlowTvMaterialViewer.open(reference(
            StudyFlowTvMaterialViewerPresentation(
                kind = StudyFlowTvMaterialViewerKind.MEDIA,
                sourceUri = "https://example.invalid/video.mp4",
                mimeType = "video/mp4",
                durationMillis = 60_000L,
            ),
        )))
        val playing = StudyFlowTvMaterialViewer.reduce(media, StudyFlowTvMaterialViewerAction.PLAY_PAUSE)
        val seeked = StudyFlowTvMaterialViewer.reduce(playing, StudyFlowTvMaterialViewerAction.SEEK_FORWARD)
        assertTrue(playing.playing)
        assertEquals(10_000L, seeked.mediaPositionMillis)
    }

    private fun reference(viewer: StudyFlowTvMaterialViewerPresentation) = StudyFlowTvLibraryReference(
        id = StudyFlowTvLibraryReferenceId.CONSTITUTION_ARTICLE_FIVE,
        sourceId = "material-1",
        title = "Material",
        subject = "Subject",
        sourceLabel = "Source",
        excerpt = "Fallback excerpt",
        kind = StudyFlowTvLibraryReferenceKind.SUMMARY,
        relatedDestination = StudyFlowTvDestination.LIBRARY,
        viewer = viewer,
        row = 0,
        column = 1,
    )
}
