package com.mikeyphw.studyflow.feature.schedule

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.SubjectId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ScheduleActivityProvenance {
    MANUAL,
    GENERATED,
}

data class ScheduleActivityFilter(
    val subjectId: SubjectId? = null,
    val type: StudyActivityType? = null,
    val status: PlannedActivityStatus? = null,
    val provenance: ScheduleActivityProvenance? = null,
)

data class ScheduleDayGroup(
    val date: LocalDate?,
    val activities: List<PlannedActivity>,
)

data class ScheduleIntelligenceReport(
    val conflictActivityIds: Set<PlannedActivityId>,
    val overloadedDates: Set<LocalDate>,
)

object ScheduleIntelligence {
    private val activeStatuses = setOf(
        PlannedActivityStatus.PLANNED,
        PlannedActivityStatus.MISSED,
        PlannedActivityStatus.IN_PROGRESS,
    )

    fun provenance(activity: PlannedActivity): ScheduleActivityProvenance =
        if (activity.id.value.startsWith("generated:")) {
            ScheduleActivityProvenance.GENERATED
        } else {
            ScheduleActivityProvenance.MANUAL
        }

    fun filter(
        activities: List<PlannedActivity>,
        filter: ScheduleActivityFilter,
    ): List<PlannedActivity> = activities.filter { activity ->
        (filter.subjectId == null || activity.subjectId == filter.subjectId) &&
            (filter.type == null || activity.type == filter.type) &&
            (filter.status == null || activity.status == filter.status) &&
            (filter.provenance == null || provenance(activity) == filter.provenance)
    }

    fun groupByDay(
        activities: List<PlannedActivity>,
        zoneId: ZoneId,
    ): List<ScheduleDayGroup> = activities
        .groupBy { activity -> activity.localDate(zoneId) }
        .entries
        .sortedWith(compareBy<Map.Entry<LocalDate?, List<PlannedActivity>>> { it.key == null }.thenBy { it.key })
        .map { (date, grouped) ->
            ScheduleDayGroup(
                date = date,
                activities = grouped.sortedWith(
                    compareBy<PlannedActivity> { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
                        .thenBy { it.id.value },
                ),
            )
        }

    fun inspect(
        activities: List<PlannedActivity>,
        zoneId: ZoneId,
        dailyOverloadMinutes: Int = 240,
    ): ScheduleIntelligenceReport {
        require(dailyOverloadMinutes > 0) { "Daily overload threshold must be positive." }
        val active = activities.filter { it.status in activeStatuses && it.scheduledStartEpochMillis != null }
        val conflicts = linkedSetOf<PlannedActivityId>()
        active.forEachIndexed { index, left ->
            active.drop(index + 1).forEach { right ->
                if (left.overlaps(right)) {
                    conflicts += left.id
                    conflicts += right.id
                }
            }
        }
        val overloadedDates = active
            .groupBy { requireNotNull(it.localDate(zoneId)) }
            .filterValues { dayActivities -> dayActivities.sumOf(PlannedActivity::plannedDurationMinutes) > dailyOverloadMinutes }
            .keys
        return ScheduleIntelligenceReport(conflicts, overloadedDates)
    }

    fun isBulkMutable(activity: PlannedActivity): Boolean =
        !activity.locked && activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)

    private fun PlannedActivity.localDate(zoneId: ZoneId): LocalDate? = scheduledStartEpochMillis?.let { epoch ->
        Instant.ofEpochMilli(epoch).atZone(zoneId).toLocalDate()
    }

    private fun PlannedActivity.overlaps(other: PlannedActivity): Boolean {
        val leftStart = scheduledStartEpochMillis ?: return false
        val rightStart = other.scheduledStartEpochMillis ?: return false
        val leftEnd = leftStart + plannedDurationMinutes * 60_000L
        val rightEnd = rightStart + other.plannedDurationMinutes * 60_000L
        return leftStart < rightEnd && rightStart < leftEnd
    }
}
