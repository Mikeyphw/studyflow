package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar

private const val MinuteMillis = 60_000L
private const val HourMillis = 3_600_000L
private const val PostNotificationsApi = 33

object StudyReminderPlanner {
    /** Mirrors the inexact AlarmManager delivery window used by the Android delivery layer. */
    const val PlannedActivityDeliveryGraceMillis: Long = 15 * MinuteMillis

    /** Bounded late-delivery window for Doze/App Standby/wall-clock adjustments. */
    const val PlannedActivityLateDeliveryGraceMillis: Long = 2 * HourMillis

    fun buildPlan(input: StudyReminderInput): StudyReminderPlan {
        val policy = input.policy ?: StudyReminderPolicy.fromLegacy(input.settings)
        val activeActivities = input.schedule.plannedActivities.filter { activity ->
            activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)
        }
        val plannedActivities = activeActivities.filter { it.status == PlannedActivityStatus.PLANNED }
        val widgetNextActivity = plannedActivities
            .filter { it.scheduledStartEpochMillis != null }
            .filter { activity ->
                val scheduledAt = requireNotNull(activity.scheduledStartEpochMillis)
                val fireAt = delayUntilQuietHoursEnd(
                    scheduledAt - policy.plannedStudyLeadMinutes * MinuteMillis,
                    input.settings,
                )
                plannedActivityWithinDeliveryWindow(fireAt, input.nowEpochMillis)
            }
            .minByOrNull { requireNotNull(it.scheduledStartEpochMillis) }
        val nextActivityCandidate = plannedActivities
            .asSequence()
            .filter { it.scheduledStartEpochMillis != null }
            .map { activity -> plannedActivityCandidate(activity, input.settings, policy) }
            .filter { candidate -> candidate.occurrenceId !in input.deliveredOccurrenceIds }
            .filter { candidate ->
                candidate.occurrenceId in input.reconciliationEligiblePlannedOccurrenceIds ||
                    plannedActivityWithinDeliveryWindow(candidate.fireAtEpochMillis, input.nowEpochMillis)
            }
            .minByOrNull(ReminderNotificationCandidate::fireAtEpochMillis)
        val missedActivities = activeActivities.filter { it.status == PlannedActivityStatus.MISSED }
        val dueReviews = input.reviewQueue.dueCount
        val quietHoursActive = isQuietNow(input.settings, input.minuteOfDay)

