package com.mikeyphw.studyflow.reminders

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.mikeyphw.studyflow.requireStudyFlowAppContainer
import com.mikeyphw.studyflow.core.domain.StudyReminderInput
import com.mikeyphw.studyflow.core.domain.StudyReminderPlanner
import com.mikeyphw.studyflow.core.domain.TodayWidgetSummary
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.widget.StudyFlowTodayWidgetProvider
import com.mikeyphw.studyflow.widget.TodayWidgetRenderState
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val WidgetUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val WidgetRefreshRunning = AtomicBoolean(false)
private val WidgetRefreshDirty = AtomicBoolean(false)

object StudyFlowWidgetUpdateCoordinator {
    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        WidgetRefreshDirty.set(true)
        if (!WidgetRefreshRunning.compareAndSet(false, true)) return
        WidgetUpdateScope.launch {
            try {
                do {
                    WidgetRefreshDirty.set(false)
                    updateAllWidgets(appContext, buildState(appContext))
                } while (WidgetRefreshDirty.get())
            } finally {
                WidgetRefreshRunning.set(false)
                if (WidgetRefreshDirty.get()) refreshAll(appContext)
            }
        }
    }

    private fun updateAllWidgets(context: Context, state: TodayWidgetRenderState) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, StudyFlowTodayWidgetProvider::class.java)
        manager.getAppWidgetIds(component).forEach { id ->
            StudyFlowTodayWidgetProvider.updateWidget(context, manager, id, state)
        }
    }

    private suspend fun buildState(context: Context): TodayWidgetRenderState {
        val summary = buildSummary(context) ?: return TodayWidgetRenderState.NoPlan
        return TodayWidgetRenderState.Ready(summary)
    }

    private suspend fun buildSummary(context: Context): TodayWidgetSummary? {
        val container = context.requireStudyFlowAppContainer()
        val defaultPlanId = container.appPreferencesRepository.preferences.first().defaultStudyPlanId ?: return null
        val plan = resolveCanonicalWidgetPlan(container.manageStudyPlan.plans.first(), defaultPlanId) ?: return null
        val settings = container.manageStudyPlan.observeSettings(plan.id).first() ?: return null
        val calendar = Calendar.getInstance()
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return StudyReminderPlanner.buildPlan(
            StudyReminderInput(
                plan = plan,
                settings = settings,
                schedule = container.manageStudySchedule.observeSchedule(plan.id).first(),
                reviewQueue = container.manageStudyReview.observeQueue(plan.id).first(),
                nowEpochMillis = System.currentTimeMillis(),
                minuteOfDay = minuteOfDay,
                apiLevel = Build.VERSION.SDK_INT,
                notificationPermissionGranted = notificationsPermissionGranted(context),
            ),
        ).widgetSummary
    }

    private fun notificationsPermissionGranted(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

internal fun resolveCanonicalWidgetPlan(
    plans: List<StudyPlan>,
    defaultPlanId: StudyPlanId,
): StudyPlan? = plans.firstOrNull { candidate ->
    candidate.id == defaultPlanId &&
        candidate.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED)
}
