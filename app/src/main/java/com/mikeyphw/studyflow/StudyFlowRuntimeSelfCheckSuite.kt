package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.diagnostics.DebugProbeCatalog
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeId
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeReport
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeReportPolicy
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeResult
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticSeverity
import com.mikeyphw.studyflow.core.diagnostics.RuntimeSelfCheckRun
import java.util.concurrent.CancellationException

internal class StudyFlowRuntimeSelfCheckSuite(
    private val probeIds: List<DebugProbeId> = DebugProbeCatalog.safeSuiteIds,
    private val runProbe: suspend (DebugProbeId) -> DebugProbeResult,
    private val recorder: DiagnosticRecorder,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(probeIds.isNotEmpty()) { "Runtime self-check suite must include probes." }
        require(probeIds.all(DebugProbeCatalog.safeSuiteIds::contains)) {
            "Runtime self-check suite can run read-only safe probes only."
        }
        require(probeIds.distinct().size == probeIds.size) {
            "Runtime self-check suite cannot contain duplicate probes."
        }
    }

    suspend fun run(initialReport: DebugProbeReport): Pair<DebugProbeReport, RuntimeSelfCheckRun> {
        val startedAt = clock()
        recorder.record(
            area = DiagnosticArea.VALIDATION,
            action = "debug-runtime-self-check-suite",
            outcome = DiagnosticOutcome.STARTED,
            safeDetails = mapOf("count" to probeIds.size.toString()),
        )
        var report = initialReport
        val results = linkedMapOf<DebugProbeId, DebugProbeResult>()
        return try {
            probeIds.forEach { id ->
                report = DebugProbeReportPolicy.markRunning(report, id)
                val result = runProbe(id).sanitized()
                report = DebugProbeReportPolicy.complete(report, result)
                results[id] = result
            }
            val run = RuntimeSelfCheckRun(
                startedAtEpochMillis = startedAt,
                completedAtEpochMillis = clock(),
                attemptedProbeIds = probeIds,
                results = results,
            )
            recorder.record(
                area = DiagnosticArea.VALIDATION,
                action = "debug-runtime-self-check-suite",
                outcome = if (run.successful) DiagnosticOutcome.SUCCEEDED else DiagnosticOutcome.FAILED,
                severity = if (run.successful) DiagnosticSeverity.INFO else DiagnosticSeverity.WARNING,
                safeDetails = mapOf(
                    "count" to run.results.size.toString(),
                    "failed" to run.failedCount.toString(),
                    "blocked" to run.blockedCount.toString(),
                ),
            )
            report to run
        } catch (cancelled: CancellationException) {
            recorder.record(
                area = DiagnosticArea.VALIDATION,
                action = "debug-runtime-self-check-suite",
                outcome = DiagnosticOutcome.CANCELLED,
                severity = DiagnosticSeverity.WARNING,
            )
            throw cancelled
        } catch (fatal: Error) {
            throw fatal
        } catch (failure: Exception) {
            recorder.record(
                area = DiagnosticArea.VALIDATION,
                action = "debug-runtime-self-check-suite",
                outcome = DiagnosticOutcome.FAILED,
                severity = DiagnosticSeverity.ERROR,
                throwable = failure,
            )
            throw failure
        }
    }
}
