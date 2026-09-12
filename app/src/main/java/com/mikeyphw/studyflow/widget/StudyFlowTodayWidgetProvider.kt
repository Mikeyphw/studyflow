package com.mikeyphw.studyflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.mikeyphw.studyflow.MainActivity
import com.mikeyphw.studyflow.R
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.domain.TodayWidgetSummary
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator
import com.mikeyphw.studyflow.reminders.studyReminderOpenIntent

sealed interface TodayWidgetRenderState {
    data object Loading : TodayWidgetRenderState
    data object NoPlan : TodayWidgetRenderState
    data class Ready(val summary: TodayWidgetSummary) : TodayWidgetRenderState
}

class StudyFlowTodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId, TodayWidgetRenderState.Loading)
        }
        StudyFlowWidgetUpdateCoordinator.refreshAll(context)
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            state: TodayWidgetRenderState,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            when (state) {
                TodayWidgetRenderState.Loading -> renderLoading(context, views)
                TodayWidgetRenderState.NoPlan -> renderNoPlan(context, views)
                is TodayWidgetRenderState.Ready -> renderSummary(context, views, state.summary)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun renderLoading(context: Context, views: RemoteViews) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_loading_title))
            views.setTextViewText(R.id.widget_body, context.getString(R.string.widget_loading_body))
            views.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_today_footer))
            views.setViewVisibility(R.id.widget_action_row, View.GONE)
            views.setOnClickPendingIntent(android.R.id.background, openAppPendingIntent(context, null, null))
        }

        private fun renderNoPlan(context: Context, views: RemoteViews) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_no_plan_title))
            views.setTextViewText(R.id.widget_body, context.getString(R.string.widget_no_plan_body))
            views.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_today_footer))
            views.setViewVisibility(R.id.widget_action_row, View.GONE)
            views.setOnClickPendingIntent(android.R.id.background, openAppPendingIntent(context, null, null))
        }

        private fun renderSummary(context: Context, views: RemoteViews, summary: TodayWidgetSummary) {
            views.setTextViewText(R.id.widget_title, summary.studyPlanName)
            views.setTextViewText(
                R.id.widget_body,
                context.getString(
                    R.string.widget_due_reviews_format,
                    summary.dueReviewCount,
                    summary.plannedActivityCount,
                ),
            )
            views.setTextViewText(R.id.widget_footer, buildFooter(context, summary))
            views.setViewVisibility(R.id.widget_action_row, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_primary_action,
                primaryActionLabel(context, summary.primaryActionType),
            )
            views.setOnClickPendingIntent(
                android.R.id.background,
                openAppPendingIntent(context, summary, ReminderNotificationType.DAILY_SUMMARY),
            )
            views.setOnClickPendingIntent(
                R.id.widget_primary_action,
                openAppPendingIntent(context, summary, summary.primaryActionType),
            )
            views.setOnClickPendingIntent(
                R.id.widget_reviews_action,
                openAppPendingIntent(context, summary, ReminderNotificationType.DUE_REVIEWS),
            )
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

        private fun primaryActionLabel(context: Context, type: ReminderNotificationType): String = context.getString(
            when (type) {
                ReminderNotificationType.DUE_REVIEWS -> R.string.widget_action_reviews
                ReminderNotificationType.PLANNED_ACTIVITY -> R.string.widget_action_next_block
                ReminderNotificationType.MISSED_ACTIVITY -> R.string.widget_action_recover
                ReminderNotificationType.DAILY_SUMMARY -> R.string.widget_action_today
                ReminderNotificationType.SESSION_ENDING,
                ReminderNotificationType.SESSION_COMPLETED -> R.string.widget_action_session
                ReminderNotificationType.EXAM_COUNTDOWN -> R.string.widget_action_progress
            },
        )

        private fun openAppPendingIntent(
            context: Context,
            summary: TodayWidgetSummary?,
            type: ReminderNotificationType?,
        ): PendingIntent {
            val identity = Uri.Builder()
                .scheme("studyflow")
                .authority("widget-open")
                .appendPath(context.packageName)
                .appendPath(summary?.studyPlanId?.value ?: "app")
                .appendPath(type?.name ?: "root")
                .build()
            val intent = if (summary != null && type != null) {
                studyReminderOpenIntent(context, summary.studyPlanId, type, identity)
            } else {
                Intent(context, MainActivity::class.java).apply {
                    data = identity
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
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
