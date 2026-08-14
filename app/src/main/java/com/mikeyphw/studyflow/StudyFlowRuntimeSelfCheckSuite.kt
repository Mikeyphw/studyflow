package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.diagnostics.DebugProbeCatalog
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeId
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeReport
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeReportPolicy
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeResult
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeStatus
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticSeverity
import com.mikeyphw.studyflow.core.diagnostics.RuntimeSelfCheckInvariantPolicy
import com.mikeyphw.studyflow.core.diagnostics.RuntimeSelfCheckRun
import com.mikeyphw.studyflow.core.diagnostics.RuntimeSelfCheckVerdict
import java.util.concurrent.CancellationException

internal class RuntimeSelfCheckCancellation(
    val report: DebugProbeReport,
    val run: RuntimeSelfCheckRun,
    cause: CancellationException,
) : CancellationException("Runtime self-check cancelled after partial evidence was captured.") {
    init { initCause(cause) }
}

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
        val attempted = mutableListOf<DebugProbeId>()
        val results = linkedMapOf<DebugProbeId, DebugProbeResult>()
        try {
            probeIds.forEach { id ->
                attempted += id
                report = DebugProbeReportPolicy.markRunning(report, id)
                val result = try {
                    runProbe(id).sanitized()
                } catch (cancelled: CancellationException) {
                    val terminal = DebugProbeResult(
                        id = id,
                        status = DebugProbeStatus.CANCELLED,
                        completedAtEpochMillis = clock(),
                    ).sanitized()
                    report = DebugProbeReportPolicy.complete(report, terminal)
                    results[id] = terminal
                    val partial = buildRun(startedAt, attempted, results)
                    recordTerminal(partial)
                    throw RuntimeSelfCheckCancellation(report, partial, cancelled)
                }
                report = DebugProbeReportPolicy.complete(report, result)
                results[id] = result
            }
            val run = buildRun(startedAt, attempted, results)
            recordTerminal(run)
            return report to run
        } catch (cancelled: RuntimeSelfCheckCancellation) {
            throw cancelled
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

    private fun buildRun(
        startedAt: Long,
        attempted: List<DebugProbeId>,
        results: Map<DebugProbeId, DebugProbeResult>,
    ): RuntimeSelfCheckRun = RuntimeSelfCheckRun(
        startedAtEpochMillis = startedAt,
        completedAtEpochMillis = clock(),
        attemptedProbeIds = attempted.toList(),
        results = results.toMap(),
        invariantFailures = RuntimeSelfCheckInvariantPolicy.evaluate(results),
    )

    private fun recordTerminal(run: RuntimeSelfCheckRun) {
        val (outcome, severity) = when (run.verdict) {
            RuntimeSelfCheckVerdict.PASS -> DiagnosticOutcome.SUCCEEDED to DiagnosticSeverity.INFO
            RuntimeSelfCheckVerdict.PASS_WITH_WARNINGS -> DiagnosticOutcome.SUCCEEDED to DiagnosticSeverity.WARNING
            RuntimeSelfCheckVerdict.BLOCKED -> DiagnosticOutcome.BLOCKED to DiagnosticSeverity.WARNING
            RuntimeSelfCheckVerdict.FAIL -> DiagnosticOutcome.FAILED to DiagnosticSeverity.ERROR
            RuntimeSelfCheckVerdict.CANCELLED -> DiagnosticOutcome.CANCELLED to DiagnosticSeverity.WARNING
        }
        recorder.record(
            area = DiagnosticArea.VALIDATION,
            action = "debug-runtime-self-check-suite",
            outcome = outcome,
            severity = severity,
            safeDetails = mapOf(
                "count" to run.results.size.toString(),
                "status" to run.verdict.name.lowercase(),
            ),
        )
    }
}
