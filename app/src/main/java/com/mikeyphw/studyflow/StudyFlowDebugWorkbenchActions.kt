package com.mikeyphw.studyflow

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticStore
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticTimelineReader
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticTimelineSnapshot
import com.mikeyphw.studyflow.feature.settings.DebugWorkbenchActionResult
import java.io.File
import java.util.concurrent.CancellationException
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
    private val prepareBundleShare: (() -> Intent?)? = null,
    private val launchShare: ((Intent) -> Unit)? = null,
) {
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
        val shareIntent = try {
            withContext(ioContext) {
                prepareBundleShare?.invoke()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return DebugWorkbenchActionResult.BUNDLE_UNAVAILABLE

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
                Intent.createChooser(shareIntent, chooserTitle)
            },
            launchShare = context::startActivity,
        )
    }
}
