package com.mikeyphw.studyflow.feature.workspace

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import java.util.Calendar

internal data class TodayActivitySelection(
    val activity: PlannedActivity,
    val isFutureDay: Boolean,
)

internal fun activitiesForLocalDay(
    activities: List<PlannedActivity>,
    nowEpochMillis: Long,
): List<PlannedActivity> {
    val (start, end) = localDayBounds(nowEpochMillis)
    return activities
        .filter { activity ->
            val startAt = activity.scheduledStartEpochMillis
            startAt != null &&
                startAt in start until end &&
                activity.status != PlannedActivityStatus.CANCELLED
        }
        .sortedBy { it.scheduledStartEpochMillis }
}

internal fun selectTodayOrNextScheduledActivity(
    activities: List<PlannedActivity>,
    nowEpochMillis: Long,
): TodayActivitySelection? {
    val (_, endOfToday) = localDayBounds(nowEpochMillis)
    val today = activitiesForLocalDay(activities, nowEpochMillis)
        .firstOrNull(PlannedActivity::isPendingForWorkspace)
    if (today != null) {
        return TodayActivitySelection(activity = today, isFutureDay = false)
    }

    val future = activities
        .asSequence()
        .filter(PlannedActivity::isPendingForWorkspace)
        .filter { activity ->
            val startAt = activity.scheduledStartEpochMillis
            startAt != null && startAt >= endOfToday
        }
        .minByOrNull { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
        ?: return null

    return TodayActivitySelection(activity = future, isFutureDay = true)
}

private fun PlannedActivity.isPendingForWorkspace(): Boolean =
    status == PlannedActivityStatus.PLANNED || status == PlannedActivityStatus.MISSED

private fun localDayBounds(nowEpochMillis: Long): Pair<Long, Long> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowEpochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val start = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_MONTH, 1)
    return start to calendar.timeInMillis
}
