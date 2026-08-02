package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthLabReport
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthMessage
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabReport
import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticSeverity
import com.mikeyphw.studyflow.core.diagnostics.PortabilityDrillDiagnostic
import com.mikeyphw.studyflow.core.diagnostics.PortabilityDrillKind
import com.mikeyphw.studyflow.core.diagnostics.PortabilityLabMessage
import com.mikeyphw.studyflow.core.domain.BackupRestoreDrillKind
import com.mikeyphw.studyflow.core.domain.BackupRestoreDrillSeal
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun interface DatabaseHealthOperation {
    suspend fun inspect(): DatabaseHealthLabReport
}

internal fun interface PortabilityDrillOperation {
    suspend fun run(planId: StudyPlanId): DatabasePortabilityLabReport
}

internal class StudyFlowDatabasePortabilityLab(
    private val databaseOperation: DatabaseHealthOperation,
    private val portabilityOperation: PortabilityDrillOperation,
    private val defaultPlanId: () -> StudyPlanId?,
    private val recorder: DiagnosticRecorder,
    private val executionContext: CoroutineContext = Dispatchers.IO,
) {
    constructor(
        container: AppContainer,
        defaultPlanId: () -> StudyPlanId?,
        executionContext: CoroutineContext = Dispatchers.IO,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        databaseOperation = DatabaseHealthOperation(container::inspectDatabaseHealth),
        portabilityOperation = PortabilityDrillOperation { planId ->
            val database = container.inspectDatabaseHealth()
            val drill = BackupRestoreDrillSeal.runManagedDrills(
                generatedAtEpochMillis = clock(),
                planId = planId,
                portability = container.manageStudyPlanPortability,
            )
            DatabasePortabilityLabReport(
                database = database,
                portabilityStatus = when {
                    drill.releaseReady -> DebugLabStatus.PASSED
                    drill.blockedCount > 0 -> DebugLabStatus.WARNING
                    else -> DebugLabStatus.FAILED
                },
                portabilityMessage = when {
                    drill.releaseReady -> PortabilityLabMessage.NONE
                    drill.blockedCount > 0 -> PortabilityLabMessage.DRILL_BLOCKED
                    else -> PortabilityLabMessage.DRILL_FAILED
                },
                portabilityGeneratedAtEpochMillis = drill.generatedAtEpochMillis,
                drills = drill.results.map { result ->
                    PortabilityDrillDiagnostic(
                        kind = result.kind.toDiagnosticKind(),
                        passed = result.passed,
                        destructive = result.destructive,
                    )
                },
            )
        },
        defaultPlanId = defaultPlanId,
        recorder = container.diagnosticRecorder,
        executionContext = executionContext,
    )

    suspend fun inspectDatabase(
        previous: DatabasePortabilityLabReport,
    ): Pair<DatabasePortabilityLabReport, DatabasePortabilityLabActionResult> =
        withContext(executionContext) {
            val action = "database-lab-inspect"
            recordStarted(DiagnosticArea.DATABASE, action)
            try {
                previous.copy(database = databaseOperation.inspect()).also {
                    recordSucceeded(DiagnosticArea.DATABASE, action)
                } to DatabasePortabilityLabActionResult.DATABASE_INSPECTED
            } catch (cancelled: CancellationException) {
                recordCancelled(DiagnosticArea.DATABASE, action)
                throw cancelled
            } catch (failure: Exception) {
                recordFailed(DiagnosticArea.DATABASE, action, failure)
                previous.copy(
                    database = DatabaseHealthLabReport(
                        status = DebugLabStatus.FAILED,
                        message = DatabaseHealthMessage.DATABASE_UNAVAILABLE,
                    ),
                ) to DatabasePortabilityLabActionResult.FAILED
            }
        }

    suspend fun runPortabilityDrills(
        previous: DatabasePortabilityLabReport,
    ): Pair<DatabasePortabilityLabReport, DatabasePortabilityLabActionResult> =
        withContext(executionContext) {
            val action = "portability-lab-drills"
            recordStarted(DiagnosticArea.BACKUP_RESTORE, action)
            val planId = defaultPlanId()
            if (planId == null) {
                recorder.record(
                    area = DiagnosticArea.BACKUP_RESTORE,
                    action = action,
                    outcome = DiagnosticOutcome.BLOCKED,
                    severity = DiagnosticSeverity.WARNING,
                )
                return@withContext previous.copy(
                    portabilityStatus = DebugLabStatus.BLOCKED,
                    portabilityMessage = PortabilityLabMessage.MISSING_DEFAULT_PLAN,
                ) to DatabasePortabilityLabActionResult.MISSING_DEFAULT_PLAN
            }
            try {
                portabilityOperation.run(planId).also {
                    recordSucceeded(DiagnosticArea.BACKUP_RESTORE, action)
                } to DatabasePortabilityLabActionResult.PORTABILITY_DRILLS_COMPLETED
            } catch (cancelled: CancellationException) {
                recordCancelled(DiagnosticArea.BACKUP_RESTORE, action)
                throw cancelled
            } catch (failure: Exception) {
                recordFailed(DiagnosticArea.BACKUP_RESTORE, action, failure)
                previous.copy(
                    portabilityStatus = DebugLabStatus.FAILED,
                    portabilityMessage = PortabilityLabMessage.DRILL_FAILED,
                ) to DatabasePortabilityLabActionResult.FAILED
            }
        }

    private fun recordStarted(area: DiagnosticArea, action: String) {
        recorder.record(area = area, action = action, outcome = DiagnosticOutcome.STARTED)
    }

    private fun recordSucceeded(area: DiagnosticArea, action: String) {
        recorder.record(area = area, action = action, outcome = DiagnosticOutcome.SUCCEEDED)
    }

    private fun recordCancelled(area: DiagnosticArea, action: String) {
        recorder.record(
            area = area,
            action = action,
            outcome = DiagnosticOutcome.CANCELLED,
            severity = DiagnosticSeverity.WARNING,
        )
    }

    private fun recordFailed(area: DiagnosticArea, action: String, failure: Exception) {
        recorder.record(
            area = area,
            action = action,
            outcome = DiagnosticOutcome.FAILED,
            severity = DiagnosticSeverity.ERROR,
            throwable = failure,
        )
    }
}

private fun BackupRestoreDrillKind.toDiagnosticKind(): PortabilityDrillKind = when (this) {
    BackupRestoreDrillKind.ENCRYPTED_EXPORT -> PortabilityDrillKind.ENCRYPTED_EXPORT
    BackupRestoreDrillKind.WRONG_PASSWORD -> PortabilityDrillKind.WRONG_PASSWORD
    BackupRestoreDrillKind.TAMPER_DETECTION -> PortabilityDrillKind.TAMPER_DETECTION
    BackupRestoreDrillKind.METADATA_PRIVATE_ANNEX -> PortabilityDrillKind.METADATA_PRIVATE_ANNEX
    BackupRestoreDrillKind.RESTORE_DRY_RUN -> PortabilityDrillKind.RESTORE_DRY_RUN
    BackupRestoreDrillKind.RETENTION_CLEANUP -> PortabilityDrillKind.RETENTION_CLEANUP
    BackupRestoreDrillKind.RECOVERY_REPORT -> PortabilityDrillKind.RECOVERY_REPORT
}
