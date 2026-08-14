package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudySchedule
import java.util.Calendar

private const val HourMillis = 3_600_000L
private const val PostNotificationsApi = 33

object StudyReminderPlanner {
    /** Mirrors the inexact AlarmManager delivery window used by the Android delivery layer. */
    const val PlannedActivityDeliveryGraceMillis: Long = 15 * 60 * 1000L

    /**
     * Doze, App Standby, and wall-clock adjustments can deliver an inexact reminder late. Quiet
     * hours are applied before this check so an occurrence delayed to quiet-hours end remains
     * eligible even when its original study-block time is older than this grace period.
     */
    const val PlannedActivityLateDeliveryGraceMillis: Long = 2 * 60 * 60 * 1000L

    fun buildPlan(input: StudyReminderInput): StudyReminderPlan {
        val activeActivities = input.schedule.plannedActivities.filter { activity ->
            activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)
        }
        val plannedActivities = activeActivities.filter { it.status == PlannedActivityStatus.PLANNED }
        val widgetNextActivity = plannedActivities
            .filter { it.scheduledStartEpochMillis != null }
            .filter { activity ->
                val scheduledAt = requireNotNull(activity.scheduledStartEpochMillis)
                val fireAt = delayUntilQuietHoursEnd(scheduledAt, input.settings)
                plannedActivityWithinDeliveryWindow(fireAt, input.nowEpochMillis)
            }
            .minByOrNull { requireNotNull(it.scheduledStartEpochMillis) }
        val nextActivityCandidate = plannedActivities
            .asSequence()
            .filter { it.scheduledStartEpochMillis != null }
            .map { activity -> plannedActivityCandidate(activity, input.settings) }
            .filter { candidate -> candidate.occurrenceId !in input.deliveredOccurrenceIds }
            .filter { candidate ->
                plannedActivityWithinDeliveryWindow(
                    scheduledStartEpochMillis = candidate.fireAtEpochMillis,
                    nowEpochMillis = input.nowEpochMillis,
                )
            }
            .minByOrNull(ReminderNotificationCandidate::fireAtEpochMillis)
        val missedActivities = activeActivities.filter { it.status == PlannedActivityStatus.MISSED }
        val dueReviews = input.reviewQueue.dueCount
        val quietHoursActive = isQuietNow(input.settings, input.minuteOfDay)

        val candidates = buildList {
            if (input.settings.reviewNotificationsEnabled && dueReviews > 0) {
                val dueOccurrenceId = dueReviewOccurrenceId(input.reviewQueue)
                if (dueOccurrenceId !in input.deliveredOccurrenceIds) {
                    add(
                        candidate(
                            type = ReminderNotificationType.DUE_REVIEWS,
                            occurrenceId = dueOccurrenceId,
                            content = ReminderNotificationContent(
                                kind = ReminderNotificationContentKind.DUE_REVIEWS,
                                count = dueReviews,
                            ),
                            scheduledAtEpochMillis = input.persistedScheduledAt(dueOccurrenceId)
                                ?: input.nowEpochMillis,
                            settings = input.settings,
                        ),
                    )
                }
            }
            nextActivityCandidate?.let(::add)
            if (missedActivities.isNotEmpty()) {
                val missedOccurrenceId = missedActivityOccurrenceId(missedActivities)
                if (missedOccurrenceId !in input.deliveredOccurrenceIds) {
                    add(
                        candidate(
                            type = ReminderNotificationType.MISSED_ACTIVITY,
                            occurrenceId = missedOccurrenceId,
                            content = ReminderNotificationContent(
                                kind = ReminderNotificationContentKind.MISSED_ACTIVITY,
                                count = missedActivities.size,
                            ),
                            scheduledAtEpochMillis = input.persistedScheduledAt(missedOccurrenceId)
                                ?: input.nowEpochMillis + HourMillis,
                            settings = input.settings,
                        ),
                    )
                }
            }
            val dailyScheduledAt = startOfNextLocalDay(input.nowEpochMillis)
            val dailyOccurrenceId = "daily:${localDayIdentity(dailyScheduledAt)}"
            if (dailyOccurrenceId !in input.deliveredOccurrenceIds) {
                add(
                    candidate(
                        type = ReminderNotificationType.DAILY_SUMMARY,
                        occurrenceId = dailyOccurrenceId,
                        content = ReminderNotificationContent(
                            kind = ReminderNotificationContentKind.DAILY_SUMMARY,
                            userLabel = input.plan.name,
                        ),
                        scheduledAtEpochMillis = dailyScheduledAt,
                        settings = input.settings,
                    ),
                )
            }
        }

