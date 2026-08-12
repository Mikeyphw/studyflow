package com.mikeyphw.studyflow

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
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
import com.mikeyphw.studyflow.reminders.studyReminderPendingIntentIdentityExists
import com.mikeyphw.studyflow.reminders.studyReminderRequestCode
import java.util.Calendar
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val MinimumFutureDelayMillis = 60_000L
private const val DiagnosticNotificationId = 23_971
private const val NotificationImportanceNone = 0

internal fun interface ReminderLabInspectOperation {
    suspend fun inspect(planId: StudyPlanId?): ReminderNotificationLabReport
}

internal class StudyFlowReminderNotificationLab(
    private val inspectOperation: ReminderLabInspectOperation,
    private val reconcileOperation: suspend (StudyPlanId) -> Unit,
    private val notificationOperation: suspend () -> Boolean,
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
        notificationOperation = { postDiagnosticNotification(context.applicationContext, container) },
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
        try {
            if (notificationOperation()) {
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
        if (planId == null) {
            return ReminderNotificationLabReport(
                status = DebugLabStatus.BLOCKED,
                message = ReminderLabMessage.MISSING_DEFAULT_PLAN,
                inspectedAtEpochMillis = now,
                apiLevel = Build.VERSION.SDK_INT,
            )
        }
        val plan = container.manageStudyPlan.observePlan(planId).first()
            ?: return ReminderNotificationLabReport(
                status = DebugLabStatus.BLOCKED,
                message = ReminderLabMessage.PLAN_UNAVAILABLE,
                inspectedAtEpochMillis = now,
                apiLevel = Build.VERSION.SDK_INT,
            )
        if (plan.status != StudyPlanStatus.ACTIVE) {
            return ReminderNotificationLabReport(
                status = DebugLabStatus.WARNING,
                message = ReminderLabMessage.PLAN_NOT_ACTIVE,
                inspectedAtEpochMillis = now,
                apiLevel = Build.VERSION.SDK_INT,
            )
        }
        val settings = container.manageStudyPlan.observeSettings(planId).first()
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
        val message = when {
            !settings.reviewNotificationsEnabled -> ReminderLabMessage.REMINDERS_DISABLED
            !permissionGranted -> ReminderLabMessage.NOTIFICATION_PERMISSION_DENIED
            !appNotificationsEnabled -> ReminderLabMessage.NOTIFICATIONS_BLOCKED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channel == null -> ReminderLabMessage.CHANNEL_MISSING
            channelImportance == NotificationImportanceNone -> ReminderLabMessage.CHANNEL_BLOCKED
            planResult.notificationCandidates.isEmpty() -> ReminderLabMessage.NO_CANDIDATES
            else -> ReminderLabMessage.NONE
        }
        val status = when (message) {
            ReminderLabMessage.NONE -> DebugLabStatus.PASSED
            ReminderLabMessage.NO_CANDIDATES -> DebugLabStatus.BLOCKED
            ReminderLabMessage.MISSING_DEFAULT_PLAN,
            ReminderLabMessage.PLAN_UNAVAILABLE -> DebugLabStatus.BLOCKED
            ReminderLabMessage.INSPECTION_FAILED -> DebugLabStatus.FAILED
            else -> DebugLabStatus.WARNING
        }
        return ReminderNotificationLabReport(
            status = status,
            message = message,
            inspectedAtEpochMillis = now,
            apiLevel = Build.VERSION.SDK_INT,
            notificationsEnabled = appNotificationsEnabled,
            notificationPermissionGranted = permissionGranted,
            channelPresent = channel != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O,
            channelBlocked = channelImportance == NotificationImportanceNone,
            channelImportance = channelImportance,
            remindersEnabled = settings.reviewNotificationsEnabled,
            quietHoursActive = planResult.quietHoursActive,
            exactAlarmUsed = false,
            candidates = planResult.notificationCandidates.map { candidate ->
                ReminderCandidateDiagnostic(
                    kind = candidate.type.toDiagnosticKind(),
                    scheduledAtEpochMillis = candidate.scheduledAtEpochMillis,
                    effectiveTriggerAtEpochMillis = candidate.scheduledAtEpochMillis
                        .coerceAtLeast(now + MinimumFutureDelayMillis),
                    deliveryWindowMillis = StudyReminderPlanner.PlannedActivityDeliveryGraceMillis,
                    lateDeliveryGraceMillis = StudyReminderPlanner.PlannedActivityLateDeliveryGraceMillis,
                    suppressedByQuietHours = candidate.suppressedByQuietHours,
                    pendingIntentIdentityExists = studyReminderPendingIntentIdentityExists(
                        appContext,
                        planId,
                        candidate.type,
                    ),
                    requestCode = studyReminderRequestCode(planId, candidate.type),
                )
            },
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
private fun postDiagnosticNotification(context: Context, container: AppContainer): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    if (!notificationPermissionGranted(context)) return false
    container.reminderDeliveryCoordinator.createNotificationChannels()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(StudyReminderChannelId)
        if (channel == null ||
            notificationChannelImportance(channel) == NotificationImportanceNone
        ) {
            return false
        }
    }
    val openIntent = PendingIntent.getActivity(
        context,
        DiagnosticNotificationId,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, StudyReminderChannelId)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(context.getString(R.string.debug_diagnostic_notification_title))
        .setContentText(context.getString(R.string.debug_diagnostic_notification_body))
        .setContentIntent(openIntent)
        .setAutoCancel(true)
        .setLocalOnly(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    NotificationManagerCompat.from(context).notify(DiagnosticNotificationId, notification)
    return true
}