        val candidates = buildList {
            if (policy.dueReviewEnabled && dueReviews > 0) {
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
            if (policy.plannedStudyEnabled) nextActivityCandidate?.let(::add)
            if (policy.missedActivityEnabled && missedActivities.isNotEmpty()) {
                val firstMissed = missedActivities.minWithOrNull(
                    compareBy({ it.scheduledStartEpochMillis ?: Long.MAX_VALUE }, { it.id.value }),
                )!!
                val missedOccurrenceId = missedActivityOccurrenceId(missedActivities)
                if (missedOccurrenceId !in input.deliveredOccurrenceIds) {
                    add(
                        candidate(
                            type = ReminderNotificationType.MISSED_ACTIVITY,
                            occurrenceId = missedOccurrenceId,
                            content = ReminderNotificationContent(
                                kind = ReminderNotificationContentKind.MISSED_ACTIVITY,
                                count = missedActivities.size,
                                targetId = firstMissed.id.value,
                            ),
                            scheduledAtEpochMillis = input.persistedScheduledAt(missedOccurrenceId)
                                ?: input.nowEpochMillis + policy.missedActivityDelayMinutes * MinuteMillis,
                            settings = input.settings,
                        ),
                    )
                }
            }
            if (policy.dailySummaryEnabled) {
                val dailyScheduledAt = nextLocalTime(input.nowEpochMillis, policy.dailySummaryMinuteOfDay)
                val dailyOccurrenceId = "daily:${localDayIdentity(dailyScheduledAt)}:${policy.dailySummaryMinuteOfDay}"
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
            if (policy.sessionTimerEnabled) {
                input.activeSessions
                    .filter { session ->
                        session.studyPlanId == input.plan.id && session.status == StudySessionStatus.RUNNING
                    }
                    .forEach { session ->
                        sessionTimerCandidates(
                            session = session,
                            policy = policy,
                            settings = input.settings,
                            nowEpochMillis = input.nowEpochMillis,
                        ).forEach { sessionCandidate ->
                            if (sessionCandidate.occurrenceId !in input.deliveredOccurrenceIds) add(sessionCandidate)
                        }
                    }
            }
            if (policy.examCountdownEnabled) {
                examCountdownCandidate(input.plan, input.nowEpochMillis, policy, input.settings)?.let { exam ->
                    if (exam.occurrenceId !in input.deliveredOccurrenceIds) add(exam)
                }
            }
        }

        return StudyReminderPlan(
            studyPlanName = input.plan.name,
            permissionRequired = notificationPermissionRequired(input.apiLevel, input.notificationPermissionGranted),
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

    fun shouldRescheduleAfterDelivery(candidate: ReminderNotificationCandidate, deliveredAtEpochMillis: Long): Boolean =
        when (candidate.type) {
            ReminderNotificationType.DUE_REVIEWS -> false
            ReminderNotificationType.PLANNED_ACTIVITY -> candidate.fireAtEpochMillis > deliveredAtEpochMillis
            ReminderNotificationType.MISSED_ACTIVITY,
            ReminderNotificationType.DAILY_SUMMARY,
            ReminderNotificationType.SESSION_ENDING,
            ReminderNotificationType.SESSION_COMPLETED,
            ReminderNotificationType.EXAM_COUNTDOWN -> true
        }

    fun notificationPermissionRequired(apiLevel: Int, permissionGranted: Boolean): Boolean =
        apiLevel >= PostNotificationsApi && !permissionGranted

    fun isQuietNow(settings: StudyPlanSettings, minuteOfDay: Int): Boolean = isMinuteInQuietHours(settings, minuteOfDay)

    fun delayUntilQuietHoursEnd(candidateEpochMillis: Long, settings: StudyPlanSettings): Long {
        val start = settings.quietHoursStartMinute ?: return candidateEpochMillis
        val end = settings.quietHoursEndMinute ?: return candidateEpochMillis
        if (start == end) return candidateEpochMillis
        val calendar = Calendar.getInstance().apply { timeInMillis = candidateEpochMillis }
        val candidateMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        if (!isMinuteInQuietHours(settings, candidateMinute)) return candidateEpochMillis
        if (start > end && candidateMinute >= start) calendar.add(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, end / 60)
        calendar.set(Calendar.MINUTE, end % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun plannedActivityWithinDeliveryWindow(scheduledStartEpochMillis: Long, nowEpochMillis: Long): Boolean =
        scheduledStartEpochMillis >= nowEpochMillis - PlannedActivityLateDeliveryGraceMillis

    fun plannedActivityWithinReconciliationDeliveryWindow(
        fireAtEpochMillis: Long,
        nowEpochMillis: Long,
        reconciliationSlackMillis: Long,
    ): Boolean {
        require(reconciliationSlackMillis >= 0L) { "Reminder reconciliation slack cannot be negative." }
        return fireAtEpochMillis >= nowEpochMillis - PlannedActivityLateDeliveryGraceMillis - reconciliationSlackMillis
    }

    fun startOfNextLocalDay(nowEpochMillis: Long): Long = nextLocalTime(nowEpochMillis, 0)

    fun nextLocalTime(nowEpochMillis: Long, minuteOfDay: Int): Long {
        require(minuteOfDay in 0..1439) { "Minute of day must be 0..1439." }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowEpochMillis
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowEpochMillis) add(Calendar.DAY_OF_YEAR, 1)
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
        policy: StudyReminderPolicy,
    ): ReminderNotificationCandidate {
        val activityStart = requireNotNull(activity.scheduledStartEpochMillis)
        val scheduledAt = (activityStart - policy.plannedStudyLeadMinutes * MinuteMillis).coerceAtLeast(0L)
        return candidate(
            type = ReminderNotificationType.PLANNED_ACTIVITY,
            occurrenceId = "planned:${activity.id.value}:$activityStart:${policy.plannedStudyLeadMinutes}",
            content = ReminderNotificationContent(
                kind = ReminderNotificationContentKind.PLANNED_ACTIVITY,
                userLabel = activity.title,
                targetId = activity.id.value,
            ),
            scheduledAtEpochMillis = scheduledAt,
            settings = settings,
        )
    }

    private fun sessionTimerCandidates(
        session: StudySession,
        policy: StudyReminderPolicy,
        settings: StudyPlanSettings,
        nowEpochMillis: Long,
    ): List<ReminderNotificationCandidate> {
        val targetMinutes = session.targetDurationMinutes ?: return emptyList()
        val targetAt =
            session.startedAtEpochMillis +
                targetMinutes * MinuteMillis +
                session.pauseDurationSeconds * 1_000L
        val endingAt =
            (targetAt - policy.sessionEndingLeadMinutes * MinuteMillis)
                .coerceAtLeast(session.startedAtEpochMillis)
        return buildList {
            if (endingAt > nowEpochMillis && targetAt > nowEpochMillis) {
                add(
                    candidate(
                        type = ReminderNotificationType.SESSION_ENDING,
                        occurrenceId =
                            "session-ending:${session.id.value}:$targetAt:" +
                                "${policy.sessionEndingLeadMinutes}",
                        content = ReminderNotificationContent(
                            kind = ReminderNotificationContentKind.SESSION_ENDING,
                            userLabel = session.notes.takeIf(String::isNotBlank),
                            targetId = session.id.value,
                        ),
                        scheduledAtEpochMillis = endingAt,
                        settings = settings,
                    ),
                )
            }
            if (targetAt >= nowEpochMillis - PlannedActivityLateDeliveryGraceMillis) {
                add(
                    candidate(
                        type = ReminderNotificationType.SESSION_COMPLETED,
                        occurrenceId = "session-completed:${session.id.value}:$targetAt",
                        content = ReminderNotificationContent(
                            kind = ReminderNotificationContentKind.SESSION_COMPLETED,
                            userLabel = session.notes.takeIf(String::isNotBlank),
                            targetId = session.id.value,
                        ),
                        scheduledAtEpochMillis = targetAt,
                        settings = settings,
                    ),
                )
            }
        }
    }

    private fun examCountdownCandidate(
        plan: StudyPlan,
        nowEpochMillis: Long,
        policy: StudyReminderPolicy,
        settings: StudyPlanSettings,
    ): ReminderNotificationCandidate? {
        val examDate = plan.examDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val zone = ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, examDate).toInt()
        if (days !in 0..policy.examCountdownDays) return null
        val scheduledAt = nextLocalTime(nowEpochMillis, policy.dailySummaryMinuteOfDay)
        return candidate(
            type = ReminderNotificationType.EXAM_COUNTDOWN,
            occurrenceId = "exam:${plan.id.value}:${examDate}:${localDayIdentity(scheduledAt)}",
            content = ReminderNotificationContent(
                kind = ReminderNotificationContentKind.EXAM_COUNTDOWN,
                count = days,
                userLabel = plan.name,
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
        val firstDue = queue.due.minWithOrNull(compareBy({ it.dueAtEpochMillis }, { it.id.value }))
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
        return if (start < end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end
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
    val reconciliationEligiblePlannedOccurrenceIds: Set<String> = emptySet(),
    val persistedScheduledAtByOccurrenceId: Map<String, Long> = emptyMap(),
    val policy: StudyReminderPolicy? = null,
    val activeSessions: List<StudySession> = emptyList(),
) {
    fun persistedScheduledAt(occurrenceId: String): Long? = persistedScheduledAtByOccurrenceId[occurrenceId]
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
    val suppressedByQuietHours: Boolean = false
}

data class ReminderNotificationContent(
    val kind: ReminderNotificationContentKind,
    val count: Int? = null,
    val userLabel: String? = null,
    val targetId: String? = null,
)

enum class ReminderNotificationContentKind {
    DUE_REVIEWS,
    PLANNED_ACTIVITY,
    MISSED_ACTIVITY,
    DAILY_SUMMARY,
    SESSION_ENDING,
    SESSION_COMPLETED,
    BREAK_COMPLETED,
    EXAM_COUNTDOWN,
}

enum class ReminderNotificationType {
    DUE_REVIEWS,
    PLANNED_ACTIVITY,
    MISSED_ACTIVITY,
    DAILY_SUMMARY,
    SESSION_ENDING,
    SESSION_COMPLETED,
    EXAM_COUNTDOWN,
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
