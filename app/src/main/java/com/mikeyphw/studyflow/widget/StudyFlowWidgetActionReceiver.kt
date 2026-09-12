package com.mikeyphw.studyflow.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mikeyphw.studyflow.requireStudyFlowAppContainer
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator
import com.mikeyphw.studyflow.reminders.studyReminderOpenIntent
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val WidgetActionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class StudyFlowWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyFlowWidgetAction) return
        val widgetId = intent.getIntExtra(ExtraWidgetId, -1).takeIf { it >= 0 } ?: return
        val action = intent.getStringExtra(ExtraWidgetAction)
            ?.let { raw -> StudyFlowWidgetActionType.entries.firstOrNull { it.name == raw } }
            ?: return
        val result = goAsync()
        WidgetActionScope.launch {
            try {
                val container = context.requireStudyFlowAppContainer()
                val configuration = StudyFlowWidgetConfigurationStore(context).load(widgetId)
                val planId = resolvePlanId(context, configuration) ?: return@launch
                when (action) {
                    StudyFlowWidgetActionType.START_NEXT -> {
                        val active = container.manageStudySession.observeActiveSessions().first().firstOrNull()
                        if (active != null) {
                            context.startActivity(
                                studyReminderOpenIntent(
                                    context = context,
                                    planId = active.studyPlanId,
                                    type = ReminderNotificationType.SESSION_ENDING,
                                    identity = Uri.parse("studyflow://widget-active-session/${context.packageName}/$widgetId"),
                                    sessionId = active.id.value,
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } else {
                            val next = container.manageStudySchedule.observeSchedule(planId).first().plannedActivities
                                .asSequence()
                                .filter { it.status == PlannedActivityStatus.PLANNED }
                                .filter { (it.scheduledStartEpochMillis ?: Long.MAX_VALUE) >= System.currentTimeMillis() - 2 * 60 * 60 * 1_000L }
                                .minWithOrNull(compareBy({ it.scheduledStartEpochMillis ?: Long.MAX_VALUE }, { it.id.value }))
                            if (next != null) {
                                container.manageStudySession.startFromActivity(
                                    sessionId = StudySessionId("widget-${UUID.randomUUID()}"),
                                    activity = next,
                                )
                            }
                        }
                    }
                    StudyFlowWidgetActionType.TOGGLE_SESSION -> {
                        val active = container.manageStudySession.observeActiveSessions().first()
                            .firstOrNull { it.studyPlanId == planId }
                        when (active?.status) {
                            StudySessionStatus.RUNNING -> container.manageStudySession.pause(planId, active.id)
                            StudySessionStatus.PAUSED -> container.manageStudySession.resume(planId, active.id)
                            else -> Unit
                        }
                    }
                }
            } finally {
                StudyFlowWidgetUpdateCoordinator.refreshAll(context.applicationContext)
                result.finish()
            }
        }
    }

    private suspend fun resolvePlanId(
        context: Context,
        configuration: StudyFlowWidgetConfiguration,
    ): StudyPlanId? {
        val container = context.requireStudyFlowAppContainer()
        val selected = configuration.studyPlanId?.let(::StudyPlanId)
            ?: container.appPreferencesRepository.preferences.first().defaultStudyPlanId
            ?: return null
        return selected.takeIf { planId ->
            container.manageStudyPlan.plans.first().any { plan ->
                plan.id == planId && plan.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED)
            }
        }
    }
}

enum class StudyFlowWidgetActionType {
    START_NEXT,
    TOGGLE_SESSION,
}

internal const val StudyFlowWidgetAction = "com.mikeyphw.studyflow.action.WIDGET_MUTATION"
internal const val ExtraWidgetId = "studyflow_widget_id"
internal const val ExtraWidgetAction = "studyflow_widget_action"
