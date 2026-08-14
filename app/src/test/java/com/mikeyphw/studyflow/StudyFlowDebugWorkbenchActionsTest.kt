package com.mikeyphw.studyflow

import android.content.ActivityNotFoundException
import android.content.Intent
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticEvent
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticStore
import com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealPolicy
import com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealReport
import com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealStatus
import com.mikeyphw.studyflow.feature.settings.DebugWorkbenchActionResult
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowDebugWorkbenchActionsTest {
    @Test
    fun `timeline copy and clear execute on injected io context`() {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "studyflow-debug-io")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val store = RecordingStore()
            val actions = StudyFlowDebugWorkbenchActions(
                store = store,
                ioContext = dispatcher,
            )

            runBlocking {
                actions.loadTimeline()
                actions.copyTimeline()
                actions.clearHistory()
            }

            assertEquals(2, store.copyThreads.size)
            assertEquals(
                listOf("studyflow-debug-io", "studyflow-debug-io"),
                store.copyThreads.map(::baseThreadName),
            )
            assertEquals(listOf("studyflow-debug-io"), store.clearThreads.map(::baseThreadName))
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `bundle preparation runs on io and launcher stays on caller`() {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "studyflow-bundle-io")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            var preparationThread = ""
            var launchThread = ""
            val actions = StudyFlowDebugWorkbenchActions(
                store = RecordingStore(),
                ioContext = dispatcher,
                prepareBundleShare = {
                    preparationThread = Thread.currentThread().name
                    preparedShare()
                },
                launchShare = {
                    launchThread = Thread.currentThread().name
                },
            )

            val result = runBlocking { actions.exportAndShare() }

            assertEquals(DebugWorkbenchActionResult.BUNDLE_READY, result)
            assertEquals("studyflow-bundle-io", baseThreadName(preparationThread))
            assertTrue(launchThread.isNotBlank())
            assertTrue(baseThreadName(launchThread) != "studyflow-bundle-io")
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `bundle preparation failure becomes unavailable result`() = runBlocking {
        val actions = StudyFlowDebugWorkbenchActions(
            store = RecordingStore(),
            prepareBundleShare = { throw IllegalStateException("private path") },
            launchShare = { error("must not launch") },
        )

        assertEquals(
            DebugWorkbenchActionResult.BUNDLE_UNAVAILABLE,
            actions.exportAndShare(),
        )
    }

    @Test
    fun `share launcher failure becomes explicit result`() = runBlocking {
        val actions = StudyFlowDebugWorkbenchActions(
            store = RecordingStore(),
            prepareBundleShare = { preparedShare() },
            launchShare = { throw ActivityNotFoundException() },
        )

        assertEquals(
            DebugWorkbenchActionResult.BUNDLE_SHARE_FAILED,
            actions.exportAndShare(),
        )
    }


    @Test
    fun `failed support bundle seal prevents share launch`() = runBlocking {
        var launched = false
        val actions = StudyFlowDebugWorkbenchActions(
            store = RecordingStore(),
            prepareBundleShare = {
                StudyFlowDebugWorkbenchActions.PreparedSupportBundle(
                    intent = Intent("share"),
                    sealReport = SupportBundleSealReport(
                        status = SupportBundleSealStatus.FAILED,
                        checkedAtEpochMillis = 1L,
                    ),
                )
            },
            launchShare = { launched = true },
        )

        assertEquals(DebugWorkbenchActionResult.BUNDLE_SEAL_FAILED, actions.exportAndShare())
        assertEquals(SupportBundleSealStatus.FAILED, actions.supportBundleSealSnapshot().status)
        assertTrue(!launched)
    }

    @Test
    fun `unexpected recoverable share failure becomes explicit result`() = runBlocking {
        val actions = StudyFlowDebugWorkbenchActions(
            store = RecordingStore(),
            prepareBundleShare = { preparedShare() },
            launchShare = { throw IllegalStateException("platform failure") },
        )

        assertEquals(
            DebugWorkbenchActionResult.BUNDLE_SHARE_FAILED,
            actions.exportAndShare(),
        )
    }

    @Test(expected = CancellationException::class)
    fun `bundle cancellation is never converted into ordinary failure`(): Unit {
        runBlocking {
            val actions = StudyFlowDebugWorkbenchActions(
                store = RecordingStore(),
                prepareBundleShare = { throw CancellationException("cancel") },
                launchShare = { error("must not launch") },
            )
            actions.exportAndShare()
        }
    }

    private fun baseThreadName(name: String): String = name.substringBefore(" @coroutine#")


    private fun preparedShare(): StudyFlowDebugWorkbenchActions.PreparedSupportBundle =
        StudyFlowDebugWorkbenchActions.PreparedSupportBundle(
            intent = Intent("share"),
            sealReport = SupportBundleSealPolicy.inspect(
                entryNames = listOf(
                    "events/current.jsonl",
                    "environment.json",
                    "manifest.json",
                    "redaction-report.txt",
                    "checksums.sha256",
                ),
                checkedAtEpochMillis = 1L,
            ),
        )

    private class RecordingStore : DiagnosticStore {
        override val enabled: Boolean = true
        override val droppedEventCount: Long = 0L
        override val writerFailureFingerprint: String? = null
        val copyThreads = mutableListOf<String>()
        val clearThreads = mutableListOf<String>()

        override fun record(event: DiagnosticEvent) = Unit

        override fun copySanitizedTimeline(maxChars: Int): String {
            copyThreads += Thread.currentThread().name
            return ""
        }

        override fun clear(): Boolean {
            clearThreads += Thread.currentThread().name
            return true
        }

        override fun exportSupportBundle(
            destinationZip: File,
            metadata: Map<String, String>,
        ): File? = destinationZip
    }
}
