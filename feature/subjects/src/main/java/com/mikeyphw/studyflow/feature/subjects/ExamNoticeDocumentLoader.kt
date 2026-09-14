package com.mikeyphw.studyflow.feature.subjects

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import com.mikeyphw.studyflow.core.domain.SyllabusImportFormat
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LoadedExamNoticeDocument(
    val displayName: String,
    val text: String,
    val format: SyllabusImportFormat,
)

internal enum class ExamNoticeDocumentFailure {
    UNSUPPORTED_FORMAT,
    PDF_TEXT_REQUIRES_ANDROID_15,
    PDF_HAS_NO_TEXT_LAYER,
    DOCUMENT_TOO_LARGE,
    READ_FAILED,
}

internal sealed interface ExamNoticeDocumentLoadResult {
    data class Success(val document: LoadedExamNoticeDocument) : ExamNoticeDocumentLoadResult
    data class Failure(val reason: ExamNoticeDocumentFailure) : ExamNoticeDocumentLoadResult
}

internal object ExamNoticeDocumentLoader {
    private const val MaxTextBytes = 4 * 1024 * 1024

    suspend fun load(context: Context, uri: Uri): ExamNoticeDocumentLoadResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri) ?: "exam-notice"
        val mimeType = resolver.getType(uri).orEmpty().lowercase()
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

        when {
            mimeType == "application/pdf" || extension == "pdf" -> loadPdf(context, uri, displayName)
            mimeType == "text/csv" || extension == "csv" -> loadText(
                context = context,
                uri = uri,
                displayName = displayName,
                format = SyllabusImportFormat.CSV,
            )
            mimeType == "text/tab-separated-values" || extension == "tsv" -> loadText(
                context = context,
                uri = uri,
                displayName = displayName,
                format = SyllabusImportFormat.TSV,
            )
            mimeType.startsWith("text/") || extension in setOf("txt", "text") -> loadText(
                context = context,
                uri = uri,
                displayName = displayName,
                format = SyllabusImportFormat.STRUCTURED_TEXT,
            )
            else -> ExamNoticeDocumentLoadResult.Failure(ExamNoticeDocumentFailure.UNSUPPORTED_FORMAT)
        }
    }

    private fun loadText(
        context: Context,
        uri: Uri,
        displayName: String,
        format: SyllabusImportFormat,
    ): ExamNoticeDocumentLoadResult = try {
        val input = context.contentResolver.openInputStream(uri)
            ?: return ExamNoticeDocumentLoadResult.Failure(ExamNoticeDocumentFailure.READ_FAILED)
        val bytes = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MaxTextBytes) {
                    return ExamNoticeDocumentLoadResult.Failure(
                        ExamNoticeDocumentFailure.DOCUMENT_TOO_LARGE,
                    )
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        ExamNoticeDocumentLoadResult.Success(
            LoadedExamNoticeDocument(
                displayName = displayName,
                text = bytes.toString(Charsets.UTF_8),
                format = format,
            ),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ExamNoticeDocumentLoadResult.Failure(ExamNoticeDocumentFailure.READ_FAILED)
    }

    private fun loadPdf(
        context: Context,
        uri: Uri,
        displayName: String,
    ): ExamNoticeDocumentLoadResult {
        if (Build.VERSION.SDK_INT < 35) {
            return ExamNoticeDocumentLoadResult.Failure(
                ExamNoticeDocumentFailure.PDF_TEXT_REQUIRES_ANDROID_15,
            )
        }
        return loadPdfApi35(context, uri, displayName)
    }

    @RequiresApi(35)
    private fun loadPdfApi35(
        context: Context,
        uri: Uri,
        displayName: String,
    ): ExamNoticeDocumentLoadResult = try {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return ExamNoticeDocumentLoadResult.Failure(ExamNoticeDocumentFailure.READ_FAILED)
        if (descriptor.statSize > Ov15MaxPdfImportBytes) {
            descriptor.close()
            return ExamNoticeDocumentLoadResult.Failure(ExamNoticeDocumentFailure.DOCUMENT_TOO_LARGE)
        }
        val text = descriptor.use { fileDescriptor ->
            PdfRenderer(fileDescriptor).use { renderer ->
                buildString {
                    for (pageIndex in 0 until renderer.pageCount) {
                        renderer.openPage(pageIndex).use { page ->
                            val pageText = page.textContents
                                .joinToString(separator = "\n") { content -> content.text }
                                .trim()
                            if (pageText.isNotEmpty()) {
                                if (isNotEmpty()) append("\n\n")
                                append(pageText)
                                if (length > MaxTextBytes) {
                                    throw DocumentTooLargeException()
                                }
                            }
                        }
                    }
                }
            }
        }
        if (text.isBlank()) {
            ExamNoticeDocumentLoadResult.Failure(ExamNoticeDocumentFailure.PDF_HAS_NO_TEXT_LAYER)
        } else {
            ExamNoticeDocumentLoadResult.Success(
                LoadedExamNoticeDocument(
                    displayName = displayName,
                    text = text,
                    format = SyllabusImportFormat.STRUCTURED_TEXT,
                ),
            )
        }
    } catch (tooLarge: DocumentTooLargeException) {
        ExamNoticeDocumentLoadResult.Failure(ExamNoticeDocumentFailure.DOCUMENT_TOO_LARGE)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ExamNoticeDocumentLoadResult.Failure(ExamNoticeDocumentFailure.READ_FAILED)
    }

    private class DocumentTooLargeException : RuntimeException()

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) null else cursor.getString(index)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }
}
