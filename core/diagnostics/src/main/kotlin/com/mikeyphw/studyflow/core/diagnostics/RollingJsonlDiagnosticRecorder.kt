package com.mikeyphw.studyflow.core.diagnostics

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Bounded asynchronous recorder for app-private storage.
 *
 * Recording is non-blocking. Each process session starts a fresh current segment, retained segments
 * are read/exported oldest-first, and outbound support bundles pass a fail-closed privacy/schema gate.
 */
class RollingJsonlDiagnosticRecorder private constructor(
    private val rootDirectory: File,
    private val policy: DiagnosticSessionPolicy,
    private val maxSessionBytes: Long,
    private val retainedSessions: Int,
    queueCapacity: Int,
    private val clock: () -> Long,
    private val beforeWrite: (() -> Unit)?,
) : DiagnosticStore, AutoCloseable {
    constructor(
        rootDirectory: File,
        policy: DiagnosticSessionPolicy,
        maxSessionBytes: Long = 2L * 1024L * 1024L,
        retainedSessions: Int = 5,
        queueCapacity: Int = 256,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        rootDirectory = rootDirectory,
        policy = policy,
        maxSessionBytes = maxSessionBytes,
        retainedSessions = retainedSessions,
        queueCapacity = queueCapacity,
        clock = clock,
        beforeWrite = null,
    )

    private sealed interface Command {
        data class Write(val event: DiagnosticEvent) : Command
        data class Flush(val latch: CountDownLatch) : Command
        data object Stop : Command
    }

    private val queue = ArrayBlockingQueue<Command>(queueCapacity.coerceAtLeast(1))
    private val sequence = AtomicLong(0L)
    private val rotationSequence = AtomicLong(0L)
    private val droppedEvents = AtomicLong(0L)
    private val closed = AtomicBoolean(false)
    private val writerFailure = AtomicReference<Throwable?>(null)
    private val fileLock = Any()
    private val worker: Thread

    private val sessionsDirectory: File
        get() = File(rootDirectory, "sessions")

    private val currentFile: File
        get() = File(rootDirectory, "current.jsonl")

    override val enabled: Boolean
        get() = !closed.get() && writerFailure.get() == null && policy.allowsRecording(clock())

    override val droppedEventCount: Long
        get() = droppedEvents.get()

    override val writerFailureFingerprint: String?
        get() = writerFailure.get()?.let { failure ->
            DiagnosticRedactor.summarizeThrowable(failure).fingerprint
        }

    init {
        require(maxSessionBytes > 0L) { "Maximum diagnostic session size must be positive." }
        require(retainedSessions >= 0) { "Retained diagnostic session count must not be negative." }
        synchronized(fileLock) { initializeSessionBoundary() }
        worker = Thread(::runWorker, "studyflow-diagnostics-writer").apply {
            isDaemon = true
            start()
        }
    }

    override fun record(event: DiagnosticEvent) {
        if (!enabled) return
        val timestamp = event.timestampEpochMillis.takeIf { value -> value > 0L } ?: clock()
        val normalized = event.copy(
            sessionId = policy.sessionId,
            sequence = sequence.incrementAndGet(),
            timestampEpochMillis = timestamp,
        )
        if (!queue.offer(Command.Write(normalized))) droppedEvents.incrementAndGet()
    }

    override fun flush(timeoutMillis: Long): Boolean {
        if (closed.get()) return writerFailure.get() == null
        if (writerFailure.get() != null) return false
        val latch = CountDownLatch(1)
        val timeout = timeoutMillis.coerceAtLeast(1L)
        val enqueued = queue.offer(Command.Flush(latch), timeout, TimeUnit.MILLISECONDS)
        return enqueued && latch.await(timeout, TimeUnit.MILLISECONDS) && writerFailure.get() == null
    }

    override fun copySanitizedTimeline(maxChars: Int): String {
        flush()
        val completeTimeline = synchronized(fileLock) {
            timelineFilesInOrder().joinToString(separator = "") { file -> file.readCompleteJsonl() }
        }
        return tailCompleteLines(completeTimeline, maxChars.coerceAtLeast(0))
    }

    override fun clear(): Boolean {
        if (!flush()) return false
        return synchronized(fileLock) {
            if (rootDirectory.exists() && !rootDirectory.deleteRecursively()) return@synchronized false
            true
        }
    }

    override fun exportSupportBundle(
        destinationZip: File,
        metadata: Map<String, String>,
    ): File {
        val flushCompleted = writerFailure.get() == null && flush()
        val failureFingerprint = writerFailureFingerprint
        check(flushCompleted || failureFingerprint != null) {
            "Diagnostic recorder could not flush before export."
        }

        destinationZip.parentFile?.mkdirs()
        val generatedAt = clock()
        val entries = linkedMapOf<String, ByteArray>()
        var retainedSegmentCount = 0
        var currentIncluded = false
        var timelineLines = 0
        var schemaViolations = 0
        var privacyViolations = 0

        synchronized(fileLock) {
            val retained = retainedTimelineFilesInOrder()
            retained.forEachIndexed { index, file ->
                val raw = file.readCompleteJsonl().toByteArray(Charsets.UTF_8)
                if (raw.isEmpty()) return@forEachIndexed
                val name = "events/retained-${index.toString().padStart(3, '0')}.jsonl"
                val scan = SupportBundlePrivacyScanner.sanitize(name, raw)
                schemaViolations += scan.schemaViolationCount
                privacyViolations += scan.privacyViolationCount
                timelineLines += scan.timelineLineCount
                entries[name] = scan.sanitizedBytes
                retainedSegmentCount += 1
            }
            if (currentFile.isFile) {
                val raw = currentFile.readCompleteJsonl().toByteArray(Charsets.UTF_8)
                if (raw.isNotEmpty()) {
                    val name = "events/current.jsonl"
                    val scan = SupportBundlePrivacyScanner.sanitize(name, raw)
                    schemaViolations += scan.schemaViolationCount
                    privacyViolations += scan.privacyViolationCount
                    timelineLines += scan.timelineLineCount
                    entries[name] = scan.sanitizedBytes
                    currentIncluded = true
                }
            }
        }

        val redactedMetadata = DiagnosticRedactor.redactDetails(metadata)
        entries["environment.json"] = (DiagnosticJson.encodeFlatObject(redactedMetadata) + "\n")
            .toByteArray(Charsets.UTF_8)
        entries["manifest.json"] = buildManifest(
            generatedAt = generatedAt,
            retainedSegmentCount = retainedSegmentCount,
            currentIncluded = currentIncluded,
            flushCompleted = flushCompleted,
        ).toByteArray(Charsets.UTF_8)

        listOf("environment.json", "manifest.json").forEach { name ->
            val scan = SupportBundlePrivacyScanner.sanitize(name, entries.getValue(name))
            schemaViolations += scan.schemaViolationCount
            privacyViolations += scan.privacyViolationCount
            entries[name] = scan.sanitizedBytes
        }

        check(schemaViolations == 0 && privacyViolations == 0) {
            destinationZip.delete()
            "Support bundle privacy/schema scan failed."
        }

        val finalTextPayloadCount = entries.size + 2 // redaction report + checksum manifest
        entries["redaction-report.txt"] = buildRedactionReport(
            textPayloadsScanned = finalTextPayloadCount,
            timelineSegmentCount = retainedSegmentCount + if (currentIncluded) 1 else 0,
            timelineLinesResanitized = timelineLines,
            schemaViolations = schemaViolations,
            privacyViolations = privacyViolations,
        ).toByteArray(Charsets.UTF_8)
        SupportBundlePrivacyScanner.sanitize(
            "redaction-report.txt",
            entries.getValue("redaction-report.txt"),
        ).also { reportScan ->
            check(reportScan.passed && SupportBundlePrivacyScanner.redactionReportIsValid(
                reportScan.sanitizedBytes.toString(Charsets.UTF_8),
            )) {
                destinationZip.delete()
                "Support bundle redaction report validation failed."
            }
            entries["redaction-report.txt"] = reportScan.sanitizedBytes
        }

        entries["checksums.sha256"] = entries.entries
            .joinToString(separator = "\n", postfix = "\n") { (name, bytes) ->
                "${sha256(bytes)}  $name"
            }
            .toByteArray(Charsets.UTF_8)
        check(SupportBundlePrivacyScanner.sanitize(
            "checksums.sha256",
            entries.getValue("checksums.sha256"),
        ).passed) {
            destinationZip.delete()
            "Support bundle checksum manifest privacy scan failed."
        }

        try {
            ZipOutputStream(FileOutputStream(destinationZip)).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        } catch (failure: Exception) {
            destinationZip.delete()
            throw failure
        }
        return destinationZip
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val offered = queue.offer(Command.Stop, 2L, TimeUnit.SECONDS)
        if (!offered) worker.interrupt()
        worker.join(2_000L)
    }

    private fun runWorker() {
        try {
            while (true) {
                when (val command = queue.take()) {
                    is Command.Write -> {
                        beforeWrite?.invoke()
                        writeEvent(command.event)
                    }
                    is Command.Flush -> command.latch.countDown()
                    Command.Stop -> return
                }
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (failure: Throwable) {
            writerFailure.compareAndSet(null, failure)
        }
    }

    private fun writeEvent(event: DiagnosticEvent) {
        val bytes = (event.toJsonLine() + "\n").toByteArray(Charsets.UTF_8)
        synchronized(fileLock) {
            check(rootDirectory.mkdirs() || rootDirectory.isDirectory) {
                "Unable to create diagnostic root directory."
            }
            check(sessionsDirectory.mkdirs() || sessionsDirectory.isDirectory) {
                "Unable to create diagnostic sessions directory."
            }
            rotateIfNeeded(bytes.size.toLong())
            currentFile.appendBytes(bytes)
        }
    }

    private fun initializeSessionBoundary() {
        if (!currentFile.isFile) return
        if (currentFile.length() == 0L) {
            currentFile.delete()
            return
        }
        check(sessionsDirectory.mkdirs() || sessionsDirectory.isDirectory) {
            "Unable to create diagnostic sessions directory."
        }
        val rotated = nextRotationFile(timestamp = currentFile.lastModified().takeIf { it > 0L } ?: clock())
        if (!currentFile.renameTo(rotated)) {
            currentFile.copyTo(rotated, overwrite = false)
            check(currentFile.delete()) { "Unable to rotate prior diagnostic session." }
        }
        pruneRetainedSessions()
    }

    private fun rotateIfNeeded(nextBytes: Long) {
        if (!currentFile.isFile || currentFile.length() + nextBytes <= maxSessionBytes) return
        val rotated = nextRotationFile()
        if (!currentFile.renameTo(rotated)) {
            currentFile.copyTo(rotated, overwrite = false)
            check(currentFile.delete()) { "Unable to clear current diagnostic timeline after rotation." }
        }
        pruneRetainedSessions()
    }

    private fun pruneRetainedSessions() {
        retainedTimelineFilesNewestFirst()
            .drop(retainedSessions)
            .forEach(File::delete)
    }

    private fun retainedTimelineFilesNewestFirst(): List<File> = sessionsDirectory
        .listFiles { candidate -> candidate.isFile && candidate.extension == "jsonl" }
        .orEmpty()
        .sortedWith(compareByDescending<File>(File::lastModified).thenByDescending(File::getName))

    private fun retainedTimelineFilesInOrder(): List<File> = retainedTimelineFilesNewestFirst().asReversed()

    private fun timelineFilesInOrder(): List<File> = buildList {
        addAll(retainedTimelineFilesInOrder())
        if (currentFile.isFile) add(currentFile)
    }

    private fun nextRotationFile(timestamp: Long = clock()): File {
        val sessionFingerprint = DiagnosticRedactor.fingerprint(policy.sessionId)
        while (true) {
            val candidate = File(
                sessionsDirectory,
                "session-$timestamp-${rotationSequence.incrementAndGet()}-$sessionFingerprint.jsonl",
            )
            if (!candidate.exists()) return candidate
        }
    }

    private fun File.readCompleteJsonl(): String {
        val text = readText(Charsets.UTF_8)
        val completeEnd = text.lastIndexOf('\n')
        return if (completeEnd < 0) "" else text.substring(0, completeEnd + 1)
    }

    private fun tailCompleteLines(jsonl: String, maxChars: Int): String {
        if (maxChars <= 0 || jsonl.isEmpty()) return ""
        val lines = jsonl.lineSequence().filter(String::isNotBlank).toList()
        val selected = ArrayDeque<String>()
        var total = 0
        for (line in lines.asReversed()) {
            val size = line.length + 1
            if (size > maxChars || total + size > maxChars) break
            selected.addFirst(line)
            total += size
        }
        return if (selected.isEmpty()) "" else selected.joinToString(separator = "\n", postfix = "\n")
    }

    private fun buildManifest(
        generatedAt: Long,
        retainedSegmentCount: Int,
        currentIncluded: Boolean,
        flushCompleted: Boolean,
    ): String {
        val values = linkedMapOf(
            "schemaVersion" to "3",
            "generatedAtEpochMillis" to generatedAt.toString(),
            "sessionFingerprint" to DiagnosticRedactor.fingerprint(policy.sessionId),
            "retainedSegmentCount" to retainedSegmentCount.toString(),
            "currentSegmentIncluded" to currentIncluded.toString(),
            "timelineIncluded" to (retainedSegmentCount > 0 || currentIncluded).toString(),
            "flushCompleted" to flushCompleted.toString(),
            "timelineMayBeIncomplete" to (!flushCompleted).toString(),
            "droppedEventCount" to droppedEvents.get().toString(),
            "writerFailureFingerprint" to writerFailureFingerprint.orEmpty(),
            "automaticUpload" to "false",
        )
        return DiagnosticJson.encodeFlatObject(values) + "\n"
    }

    private fun buildRedactionReport(
        textPayloadsScanned: Int,
        timelineSegmentCount: Int,
        timelineLinesResanitized: Int,
        schemaViolations: Int,
        privacyViolations: Int,
    ): String = buildString {
        appendLine("status=${if (schemaViolations == 0 && privacyViolations == 0) "PASS" else "FAIL"}")
        appendLine("textPayloadsScanned=$textPayloadsScanned")
        appendLine("timelineSegments=$timelineSegmentCount")
        appendLine("timelineLinesResanitized=$timelineLinesResanitized")
        appendLine("schemaViolations=$schemaViolations")
        appendLine("privacyViolations=$privacyViolations")
        appendLine("automaticUpload=false")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    internal companion object {
        fun createForTesting(
            rootDirectory: File,
            policy: DiagnosticSessionPolicy,
            maxSessionBytes: Long,
            retainedSessions: Int,
            queueCapacity: Int,
            clock: () -> Long,
            beforeWriteForTesting: () -> Unit,
        ): RollingJsonlDiagnosticRecorder = RollingJsonlDiagnosticRecorder(
            rootDirectory = rootDirectory,
            policy = policy,
            maxSessionBytes = maxSessionBytes,
            retainedSessions = retainedSessions,
            queueCapacity = queueCapacity,
            clock = clock,
            beforeWrite = beforeWriteForTesting,
        )
    }
}
