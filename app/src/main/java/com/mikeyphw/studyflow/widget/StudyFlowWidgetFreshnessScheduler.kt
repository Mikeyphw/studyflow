package com.mikeyphw.studyflow.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator
import com.mikeyphw.studyflow.reminders.StudyReminderWakeupContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object StudyFlowWidgetFreshnessScheduler {
    private const val WindowMillis = 15 * 60 * 1_000L
    private const val StoreName = "studyflow_widget_freshness_v2"
    private const val NextRefreshKey = "next_refresh_epoch_millis"

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(context: Context, nextEpochMillis: Long?) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val operation = refreshPendingIntent(appContext)
        alarmManager.cancel(operation)
        if (nextEpochMillis == null) {
            recordNext(appContext, null)
            return
        }
        val now = System.currentTimeMillis()
        val trigger = nextEpochMillis.coerceAtLeast(now + StudyReminderWakeupContracts.MinimumImmediateWakeupDelayMillis)
        if (StudyReminderWakeupContracts.requiresExactWidgetRefresh(trigger, now)) {
            when {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                    alarmManager.setWindow(AlarmManager.RTC_WAKEUP, trigger, StudyReminderWakeupContracts.NearTermWidgetExactThresholdMillis, operation)
                }
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
                }
                else -> alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, operation)
            }
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, trigger, WindowMillis, operation)
        }
        recordNext(appContext, trigger)
    }

    fun cancel(context: Context) = schedule(context, null)

    fun nextScheduledRefresh(context: Context): Long? = context.applicationContext
        .getSharedPreferences(StoreName, Context.MODE_PRIVATE)
        .getLong(NextRefreshKey, -1L)
        .takeIf { it > 0L }

    private fun recordNext(context: Context, value: Long?) {
        context.getSharedPreferences(StoreName, Context.MODE_PRIVATE).edit().apply {
            if (value == null) remove(NextRefreshKey) else putLong(NextRefreshKey, value)
        }.apply()
    }

    private fun refreshPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, StudyFlowWidgetRefreshReceiver::class.java).apply {
            action = StudyFlowWidgetRefreshAction
            data = Uri.parse("studyflow://widget-refresh/${context.packageName}")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private val WidgetFreshnessReceiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class StudyFlowWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyFlowWidgetRefreshAction) return
        val result = goAsync()
        WidgetFreshnessReceiverScope.launch {
            try {
                StudyFlowWidgetUpdateCoordinator.refreshAllAndAwait(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}

internal const val StudyFlowWidgetRefreshAction = "com.mikeyphw.studyflow.action.WIDGET_REFRESH"
