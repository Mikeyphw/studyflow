package com.mikeyphw.studyflow.core.diagnostics

/** Terminal result for a single safe-suite execution. */
data class RuntimeSelfCheckRun(
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val attemptedProbeIds: List<DebugProbeId>,
    val results: Map<DebugProbeId, DebugProbeResult>,
) {
    init {
        require(startedAtEpochMillis >= 0L) { "Self-check start time must not be negative." }
        require(completedAtEpochMillis >= startedAtEpochMillis) {
            "Self-check completion must not precede start."
        }
        require(attemptedProbeIds.isNotEmpty()) { "Self-check suite must attempt at least one probe." }
        require(attemptedProbeIds.distinct().size == attemptedProbeIds.size) {
            "Self-check suite cannot run duplicate probes."
        }
        require(attemptedProbeIds.all(DebugProbeCatalog.safeSuiteIds::contains)) {
            "Self-check suite must contain read-only safe probes only."
        }
        require(attemptedProbeIds.all(results::containsKey)) {
            "Every attempted self-check probe must have a terminal result."
        }
        require(results.values.none { result ->
            result.status in setOf(DebugProbeStatus.NOT_RUN, DebugProbeStatus.RUNNING)
        }) { "Self-check history cannot store running probes." }
    }

    val passedCount: Int = results.values.count { result -> result.status == DebugProbeStatus.PASSED }
    val warningCount: Int = results.values.count { result -> result.status == DebugProbeStatus.WARNING }
    val blockedCount: Int = results.values.count { result -> result.status == DebugProbeStatus.BLOCKED }
    val failedCount: Int = results.values.count { result -> result.status == DebugProbeStatus.FAILED }
    val cancelledCount: Int = results.values.count { result -> result.status == DebugProbeStatus.CANCELLED }

    val successful: Boolean = failedCount == 0 && cancelledCount == 0

    fun sanitized(): RuntimeSelfCheckRun = copy(
        results = results.mapValues { (_, result) -> result.sanitized() },
    )
}

data class RuntimeSelfCheckHistory(
    val runs: List<RuntimeSelfCheckRun> = emptyList(),
    val retainedRunCount: Int = 10,
) {
    init {
        require(retainedRunCount in 1..50) { "Self-check history retention must be bounded." }
        require(runs.size <= retainedRunCount) { "Self-check history exceeds retention." }
        require(runs == runs.sortedByDescending(RuntimeSelfCheckRun::completedAtEpochMillis)) {
            "Self-check history must be newest first."
        }
    }

    val latest: RuntimeSelfCheckRun? = runs.firstOrNull()
    val lastSuccessful: Boolean? = latest?.successful

    fun record(run: RuntimeSelfCheckRun): RuntimeSelfCheckHistory = copy(
        runs = (listOf(run.sanitized()) + runs)
            .sortedByDescending(RuntimeSelfCheckRun::completedAtEpochMillis)
            .take(retainedRunCount),
    )

    fun toClipboardReport(): String = buildString {
        appendLine("StudyFlow Runtime Self-Check History")
        appendLine("Runs retained: ${runs.size}")
        latest?.let { run ->
            appendLine("Latest completed: ${run.completedAtEpochMillis}")
            appendLine("Passed: ${run.passedCount}")
            appendLine("Warnings: ${run.warningCount}")
            appendLine("Blocked: ${run.blockedCount}")
            appendLine("Failed: ${run.failedCount}")
            appendLine("Cancelled: ${run.cancelledCount}")
        } ?: appendLine("Latest completed: none")
    }
}

enum class SupportBundleSealStatus {
    NOT_RUN,
    PASSED,
    FAILED,
}

enum class SupportBundleSealMessage {
    NONE,
    BUNDLE_MISSING,
    ENTRY_NOT_ALLOWED,
    ENTRY_TRAVERSAL,
    MANIFEST_MISSING,
    CHECKSUMS_MISSING,
    REDACTION_REPORT_MISSING,
    CHECKSUM_INVALID,
    CHECKSUM_MISMATCH,
}

