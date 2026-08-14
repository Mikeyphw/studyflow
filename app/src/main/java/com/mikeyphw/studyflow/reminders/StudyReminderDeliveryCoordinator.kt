package com.mikeyphw.studyflow.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mikeyphw.studyflow.MainActivity
import com.mikeyphw.studyflow.R
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContent
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContentKind
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.domain.StudyPracticeRepository
import com.mikeyphw.studyflow.core.domain.StudyReminderInput
import com.mikeyphw.studyflow.core.domain.StudyReminderPlan
import com.mikeyphw.studyflow.core.domain.StudyReminderPlanner
import com.mikeyphw.studyflow.core.domain.StudyReviewRepository
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import java.util.Calendar
import kotlinx.coroutines.flow.first

private const val MinimumFutureDelayMillis = 60 * 1000L
const val StudyReminderAction = "com.mikeyphw.studyflow.action.STUDY_REMINDER"
const val StudyReminderExtraPlanId = "plan_id"
const val StudyReminderExtraType = "reminder_type"
const val StudyReminderExtraOccurrenceId = "reminder_occurrence_id"
const val StudyReminderChannelId = "study_reminders"

class StudyReminderDeliveryCoordinator(
    private val context: Context,
    private val studyPlanRepository: StudyPlanRepository,
    private val studyScheduleRepository: StudyScheduleRepository,
    private val studyReviewRepository: StudyReviewRepository,
    private val studyPracticeRepository: StudyPracticeRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val occurrenceStore: StudyReminderOccurrenceStore = StudyReminderOccurrenceStore(context),
) {
    private val appContext = context.applicationContext
    private val alarmManager = requireNotNull(appContext.getSystemService(AlarmManager::class.java))
    private val reviewManager = ManageStudyReview(studyReviewRepository, studyPracticeRepository, clock)

    suspend fun reconcileAll() {
        createNotificationChannels()
        val plans = studyPlanRepository.plans.first()
        plans.forEach { plan -> reconcilePlanInternal(plan.id) }
        StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
    }

    suspend fun reconcilePlan(planId: StudyPlanId) {
        reconcilePlanInternal(planId)
        StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
    }

    suspend fun refreshLocalizedPresentation() {
        createNotificationChannels()
        reconcileAll()
    }

    private suspend fun reconcilePlanInternal(planId: StudyPlanId) {
        if (!notificationsAllowed()) {
            cancelPlanAlarms(planId, clearBookkeeping = true)
            return
        }
        // Build first so the same durable occurrence keeps its original scheduled timestamp.
        // This also avoids deleting restart recovery state before repository reads complete.
        val runtimePlan = buildRuntimePlan(planId) ?: run {
            cancelPlanAlarms(planId, clearBookkeeping = true)
            return
        }
        if (runtimePlan.permissionRequired) {
            cancelPlanAlarms(planId, clearBookkeeping = true)
            return
        }
        cancelPlanAlarms(planId, clearBookkeeping = false)
        // Persist the desired schedule before installing Android alarms. If the process dies while
        // alarms are being installed, startup reconciliation can cancel/rebuild every desired identity;
        // no newly scheduled PendingIntent can exist outside durable bookkeeping.
        occurrenceStore.replaceScheduled(planId, runtimePlan.notificationCandidates)
        runtimePlan.notificationCandidates.forEach { candidate -> scheduleCandidate(planId, candidate) }
    }

    suspend fun deliver(
        planId: StudyPlanId,
        type: ReminderNotificationType,
        occurrenceId: String,
    ) {
        if (!notificationsAllowed()) return
        val scheduled = occurrenceStore.scheduled(planId).firstOrNull { occurrence ->
            occurrence.type == type && occurrence.occurrenceId == occurrenceId
        } ?: return

        val runtimePlan = buildRuntimePlan(planId, allowOccurrenceId = occurrenceId) ?: return
        if (runtimePlan.permissionRequired) return
        if (runtimePlan.quietHoursActive) {
            reconcilePlanInternal(planId)
            return
        }

        val candidate = runtimePlan.notificationCandidates.firstOrNull { current ->
            current.type == type && current.occurrenceId == occurrenceId
        } ?: if (type == ReminderNotificationType.DAILY_SUMMARY) {
            scheduled.asDailyCandidate(runtimePlan.studyPlanName)
        } else {
            reconcilePlanInternal(planId)
            return
        }

        if (!postNotification(planId, candidate)) return
        occurrenceStore.markDelivered(planId, occurrenceId)
        reconcilePlanInternal(planId)
        StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            StudyReminderChannelId,
            appContext.getString(R.string.notification_channel_study_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appContext.getString(R.string.notification_channel_study_reminders_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private suspend fun buildRuntimePlan(
        planId: StudyPlanId,
        allowOccurrenceId: String? = null,
    ): StudyReminderPlan? {
        val plan = studyPlanRepository.observePlan(planId).first() ?: return null
        if (plan.status != StudyPlanStatus.ACTIVE) return null
        val settings = studyPlanRepository.observeStoredPlanSettings(planId).first() ?: return null
        val now = clock()
        val delivered = occurrenceStore.deliveredOccurrenceIds(planId).toMutableSet().apply {
            allowOccurrenceId?.let(::remove)
        }
        val persistedScheduledAt = occurrenceStore.scheduled(planId).associate { occurrence ->
            occurrence.occurrenceId to occurrence.scheduledAtEpochMillis
        }
        return StudyReminderPlanner.buildPlan(
            StudyReminderInput(
                plan = plan,
                settings = settings,
                schedule = studyScheduleRepository.observeSchedule(planId).first(),
                reviewQueue = reviewManager.observeQueue(planId).first(),
                nowEpochMillis = now,
                minuteOfDay = currentMinuteOfDay(),
                apiLevel = Build.VERSION.SDK_INT,
                notificationPermissionGranted = notificationsAllowed(),
                deliveredOccurrenceIds = delivered,
                persistedScheduledAtByOccurrenceId = persistedScheduledAt,
            ),
        )
    }

    private fun scheduleCandidate(planId: StudyPlanId, candidate: ReminderNotificationCandidate) {
        val triggerAt = candidate.fireAtEpochMillis.coerceAtLeast(clock() + MinimumFutureDelayMillis)
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            StudyReminderPlanner.PlannedActivityDeliveryGraceMillis,
            pendingIntent(planId, candidate.type, candidate.occurrenceId),
        )
    }

    private fun cancelPlanAlarms(planId: StudyPlanId, clearBookkeeping: Boolean) {
        occurrenceStore.scheduled(planId).forEach { occurrence ->
            alarmManager.cancel(pendingIntent(planId, occurrence.type, occurrence.occurrenceId))
        }
        if (clearBookkeeping) occurrenceStore.clearScheduled(planId)
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(planId: StudyPlanId, candidate: ReminderNotificationCandidate): Boolean {
        if (!notificationsAllowed()) return false
        val text = localizedText(candidate)
        val notification = NotificationCompat.Builder(appContext, StudyReminderChannelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(openAppPendingIntent(planId, candidate.type, candidate.occurrenceId))
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(appContext).notify(candidate.occurrenceId, 0, notification)
        return true
    }

    private fun localizedText(candidate: ReminderNotificationCandidate): LocalizedReminderText = when (
        candidate.content.kind
    ) {
        ReminderNotificationContentKind.DUE_REVIEWS -> {
            val count = candidate.content.count ?: 0
            LocalizedReminderText(
                title = appContext.getString(R.string.reminder_notification_due_reviews_title),
                body = appContext.resources.getQuantityString(
                    R.plurals.reminder_notification_due_reviews_body,
                    count,
                    count,
                ),
            )
        }
        ReminderNotificationContentKind.PLANNED_ACTIVITY -> LocalizedReminderText(
            title = candidate.content.userLabel.orEmpty(),
            body = appContext.getString(R.string.reminder_notification_planned_activity_body),
        )
        ReminderNotificationContentKind.MISSED_ACTIVITY -> {
            val count = candidate.content.count ?: 0
            LocalizedReminderText(
                title = appContext.getString(R.string.reminder_notification_missed_activity_title),
                body = appContext.resources.getQuantityString(
                    R.plurals.reminder_notification_missed_activity_body,
                    count,
                    count,
                ),
            )
        }
        ReminderNotificationContentKind.DAILY_SUMMARY -> LocalizedReminderText(
            title = candidate.content.userLabel.orEmpty(),
            body = appContext.getString(R.string.reminder_notification_daily_summary_body),
        )
    }

    private fun notificationsAllowed(): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingIntent(
        planId: StudyPlanId,
        type: ReminderNotificationType,
        occurrenceId: String,
    ): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        reminderIntent(appContext, planId, type, occurrenceId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openAppPendingIntent(
        planId: StudyPlanId,
        type: ReminderNotificationType,
        occurrenceId: String,
    ): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            data = studyReminderIdentityUri(appContext, planId, type, occurrenceId, openApp = true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(StudyReminderExtraPlanId, planId.value)
            putExtra(StudyReminderExtraType, type.name)
            putExtra(StudyReminderExtraOccurrenceId, occurrenceId)
        }
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun currentMinuteOfDay(): Int = Calendar.getInstance().run {
        get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
    }
}

private data class LocalizedReminderText(val title: String, val body: String)

private fun ScheduledReminderOccurrence.asDailyCandidate(planName: String): ReminderNotificationCandidate =
    ReminderNotificationCandidate(
        type = ReminderNotificationType.DAILY_SUMMARY,
        occurrenceId = occurrenceId,
        content = ReminderNotificationContent(
            kind = ReminderNotificationContentKind.DAILY_SUMMARY,
            userLabel = planName,
        ),
        scheduledAtEpochMillis = scheduledAtEpochMillis,
        fireAtEpochMillis = fireAtEpochMillis,
        delayedByQuietHours = fireAtEpochMillis != scheduledAtEpochMillis,
    )

internal fun reminderIntent(
    context: Context,
    planId: StudyPlanId,
    type: ReminderNotificationType,
    occurrenceId: String,
): Intent = Intent(context.applicationContext, StudyReminderReceiver::class.java).apply {
    action = StudyReminderAction
    data = studyReminderIdentityUri(context, planId, type, occurrenceId, openApp = false)
    putExtra(StudyReminderExtraPlanId, planId.value)
    putExtra(StudyReminderExtraType, type.name)
    putExtra(StudyReminderExtraOccurrenceId, occurrenceId)
}

internal fun studyReminderIdentityUri(
    context: Context,
    planId: StudyPlanId,
    type: ReminderNotificationType,
    occurrenceId: String,
    openApp: Boolean,
): Uri = Uri.Builder()
    .scheme("studyflow")
    .authority(if (openApp) "reminder-open" else "reminder-alarm")
    .appendPath(context.packageName)
    .appendPath(planId.value)
    .appendPath(type.name)
    .appendPath(occurrenceId)
    .build()

internal fun studyReminderPendingIntentIdentityExists(
    context: Context,
    planId: StudyPlanId,
    type: ReminderNotificationType,
    occurrenceId: String,
): Boolean = PendingIntent.getBroadcast(
    context.applicationContext,
    0,
    reminderIntent(context, planId, type, occurrenceId),
    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
) != null