        return StudyReminderPlan(
            studyPlanName = input.plan.name,
            permissionRequired = notificationPermissionRequired(
                apiLevel = input.apiLevel,
                permissionGranted = input.notificationPermissionGranted,
            ),
            quietHoursActive = quietHoursActive,
            notificationCandidates = candidates,
            widgetSummary = TodayWidgetSummary(
                studyPlanId = input.plan.id,
                studyPlanName = input.plan.name,
                dueReviewCount = dueReviews,
                plannedActivityCount = activeActivities.count { activity ->
                    activity.status == PlannedActivityStatus.PLANNED && activity.isToday(input.nowEpochMillis)
                },
                missedActivityCount = missedActivities.size,
                nextActivityTitle = widgetNextActivity?.title,
                progressPercent = input.plan.progressPercent,
                primaryActionType = when {
                    dueReviews > 0 -> ReminderNotificationType.DUE_REVIEWS
                    widgetNextActivity != null -> ReminderNotificationType.PLANNED_ACTIVITY
                    missedActivities.isNotEmpty() -> ReminderNotificationType.MISSED_ACTIVITY
                    else -> ReminderNotificationType.DAILY_SUMMARY
                },
            ),
        )
    }

    fun shouldRescheduleAfterDelivery(
        candidate: ReminderNotificationCandidate,
        deliveredAtEpochMillis: Long,
    ): Boolean = when (candidate.type) {
        ReminderNotificationType.DUE_REVIEWS -> false
        ReminderNotificationType.PLANNED_ACTIVITY -> candidate.fireAtEpochMillis > deliveredAtEpochMillis
        ReminderNotificationType.MISSED_ACTIVITY,
        ReminderNotificationType.DAILY_SUMMARY -> true
    }

    fun notificationPermissionRequired(apiLevel: Int, permissionGranted: Boolean): Boolean =
        apiLevel >= PostNotificationsApi && !permissionGranted

    fun isQuietNow(settings: StudyPlanSettings, minuteOfDay: Int): Boolean =
        isMinuteInQuietHours(settings, minuteOfDay)

    fun delayUntilQuietHoursEnd(
        candidateEpochMillis: Long,
        settings: StudyPlanSettings,
    ): Long {
        val start = settings.quietHoursStartMinute ?: return candidateEpochMillis
        val end = settings.quietHoursEndMinute ?: return candidateEpochMillis
        if (start == end) return candidateEpochMillis
        val calendar = Calendar.getInstance().apply { timeInMillis = candidateEpochMillis }
        val candidateMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        if (!isMinuteInQuietHours(settings, candidateMinute)) return candidateEpochMillis

        val endHour = end / 60
        val endMinute = end % 60
        if (start > end && candidateMinute >= start) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        calendar.set(Calendar.HOUR_OF_DAY, endHour)
        calendar.set(Calendar.MINUTE, endMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun plannedActivityWithinDeliveryWindow(
        scheduledStartEpochMillis: Long,
        nowEpochMillis: Long,
    ): Boolean = scheduledStartEpochMillis >= nowEpochMillis - PlannedActivityLateDeliveryGraceMillis

    fun startOfNextLocalDay(nowEpochMillis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowEpochMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun sameLocalDay(firstEpochMillis: Long, secondEpochMillis: Long): Boolean {
        val first = Calendar.getInstance().apply { timeInMillis = firstEpochMillis }
        val second = Calendar.getInstance().apply { timeInMillis = secondEpochMillis }
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun plannedActivityCandidate(
        activity: PlannedActivity,
        settings: StudyPlanSettings,
    ): ReminderNotificationCandidate {
        val scheduledAt = requireNotNull(activity.scheduledStartEpochMillis)
        return candidate(
            type = ReminderNotificationType.PLANNED_ACTIVITY,
            occurrenceId = "planned:${activity.id.value}:$scheduledAt",
            content = ReminderNotificationContent(
                kind = ReminderNotificationContentKind.PLANNED_ACTIVITY,
                userLabel = activity.title,
            ),
            scheduledAtEpochMillis = scheduledAt,
            settings = settings,
        )
    }

    private fun candidate(
        type: ReminderNotificationType,
        occurrenceId: String,
        content: ReminderNotificationContent,
        scheduledAtEpochMillis: Long,
        settings: StudyPlanSettings,
    ): ReminderNotificationCandidate {
        val fireAt = delayUntilQuietHoursEnd(scheduledAtEpochMillis, settings)
        return ReminderNotificationCandidate(
            type = type,
            occurrenceId = occurrenceId,
            content = content,
            scheduledAtEpochMillis = scheduledAtEpochMillis,
            fireAtEpochMillis = fireAt,
            delayedByQuietHours = fireAt != scheduledAtEpochMillis,
        )
    }

    private fun dueReviewOccurrenceId(queue: ReviewQueue): String {
        val firstDue = queue.due.minWithOrNull(
            compareBy({ it.dueAtEpochMillis }, { it.id.value }),
        )
        return "due:${firstDue?.dueAtEpochMillis ?: 0L}:${firstDue?.id?.value.orEmpty()}:${queue.dueCount}"
    }

    private fun missedActivityOccurrenceId(activities: List<PlannedActivity>): String {
        val first = activities.minWithOrNull(
            compareBy({ it.scheduledStartEpochMillis ?: Long.MAX_VALUE }, { it.id.value }),
        ) ?: error("Missed-activity identity requires at least one activity.")
        return "missed:${first.id.value}:${first.scheduledStartEpochMillis ?: 0L}:${activities.size}"
    }

    private fun localDayIdentity(epochMillis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun isMinuteInQuietHours(settings: StudyPlanSettings, minuteOfDay: Int): Boolean {
        val start = settings.quietHoursStartMinute ?: return false
        val end = settings.quietHoursEndMinute ?: return false
        if (start == end) return false
        return if (start < end) {
            minuteOfDay in start until end
        } else {
            minuteOfDay >= start || minuteOfDay < end
        }
    }

    private fun PlannedActivity.isToday(nowEpochMillis: Long): Boolean {
        val start = scheduledStartEpochMillis ?: return false
        return sameLocalDay(start, nowEpochMillis)
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
    val deliveredOccurrenceIds: Set<String> = emptySet(),
    /** Original schedule time keyed by durable occurrence ID, restored from Android bookkeeping. */
    val persistedScheduledAtByOccurrenceId: Map<String, Long> = emptyMap(),
) {
    fun persistedScheduledAt(occurrenceId: String): Long? =
        persistedScheduledAtByOccurrenceId[occurrenceId]
}

data class StudyReminderPlan(
    val studyPlanName: String,
    val permissionRequired: Boolean,
    val quietHoursActive: Boolean,
    val notificationCandidates: List<ReminderNotificationCandidate>,
    val widgetSummary: TodayWidgetSummary,
) {
    val activeCandidateCount: Int = notificationCandidates.size
}

data class ReminderNotificationCandidate(
    val type: ReminderNotificationType,
    val occurrenceId: String,
    val content: ReminderNotificationContent,
    val scheduledAtEpochMillis: Long,
    val fireAtEpochMillis: Long,
    val delayedByQuietHours: Boolean,
) {
    /** Compatibility surface for older debug reports: delayed reminders are no longer suppressed. */
    val suppressedByQuietHours: Boolean = false
}

data class ReminderNotificationContent(
    val kind: ReminderNotificationContentKind,
    val count: Int? = null,
    val userLabel: String? = null,
)

enum class ReminderNotificationContentKind {
    DUE_REVIEWS,
    PLANNED_ACTIVITY,
    MISSED_ACTIVITY,
    DAILY_SUMMARY,
}

enum class ReminderNotificationType {
    DUE_REVIEWS,
    PLANNED_ACTIVITY,
    MISSED_ACTIVITY,
    DAILY_SUMMARY,
}

data class TodayWidgetSummary(
    val studyPlanId: StudyPlanId,
    val studyPlanName: String,
    val dueReviewCount: Int,
    val plannedActivityCount: Int,
    val missedActivityCount: Int,
    val nextActivityTitle: String?,
    val progressPercent: Int,
    val primaryActionType: ReminderNotificationType,
)