data class SupportBundleSealReport(
    val status: SupportBundleSealStatus,
    val message: SupportBundleSealMessage = SupportBundleSealMessage.NONE,
    val entryCount: Int = 0,
    val checkedAtEpochMillis: Long = 0L,
    val failingEntryFingerprint: String? = null,
) {
    init {
        require(entryCount >= 0) { "Support bundle entry count must not be negative." }
        require(checkedAtEpochMillis >= 0L) { "Support bundle seal time must not be negative." }
        require(failingEntryFingerprint == null || !failingEntryFingerprint.contains('/')) {
            "Support bundle seal must not expose raw entry paths."
        }
    }

    val passed: Boolean = status == SupportBundleSealStatus.PASSED

    companion object {
        fun notRun(): SupportBundleSealReport = SupportBundleSealReport(SupportBundleSealStatus.NOT_RUN)
    }
}

object SupportBundleSealPolicy {
    private val exactAllowedEntries = setOf(
        "checksums.sha256",
        "environment.json",
        "events/current.jsonl",
        "manifest.json",
        "redaction-report.txt",
    )

    fun inspect(
        entryNames: List<String>,
        checkedAtEpochMillis: Long,
        checksums: Map<String, String>? = null,
        actualChecksums: Map<String, String>? = null,
    ): SupportBundleSealReport {
        if (entryNames.isEmpty()) {
            return failure(SupportBundleSealMessage.BUNDLE_MISSING, checkedAtEpochMillis)
        }
        entryNames.forEach { entry ->
            if (entry.contains("..") || entry.startsWith('/') || entry.startsWith('\\')) {
                return failure(SupportBundleSealMessage.ENTRY_TRAVERSAL, checkedAtEpochMillis, entry)
            }
            if (entry !in exactAllowedEntries) {
                return failure(SupportBundleSealMessage.ENTRY_NOT_ALLOWED, checkedAtEpochMillis, entry)
            }
        }
        val entries = entryNames.toSet()
        val missingMessage = when {
            "manifest.json" !in entries -> SupportBundleSealMessage.MANIFEST_MISSING
            "checksums.sha256" !in entries -> SupportBundleSealMessage.CHECKSUMS_MISSING
            "redaction-report.txt" !in entries -> SupportBundleSealMessage.REDACTION_REPORT_MISSING
            else -> null
        }
        if (missingMessage != null) {
            return failure(missingMessage, checkedAtEpochMillis)
        }
        if (checksums != null || actualChecksums != null) {
            val expected = checksums ?: return failure(
                SupportBundleSealMessage.CHECKSUM_INVALID,
                checkedAtEpochMillis,
            )
            val actual = actualChecksums ?: return failure(
                SupportBundleSealMessage.CHECKSUM_INVALID,
                checkedAtEpochMillis,
            )
            val payloadEntries = entries - "checksums.sha256"
            if (expected.keys != payloadEntries || actual.keys != payloadEntries) {
                return failure(SupportBundleSealMessage.CHECKSUM_INVALID, checkedAtEpochMillis)
            }
            payloadEntries.forEach { entry ->
                val expectedDigest = expected[entry].orEmpty()
                if (!expectedDigest.matches(Regex("[0-9a-f]{64}"))) {
                    return failure(SupportBundleSealMessage.CHECKSUM_INVALID, checkedAtEpochMillis, entry)
                }
                if (expectedDigest != actual[entry]) {
                    return failure(SupportBundleSealMessage.CHECKSUM_MISMATCH, checkedAtEpochMillis, entry)
                }
            }
        }
        return SupportBundleSealReport(
                status = SupportBundleSealStatus.PASSED,
                entryCount = entryNames.size,
                checkedAtEpochMillis = checkedAtEpochMillis,
            )
        }

    private fun failure(
        message: SupportBundleSealMessage,
        checkedAtEpochMillis: Long,
        entry: String? = null,
    ): SupportBundleSealReport = SupportBundleSealReport(
        status = SupportBundleSealStatus.FAILED,
        message = message,
        checkedAtEpochMillis = checkedAtEpochMillis,
        failingEntryFingerprint = entry?.let(DiagnosticRedactor::fingerprint),
    )
}
