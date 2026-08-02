package com.mikeyphw.studyflow

import android.app.Application
import android.os.SystemClock
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRedactor
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticSessionPolicy
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticSeverity
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticStore
import com.mikeyphw.studyflow.core.diagnostics.NoOpDiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.RollingJsonlDiagnosticRecorder
import java.io.File
import java.util.concurrent.CancellationException

internal fun createStudyFlowDiagnosticRecorder(application: Application): DiagnosticStore {
    if (!BuildConfig.DEBUG) return NoOpDiagnosticRecorder
    val sessionFingerprint = DiagnosticRedactor.fingerprint(
        "${application.packageName}:${System.currentTimeMillis()}:${System.nanoTime()}",
    )
    return RollingJsonlDiagnosticRecorder(
        rootDirectory = File(application.filesDir, "diagnostics"),
        policy = DiagnosticSessionPolicy.debug("debug-$sessionFingerprint"),
    )
}

internal inline fun <T> DiagnosticRecorder.recordStep(
    area: DiagnosticArea,
    action: String,
    safeDetails: Map<String, String> = emptyMap(),
    block: () -> T,
): T {
    val startedAt = SystemClock.elapsedRealtime()
    record(
        area = area,
        action = action,
        outcome = DiagnosticOutcome.STARTED,
        safeDetails = safeDetails,
    )
    return try {
        block().also {
            record(
                area = area,
                action = action,
                outcome = DiagnosticOutcome.SUCCEEDED,
                durationMillis = SystemClock.elapsedRealtime() - startedAt,
                safeDetails = safeDetails,
            )
        }
    } catch (failure: Throwable) {
        record(
            area = area,
            action = action,
            outcome = DiagnosticOutcome.FAILED,
            severity = DiagnosticSeverity.ERROR,
            durationMillis = SystemClock.elapsedRealtime() - startedAt,
            safeDetails = safeDetails,
            throwable = failure,
        )
        throw failure
    }
}

internal suspend fun <T> DiagnosticRecorder.recordSuspendStep(
    area: DiagnosticArea,
    action: String,
    safeDetails: Map<String, String> = emptyMap(),
    block: suspend () -> T,
): T {
    val startedAt = SystemClock.elapsedRealtime()
    record(
        area = area,
        action = action,
        outcome = DiagnosticOutcome.STARTED,
        safeDetails = safeDetails,
    )
    return try {
        block().also {
            record(
                area = area,
                action = action,
                outcome = DiagnosticOutcome.SUCCEEDED,
                durationMillis = SystemClock.elapsedRealtime() - startedAt,
                safeDetails = safeDetails,
            )
        }
    } catch (cancelled: CancellationException) {
        record(
            area = area,
            action = action,
            outcome = DiagnosticOutcome.CANCELLED,
            severity = DiagnosticSeverity.WARNING,
            durationMillis = SystemClock.elapsedRealtime() - startedAt,
            safeDetails = safeDetails,
            throwable = cancelled,
        )
        throw cancelled
    } catch (failure: Throwable) {
        record(
            area = area,
            action = action,
            outcome = DiagnosticOutcome.FAILED,
            severity = DiagnosticSeverity.ERROR,
            durationMillis = SystemClock.elapsedRealtime() - startedAt,
            safeDetails = safeDetails,
            throwable = failure,
        )
        throw failure
    }
}
