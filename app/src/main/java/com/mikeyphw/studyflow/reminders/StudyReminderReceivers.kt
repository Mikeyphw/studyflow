package com.mikeyphw.studyflow.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mikeyphw.studyflow.requireStudyFlowAppContainer
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val ReminderReceiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyReminderAction) return
        val planId = intent.getStringExtra(StudyReminderExtraPlanId)?.takeIf(String::isNotBlank) ?: return
        val type = intent.getStringExtra(StudyReminderExtraType)
            ?.let { raw -> runCatching { ReminderNotificationType.valueOf(raw) }.getOrNull() }
            ?: return
        val result = goAsync()
        ReminderReceiverScope.launch {
            try {
                context.requireStudyFlowAppContainer().reminderDeliveryCoordinator.deliver(StudyPlanId(planId), type)
            } finally {
                result.finish()
            }
        }
    }
}

class StudyReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ReconcileReminderScheduleActions) return
        val result = goAsync()
        ReminderReceiverScope.launch {
            try {
                context.requireStudyFlowAppContainer().reminderDeliveryCoordinator.reconcileAll()
            } finally {
                result.finish()
            }
        }
    }
}

private val ReconcileReminderScheduleActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_DATE_CHANGED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
)
