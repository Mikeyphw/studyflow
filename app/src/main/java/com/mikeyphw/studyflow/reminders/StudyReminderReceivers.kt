package com.mikeyphw.studyflow.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mikeyphw.studyflow.BuildConfig
import com.mikeyphw.studyflow.core.data.createStudyFlowDataLayer
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
                createCoordinator(context).deliver(StudyPlanId(planId), type)
            } finally {
                result.finish()
            }
        }
    }
}

class StudyReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        ReminderReceiverScope.launch {
            try {
                createCoordinator(context).reconcileAll()
                StudyFlowWidgetUpdateCoordinator.refreshAll(context)
            } finally {
                result.finish()
            }
        }
    }
}

fun createCoordinator(context: Context): StudyReminderDeliveryCoordinator {
    val dataLayer = createStudyFlowDataLayer(context.applicationContext, BuildConfig.VERSION_NAME)
    return StudyReminderDeliveryCoordinator(
        context = context.applicationContext,
        studyPlanRepository = dataLayer.studyPlanRepository,
        studyScheduleRepository = dataLayer.studyScheduleRepository,
        studyReviewRepository = dataLayer.studyReviewRepository,
        studyPracticeRepository = dataLayer.studyPracticeRepository,
    )
}
