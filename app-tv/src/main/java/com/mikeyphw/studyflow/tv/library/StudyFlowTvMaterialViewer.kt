package com.mikeyphw.studyflow.tv.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import android.widget.VideoView
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMaterialViewerKind
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMaterialViewerPresentation

object StudyFlowTvMaterialViewerContract {
    const val overlay = "TVOV27"
    const val nativeTextPagination = true
    const val nativeImageZoom = true
    const val nativePdfRenderer = true
    const val nativeMediaPlayback = true
    const val destructiveFileOperationsForbidden = true
    const val textPageChars = 3_600
    const val minZoomPercent = 50
    const val maxZoomPercent = 300
    const val zoomStepPercent = 25
    const val seekStepMillis = 10_000L
}

enum class StudyFlowTvMaterialViewerAction {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    ZOOM_OUT,
    ZOOM_IN,
    PLAY_PAUSE,
    SEEK_BACK,
    SEEK_FORWARD,
    CLOSE,
}

data class StudyFlowTvMaterialViewerState(
    val title: String,
    val presentation: StudyFlowTvMaterialViewerPresentation,
    val textPages: List<String>,
    val pageIndex: Int = 0,
    val zoomPercent: Int = 100,
    val playing: Boolean = false,
    val mediaPositionMillis: Long = presentation.playbackPositionMillis,
) {
    val pageCount: Int = when (presentation.kind) {
        StudyFlowTvMaterialViewerKind.TEXT -> textPages.size
        StudyFlowTvMaterialViewerKind.PDF -> presentation.pageCountHint ?: 1
        else -> 1
    }

    init {
        require(title.isNotBlank())
        require(textPages.isNotEmpty())
        require(pageIndex in 0 until pageCount)
        require(
            zoomPercent in
                StudyFlowTvMaterialViewerContract.minZoomPercent..StudyFlowTvMaterialViewerContract.maxZoomPercent,
        )
        require(mediaPositionMillis >= 0L)
    }
}

object StudyFlowTvMaterialViewer {
    fun open(reference: StudyFlowTvLibraryReference): StudyFlowTvMaterialViewerState? {
        val viewer = reference.viewer ?: return null
        val pages = paginate(viewer.inlineText ?: reference.excerpt)
        return StudyFlowTvMaterialViewerState(
            title = reference.title,
            presentation = viewer,
            textPages = pages,
            pageIndex = 0,
        )
    }

    fun availableActions(state: StudyFlowTvMaterialViewerState): List<StudyFlowTvMaterialViewerAction> = buildList {
        if (state.presentation.kind == StudyFlowTvMaterialViewerKind.TEXT ||
            state.presentation.kind == StudyFlowTvMaterialViewerKind.PDF
        ) {
            add(StudyFlowTvMaterialViewerAction.PREVIOUS_PAGE)
            add(StudyFlowTvMaterialViewerAction.NEXT_PAGE)
        }
        if (state.presentation.kind == StudyFlowTvMaterialViewerKind.IMAGE ||
            state.presentation.kind == StudyFlowTvMaterialViewerKind.PDF
        ) {
            add(StudyFlowTvMaterialViewerAction.ZOOM_OUT)
            add(StudyFlowTvMaterialViewerAction.ZOOM_IN)
        }
        if (state.presentation.kind == StudyFlowTvMaterialViewerKind.MEDIA) {
            add(StudyFlowTvMaterialViewerAction.SEEK_BACK)
            add(StudyFlowTvMaterialViewerAction.PLAY_PAUSE)
            add(StudyFlowTvMaterialViewerAction.SEEK_FORWARD)
        }
        add(StudyFlowTvMaterialViewerAction.CLOSE)
    }

    fun reduce(
        state: StudyFlowTvMaterialViewerState,
        action: StudyFlowTvMaterialViewerAction,
    ): StudyFlowTvMaterialViewerState = when (action) {
        StudyFlowTvMaterialViewerAction.PREVIOUS_PAGE -> state.copy(pageIndex = (state.pageIndex - 1).coerceAtLeast(0))
        StudyFlowTvMaterialViewerAction.NEXT_PAGE -> state.copy(
            pageIndex = (state.pageIndex + 1).coerceAtMost(state.pageCount - 1),
        )
        StudyFlowTvMaterialViewerAction.ZOOM_OUT -> state.copy(
            zoomPercent = (state.zoomPercent - StudyFlowTvMaterialViewerContract.zoomStepPercent)
                .coerceAtLeast(StudyFlowTvMaterialViewerContract.minZoomPercent),
        )
        StudyFlowTvMaterialViewerAction.ZOOM_IN -> state.copy(
            zoomPercent = (state.zoomPercent + StudyFlowTvMaterialViewerContract.zoomStepPercent)
                .coerceAtMost(StudyFlowTvMaterialViewerContract.maxZoomPercent),
        )
        StudyFlowTvMaterialViewerAction.PLAY_PAUSE -> state.copy(playing = !state.playing)
        StudyFlowTvMaterialViewerAction.SEEK_BACK -> state.copy(
            mediaPositionMillis =
                (state.mediaPositionMillis - StudyFlowTvMaterialViewerContract.seekStepMillis)
                    .coerceAtLeast(0L),
        )
        StudyFlowTvMaterialViewerAction.SEEK_FORWARD -> state.copy(
            mediaPositionMillis = (state.mediaPositionMillis + StudyFlowTvMaterialViewerContract.seekStepMillis)
                .coerceAtMost(state.presentation.durationMillis ?: Long.MAX_VALUE),
        )
        StudyFlowTvMaterialViewerAction.CLOSE -> state
    }

    fun paginate(text: String): List<String> {
        if (text.isEmpty()) return listOf("")
        val limit = StudyFlowTvMaterialViewerContract.textPageChars
        val pages = mutableListOf<String>()
        var cursor = 0
        while (cursor < text.length) {
            var end = (cursor + limit).coerceAtMost(text.length)
            if (end < text.length) {
                val boundary = maxOf(text.lastIndexOf('\n', end), text.lastIndexOf(' ', end))
                if (boundary >= cursor + limit * 3 / 5) end = boundary + 1
            }
            pages += text.substring(cursor, end).trimEnd()
            cursor = end
        }
        return pages.ifEmpty { listOf("") }
    }
}

/** Android-framework renderer: no Compose/Leanback/media SDK dependency is introduced. */
object StudyFlowTvNativeMaterialViewer {
    fun bindImage(view: ImageView, sourceUri: String, zoomPercent: Int) {
        view.setImageURI(Uri.parse(sourceUri))
        val scale = zoomPercent / 100f
        view.scaleX = scale
        view.scaleY = scale
        view.adjustViewBounds = true
    }

    fun bindMedia(view: VideoView, state: StudyFlowTvMaterialViewerState) {
        val source = state.presentation.sourceUri ?: return
        view.setVideoURI(Uri.parse(source))
        view.setOnPreparedListener { player ->
            player.seekTo(state.mediaPositionMillis.toInt().coerceAtLeast(0))
            if (state.playing) player.start()
        }
    }

    fun renderPdfPage(
        context: Context,
        sourceUri: String,
        pageIndex: Int,
        zoomPercent: Int,
    ): Bitmap? {
        val descriptor: ParcelFileDescriptor = context.contentResolver
            .openFileDescriptor(Uri.parse(sourceUri), "r") ?: return null
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) return null
                val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safeIndex).use { page ->
                    val scale = (zoomPercent / 100f).coerceIn(0.5f, 3f)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }
}
