package com.mikeyphw.studyflow.widget

import com.mikeyphw.studyflow.core.domain.ReviewQueue
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class StudyFlowWidgetMode {
    TODAY,
    NEXT,
    REVIEWS,
    SESSION,
    COMPACT,
}

data class StudyFlowWidgetConfiguration(
    val appWidgetId: Int,
    val mode: StudyFlowWidgetMode = StudyFlowWidgetMode.TODAY,
    /** null means follow the current app default plan. */
    val studyPlanId: String? = null,
    val showReviewCount: Boolean = true,
)

data class StudyFlowWidgetSnapshot(
    val planId: String,
    val planName: String,
    val progressPercent: Int,
    val dueReviewCount: Int,
    val upcomingReviewCount: Int,
    val plannedTodayCount: Int,
    val missedActivityCount: Int,
    val nextActivityId: String?,
    val nextActivityTitle: String?,
    val nextActivityStartEpochMillis: Long?,
    val activeSessionId: String?,
    val activeSessionTitle: String?,
    val activeSessionStatus: StudySessionStatus?,
    val activeSessionElapsedSeconds: Long?,
    val activeSessionTargetSeconds: Long?,
    val nextRefreshEpochMillis: Long,
)

object StudyFlowWidgetSnapshotProjector {
    private const val MinimumRefreshDelayMillis = 60_000L
    private const val CurrentBlockGraceMillis = 2 * 60 * 60 * 1_000L

    fun project(
        plan: StudyPlan,
        schedule: StudySchedule,
        reviewQueue: ReviewQueue,
        activeSession: StudySession?,
        liveActiveSeconds: Long?,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): StudyFlowWidgetSnapshot {
        val day = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        val nextActivity = schedule.plannedActivities
            .asSequence()
            .filter { it.status == PlannedActivityStatus.PLANNED }
            .filter { (it.scheduledStartEpochMillis ?: Long.MAX_VALUE) >= nowEpochMillis - CurrentBlockGraceMillis }
            .minWithOrNull(compareBy<PlannedActivity>({ it.scheduledStartEpochMillis ?: Long.MAX_VALUE }, { it.id.value }))
        val plannedTodayCount = schedule.plannedActivities.count { activity ->
            activity.status == PlannedActivityStatus.PLANNED &&
                activity.scheduledStartEpochMillis?.let { epoch ->
                    Instant.ofEpochMilli(epoch).atZone(zoneId).toLocalDate() == day
                } == true
        }
        val missedCount = schedule.plannedActivities.count { it.status == PlannedActivityStatus.MISSED }
        val nextUpcomingReview = reviewQueue.upcoming.minOfOrNull { it.dueAtEpochMillis }
        val currentBlockRecheck = nextActivity?.scheduledStartEpochMillis
            ?.takeIf { it <= nowEpochMillis }
            ?.let { nowEpochMillis + 15 * 60 * 1_000L }
        val sessionFinish = activeSession
            ?.takeIf { it.status == StudySessionStatus.RUNNING }
            ?.targetDurationMinutes
            ?.let { target ->
                val elapsed = liveActiveSeconds ?: 0L
                nowEpochMillis + ((target * 60L - elapsed).coerceAtLeast(1L) * 1_000L)
            }
        val nextMidnight = ZonedDateTime.of(day.plusDays(1).atStartOfDay(), zoneId).toInstant().toEpochMilli()
            .coerceAtLeast(nowEpochMillis + MinimumRefreshDelayMillis)
        val sessionMinuteTick = activeSession
            ?.takeIf { it.status == StudySessionStatus.RUNNING }
            ?.let { nowEpochMillis + MinimumRefreshDelayMillis }
        val nextRefresh = listOfNotNull(
            nextActivity?.scheduledStartEpochMillis?.takeIf { it >= nowEpochMillis },
            currentBlockRecheck,
            nextUpcomingReview,
            sessionFinish,
            sessionMinuteTick,
            nextMidnight,
        // Keep imminent domain boundaries (even under one minute); the Android alarm
        // scheduler separately applies its one-second safety floor to dispatch.
        ).filter { it > nowEpochMillis }
            .minOrNull()
            ?: nextMidnight

        return StudyFlowWidgetSnapshot(
            planId = plan.id.value,
            planName = plan.name,
            progressPercent = plan.progressPercent,
            dueReviewCount = reviewQueue.dueCount,
            upcomingReviewCount = reviewQueue.upcomingCount,
            plannedTodayCount = plannedTodayCount,
            missedActivityCount = missedCount,
            nextActivityId = nextActivity?.id?.value,
            nextActivityTitle = nextActivity?.title,
            nextActivityStartEpochMillis = nextActivity?.scheduledStartEpochMillis,
            activeSessionId = activeSession?.id?.value,
            activeSessionTitle = activeSession?.let { session ->
                session.plannedActivityId
                    ?.let { activityId -> schedule.plannedActivities.firstOrNull { it.id == activityId }?.title }
                    ?: session.notes.lineSequence().firstOrNull()?.trim()?.takeIf(String::isNotBlank)
            },
            activeSessionStatus = activeSession?.status,
            activeSessionElapsedSeconds = liveActiveSeconds,
            activeSessionTargetSeconds = activeSession?.targetDurationMinutes?.times(60L),
            nextRefreshEpochMillis = nextRefresh,
        )
    }
}
