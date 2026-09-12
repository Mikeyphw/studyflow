package com.mikeyphw.studyflow

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticSeverity
import com.mikeyphw.studyflow.core.diagnostics.ReminderCandidateDiagnostic
import com.mikeyphw.studyflow.core.diagnostics.ReminderCandidateKind
import com.mikeyphw.studyflow.core.diagnostics.ReminderLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.ReminderLabMessage
import com.mikeyphw.studyflow.core.diagnostics.ReminderNotificationLabReport
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.domain.StudyReminderInput
import com.mikeyphw.studyflow.core.domain.StudyReminderPlanner
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.reminders.StudyReminderChannelId
import com.mikeyphw.studyflow.reminders.StudyReminderOccurrenceStore
import com.mikeyphw.studyflow.reminders.studyReminderPendingIntentIdentityExists
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val MinimumFutureDelayMillis = 60_000L
private const val NotificationImportanceNone = 0

internal fun interface ReminderLabInspectOperation {
    suspend fun inspect(planId: StudyPlanId?): ReminderNotificationLabReport
}

internal class StudyFlowReminderNotificationLab(
    private val inspectOperation: ReminderLabInspectOperation,
    private val reconcileOperation: suspend (StudyPlanId) -> Unit,
    private val notificationOperation: suspend (StudyPlanId) -> Boolean,
    private val defaultPlanId: () -> StudyPlanId?,
    private val recorder: DiagnosticRecorder,
    private val executionContext: CoroutineContext = Dispatchers.IO,
) {
    constructor(
        context: Context,
        container: AppContainer,
        defaultPlanId: () -> StudyPlanId?,
        executionContext: CoroutineContext = Dispatchers.IO,
    ) : this(
        inspectOperation = AndroidReminderLabInspectOperation(context.applicationContext, container),
        reconcileOperation = container.reminderDeliveryCoordinator::reconcilePlan,
        notificationOperation = { planId ->
            runProductionPipelineNotificationCanary(context.applicationContext, container, planId)
        },
        defaultPlanId = defaultPlanId,
        recorder = container.diagnosticRecorder,
        executionContext = executionContext,
    )

    suspend fun inspect(): Pair<ReminderNotificationLabReport, ReminderLabActionResult> =
        withContext(executionContext) {
            recordStarted(DiagnosticArea.REMINDERS, "reminder-lab-inspect")
            try {
                val result = inspectOperation.inspect(defaultPlanId()) to ReminderLabActionResult.INSPECTED
                recordSucceeded(DiagnosticArea.REMINDERS, "reminder-lab-inspect")
                result
            } catch (cancelled: CancellationException) {
                recordCancelled(DiagnosticArea.REMINDERS, "reminder-lab-inspect")
                throw cancelled
            } catch (failure: Exception) {
                recordFailed(DiagnosticArea.REMINDERS, "reminder-lab-inspect", failure)
                ReminderNotificationLabReport(
                    status = DebugLabStatus.FAILED,
                    message = ReminderLabMessage.INSPECTION_FAILED,
                    inspectedAtEpochMillis = System.currentTimeMillis(),
                    apiLevel = Build.VERSION.SDK_INT,
                ) to ReminderLabActionResult.FAILED
            }
        }

    suspend fun reconcile(): ReminderLabActionResult = withContext(executionContext) {
        val action = "reminder-lab-reconcile"
        recordStarted(DiagnosticArea.REMINDERS, action)
        val planId = defaultPlanId()
        if (planId == null) {
            recorder.record(
                area = DiagnosticArea.REMINDERS,
                action = action,
                outcome = DiagnosticOutcome.BLOCKED,
                severity = DiagnosticSeverity.WARNING,
            )
            return@withContext ReminderLabActionResult.MISSING_DEFAULT_PLAN
        }
        try {
            reconcileOperation(planId)
            recordSucceeded(DiagnosticArea.REMINDERS, action)
            ReminderLabActionResult.RECONCILED
        } catch (cancelled: CancellationException) {
            recordCancelled(DiagnosticArea.REMINDERS, action)
            throw cancelled
        } catch (failure: Exception) {
            recordFailed(DiagnosticArea.REMINDERS, action, failure)
            ReminderLabActionResult.FAILED
        }
    }

    suspend fun postDiagnosticNotification(): ReminderLabActionResult = withContext(executionContext) {
        val action = "reminder-lab-diagnostic-notification"
        recordStarted(DiagnosticArea.NOTIFICATIONS, action)
        val planId = defaultPlanId()
        if (planId == null) {
            recorder.record(
                area = DiagnosticArea.NOTIFICATIONS,
                action = action,
                outcome = DiagnosticOutcome.BLOCKED,
                severity = DiagnosticSeverity.WARNING,
            )
            return@withContext ReminderLabActionResult.MISSING_DEFAULT_PLAN
        }
        try {
            if (notificationOperation(planId)) {
                recordSucceeded(DiagnosticArea.NOTIFICATIONS, action)
                ReminderLabActionResult.DIAGNOSTIC_NOTIFICATION_POSTED
            } else {
                recorder.record(
                    area = DiagnosticArea.NOTIFICATIONS,
                    action = action,
                    outcome = DiagnosticOutcome.BLOCKED,
                    severity = DiagnosticSeverity.WARNING,
                )
                ReminderLabActionResult.DIAGNOSTIC_NOTIFICATION_BLOCKED
            }
        } catch (cancelled: CancellationException) {
            recordCancelled(DiagnosticArea.NOTIFICATIONS, action)
            throw cancelled
        } catch (failure: Exception) {
            recordFailed(DiagnosticArea.NOTIFICATIONS, action, failure)
            ReminderLabActionResult.FAILED
        }
    }

    private fun recordStarted(area: DiagnosticArea, action: String) {
        recorder.record(
            area = area,
            action = action,
            outcome = DiagnosticOutcome.STARTED,
        )
    }

    private fun recordSucceeded(area: DiagnosticArea, action: String) {
        recorder.record(
            area = area,
            action = action,
            outcome = DiagnosticOutcome.SUCCEEDED,
        )
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

private class AndroidReminderLabInspectOperation(
    context: Context,
    private val container: AppContainer,
    private val clock: () -> Long = System::currentTimeMillis,
) : ReminderLabInspectOperation {
    private val appContext = context.applicationContext

    override suspend fun inspect(planId: StudyPlanId?): ReminderNotificationLabReport {
        val now = clock()
        val attentionDiagnostics = container.attentionLedger.diagnostics()
        if (planId == null) {
            return ReminderNotificationLabReport(
                status = DebugLabStatus.BLOCKED,
                message = ReminderLabMessage.MISSING_DEFAULT_PLAN,
                inspectedAtEpochMillis = now,
                apiLevel = Build.VERSION.SDK_INT,
                attentionLedgerEntryCount = attentionDiagnostics.entryCount,
                attentionLedgerActiveCount = attentionDiagnostics.activeCount,
                attentionLedgerTerminalCount = attentionDiagnostics.terminalCount,
                attentionLedgerSourceSequenceCount = attentionDiagnostics.sourceSequenceCount,
                attentionLedgerLegacyAliasCount = attentionDiagnostics.legacyDeliveredAliasCount,
                attentionLedgerRecoveredFromInvalidState = attentionDiagnostics.loadRecoveredFromInvalidState,
            )
        }
        val plan = container.manageStudyPlan.observePlan(planId).first()
            ?: return ReminderNotificationLabReport(
                status = DebugLabStatus.BLOCKED,
                message = ReminderLabMessage.PLAN_UNAVAILABLE,
                inspectedAtEpochMillis = now,
                apiLevel = Build.VERSION.SDK_INT,
                attentionLedgerEntryCount = attentionDiagnostics.entryCount,
                attentionLedgerActiveCount = attentionDiagnostics.activeCount,
                attentionLedgerTerminalCount = attentionDiagnostics.terminalCount,
                attentionLedgerSourceSequenceCount = attentionDiagnostics.sourceSequenceCount,
                attentionLedgerLegacyAliasCount = attentionDiagnostics.legacyDeliveredAliasCount,
                attentionLedgerRecoveredFromInvalidState = attentionDiagnostics.loadRecoveredFromInvalidState,
            )
        if (plan.status != StudyPlanStatus.ACTIVE) {
            return ReminderNotificationLabReport(
                status = DebugLabStatus.WARNING,
                message = ReminderLabMessage.PLAN_NOT_ACTIVE,
                inspectedAtEpochMillis = now,
                apiLevel = Build.VERSION.SDK_INT,
                attentionLedgerEntryCount = attentionDiagnostics.entryCount,
                attentionLedgerActiveCount = attentionDiagnostics.activeCount,
                attentionLedgerTerminalCount = attentionDiagnostics.terminalCount,
                attentionLedgerSourceSequenceCount = attentionDiagnostics.sourceSequenceCount,
                attentionLedgerLegacyAliasCount = attentionDiagnostics.legacyDeliveredAliasCount,
                attentionLedgerRecoveredFromInvalidState = attentionDiagnostics.loadRecoveredFromInvalidState,
            )
        }
        val settings = requireNotNull(container.manageStudyPlan.observeSettings(planId)
            .first()) { "Study Plan settings are unavailable." }
        val schedule = container.manageStudySchedule.observeSchedule(planId).first()
        val reviewQueue = container.manageStudyReview.observeQueue(planId).first()
        val permissionGranted = notificationPermissionGranted(appContext)
        val appNotificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(StudyReminderChannelId)
        } else {
            null
        }
        val channelImportance = notificationChannelImportance(channel)
        val planResult = StudyReminderPlanner.buildPlan(
            StudyReminderInput(
                plan = plan,
                settings = settings,
                schedule = schedule,
                reviewQueue = reviewQueue,
                nowEpochMillis = now,
                minuteOfDay = currentMinuteOfDay(),
                apiLevel = Build.VERSION.SDK_INT,
                notificationPermissionGranted = permissionGranted,
            ),
        )
        val occurrenceStore = StudyReminderOccurrenceStore(appContext)
        val scheduledOccurrences = occurrenceStore.scheduled(planId)
            .associateBy { occurrence -> occurrence.occurrenceId }
        val pipelineCanaryVerified = occurrenceStore.productionPipelineCanaryAt(planId)
            ?.let { verifiedAt -> now - verifiedAt in 0..ProductionPipelineCanaryFreshnessMillis } == true
        val candidates = planResult.notificationCandidates.map { candidate ->
            val scheduled = scheduledOccurrences[candidate.occurrenceId]
            ReminderCandidateDiagnostic(
                kind = candidate.type.toDiagnosticKind(),
                scheduledAtEpochMillis = candidate.scheduledAtEpochMillis,
                effectiveTriggerAtEpochMillis = candidate.fireAtEpochMillis
                    .coerceAtLeast(now + MinimumFutureDelayMillis),
                deliveryWindowMillis = StudyReminderPlanner.PlannedActivityDeliveryGraceMillis,
                lateDeliveryGraceMillis = StudyReminderPlanner.PlannedActivityLateDeliveryGraceMillis,
                suppressedByQuietHours = false,
                delayedByQuietHours = candidate.delayedByQuietHours,
                pendingIntentIdentityExists = studyReminderPendingIntentIdentityExists(
                    appContext,
                    planId,
                    candidate.type,
                    candidate.occurrenceId,
                ),
                scheduledBookkeepingPresent = scheduled?.let { occurrence ->
                    occurrence.type == candidate.type &&
                        occurrence.scheduledAtEpochMillis == candidate.scheduledAtEpochMillis &&
                        occurrence.fireAtEpochMillis == candidate.fireAtEpochMillis
                } == true,
                requestCode = 0,
            )
        }
        val desiredIds = planResult.notificationCandidates.mapTo(linkedSetOf()) { it.occurrenceId }
        val recordedIds = scheduledOccurrences.keys
        val plannerStatus = if (candidates.isEmpty()) DebugLabStatus.EMPTY else DebugLabStatus.PASSED
        val platformStatus = when {
            !permissionGranted -> DebugLabStatus.WARNING
            !appNotificationsEnabled -> DebugLabStatus.WARNING
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channel == null -> DebugLabStatus.WARNING
            channelImportance == NotificationImportanceNone -> DebugLabStatus.WARNING
            else -> DebugLabStatus.PASSED
        }
        val schedulingAligned = desiredIds == recordedIds && candidates.all { candidate ->
            candidate.scheduledBookkeepingPresent && candidate.pendingIntentIdentityExists
        }
        val schedulingStatus = when {
            candidates.isEmpty() && recordedIds.isEmpty() -> DebugLabStatus.NOT_APPLICABLE
            schedulingAligned && pipelineCanaryVerified -> DebugLabStatus.PASSED
            else -> DebugLabStatus.WARNING
        }
        val message = when {
            !permissionGranted -> ReminderLabMessage.NOTIFICATION_PERMISSION_DENIED
            !appNotificationsEnabled -> ReminderLabMessage.NOTIFICATIONS_BLOCKED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channel == null -> ReminderLabMessage.CHANNEL_MISSING
            channelImportance == NotificationImportanceNone -> ReminderLabMessage.CHANNEL_BLOCKED
            candidates.isEmpty() -> ReminderLabMessage.NO_CANDIDATES
            else -> ReminderLabMessage.NONE
        }
        val status = when {
            platformStatus == DebugLabStatus.FAILED || schedulingStatus == DebugLabStatus.FAILED ->
                DebugLabStatus.FAILED
            platformStatus == DebugLabStatus.WARNING || schedulingStatus == DebugLabStatus.WARNING ->
                DebugLabStatus.WARNING
            plannerStatus == DebugLabStatus.EMPTY -> DebugLabStatus.EMPTY
            else -> DebugLabStatus.PASSED
        }
        return ReminderNotificationLabReport(
            status = status,
            message = message,
            inspectedAtEpochMillis = now,
            apiLevel = Build.VERSION.SDK_INT,
            attentionLedgerEntryCount = attentionDiagnostics.entryCount,
            attentionLedgerActiveCount = attentionDiagnostics.activeCount,
            attentionLedgerTerminalCount = attentionDiagnostics.terminalCount,
            attentionLedgerSourceSequenceCount = attentionDiagnostics.sourceSequenceCount,
            attentionLedgerLegacyAliasCount = attentionDiagnostics.legacyDeliveredAliasCount,
            attentionLedgerRecoveredFromInvalidState = attentionDiagnostics.loadRecoveredFromInvalidState,
            notificationsEnabled = appNotificationsEnabled,
            notificationPermissionGranted = permissionGranted,
            channelPresent = channel != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O,
            channelBlocked = channelImportance == NotificationImportanceNone,
            channelImportance = channelImportance,
            remindersEnabled = true,
            quietHoursActive = planResult.quietHoursActive,
            exactAlarmUsed = false,
            plannerStatus = plannerStatus,
            platformStatus = platformStatus,
            schedulingStatus = schedulingStatus,
            productionPipelineVerified = pipelineCanaryVerified,
            candidates = candidates,
        )
    }
}

private fun ReminderNotificationType.toDiagnosticKind(): ReminderCandidateKind = when (this) {
    ReminderNotificationType.DUE_REVIEWS -> ReminderCandidateKind.DUE_REVIEWS
    ReminderNotificationType.PLANNED_ACTIVITY -> ReminderCandidateKind.PLANNED_ACTIVITY
    ReminderNotificationType.MISSED_ACTIVITY -> ReminderCandidateKind.MISSED_ACTIVITY
    ReminderNotificationType.DAILY_SUMMARY -> ReminderCandidateKind.DAILY_SUMMARY
}

private fun currentMinuteOfDay(): Int = Calendar.getInstance().run {
    get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
}

private fun notificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

@SuppressLint("NewApi")
private fun notificationChannelImportance(notificationChannel: android.app.NotificationChannel?): Int? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationChannel?.importance
    } else {
        null
    }

