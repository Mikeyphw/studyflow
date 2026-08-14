package com.mikeyphw.studyflow

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.mikeyphw.studyflow.feature.workspace.StudyExamReportDocument
import com.mikeyphw.studyflow.feature.workspace.StudyExamReportFormat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StudyFlowExamReportExporter(
    private val context: Context,
) {
    suspend fun exportAndShare(
        document: StudyExamReportDocument,
        format: StudyExamReportFormat,
        chooserTitle: String,
    ): Boolean {
        val output = withContext(Dispatchers.IO) {
            runCatching {
                val directory = File(context.cacheDir, REPORT_DIRECTORY).apply { mkdirs() }
                pruneCache(directory, System.currentTimeMillis())
                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val extension = when (format) {
                    StudyExamReportFormat.HTML -> "html"
                    StudyExamReportFormat.PDF -> "pdf"
                }
                File(directory, "${safeFileStem(document.fileStem)}-$timestamp.$extension").also { file ->
                    when (format) {
                        StudyExamReportFormat.HTML -> file.writeText(renderHtml(document), Charsets.UTF_8)
                        StudyExamReportFormat.PDF -> writePdf(document, file)
                    }
                }
            }.getOrNull()
        } ?: return false

        return withContext(Dispatchers.Main) {
            runCatching { share(output, format, chooserTitle) }.isSuccess
        }
    }

    private fun share(file: File, format: StudyExamReportFormat, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        val mimeType = when (format) {
            StudyExamReportFormat.HTML -> "text/html"
            StudyExamReportFormat.PDF -> "application/pdf"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun renderHtml(document: StudyExamReportDocument): String = buildString {
        appendLine("<!doctype html>")
        appendLine("<html><head><meta charset=\"utf-8\">")
        appendLine("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        appendLine("<title>${escapeHtml(document.title)}</title>")
        appendLine("<style>")
        appendLine("body{font-family:sans-serif;max-width:780px;margin:32px auto;padding:0 20px;line-height:1.45}")
        appendLine("h1{margin-bottom:4px}h2{margin-top:28px;border-bottom:1px solid #bbb;padding-bottom:6px}")
        appendLine("table{width:100%;border-collapse:collapse}td{padding:7px 4px;vertical-align:top}")
        appendLine("td:first-child{font-weight:600;width:44%}tr+tr td{border-top:1px solid #ddd}")
        appendLine("footer{margin-top:32px;font-size:.9em;color:#555}")
        appendLine("</style></head><body>")
        appendLine("<h1>${escapeHtml(document.title)}</h1>")
        appendLine("<p>${escapeHtml(document.generatedAtLabel)}</p>")
        document.sections.forEach { section ->
            appendLine("<h2>${escapeHtml(section.title)}</h2>")
            appendLine("<table>")
            section.rows.forEach { row ->
                appendLine("<tr><td>${escapeHtml(row.label)}</td><td>${escapeHtml(row.value)}</td></tr>")
            }
            appendLine("</table>")
        }
        appendLine("<footer>${escapeHtml(document.privacyNotice)}</footer>")
        appendLine("</body></html>")
    }

    private fun writePdf(document: StudyExamReportDocument, output: File) {
        val pdf = PdfDocument()
        val writer = PdfReportWriter(pdf)
        writer.heading(document.title, 20f)
        writer.body(document.generatedAtLabel)
        document.sections.forEach { section ->
            writer.gap(8f)
            writer.heading(section.title, 15f)
            section.rows.forEach { row ->
                writer.body("${row.label}: ${row.value}")
            }
        }
        writer.gap(10f)
        writer.body(document.privacyNotice)
        writer.finish()
        FileOutputStream(output).use(pdf::writeTo)
        pdf.close()
    }

    private fun pruneCache(directory: File, nowEpochMillis: Long) {
        val files = directory.listFiles()?.filter(File::isFile).orEmpty()
        files.filter { nowEpochMillis - it.lastModified() > REPORT_MAX_AGE_MILLIS }
            .forEach { runCatching { it.delete() } }
        directory.listFiles()?.filter(File::isFile).orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(REPORT_MAX_FILES)
            .forEach { runCatching { it.delete() } }
    }

    private fun safeFileStem(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(64)
        .ifBlank { "studyflow-exam-report" }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private class PdfReportWriter(
        private val pdf: PdfDocument,
    ) {
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f }
        private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var y = TOP_MARGIN

        fun heading(text: String, size: Float) {
            ensurePage(size + LINE_GAP)
            headingPaint.textSize = size
            drawWrapped(text, headingPaint, size + 5f)
        }

        fun body(text: String) {
            drawWrapped(text, bodyPaint, BODY_LINE_HEIGHT)
        }

        fun gap(points: Float) {
            ensurePage(points)
            y += points
        }

        fun finish() {
            page?.let(pdf::finishPage)
            page = null
        }

        private fun drawWrapped(text: String, paint: Paint, lineHeight: Float) {
            wrap(text, paint).forEach { line ->
                ensurePage(lineHeight)
                page?.canvas?.drawText(line, LEFT_MARGIN, y, paint)
                y += lineHeight
            }
        }

        private fun wrap(text: String, paint: Paint): List<String> {
            if (text.isBlank()) return listOf("")
            val lines = mutableListOf<String>()
            var current = ""
            text.split(Regex("\\s+")).forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= TEXT_WIDTH || current.isEmpty()) {
                    current = candidate
                } else {
                    lines += current
                    current = word
                }
            }
            if (current.isNotEmpty()) lines += current
            return lines
        }

        private fun ensurePage(requiredHeight: Float) {
            if (page == null || y + requiredHeight > PAGE_HEIGHT - BOTTOM_MARGIN) {
                page?.let(pdf::finishPage)
                pageNumber += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                y = TOP_MARGIN
            }
        }

        private companion object {
            const val PAGE_WIDTH = 595
            const val PAGE_HEIGHT = 842
            const val LEFT_MARGIN = 40f
            const val TOP_MARGIN = 48f
            const val BOTTOM_MARGIN = 48f
            const val TEXT_WIDTH = PAGE_WIDTH - LEFT_MARGIN * 2
            const val BODY_LINE_HEIGHT = 15f
            const val LINE_GAP = 5f
        }
    }

    private companion object {
        const val REPORT_DIRECTORY = "exam-reports"
        const val REPORT_MAX_FILES = 5
        const val REPORT_MAX_AGE_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
