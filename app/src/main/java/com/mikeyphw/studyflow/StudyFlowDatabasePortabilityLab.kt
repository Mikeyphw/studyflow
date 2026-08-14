package com.mikeyphw.studyflow

import android.content.Context
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
import com.mikeyphw.studyflow.core.model.PlanSnapshotKind
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
        context: Context,
        container: AppContainer,
        defaultPlanId: () -> StudyPlanId?,
        executionContext: CoroutineContext = Dispatchers.IO,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        databaseOperation = DatabaseHealthOperation(container::inspectDatabaseHealth),
        portabilityOperation = PortabilityDrillOperation { planId ->
            runProductionPortabilityDrills(
                context = context.applicationContext,
                container = container,
                planId = planId,
                generatedAtEpochMillis = clock(),
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
            } catch (fatal: Error) {
                throw fatal
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
            } catch (fatal: Error) {
                throw fatal
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

/**
 * Production-path portability canary. Packages are always streamed through files so the lab cannot
 * hide an OOM-prone byte-array implementation. Any plan/snapshot mutation is disposable and verified
 * after cleanup.
 */
private suspend fun runProductionPortabilityDrills(
    context: Context,
    container: AppContainer,
    planId: StudyPlanId,
    generatedAtEpochMillis: Long,
): DatabasePortabilityLabReport {
    val directory = File(context.cacheDir, "debug-portability-lab").apply { mkdirs() }
    val exported = File.createTempFile("studyflow-export-", ".sfpkg", directory)
    val tampered = File.createTempFile("studyflow-tampered-", ".sfpkg", directory)
    val password = "studyflow debug portability canary".toCharArray()
    val wrongPassword = "studyflow wrong portability canary".toCharArray()
    var importedPlanId: StudyPlanId? = null
    var createdSnapshotId: com.mikeyphw.studyflow.core.model.PlanSnapshotId? = null

    try {
        val options = StudyPlanExportOptions(
            mode = StudyPlanExportMode.PRIVATE_STRIPPED,
            includeAnnexMetadata = true,
            includeAnnexPayloads = true,
            metadataPrivacyMode = true,
            redactNotes = true,
            redactHistory = true,
        )
        exported.outputStream().buffered().use { output ->
            container.manageStudyPlanPortability.exportPlan(planId, options, output, password)
        }
        val integrity = exported.inputStream().buffered().use { input ->
            container.manageStudyPlanPortability.inspectBackup(input, password)
        }
        val dryRun = exported.inputStream().buffered().use { input ->
            container.manageStudyPlanPortability.dryRunImport(
                input = input,
                strategy = StudyPlanImportStrategy.CREATE_AS_NEW,
                password = password,
            )
        }

        exported.inputStream().buffered().use { input ->
            tampered.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        RandomAccessFile(tampered, "rw").use { random ->
            require(random.length() > 0L) { "Debug portability export is empty." }
            val index = random.length() - 1L
            random.seek(index)
            val value = random.readByte().toInt()
            random.seek(index)
            random.writeByte(value xor 0x01)
        }

        val wrongPasswordBlocked = expectedFailure {
            exported.inputStream().buffered().use { input ->
                container.manageStudyPlanPortability.inspectBackup(input, wrongPassword)
            }
        }
        val tamperBlocked = expectedFailure {
            tampered.inputStream().buffered().use { input ->
                container.manageStudyPlanPortability.inspectBackup(input, password)
            }
        }

        val planIdsBeforeFailure = container.manageStudyPlan.plans.first().mapTo(linkedSetOf()) { it.id }
        val tamperedImportBlocked = expectedFailure {
            tampered.inputStream().buffered().use { input ->
                container.manageStudyPlanPortability.importPlan(
                    input = input,
                    strategy = StudyPlanImportStrategy.CREATE_AS_NEW,
                    password = password,
                )
            }
        }
        val planIdsAfterFailure = container.manageStudyPlan.plans.first().mapTo(linkedSetOf()) { it.id }
        val failureIsolationVerified = tamperedImportBlocked && planIdsBeforeFailure == planIdsAfterFailure

        var realRestoreVerified = false
        var realRestoreCleanupVerified = false
        try {
            val imported = exported.inputStream().buffered().use { input ->
                container.manageStudyPlanPortability.importPlan(
                    input = input,
                    strategy = StudyPlanImportStrategy.CREATE_AS_NEW,
                    password = password,
                )
            }
            importedPlanId = StudyPlanId(imported.studyPlanId)
            realRestoreVerified = imported.importedRows > 0 &&
                container.manageStudyPlan.observePlan(importedPlanId!!).first() != null
        } finally {
            importedPlanId?.let { disposableId ->
                container.manageStudyPlan.deletePermanently(disposableId)
                realRestoreCleanupVerified = container.manageStudyPlan.observePlan(disposableId).first() == null
                importedPlanId = null
            }
        }

        var retentionVerified = false
        try {
            val snapshot = container.manageStudyPlanPortability.createSnapshot(
                planId = planId,
                kind = PlanSnapshotKind.MANUAL,
                label = "Debug portability canary",
            )
            createdSnapshotId = snapshot.id
            val observedCreated = container.manageStudyPlanPortability.observeSnapshots(planId).first()
                .any { it.id == snapshot.id }
            container.manageStudyPlanPortability.deleteSnapshot(snapshot.id)
            createdSnapshotId = null
            val observedDeleted = container.manageStudyPlanPortability.observeSnapshots(planId).first()
                .none { it.id == snapshot.id }
            retentionVerified = observedCreated && observedDeleted
        } finally {
            createdSnapshotId?.let { snapshotId ->
                container.manageStudyPlanPortability.deleteSnapshot(snapshotId)
                createdSnapshotId = null
            }
        }

        val database = container.inspectDatabaseHealth()
        val logicalIntegrityVerified = database.quickCheckPassed &&
            database.foreignKeyViolationCount == 0 &&
            database.logicalIntegrityViolationCount == 0
        val metadataStatus = when {
            integrity.annexPayloadCount == 0 -> DebugLabStatus.NOT_APPLICABLE
            integrity.metadataPrivacyMode && integrity.privateIdentifiersVerified -> DebugLabStatus.PASSED
            else -> DebugLabStatus.FAILED
        }
        val drills = listOf(
            drill(
                PortabilityDrillKind.ENCRYPTED_EXPORT,
                integrity.encrypted && integrity.checksumVerified && integrity.canRestore,
            ),
            drill(PortabilityDrillKind.WRONG_PASSWORD, wrongPasswordBlocked),
            drill(PortabilityDrillKind.TAMPER_DETECTION, tamperBlocked),
            PortabilityDrillDiagnostic(
                kind = PortabilityDrillKind.METADATA_PRIVATE_ANNEX,
                status = metadataStatus,
                destructive = false,
            ),
            drill(
                PortabilityDrillKind.RESTORE_DRY_RUN,
                dryRun.encrypted && dryRun.databaseRowsChanged == 0 && dryRun.totalRows > 0,
            ),
            drill(PortabilityDrillKind.REAL_RESTORE, realRestoreVerified && realRestoreCleanupVerified),
            drill(PortabilityDrillKind.RESTORE_FAILURE_ISOLATION, failureIsolationVerified),
            drill(PortabilityDrillKind.RETENTION_CLEANUP, retentionVerified),
            drill(PortabilityDrillKind.LOGICAL_INTEGRITY, logicalIntegrityVerified),
        ).let { coreDrills ->
            coreDrills + drill(
                PortabilityDrillKind.RECOVERY_REPORT,
                dryRun.databaseRowsChanged == 0 &&
                    realRestoreVerified && realRestoreCleanupVerified &&
                    failureIsolationVerified && logicalIntegrityVerified,
            )
        }
        val aggregate = when {
            drills.any { it.status == DebugLabStatus.FAILED } -> DebugLabStatus.FAILED
            drills.any { it.status == DebugLabStatus.BLOCKED } -> DebugLabStatus.WARNING
            else -> DebugLabStatus.PASSED
        }
        return DatabasePortabilityLabReport(
            database = database,
            portabilityStatus = aggregate,
            portabilityMessage = when (aggregate) {
                DebugLabStatus.PASSED -> PortabilityLabMessage.NONE
                DebugLabStatus.WARNING -> PortabilityLabMessage.DRILL_BLOCKED
                else -> PortabilityLabMessage.DRILL_FAILED
            },
            portabilityGeneratedAtEpochMillis = generatedAtEpochMillis,
            drills = drills,
        )
    } finally {
        importedPlanId?.let { disposableId ->
            try {
                container.manageStudyPlan.deletePermanently(disposableId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: Error) {
                throw fatal
            } catch (_: Exception) {
                // The primary drill has already failed; leave the error to the outer lab boundary.
            }
        }
        createdSnapshotId?.let { snapshotId ->
            try {
                container.manageStudyPlanPortability.deleteSnapshot(snapshotId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: Error) {
                throw fatal
            } catch (_: Exception) {
                // The primary drill has already failed; leave the error to the outer lab boundary.
            }
        }
        password.fill('\u0000')
        wrongPassword.fill('\u0000')
        exported.delete()
        tampered.delete()
        directory.listFiles()?.takeIf { it.isEmpty() }?.let { directory.delete() }
    }
}

private fun drill(kind: PortabilityDrillKind, passed: Boolean): PortabilityDrillDiagnostic =
    PortabilityDrillDiagnostic(
        kind = kind,
        status = if (passed) DebugLabStatus.PASSED else DebugLabStatus.FAILED,
        destructive = false,
    )

private suspend fun expectedFailure(block: suspend () -> Unit): Boolean = try {
    block()
    false
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (fatal: Error) {
    throw fatal
} catch (_: Exception) {
    true
}
