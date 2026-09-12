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
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator
import com.mikeyphw.studyflow.reminders.studyReminderOpenIntent
import java.util.Date
import kotlin.math.roundToInt

sealed interface TodayWidgetRenderState {
    val configuration: StudyFlowWidgetConfiguration
    val debugName: String

    data class Loading(override val configuration: StudyFlowWidgetConfiguration) : TodayWidgetRenderState {
        override val debugName = "loading"
    }
    data class NoPlan(override val configuration: StudyFlowWidgetConfiguration) : TodayWidgetRenderState {
        override val debugName = "no_plan"
    }
    data class PlanUnavailable(override val configuration: StudyFlowWidgetConfiguration) : TodayWidgetRenderState {
        override val debugName = "plan_unavailable"
    }
    data class DataUnavailable(override val configuration: StudyFlowWidgetConfiguration) : TodayWidgetRenderState {
        override val debugName = "data_unavailable"
    }
    data class Ready(
        override val configuration: StudyFlowWidgetConfiguration,
        val snapshot: StudyFlowWidgetSnapshot,
    ) : TodayWidgetRenderState {
        override val debugName = "ready"
    }
}

class StudyFlowTodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val store = StudyFlowWidgetConfigurationStore(context)
        appWidgetIds.forEach { id -> updateWidget(context, manager, id, TodayWidgetRenderState.Loading(store.load(id))) }
        StudyFlowWidgetUpdateCoordinator.refreshAll(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        StudyFlowWidgetUpdateCoordinator.refreshAll(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        StudyFlowWidgetConfigurationStore(context).delete(appWidgetIds)
        StudyFlowWidgetUpdateCoordinator.refreshAll(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        StudyFlowWidgetConfigurationStore(context).restore(oldWidgetIds, newWidgetIds)
        StudyFlowWidgetUpdateCoordinator.refreshAll(context)
    }

    override fun onDisabled(context: Context) {
        StudyFlowWidgetFreshnessScheduler.cancel(context)
    }

    companion object {
        fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            state: TodayWidgetRenderState,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            clearTransientViews(views)
            val options = manager.getAppWidgetOptions(appWidgetId)
            val compactBySize = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) < 155 ||
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) < 95
            when (state) {
                is TodayWidgetRenderState.Loading -> renderMessage(
                    context, views,
                    context.getString(R.string.widget_loading_title),
                    context.getString(R.string.widget_loading_body),
                )
                is TodayWidgetRenderState.NoPlan -> renderMessage(
                    context, views,
                    context.getString(R.string.widget_no_plan_title),
                    context.getString(R.string.widget_no_plan_body),
                )
                is TodayWidgetRenderState.PlanUnavailable -> renderMessage(
                    context, views,
                    context.getString(R.string.widget_plan_unavailable_title),
                    context.getString(R.string.widget_plan_unavailable_body),
                )
                is TodayWidgetRenderState.DataUnavailable -> renderMessage(
                    context, views,
                    context.getString(R.string.widget_data_unavailable_title),
                    context.getString(R.string.widget_data_unavailable_body),
                )
                is TodayWidgetRenderState.Ready -> renderReady(
                    context = context,
                    views = views,
                    appWidgetId = appWidgetId,
                    configuration = state.configuration,
                    snapshot = state.snapshot,
                    compactBySize = compactBySize,
                )
            }
            views.setOnClickPendingIntent(android.R.id.background, rootPendingIntent(context, appWidgetId, state))
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun clearTransientViews(views: RemoteViews) {
            views.setViewVisibility(R.id.widget_title, View.VISIBLE)
            views.setViewVisibility(R.id.widget_body, View.VISIBLE)
            views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
            views.setViewVisibility(R.id.widget_action_row, View.GONE)
            views.setViewVisibility(R.id.widget_reviews_action, View.GONE)
            views.setViewVisibility(R.id.widget_primary_action, View.GONE)
        }

        private fun renderMessage(context: Context, views: RemoteViews, title: String, body: String) {
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_body, body)
            views.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_tap_to_configure))
            views.setContentDescription(android.R.id.background, "$title. $body")
        }

        private fun renderReady(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            configuration: StudyFlowWidgetConfiguration,
            snapshot: StudyFlowWidgetSnapshot,
            compactBySize: Boolean,
        ) {
            views.setContentDescription(
                android.R.id.background,
                context.getString(
                    R.string.widget_accessibility_summary,
                    widgetModeLabel(context, configuration.mode),
                    snapshot.planName,
                ),
            )
            if (compactBySize || configuration.mode == StudyFlowWidgetMode.COMPACT) {
                renderCompact(context, views, snapshot, configuration.showReviewCount)
                return
            }
            when (configuration.mode) {
                StudyFlowWidgetMode.TODAY -> renderToday(context, views, appWidgetId, snapshot)
                StudyFlowWidgetMode.NEXT -> renderNext(context, views, appWidgetId, snapshot)
                StudyFlowWidgetMode.REVIEWS -> renderReviews(context, views, appWidgetId, snapshot)
                StudyFlowWidgetMode.SESSION -> renderSession(context, views, appWidgetId, snapshot)
                StudyFlowWidgetMode.COMPACT -> renderCompact(context, views, snapshot, configuration.showReviewCount)
            }
        }

        private fun renderToday(context: Context, views: RemoteViews, widgetId: Int, snapshot: StudyFlowWidgetSnapshot) {
            views.setTextViewText(R.id.widget_title, snapshot.planName)
            views.setTextViewText(
                R.id.widget_body,
                context.getString(R.string.widget_today_counts, snapshot.dueReviewCount, snapshot.plannedTodayCount),
            )
            views.setTextViewText(
                R.id.widget_footer,
                buildString {
                    append(nextLine(context, snapshot))
                    append("\n")
                    append(context.getString(R.string.widget_progress_format, snapshot.missedActivityCount, snapshot.progressPercent))
                },
            )
            showActions(views)
            views.setViewVisibility(R.id.widget_reviews_action, View.VISIBLE)
            views.setTextViewText(R.id.widget_reviews_action, context.getString(R.string.widget_action_reviews))
            views.setOnClickPendingIntent(
                R.id.widget_reviews_action,
                openPendingIntent(context, widgetId, snapshot, ReminderNotificationType.DUE_REVIEWS),
            )
            views.setViewVisibility(R.id.widget_primary_action, View.VISIBLE)
            if (snapshot.activeSessionId != null) {
                views.setTextViewText(R.id.widget_primary_action, context.getString(R.string.widget_action_session))
                views.setOnClickPendingIntent(
                    R.id.widget_primary_action,
                    openPendingIntent(context, widgetId, snapshot, ReminderNotificationType.SESSION_ENDING),
                )
            } else if (snapshot.nextActivityId != null) {
                views.setTextViewText(R.id.widget_primary_action, context.getString(R.string.widget_action_start))
                views.setOnClickPendingIntent(
                    R.id.widget_primary_action,
                    mutationPendingIntent(context, widgetId, StudyFlowWidgetActionType.START_NEXT),
                )
            } else {
                views.setTextViewText(R.id.widget_primary_action, context.getString(R.string.widget_action_today))
                views.setOnClickPendingIntent(
                    R.id.widget_primary_action,
                    openPendingIntent(context, widgetId, snapshot, ReminderNotificationType.DAILY_SUMMARY),
                )
            }
        }

        private fun renderNext(context: Context, views: RemoteViews, widgetId: Int, snapshot: StudyFlowWidgetSnapshot) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_next_title, snapshot.planName))
            val title = snapshot.nextActivityTitle ?: context.getString(R.string.widget_next_activity_empty)
            views.setTextViewText(R.id.widget_body, title)
            views.setTextViewText(
                R.id.widget_footer,
                snapshot.nextActivityStartEpochMillis?.let { context.getString(R.string.widget_next_time, formatTime(context, it)) }
                    ?: context.getString(R.string.widget_next_none_hint),
            )
            if (snapshot.nextActivityId != null) {
                showActions(views)
                views.setViewVisibility(R.id.widget_primary_action, View.VISIBLE)
                views.setTextViewText(R.id.widget_primary_action, context.getString(R.string.widget_action_start))
                views.setOnClickPendingIntent(
                    R.id.widget_primary_action,
                    mutationPendingIntent(context, widgetId, StudyFlowWidgetActionType.START_NEXT),
                )
                views.setViewVisibility(R.id.widget_reviews_action, View.VISIBLE)
                views.setTextViewText(R.id.widget_reviews_action, context.getString(R.string.widget_action_open))
                views.setOnClickPendingIntent(
                    R.id.widget_reviews_action,
                    openPendingIntent(context, widgetId, snapshot, ReminderNotificationType.PLANNED_ACTIVITY),
                )
            }
        }

        private fun renderReviews(context: Context, views: RemoteViews, widgetId: Int, snapshot: StudyFlowWidgetSnapshot) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_reviews_title, snapshot.planName))
            views.setTextViewText(
                R.id.widget_body,
                context.resources.getQuantityString(
                    R.plurals.widget_reviews_due,
                    snapshot.dueReviewCount,
                    snapshot.dueReviewCount,
                ),
            )
            views.setTextViewText(
                R.id.widget_footer,
                context.getString(R.string.widget_reviews_upcoming, snapshot.upcomingReviewCount),
            )
            showActions(views)
            views.setViewVisibility(R.id.widget_primary_action, View.VISIBLE)
            views.setTextViewText(R.id.widget_primary_action, context.getString(R.string.widget_action_review_now))
            views.setOnClickPendingIntent(
                R.id.widget_primary_action,
                openPendingIntent(context, widgetId, snapshot, ReminderNotificationType.DUE_REVIEWS),
            )
        }

        private fun renderSession(context: Context, views: RemoteViews, widgetId: Int, snapshot: StudyFlowWidgetSnapshot) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_session_title, snapshot.planName))
            if (snapshot.activeSessionId == null) {
                views.setTextViewText(R.id.widget_body, context.getString(R.string.widget_session_empty))
                views.setTextViewText(R.id.widget_footer, nextLine(context, snapshot))
                if (snapshot.nextActivityId != null) {
                    showActions(views)
                    views.setViewVisibility(R.id.widget_primary_action, View.VISIBLE)
                    views.setTextViewText(R.id.widget_primary_action, context.getString(R.string.widget_action_start))
                    views.setOnClickPendingIntent(
                        R.id.widget_primary_action,
                        mutationPendingIntent(context, widgetId, StudyFlowWidgetActionType.START_NEXT),
                    )
                }
                return
            }
            views.setTextViewText(
                R.id.widget_body,
                snapshot.activeSessionTitle ?: context.getString(R.string.widget_session_active),
            )
            views.setTextViewText(
                R.id.widget_footer,
                context.getString(
                    R.string.widget_session_progress,
                    formatDuration(snapshot.activeSessionElapsedSeconds ?: 0L),
                    snapshot.activeSessionTargetSeconds?.let(::formatDuration) ?: "—",
                ),
            )
            showActions(views)
            views.setViewVisibility(R.id.widget_reviews_action, View.VISIBLE)
            val paused = snapshot.activeSessionStatus == StudySessionStatus.PAUSED
            views.setTextViewText(
                R.id.widget_reviews_action,
                context.getString(if (paused) R.string.widget_action_resume else R.string.widget_action_pause),
            )
            views.setOnClickPendingIntent(
                R.id.widget_reviews_action,
                mutationPendingIntent(context, widgetId, StudyFlowWidgetActionType.TOGGLE_SESSION),
            )
            views.setViewVisibility(R.id.widget_primary_action, View.VISIBLE)
            views.setTextViewText(R.id.widget_primary_action, context.getString(R.string.widget_action_open))
            views.setOnClickPendingIntent(
                R.id.widget_primary_action,
                openPendingIntent(context, widgetId, snapshot, ReminderNotificationType.SESSION_ENDING),
            )
        }

        private fun renderCompact(
            context: Context,
            views: RemoteViews,
            snapshot: StudyFlowWidgetSnapshot,
            showReviewCount: Boolean,
        ) {
            views.setTextViewText(R.id.widget_title, snapshot.planName)
            val primary = when {
                snapshot.activeSessionId != null -> context.getString(
                    R.string.widget_compact_session,
                    snapshot.activeSessionTitle ?: context.getString(R.string.widget_session_active),
                )
                snapshot.nextActivityTitle != null -> context.getString(
                    R.string.widget_compact_next,
                    snapshot.nextActivityTitle,
                    snapshot.nextActivityStartEpochMillis?.let { formatTime(context, it) }.orEmpty(),
                )
                showReviewCount && snapshot.dueReviewCount > 0 -> context.resources.getQuantityString(
                    R.plurals.widget_reviews_due,
                    snapshot.dueReviewCount,
                    snapshot.dueReviewCount,
                )
                else -> context.getString(R.string.widget_compact_clear)
            }
            views.setTextViewText(R.id.widget_body, primary)
            views.setViewVisibility(R.id.widget_footer, View.GONE)
            views.setViewVisibility(R.id.widget_action_row, View.GONE)
        }

        private fun showActions(views: RemoteViews) {
            views.setViewVisibility(R.id.widget_action_row, View.VISIBLE)
        }


        private fun widgetModeLabel(context: Context, mode: StudyFlowWidgetMode): String = context.getString(
            when (mode) {
                StudyFlowWidgetMode.TODAY -> R.string.widget_mode_today
                StudyFlowWidgetMode.NEXT -> R.string.widget_mode_next
                StudyFlowWidgetMode.REVIEWS -> R.string.widget_mode_reviews
                StudyFlowWidgetMode.SESSION -> R.string.widget_mode_session
                StudyFlowWidgetMode.COMPACT -> R.string.widget_mode_compact
            },
        )

        private fun nextLine(context: Context, snapshot: StudyFlowWidgetSnapshot): String {
            val title = snapshot.nextActivityTitle ?: return context.getString(R.string.widget_next_activity_empty)
            val time = snapshot.nextActivityStartEpochMillis?.let { formatTime(context, it) }
            return if (time == null) title else context.getString(R.string.widget_next_activity_at, title, time)
        }

        private fun formatTime(context: Context, epochMillis: Long): String =
            android.text.format.DateFormat.getTimeFormat(context).format(Date(epochMillis))

        private fun formatDuration(seconds: Long): String {
            val minutes = (seconds.coerceAtLeast(0L) / 60.0).roundToInt()
            return "%d:%02d".format(minutes / 60, minutes % 60)
        }

        private fun rootPendingIntent(
            context: Context,
            widgetId: Int,
            state: TodayWidgetRenderState,
        ): PendingIntent = when (state) {
            is TodayWidgetRenderState.Ready -> {
                val snapshot = state.snapshot
                val type = when {
                    snapshot.activeSessionId != null -> ReminderNotificationType.SESSION_ENDING
                    snapshot.dueReviewCount > 0 -> ReminderNotificationType.DUE_REVIEWS
                    snapshot.nextActivityId != null -> ReminderNotificationType.PLANNED_ACTIVITY
                    else -> ReminderNotificationType.DAILY_SUMMARY
                }
                openPendingIntent(context, widgetId, snapshot, type)
            }
            else -> PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, StudyFlowWidgetConfigureActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    data = Uri.parse("studyflow://widget-configure/${context.packageName}/$widgetId")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun openPendingIntent(
            context: Context,
            widgetId: Int,
            snapshot: StudyFlowWidgetSnapshot,
            type: ReminderNotificationType,
        ): PendingIntent {
            val identity = Uri.parse("studyflow://widget-open/${context.packageName}/$widgetId/${type.name}")
            val targetId = if (type == ReminderNotificationType.PLANNED_ACTIVITY) snapshot.nextActivityId else null
            val sessionId = if (type in setOf(ReminderNotificationType.SESSION_ENDING, ReminderNotificationType.SESSION_COMPLETED)) {
                snapshot.activeSessionId
            } else null
            return PendingIntent.getActivity(
                context,
                widgetId * 10 + type.ordinal,
                studyReminderOpenIntent(
                    context,
                    StudyPlanId(snapshot.planId),
                    type,
                    identity,
                    targetId = targetId,
                    sessionId = sessionId,
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun mutationPendingIntent(
            context: Context,
            widgetId: Int,
            action: StudyFlowWidgetActionType,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId * 10_000 + action.ordinal,
            Intent(context, StudyFlowWidgetActionReceiver::class.java).apply {
                this.action = StudyFlowWidgetAction
                data = Uri.parse("studyflow://widget-action/${context.packageName}/$widgetId/${action.name}")
                putExtra(ExtraWidgetId, widgetId)
                putExtra(ExtraWidgetAction, action.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
