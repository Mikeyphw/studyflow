package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudySchedule

private const val DayMillis = 86_400_000L
private const val HourMillis = 3_600_000L
private const val PostNotificationsApi = 33

object StudyReminderPlanner {
    fun buildPlan(input: StudyReminderInput): StudyReminderPlan {
        val activeActivities = input.schedule.plannedActivities.filter { activity ->
            activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)
        }
        val nextActivity = activeActivities
            .filter { activity -> activity.scheduledStartEpochMillis != null }
            .filter { activity -> requireNotNull(activity.scheduledStartEpochMillis) >= input.nowEpochMillis }
            .minByOrNull { activity -> requireNotNull(activity.scheduledStartEpochMillis) }
        val missedActivities = activeActivities.filter { activity -> activity.status == PlannedActivityStatus.MISSED }
        val dueReviews = input.reviewQueue.dueCount
        val suppressed = isQuietNow(input.settings, input.minuteOfDay)
        val candidates = buildList {
            if (input.settings.reviewNotificationsEnabled && dueReviews > 0) {
                add(
                    ReminderNotificationCandidate(
                        type = ReminderNotificationType.DUE_REVIEWS,
                        title = "Due reviews",
                        body = "$dueReviews review item(s) are due now.",
                        scheduledAtEpochMillis = input.nowEpochMillis,
                        suppressedByQuietHours = suppressed,
                    ),
                )
            }
            nextActivity?.scheduledStartEpochMillis?.let { scheduledAt ->
                add(
                    ReminderNotificationCandidate(
                        type = ReminderNotificationType.PLANNED_ACTIVITY,
                        title = nextActivity.title,
                        body = "Next planned study block is ready.",
                        scheduledAtEpochMillis = scheduledAt,
                        suppressedByQuietHours = suppressed,
                    ),
                )
            }
            if (missedActivities.isNotEmpty()) {
                add(
                    ReminderNotificationCandidate(
                        type = ReminderNotificationType.MISSED_ACTIVITY,
                        title = "Missed study blocks",
                        body = "${missedActivities.size} activity item(s) need recovery.",
                        scheduledAtEpochMillis = input.nowEpochMillis + HourMillis,
                        suppressedByQuietHours = suppressed,
                    ),
                )
            }
            add(
                ReminderNotificationCandidate(
                    type = ReminderNotificationType.DAILY_SUMMARY,
                    title = input.plan.name,
                    body = "Daily study summary is available.",
                    scheduledAtEpochMillis = startOfNextDay(input.nowEpochMillis),
                    suppressedByQuietHours = suppressed,
                ),
            )
        }
        return StudyReminderPlan(
            studyPlanName = input.plan.name,
            permissionRequired = notificationPermissionRequired(
                apiLevel = input.apiLevel,
                permissionGranted = input.notificationPermissionGranted,
            ),
            quietHoursActive = suppressed,
            notificationCandidates = candidates,
            widgetSummary = TodayWidgetSummary(
                studyPlanName = input.plan.name,
                dueReviewCount = dueReviews,
                plannedActivityCount = activeActivities.count { activity ->
                    activity.status == PlannedActivityStatus.PLANNED && activity.isToday(input.nowEpochMillis)
                },
                missedActivityCount = missedActivities.size,
                nextActivityTitle = nextActivity?.title,
                progressPercent = input.plan.progressPercent,
            ),
        )
    }


    fun shouldRescheduleAfterDelivery(
        candidate: ReminderNotificationCandidate,
        deliveredAtEpochMillis: Long,
    ): Boolean = when (candidate.type) {
        ReminderNotificationType.DUE_REVIEWS -> false
        ReminderNotificationType.PLANNED_ACTIVITY -> candidate.scheduledAtEpochMillis > deliveredAtEpochMillis
        ReminderNotificationType.MISSED_ACTIVITY,
        ReminderNotificationType.DAILY_SUMMARY -> true
    }

    fun notificationPermissionRequired(apiLevel: Int, permissionGranted: Boolean): Boolean =
        apiLevel >= PostNotificationsApi && !permissionGranted

    fun isQuietNow(settings: StudyPlanSettings, minuteOfDay: Int): Boolean {
        val start = settings.quietHoursStartMinute ?: return false
        val end = settings.quietHoursEndMinute ?: return false
        return if (start < end) {
            minuteOfDay in start until end
        } else {
            minuteOfDay >= start || minuteOfDay < end
        }
    }

    private fun startOfNextDay(nowEpochMillis: Long): Long =
        ((nowEpochMillis / DayMillis) + 1) * DayMillis

    private fun PlannedActivity.isToday(nowEpochMillis: Long): Boolean {
        val start = scheduledStartEpochMillis ?: return false
        return start / DayMillis == nowEpochMillis / DayMillis
    }
}

data class StudyReminderInput(
    val plan: StudyPlan,
    val settings: StudyPlanSettings,
    val schedule: StudySchedule,
    val reviewQueue: ReviewQueue,
    val nowEpochMillis: Long,
    val minuteOfDay: Int,
    val apiLevel: Int,
    val notificationPermissionGranted: Boolean,
)

data class StudyReminderPlan(
    val studyPlanName: String,
    val permissionRequired: Boolean,
    val quietHoursActive: Boolean,
    val notificationCandidates: List<ReminderNotificationCandidate>,
    val widgetSummary: TodayWidgetSummary,
) {
    val activeCandidateCount: Int = notificationCandidates.count { candidate ->
        !candidate.suppressedByQuietHours
    }
}

data class ReminderNotificationCandidate(
    val type: ReminderNotificationType,
    val title: String,
    val body: String,
    val scheduledAtEpochMillis: Long,
    val suppressedByQuietHours: Boolean,
)

enum class ReminderNotificationType {
    DUE_REVIEWS,
    PLANNED_ACTIVITY,
    MISSED_ACTIVITY,
    DAILY_SUMMARY,
}

data class TodayWidgetSummary(
    val studyPlanName: String,
    val dueReviewCount: Int,
    val plannedActivityCount: Int,
    val missedActivityCount: Int,
    val nextActivityTitle: String?,
    val progressPercent: Int,
)
