package com.mikeyphw.studyflow.core.diagnostics

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingJsonlDiagnosticRecorderTest {
    @Test
    fun noOpRecorderDoesNotManufactureSupportEvidence() {
        val root = createTempDirectory("studyflow-diagnostics-noop").toFile()
        try {
            val destination = File(root, "support.zip")

            assertEquals(
                null,
                NoOpDiagnosticRecorder.exportSupportBundle(destination),
            )
            assertFalse(destination.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun disabledPolicyCreatesNoDiagnosticFiles() {
        val root = createTempDirectory("studyflow-diagnostics-disabled").toFile()
        try {
            RollingJsonlDiagnosticRecorder(
                rootDirectory = File(root, "diagnostics"),
                policy = DiagnosticSessionPolicy.disabled(),
            ).use { recorder ->
                recorder.record(
                    area = DiagnosticArea.APP_LIFECYCLE,
                    action = "application-created",
                    outcome = DiagnosticOutcome.SUCCEEDED,
                )
                assertTrue(recorder.flush())
            }

            assertFalse(File(root, "diagnostics").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun concurrentWritersProduceCompleteSanitizedJsonLines() {
        val root = createTempDirectory("studyflow-diagnostics-concurrent").toFile()
        try {
            RollingJsonlDiagnosticRecorder(
                rootDirectory = root,
                policy = DiagnosticSessionPolicy.debug("unit-session"),
                maxSessionBytes = 512L * 1024L,
                queueCapacity = 512,
            ).use { recorder ->
                val start = CountDownLatch(1)
                val workers = List(4) { worker ->
                    thread(start = true) {
                        start.await()
                        repeat(40) { index ->
                            recorder.record(
                                area = DiagnosticArea.REVIEWS,
                                action = "worker-$worker-event-$index",
                                outcome = DiagnosticOutcome.SUCCEEDED,
                                safeDetails = mapOf(
                                    "planId" to "plan-$worker-$index",
                                    "note" to "private-note-$index",
                                ),
                            )
                        }
                    }
                }
                start.countDown()
                workers.forEach(Thread::join)
                assertTrue(recorder.flush())

                val lines = File(root, "current.jsonl").readLines()
                assertEquals(160, lines.size)
                assertTrue(lines.all { line -> line.startsWith("{") && line.endsWith("}") })
                assertTrue(lines.all { line -> "private-note" !in line })
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun queueOverflowIsCountedWithoutBlockingCallers() {
        val root = createTempDirectory("studyflow-diagnostics-overflow").toFile()
        val writerEntered = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val recorder = RollingJsonlDiagnosticRecorder.createForTesting(
            rootDirectory = root,
            policy = DiagnosticSessionPolicy.debug("overflow-session"),
            maxSessionBytes = 64L * 1024L,
            retainedSessions = 1,
            queueCapacity = 1,
            clock = { 100L },
            beforeWriteForTesting = {
                writerEntered.countDown()
                releaseWriter.await()
            },
        )
        try {
            recorder.record(
                area = DiagnosticArea.VALIDATION,
                action = "overflow-first",
                outcome = DiagnosticOutcome.STARTED,
            )
            assertTrue(writerEntered.await(2L, TimeUnit.SECONDS))
            recorder.record(
                area = DiagnosticArea.VALIDATION,
                action = "overflow-buffered",
                outcome = DiagnosticOutcome.STARTED,
            )
            repeat(20) { index ->
                recorder.record(
                    area = DiagnosticArea.VALIDATION,
                    action = "overflow-drop-$index",
                    outcome = DiagnosticOutcome.STARTED,
                )
            }

            assertTrue(recorder.droppedEventCount > 0L)
            releaseWriter.countDown()
            assertTrue(recorder.flush(5_000L))
        } finally {
            releaseWriter.countDown()
            recorder.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun rotationRetainsConfiguredHistoryDespiteSameMillisecondClock() {
        val root = createTempDirectory("studyflow-diagnostics-rotation").toFile()
        try {
            RollingJsonlDiagnosticRecorder(
                rootDirectory = root,
                policy = DiagnosticSessionPolicy.debug("rotation-session"),
                maxSessionBytes = 420L,
                retainedSessions = 5,
                queueCapacity = 128,
                clock = { 100L },
            ).use { recorder ->
                repeat(50) { index ->
                    recorder.record(
                        area = DiagnosticArea.SCHEDULE,
                        action = "schedule-probe-$index",
                        outcome = DiagnosticOutcome.SUCCEEDED,
                        safeDetails = mapOf("status" to "complete-$index"),
                    )
                }
                assertTrue(recorder.flush())
            }

            val retained = File(root, "sessions")
                .listFiles { candidate -> candidate.extension == "jsonl" }
                .orEmpty()
            assertEquals(5, retained.size)
            assertEquals(5, retained.map(File::getName).toSet().size)
            assertTrue(retained.all { file -> file.name.startsWith("session-100-") })
            assertTrue(File(root, "current.jsonl").length() <= 420L)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun newRecorderRotatesPreviousProcessSessionAndTimelineReadsOldestFirst() {
        val root = createTempDirectory("studyflow-diagnostics-session-boundary").toFile()
        try {
            RollingJsonlDiagnosticRecorder(
                rootDirectory = root,
                policy = DiagnosticSessionPolicy.debug("session-one"),
            ).use { recorder ->
                recorder.record(DiagnosticArea.APP_LIFECYCLE, "old-session-marker", DiagnosticOutcome.SUCCEEDED)
                assertTrue(recorder.flush())
            }

            RollingJsonlDiagnosticRecorder(
                rootDirectory = root,
                policy = DiagnosticSessionPolicy.debug("session-two"),
            ).use { recorder ->
                recorder.record(DiagnosticArea.APP_LIFECYCLE, "new-session-marker", DiagnosticOutcome.SUCCEEDED)
                assertTrue(recorder.flush())
                val timeline = recorder.copySanitizedTimeline(64 * 1024)
                assertTrue(timeline.indexOf("old-session-marker") >= 0)
                assertTrue(timeline.indexOf("new-session-marker") > timeline.indexOf("old-session-marker"))
                assertEquals(1, File(root, "sessions").listFiles().orEmpty().size)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun timelineTailIgnoresIncompleteFinalJsonlLine() {
        val root = createTempDirectory("studyflow-diagnostics-incomplete-tail").toFile()
        try {
            RollingJsonlDiagnosticRecorder(
                rootDirectory = root,
                policy = DiagnosticSessionPolicy.debug("tail-session"),
            ).use { recorder ->
                recorder.record(DiagnosticArea.VALIDATION, "complete-marker", DiagnosticOutcome.SUCCEEDED)
                assertTrue(recorder.flush())
                File(root, "current.jsonl").appendText("{\"partial\":true")
                val timeline = recorder.copySanitizedTimeline(64 * 1024)
                assertTrue("complete-marker" in timeline)
                assertFalse("partial" in timeline)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun supportBundleExportsRetainedAndCurrentSegmentsInOrderWithGeneratedRedactionReport() {
        val root = createTempDirectory("studyflow-diagnostics-retained-bundle").toFile()
        try {
            RollingJsonlDiagnosticRecorder(
                rootDirectory = root,
                policy = DiagnosticSessionPolicy.debug("retained-one"),
            ).use { recorder ->
                recorder.record(DiagnosticArea.SEARCH_RECOMMENDATIONS, "retained-marker", DiagnosticOutcome.SUCCEEDED)
                assertTrue(recorder.flush())
            }
            val bundle = File(root, "support.zip")
            RollingJsonlDiagnosticRecorder(
                rootDirectory = root,
                policy = DiagnosticSessionPolicy.debug("retained-two"),
            ).use { recorder ->
                recorder.record(DiagnosticArea.SEARCH_RECOMMENDATIONS, "current-marker", DiagnosticOutcome.SUCCEEDED)
                recorder.exportSupportBundle(bundle)
            }

            ZipFile(bundle).use { zip ->
                assertTrue(zip.getEntry("events/retained-000.jsonl") != null)
                assertTrue(zip.getEntry("events/current.jsonl") != null)
                assertTrue("retained-marker" in zip.readText("events/retained-000.jsonl"))
                assertTrue("current-marker" in zip.readText("events/current.jsonl"))
                val report = zip.readText("redaction-report.txt")
                assertTrue("status=PASS" in report)
                assertTrue("timelineSegments=2" in report)
                assertTrue("privacyViolations=0" in report)
                assertTrue("schemaViolations=0" in report)
                assertTrue("automaticUpload=false" in report)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun writerFailureCanStillExportAHealthManifest() {
        val root = createTempDirectory("studyflow-diagnostics-writer-failure").toFile()
        val invalidRoot = File(root, "not-a-directory").apply { writeText("occupied") }
        val destination = File(root, "support.zip")
        val recorder = RollingJsonlDiagnosticRecorder(
            rootDirectory = invalidRoot,
            policy = DiagnosticSessionPolicy.debug("writer-failure-session"),
        )
        try {
            recorder.record(
                area = DiagnosticArea.VALIDATION,
                action = "writer-failure-probe",
                outcome = DiagnosticOutcome.STARTED,
            )
            assertTrue(waitUntil { recorder.writerFailureFingerprint != null })

            recorder.exportSupportBundle(destination)

            ZipFile(destination).use { zip ->
                val manifest = zip.readText("manifest.json")
                assertTrue(manifest.contains("\"flushCompleted\":\"false\""))
                assertTrue(manifest.contains("\"timelineMayBeIncomplete\":\"true\""))
                assertTrue(
                    Regex("\\\"writerFailureFingerprint\\\":\\\"[0-9a-f]{16}\\\"")
                        .containsMatchIn(manifest),
                )
                assertFalse(zip.entries().asSequence().any { entry -> entry.name == "events/current.jsonl" })
            }
        } finally {
            recorder.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun supportBundleUsesFixedAllowlistRedactionAndValidChecksums() {
        val root = createTempDirectory("studyflow-diagnostics-bundle").toFile()
        try {
            val bundle = File(root, "support.zip")
            RollingJsonlDiagnosticRecorder(
                rootDirectory = File(root, "diagnostics"),
                policy = DiagnosticSessionPolicy.debug("bundle-session"),
            ).use { recorder ->
                recorder.record(
                    area = DiagnosticArea.BACKUP_RESTORE,
                    action = "backup-dry-run",
                    outcome = DiagnosticOutcome.SUCCEEDED,
                    safeDetails = mapOf(
                        "sourceUri" to "content://provider/private/backup.zip",
                        "reason" to "Algebra finals plan for Mike",
                    ),
                )
                recorder.exportSupportBundle(
                    destinationZip = bundle,
                    metadata = mapOf(
                        "Authorization" to "Bearer abcdefghijklmnop",
                        "version" to "1.2.3",
                    ),
                )
            }

            ZipFile(bundle).use { zip ->
                val names = zip.entries().asSequence().map { entry -> entry.name }.toSet()
                assertEquals(
                    setOf(
                        "events/current.jsonl",
                        "environment.json",
                        "manifest.json",
                        "redaction-report.txt",
                        "checksums.sha256",
                    ),
                    names,
                )
                val environment = zip.readText("environment.json")
                assertTrue(environment.contains("<redacted>"))
                assertTrue(environment.contains("1.2.3"))
                assertFalse(environment.contains("abcdefghijklmnop"))
                val events = zip.readText("events/current.jsonl")
                assertFalse(events.contains("content://"))
                assertFalse(events.contains("Algebra"))
                assertFalse(events.contains("Mike"))
                assertTrue(events.contains("<redacted-content-uri>"))
                assertTrue(events.contains("<fingerprint:"))
                val manifest = zip.readText("manifest.json")
                assertTrue(manifest.contains("\"schemaVersion\":\"3\""))
                assertTrue(manifest.contains("\"flushCompleted\":\"true\""))

                val checksumLines = zip.readText("checksums.sha256")
                    .lineSequence()
                    .filter(String::isNotBlank)
                    .toList()
                assertEquals(4, checksumLines.size)
                checksumLines.forEach { line ->
                    val expected = line.substringBefore("  ")
                    val name = line.substringAfter("  ")
                    val bytes = zip.getInputStream(zip.getEntry(name)).readBytes()
                    assertEquals(expected, sha256(bytes))
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun waitUntil(timeoutMillis: Long = 2_000L, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10L)
        }
        return condition()
    }

    private fun ZipFile.readText(name: String): String =
        getInputStream(getEntry(name)).reader(Charsets.UTF_8).use { reader -> reader.readText() }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