@SuppressLint("MissingPermission")
private suspend fun runProductionPipelineNotificationCanary(
    context: Context,
    container: AppContainer,
    planId: StudyPlanId,
): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    if (!notificationPermissionGranted(context)) return false
    container.reminderDeliveryCoordinator.createNotificationChannels()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(StudyReminderChannelId)
        if (channel == null || notificationChannelImportance(channel) == NotificationImportanceNone) return false
    }

    val occurrenceStore = StudyReminderOccurrenceStore(context)
    val now = System.currentTimeMillis()
    val occurrenceId = "debug-pipeline-${UUID.randomUUID()}"
    val candidate = com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate(
        type = ReminderNotificationType.DAILY_SUMMARY,
        occurrenceId = occurrenceId,
        content = com.mikeyphw.studyflow.core.domain.ReminderNotificationContent(
            kind = com.mikeyphw.studyflow.core.domain.ReminderNotificationContentKind.DAILY_SUMMARY,
            userLabel = "StudyFlow",
        ),
        scheduledAtEpochMillis = now,
        fireAtEpochMillis = now,
        delayedByQuietHours = false,
    )
    occurrenceStore.addScheduled(planId, candidate)
    return try {
        context.sendBroadcast(
            com.mikeyphw.studyflow.reminders.reminderIntent(
                context = context,
                planId = planId,
                type = ReminderNotificationType.DAILY_SUMMARY,
                occurrenceId = occurrenceId,
            ),
        )
        val delivered = waitForCanaryDelivery(occurrenceStore, planId, occurrenceId)
        if (delivered) occurrenceStore.markProductionPipelineCanary(planId, System.currentTimeMillis())
        delivered
    } finally {
        occurrenceStore.removeScheduled(planId, occurrenceId)
        occurrenceStore.removeDelivered(planId, occurrenceId)
        NotificationManagerCompat.from(context).cancel(occurrenceId, 0)
    }
}

private suspend fun waitForCanaryDelivery(
    occurrenceStore: StudyReminderOccurrenceStore,
    planId: StudyPlanId,
    occurrenceId: String,
): Boolean {
    repeat(ProductionPipelineCanaryPollCount) {
        if (occurrenceId in occurrenceStore.deliveredOccurrenceIds(planId)) return true
        delay(ProductionPipelineCanaryPollMillis)
    }
    return occurrenceId in occurrenceStore.deliveredOccurrenceIds(planId)
}

private const val ProductionPipelineCanaryPollCount = 100
private const val ProductionPipelineCanaryPollMillis = 50L
private const val ProductionPipelineCanaryFreshnessMillis = 24L * 60L * 60L * 1_000L
