package com.mikeyphw.studyflow

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticStore
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticTimelineReader
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticTimelineSnapshot
import com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealPolicy
import com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealReport
import com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealStatus
import com.mikeyphw.studyflow.feature.settings.DebugWorkbenchActionResult
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes Debug Workbench storage and bundle work away from the UI thread.
 * The injectable bundle preparation and launcher boundaries keep failures testable.
 */
internal class StudyFlowDebugWorkbenchActions(
    private val store: DiagnosticStore,
    private val ioContext: CoroutineContext = Dispatchers.IO,
    private val prepareBundleShare: (() -> PreparedSupportBundle?)? = null,
    private val launchShare: ((Intent) -> Unit)? = null,
) {
    private val lastSealReport = AtomicReference(SupportBundleSealReport.notRun())

    fun supportBundleSealReport(): SupportBundleSealReport = lastSealReport.get()

    suspend fun loadTimeline(maxEntries: Int = 250): DiagnosticTimelineSnapshot = withContext(ioContext) {
        DiagnosticTimelineReader.read(
            jsonl = store.copySanitizedTimeline(),
            maxEntries = maxEntries,
        )
    }

    suspend fun copyTimeline(): String = withContext(ioContext) {
        store.copySanitizedTimeline()
    }

    suspend fun clearHistory(): Boolean = withContext(ioContext) {
        store.clear()
    }

    suspend fun exportAndShare(): DebugWorkbenchActionResult {
        val prepared = try {
            withContext(ioContext) {
                prepareBundleShare?.invoke()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: run {
            lastSealReport.set(
                SupportBundleSealReport(
                    status = SupportBundleSealStatus.FAILED,
                    message = com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealMessage.BUNDLE_MISSING,
                    checkedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            return DebugWorkbenchActionResult.BUNDLE_UNAVAILABLE
        }

        val shareIntent = prepared.intent
        lastSealReport.set(prepared.sealReport)
        if (!prepared.sealReport.passed) {
            return DebugWorkbenchActionResult.BUNDLE_SEAL_FAILED
        }

        return try {
            requireNotNull(launchShare).invoke(shareIntent)
            DebugWorkbenchActionResult.BUNDLE_READY
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ActivityNotFoundException) {
            DebugWorkbenchActionResult.BUNDLE_SHARE_FAILED
        } catch (_: SecurityException) {
            DebugWorkbenchActionResult.BUNDLE_SHARE_FAILED
        } catch (_: IllegalArgumentException) {
            DebugWorkbenchActionResult.BUNDLE_SHARE_FAILED
        } catch (_: Exception) {
            DebugWorkbenchActionResult.BUNDLE_SHARE_FAILED
        }
    }

    data class PreparedSupportBundle(
        val intent: Intent,
        val sealReport: SupportBundleSealReport,
    )

    companion object {
        fun android(
            context: Context,
            store: DiagnosticStore,
            clipboardLabel: String,
            chooserTitle: String,
        ): StudyFlowDebugWorkbenchActions = StudyFlowDebugWorkbenchActions(
            store = store,
            prepareBundleShare = prepare@{
                val directory = File(context.cacheDir, "support-bundles")
                if (!directory.mkdirs() && !directory.isDirectory) return@prepare null

                val destination = File(directory, "studyflow-debug-${System.currentTimeMillis()}.zip")
                val bundle = store.exportSupportBundle(
                    destinationZip = destination,
                    metadata = mapOf(
                        "version" to BuildConfig.VERSION_NAME,
                        "buildType" to BuildConfig.BUILD_TYPE,
                        "sdk" to android.os.Build.VERSION.SDK_INT.toString(),
                    ),
                ) ?: return@prepare null
                val sealReport = inspectSupportBundleFile(bundle)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    bundle,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(clipboardLabel, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                PreparedSupportBundle(
                    intent = Intent.createChooser(shareIntent, chooserTitle),
                    sealReport = sealReport,
                )
            },
            launchShare = context::startActivity,
        )
    }
}


private fun inspectSupportBundleFile(bundle: File): SupportBundleSealReport = try {
    ZipFile(bundle).use { zip ->
        SupportBundleSealPolicy.inspect(
            entryNames = zip.entries().asSequence().map { entry -> entry.name }.toList(),
            checkedAtEpochMillis = System.currentTimeMillis(),
            checksums = zip.checksumManifest(),
            actualChecksums = zip.actualPayloadChecksums(),
        )
    }
} catch (_: Exception) {
    SupportBundleSealReport(
        status = SupportBundleSealStatus.FAILED,
        message = com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealMessage.BUNDLE_MISSING,
        checkedAtEpochMillis = System.currentTimeMillis(),
    )
}


private fun ZipFile.checksumManifest(): Map<String, String>? {
    val entry = getEntry("checksums.sha256") ?: return null
    return getInputStream(entry).bufferedReader().useLines { lines ->
        lines.filter(String::isNotBlank).associate { line ->
            val digest = line.substringBefore("  ")
            val name = line.substringAfter("  ", missingDelimiterValue = "")
            name to digest
        }
    }
}

private fun ZipFile.actualPayloadChecksums(): Map<String, String> = entries().asSequence()
    .filter { entry -> !entry.isDirectory && entry.name != "checksums.sha256" }
    .associate { entry ->
        val bytes = getInputStream(entry).use { input -> input.readBytes() }
        entry.name to bytes.sha256Hex()
    }

private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte) }
