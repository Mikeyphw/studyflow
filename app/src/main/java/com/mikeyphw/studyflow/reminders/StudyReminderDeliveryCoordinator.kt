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
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mikeyphw.studyflow.MainActivity
import com.mikeyphw.studyflow.R
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate
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
const val StudyReminderChannelId = "study_reminders"

class StudyReminderDeliveryCoordinator(
    private val context: Context,
    private val studyPlanRepository: StudyPlanRepository,
    private val studyScheduleRepository: StudyScheduleRepository,
    private val studyReviewRepository: StudyReviewRepository,
    private val studyPracticeRepository: StudyPracticeRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
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

    private suspend fun reconcilePlanInternal(planId: StudyPlanId) {
        val runtimePlan = buildRuntimePlan(planId)
        if (runtimePlan == null || runtimePlan.permissionRequired) {
            cancelPlanAlarms(planId)
            return
        }
        cancelPlanAlarms(planId)
        runtimePlan.notificationCandidates
            .filterNot(ReminderNotificationCandidate::suppressedByQuietHours)
            .forEach { candidate -> scheduleCandidate(planId, candidate) }
    }

    suspend fun deliver(planId: StudyPlanId, type: ReminderNotificationType) {
        val runtimePlan = buildRuntimePlan(planId) ?: return
        if (runtimePlan.permissionRequired) return
        val candidate = runtimePlan.notificationCandidates.firstOrNull { candidate -> candidate.type == type } ?: return
        if (candidate.suppressedByQuietHours) return
        postNotification(planId, candidate)
        if (StudyReminderPlanner.shouldRescheduleAfterDelivery(candidate, clock())) {
            scheduleCandidate(planId, candidate)
        }
        StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            StudyReminderChannelId,
            appContext.getString(R.string.notification_channel_study_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appContext.getString(R.string.notification_channel_study_reminders_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private suspend fun buildRuntimePlan(planId: StudyPlanId): StudyReminderPlan? {
        val plan = studyPlanRepository.observePlan(planId).first() ?: return null
        if (plan.status != StudyPlanStatus.ACTIVE) return null
        val settings = studyPlanRepository.observePlanSettings(planId).first()
        if (!settings.reviewNotificationsEnabled) return null
        val now = clock()
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
            ),
        )
    }

    private fun scheduleCandidate(planId: StudyPlanId, candidate: ReminderNotificationCandidate) {
        val triggerAt = candidate.scheduledAtEpochMillis.coerceAtLeast(clock() + MinimumFutureDelayMillis)
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            StudyReminderPlanner.PlannedActivityDeliveryGraceMillis,
            pendingIntent(planId, candidate.type),
        )
    }

    private fun cancelPlanAlarms(planId: StudyPlanId) {
        ReminderNotificationType.entries.forEach { type ->
            alarmManager.cancel(pendingIntent(planId, type))
        }
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(planId: StudyPlanId, candidate: ReminderNotificationCandidate) {
        if (!notificationsAllowed()) return
        val notification = NotificationCompat.Builder(appContext, StudyReminderChannelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(candidate.title)
            .setContentText(candidate.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(candidate.body))
            .setContentIntent(openAppPendingIntent(planId, candidate.type))
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(appContext).notify(
            notificationId(planId, candidate.type),
            notification,
        )
    }

    private fun notificationsAllowed(): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingIntent(planId: StudyPlanId, type: ReminderNotificationType): PendingIntent {
        val intent = Intent(appContext, StudyReminderReceiver::class.java).apply {
            action = StudyReminderAction
            putExtra(StudyReminderExtraPlanId, planId.value)
            putExtra(StudyReminderExtraType, type.name)
        }
        return PendingIntent.getBroadcast(
            appContext,
            notificationId(planId, type),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(planId: StudyPlanId, type: ReminderNotificationType): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(StudyReminderExtraPlanId, planId.value)
            putExtra(StudyReminderExtraType, type.name)
        }
        return PendingIntent.getActivity(
            appContext,
            notificationId(planId, type),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun currentMinuteOfDay(): Int = Calendar.getInstance().run {
        get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
    }
}

private fun notificationId(planId: StudyPlanId, type: ReminderNotificationType): Int =
    studyReminderRequestCode(planId, type)

internal fun studyReminderRequestCode(planId: StudyPlanId, type: ReminderNotificationType): Int =
    24_000 + kotlin.math.abs((planId.value + type.name).hashCode() % 10_000)

internal fun studyReminderPendingIntentIdentityExists(
    context: Context,
    planId: StudyPlanId,
    type: ReminderNotificationType,
): Boolean {
    val intent = Intent(context.applicationContext, StudyReminderReceiver::class.java).apply {
        action = StudyReminderAction
        putExtra(StudyReminderExtraPlanId, planId.value)
        putExtra(StudyReminderExtraType, type.name)
    }
    return PendingIntent.getBroadcast(
        context.applicationContext,
        studyReminderRequestCode(planId, type),
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null
}
