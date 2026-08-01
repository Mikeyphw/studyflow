package com.mikeyphw.studyflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mikeyphw.studyflow.MainActivity
import com.mikeyphw.studyflow.R
import com.mikeyphw.studyflow.core.domain.TodayWidgetSummary
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator

class StudyFlowTodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId -> updateWidget(context, appWidgetManager, appWidgetId, null) }
        StudyFlowWidgetUpdateCoordinator.refreshAll(context)
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            summary: TodayWidgetSummary?,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            if (summary == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_no_plan_title))
                views.setTextViewText(R.id.widget_body, context.getString(R.string.widget_no_plan_body))
                views.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_today_footer))
            } else {
                views.setTextViewText(R.id.widget_title, summary.studyPlanName)
                views.setTextViewText(
                    R.id.widget_body,
                    context.getString(
                        R.string.widget_due_reviews_format,
                        summary.dueReviewCount,
                        summary.plannedActivityCount,
                    ),
                )
                views.setTextViewText(
                    R.id.widget_footer,
                    buildFooter(context, summary),
                )
            }
            views.setOnClickPendingIntent(android.R.id.background, openAppPendingIntent(context))
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun buildFooter(context: Context, summary: TodayWidgetSummary): String {
            val next = summary.nextActivityTitle ?: context.getString(R.string.widget_next_activity_empty)
            return listOf(
                context.getString(
                    R.string.widget_progress_format,
                    summary.missedActivityCount,
                    summary.progressPercent,
                ),
                context.getString(R.string.widget_next_activity_format, next),
            ).joinToString(separator = "\n")
        }

        private fun openAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
