package com.mikeyphw.studyflow.reminders

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.mikeyphw.studyflow.BuildConfig
import com.mikeyphw.studyflow.core.data.createStudyFlowDataLayer
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.StudyReminderInput
import com.mikeyphw.studyflow.core.domain.StudyReminderPlanner
import com.mikeyphw.studyflow.core.domain.TodayWidgetSummary
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.widget.StudyFlowTodayWidgetProvider
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val WidgetUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

object StudyFlowWidgetUpdateCoordinator {
    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        WidgetUpdateScope.launch {
            val summary = buildSummary(appContext)
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, StudyFlowTodayWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { id -> StudyFlowTodayWidgetProvider.updateWidget(appContext, manager, id, summary) }
        }
    }

    private suspend fun buildSummary(context: Context): TodayWidgetSummary? {
        val dataLayer = createStudyFlowDataLayer(context, BuildConfig.VERSION_NAME)
        val plan = dataLayer.studyPlanRepository.plans.first()
            .firstOrNull { candidate -> candidate.status == StudyPlanStatus.ACTIVE }
            ?: return null
        val settings = dataLayer.studyPlanRepository.observePlanSettings(plan.id).first()
        val review = ManageStudyReview(dataLayer.studyReviewRepository, dataLayer.studyPracticeRepository)
        val calendar = Calendar.getInstance()
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return StudyReminderPlanner.buildPlan(
            StudyReminderInput(
                plan = plan,
                settings = settings,
                schedule = dataLayer.studyScheduleRepository.observeSchedule(plan.id).first(),
                reviewQueue = review.observeQueue(plan.id).first(),
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
