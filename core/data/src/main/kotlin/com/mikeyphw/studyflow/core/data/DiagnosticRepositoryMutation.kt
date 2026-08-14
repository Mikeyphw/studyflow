package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticSeverity
import java.util.concurrent.CancellationException

/** Records a real repository mutation without attaching user-authored payloads or raw identifiers. */
internal suspend inline fun <T> DiagnosticRecorder.recordRepositoryMutation(
    area: DiagnosticArea,
    action: String,
    crossinline block: suspend () -> T,
): T {
    val startedAtNanos = System.nanoTime()
    record(area = area, action = action, outcome = DiagnosticOutcome.STARTED)
    return try {
        block().also {
            record(
                area = area,
                action = action,
                outcome = DiagnosticOutcome.SUCCEEDED,
                durationMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L,
            )
        }
    } catch (cancelled: CancellationException) {
        record(
            area = area,
            action = action,
            outcome = DiagnosticOutcome.CANCELLED,
            severity = DiagnosticSeverity.WARNING,
            durationMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L,
            throwable = cancelled,
        )
        throw cancelled
    } catch (failure: Throwable) {
        record(
            area = area,
            action = action,
            outcome = DiagnosticOutcome.FAILED,
            severity = DiagnosticSeverity.ERROR,
            durationMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L,
            throwable = failure,
        )
        throw failure
    }
}
