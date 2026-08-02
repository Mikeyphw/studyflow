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
 * Callers never block while recording. Queue overflow is counted and reported in exported metadata.
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
    private val worker = Thread(::runWorker, "studyflow-diagnostics-writer").apply {
        isDaemon = true
        start()
    }

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
    }

    override fun record(event: DiagnosticEvent) {
        if (!enabled) return
        val timestamp = event.timestampEpochMillis.takeIf { value -> value > 0L } ?: clock()
        val normalized = event.copy(
            sessionId = policy.sessionId,
            sequence = sequence.incrementAndGet(),
            timestampEpochMillis = timestamp,
        )
        if (!queue.offer(Command.Write(normalized))) {
            droppedEvents.incrementAndGet()
        }
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
        return synchronized(fileLock) {
            if (!currentFile.isFile) "" else currentFile.readText(Charsets.UTF_8).takeLast(maxChars)
        }
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
        val flushCompleted = when {
            writerFailure.get() != null -> false
            else -> flush()
        }
        val failureFingerprint = writerFailureFingerprint
        check(flushCompleted || failureFingerprint != null) {
            "Diagnostic recorder could not flush before export."
        }

        val generatedAt = clock()
        val redactedMetadata = DiagnosticRedactor.redactDetails(metadata)
        val entries = linkedMapOf<String, ByteArray>()
        synchronized(fileLock) {
            if (currentFile.isFile) {
                entries["events/current.jsonl"] = currentFile.readBytes()
            }
        }
        entries["environment.json"] = DiagnosticJson.encodeFlatObject(redactedMetadata)
            .plus("\n")
            .toByteArray(Charsets.UTF_8)
        entries["manifest.json"] = buildManifest(
            generatedAt = generatedAt,
            hasTimeline = entries.containsKey("events/current.jsonl"),
            flushCompleted = flushCompleted,
        ).toByteArray(Charsets.UTF_8)
        entries["redaction-report.txt"] = RedactionReport.toByteArray(Charsets.UTF_8)
        entries["checksums.sha256"] = entries.entries
            .joinToString(separator = "\n", postfix = "\n") { (name, bytes) ->
                "${sha256(bytes)}  $name"
            }
            .toByteArray(Charsets.UTF_8)

        destinationZip.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(destinationZip)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
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
        val line = event.toJsonLine() + "\n"
        val bytes = line.toByteArray(Charsets.UTF_8)
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

    private fun rotateIfNeeded(nextBytes: Long) {
        if (!currentFile.isFile || currentFile.length() + nextBytes <= maxSessionBytes) return
        val rotated = nextRotationFile()
        if (!currentFile.renameTo(rotated)) {
            currentFile.copyTo(rotated, overwrite = false)
            check(currentFile.delete()) { "Unable to clear current diagnostic timeline after rotation." }
        }
        sessionsDirectory
            .listFiles { candidate -> candidate.isFile && candidate.extension == "jsonl" }
            .orEmpty()
            .sortedWith(
                compareByDescending<File> { file -> file.lastModified() }
                    .thenByDescending(File::getName),
            )
            .drop(retainedSessions)
            .forEach(File::delete)
    }

    private fun nextRotationFile(): File {
        val timestamp = clock()
        val sessionFingerprint = DiagnosticRedactor.fingerprint(policy.sessionId)
        while (true) {
            val candidate = File(
                sessionsDirectory,
                "session-$timestamp-${rotationSequence.incrementAndGet()}-$sessionFingerprint.jsonl",
            )
            if (!candidate.exists()) return candidate
        }
    }

    private fun buildManifest(
        generatedAt: Long,
        hasTimeline: Boolean,
        flushCompleted: Boolean,
    ): String {
        val values = linkedMapOf(
            "schemaVersion" to "2",
            "generatedAtEpochMillis" to generatedAt.toString(),
            "sessionFingerprint" to DiagnosticRedactor.fingerprint(policy.sessionId),
            "timelineIncluded" to hasTimeline.toString(),
            "flushCompleted" to flushCompleted.toString(),
            "timelineMayBeIncomplete" to (!flushCompleted).toString(),
            "droppedEventCount" to droppedEvents.get().toString(),
            "writerFailureFingerprint" to writerFailureFingerprint.orEmpty(),
            "automaticUpload" to "false",
        )
        return DiagnosticJson.encodeFlatObject(values) + "\n"
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

        private val RedactionReport = """
            StudyFlow Debug Workbench D1 r2 support bundle.
            No automatic upload is performed.
            User study content, names, notes, questions, answers, raw identifiers, paths, URIs,
            credentials, cookies, tokens, and exception messages are excluded, fingerprinted, or redacted.
            A failed writer can still export a manifest; when flushCompleted is false,
            timeline evidence may be incomplete.
        """.trimIndent() + "\n"
    }
}
