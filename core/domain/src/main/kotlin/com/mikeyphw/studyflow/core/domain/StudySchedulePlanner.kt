package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MissedActivityHandling
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleGenerationRequest
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyStrategy
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

class StudySchedulePlanner(
    private val timeZone: TimeZone = TimeZone.getDefault(),
    @Suppress("unused") private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun generateActivities(
        request: ScheduleGenerationRequest,
        rules: List<ScheduleRule>,
        strategy: StudyStrategy? = null,
    ): List<PlannedActivity> {
        require(request.days in 1..366) { "Generated schedules must cover between 1 and 366 days." }
        validateRules(rules)
        val startDate = startOfLocalDay(request.startEpochMillis)
        val dayOffs = rules.filter { it.enabled && it.type == ScheduleRuleType.DAY_OFF }
            .mapNotNullTo(mutableSetOf(), ScheduleRule::dayOfWeek)
        val availability = rules.filter { it.enabled && it.type == ScheduleRuleType.AVAILABILITY }
            .groupBy { it.dayOfWeek }
        val activeRules = rules
            .filter { it.enabled && it.type in SchedulableRuleTypes }
            .sortedWith(compareBy<ScheduleRule> { it.position }.thenBy { it.id.value })

        val generated = mutableListOf<PlannedActivity>()
        (0 until request.days).forEach { dayOffset ->
            val day = startDate.copyAndAddDays(dayOffset)
            if (day.studyFlowDayOfWeek in dayOffs) return@forEach
            activeRules.forEach { rule ->
                if (!ruleOccurs(rule, startDate, day, dayOffset)) return@forEach
                val startMinute = requireNotNull(rule.startMinuteOfDay)
                val start = day.copyAndAddMinutes(startMinute).timeInMillis
                val candidate = PlannedActivity(
                    id = PlannedActivityId(generatedActivityId(rule.id, start)),
                    studyPlanId = request.studyPlanId,
                    subjectId = rule.subjectId,
                    topicId = null,
                    materialId = null,
                    videoResourceId = null,
                    type = activityTypeFor(rule, strategy, start),
                    title = titleFor(rule),
                    scheduledStartEpochMillis = start,
                    plannedDurationMinutes = rule.durationMinutes,
                    status = PlannedActivityStatus.PLANNED,
                    locked = rule.locked,
                    completedAtEpochMillis = null,
                )
                require(isWithinAvailability(candidate, availability[rule.dayOfWeek].orEmpty())) {
                    "Generated schedule block falls outside the configured availability window."
                }
                require(generated.none { it.overlaps(candidate) }) {
                    "Generated schedule blocks overlap. Adjust the schedule rules before generating."
                }
                generated += candidate
            }
        }
        return generated
    }

    fun validateRules(rules: List<ScheduleRule>) {
        val enabled = rules.filter(ScheduleRule::enabled)
        enabled.forEach { rule ->
            require(rule.dayOfWeek in 1..7) { "Enabled schedule rules require a day of week." }
            if (rule.type != ScheduleRuleType.DAY_OFF) {
                require(rule.startMinuteOfDay in 0..1439) { "Enabled timed rules require a valid start time." }
            }
            parseRecurrence(rule)
        }
        val timed = enabled.filter { it.type in SchedulableRuleTypes }
        timed.groupBy(ScheduleRule::dayOfWeek).values.forEach { sameDay ->
            sameDay.forEachIndexed { index, left ->
                sameDay.drop(index + 1).forEach { right ->
                    require(!left.overlaps(right)) {
                        "Schedule rules overlap on the same day."
                    }
                }
            }
        }
    }

    fun validateCandidate(
        candidate: PlannedActivity,
        allActivities: List<PlannedActivity>,
        rules: List<ScheduleRule>,
    ) {
        val start = candidate.scheduledStartEpochMillis ?: return
        if (candidate.status !in ActiveScheduleStatuses) return
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = start }
        val day = calendar.studyFlowDayOfWeek
        require(rules.none { it.enabled && it.type == ScheduleRuleType.DAY_OFF && it.dayOfWeek == day }) {
            "Adaptive schedule change conflicts with a configured day off."
        }
        val windows = rules.filter { it.enabled && it.type == ScheduleRuleType.AVAILABILITY && it.dayOfWeek == day }
        require(isWithinAvailability(candidate, windows)) {
            "Adaptive schedule change falls outside the configured availability window."
        }
        require(allActivities.none { other ->
            other.id != candidate.id && other.status in ActiveScheduleStatuses && other.overlaps(candidate)
        }) { "Adaptive schedule change overlaps another planned activity." }
    }

    fun rebalanceMissedActivities(
        activities: List<PlannedActivity>,
        handling: MissedActivityHandling,
        nowEpochMillis: Long,
    ): List<PlannedActivity> {
        val missed = activities.filter { activity ->
            !activity.locked && (
                activity.status == PlannedActivityStatus.MISSED ||
                    (activity.status == PlannedActivityStatus.PLANNED && activity.isPastGrace(nowEpochMillis))
                )
        }.sortedBy { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
        if (missed.isEmpty()) return emptyList()

        var postponeCursor = nowEpochMillis + MissedGraceMillis
        return missed.mapIndexedNotNull { index, activity ->
            when (handling) {
                MissedActivityHandling.KEEP_UNCHANGED -> null
                MissedActivityHandling.SKIP -> activity.copy(status = PlannedActivityStatus.SKIPPED)
                MissedActivityHandling.RETURN_TO_BACKLOG -> activity.copy(
                    scheduledStartEpochMillis = null,
                    status = PlannedActivityStatus.PLANNED,
                    completedAtEpochMillis = null,
                )
                MissedActivityHandling.POSTPONE -> {
                    val start = postponeCursor
                    postponeCursor += activity.plannedDurationMinutes * 60_000L + 15L * 60_000L
                    activity.copy(
                        scheduledStartEpochMillis = start,
                        status = PlannedActivityStatus.PLANNED,
                        completedAtEpochMillis = null,
                    )
                }
                MissedActivityHandling.REDISTRIBUTE -> activity.copy(
                    scheduledStartEpochMillis = redistributedLocalStart(activity, nowEpochMillis, index + 1),
                    status = PlannedActivityStatus.PLANNED,
                    completedAtEpochMillis = null,
                )
            }
        }
    }

    fun rebalanceMissedMutations(
        activities: List<PlannedActivity>,
        handling: MissedActivityHandling,
        nowEpochMillis: Long,
    ): List<PlannedActivityRebalanceMutation> {
        val expectedById = activities.associateBy { it.id }
        return rebalanceMissedActivities(activities, handling, nowEpochMillis).map { proposed ->
            PlannedActivityRebalanceMutation(
                expected = requireNotNull(expectedById[proposed.id]) { "Rebalance source activity is missing." },
                proposed = proposed,
            )
        }
    }

    private fun redistributedLocalStart(activity: PlannedActivity, nowEpochMillis: Long, dayOffset: Int): Long {
        val original = activity.scheduledStartEpochMillis?.let { value ->
            Calendar.getInstance(timeZone).apply { timeInMillis = value }
        }
        val minute = original?.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) } ?: 12 * 60
        return startOfLocalDay(nowEpochMillis).copyAndAddDays(dayOffset).copyAndAddMinutes(minute).timeInMillis
    }

    private fun PlannedActivity.isPastGrace(nowEpochMillis: Long): Boolean {
        val start = scheduledStartEpochMillis ?: return false
        val end = start + plannedDurationMinutes * 60_000L
        return end + MissedGraceMillis <= nowEpochMillis
    }

    private fun ruleOccurs(rule: ScheduleRule, rangeStart: Calendar, day: Calendar, dayOffset: Int): Boolean {
        if (rule.dayOfWeek != day.studyFlowDayOfWeek) return false
        val recurrence = parseRecurrence(rule)
        val matchingOrdinal = (0..dayOffset).count { offset ->
            rangeStart.copyAndAddDays(offset).studyFlowDayOfWeek == rule.dayOfWeek
        } - 1
        return when (recurrence.kind) {
            RecurrenceKind.ONCE -> matchingOrdinal == 0
            RecurrenceKind.WEEKLY -> matchingOrdinal >= 0
            RecurrenceKind.EVERY_N_WEEKS -> matchingOrdinal >= 0 && matchingOrdinal % recurrence.interval == 0
        }
    }

    private fun parseRecurrence(rule: ScheduleRule): Recurrence {
        if (rule.type !in SchedulableRuleTypes) return Recurrence(RecurrenceKind.WEEKLY, 1)
        val raw = rule.recurrenceRule?.trim()?.uppercase()
        if (raw.isNullOrEmpty()) {
            return if (rule.type == ScheduleRuleType.FIXED_BLOCK) {
                Recurrence(RecurrenceKind.ONCE, 1)
            } else {
                Recurrence(RecurrenceKind.WEEKLY, 1)
            }
        }
        if (raw in setOf("WEEKLY", "EVERY_WEEK", "FREQ=WEEKLY")) {
            return Recurrence(RecurrenceKind.WEEKLY, 1)
        }
        if (raw == "ONCE") return Recurrence(RecurrenceKind.ONCE, 1)
        val interval = Regex("FREQ=WEEKLY;INTERVAL=(\\d+)").matchEntire(raw)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("EVERY_(\\d+)_WEEKS").matchEntire(raw)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("EVERY_N_WEEKS:(\\d+)").matchEntire(raw)?.groupValues?.get(1)?.toIntOrNull()
        require(interval != null
            && interval in 1..52) { "Unsupported schedule recurrence rule: ${rule.recurrenceRule}" }
        return Recurrence(RecurrenceKind.EVERY_N_WEEKS, interval)
    }

    private fun isWithinAvailability(activity: PlannedActivity, windows: List<ScheduleRule>): Boolean {
        if (windows.isEmpty()) return true
        val start = activity.scheduledStartEpochMillis ?: return true
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = start }
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val end = minute + activity.plannedDurationMinutes
        return windows.any { window ->
            val windowStart = requireNotNull(window.startMinuteOfDay)
            minute >= windowStart && end <= windowStart + window.durationMinutes
        }
    }

    private fun PlannedActivity.overlaps(other: PlannedActivity): Boolean {
        val leftStart = scheduledStartEpochMillis ?: return false
        val rightStart = other.scheduledStartEpochMillis ?: return false
        val leftEnd = leftStart + plannedDurationMinutes * 60_000L
        val rightEnd = rightStart + other.plannedDurationMinutes * 60_000L
        return leftStart < rightEnd && rightStart < leftEnd
    }

    private fun ScheduleRule.overlaps(other: ScheduleRule): Boolean {
        if (dayOfWeek != other.dayOfWeek) return false
        val leftStart = startMinuteOfDay ?: return false
        val rightStart = other.startMinuteOfDay ?: return false
        return leftStart < rightStart + other.durationMinutes && rightStart < leftStart + durationMinutes
    }

    private fun activityTypeFor(rule: ScheduleRule, strategy: StudyStrategy?, start: Long): StudyActivityType {
        if (strategy == null) return if (rule.type == ScheduleRuleType.CYCLE_SLOT) StudyActivityType
            .CUSTOM else StudyActivityType.THEORY
        val bucket = stableBucket("${rule.id.value}:$start")
        return when {
            bucket < strategy.theoryRatioPercent -> StudyActivityType.THEORY
            bucket < strategy.theoryRatioPercent + strategy.questionRatioPercent -> StudyActivityType.QUESTIONS
            else -> StudyActivityType.REVIEW
        }
    }

    private fun titleFor(rule: ScheduleRule): String = when (rule.type) {
        ScheduleRuleType.CYCLE_SLOT -> "Study cycle slot"
        ScheduleRuleType.FIXED_BLOCK -> "Fixed study block"
        ScheduleRuleType.RECURRING_BLOCK -> "Recurring study block"
        ScheduleRuleType.AVAILABILITY -> "Availability"
        ScheduleRuleType.DAY_OFF -> "Day off"
    }

    private fun startOfLocalDay(epochMillis: Long): Calendar = Calendar.getInstance(timeZone).apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun stableBucket(value: String): Int = (MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray()).first().toInt() and 0xff) % 100

    private data class Recurrence(val kind: RecurrenceKind, val interval: Int)
    private enum class RecurrenceKind { ONCE, WEEKLY, EVERY_N_WEEKS }

    companion object {
        private val SchedulableRuleTypes = setOf(
            ScheduleRuleType.FIXED_BLOCK,
            ScheduleRuleType.RECURRING_BLOCK,
            ScheduleRuleType.CYCLE_SLOT,
        )
        private val ActiveScheduleStatuses = setOf(
            PlannedActivityStatus.PLANNED,
            PlannedActivityStatus.MISSED,
            PlannedActivityStatus.IN_PROGRESS,
        )
        private const val MissedGraceMillis = 5L * 60L * 1000L

        fun generatedRulePrefix(ruleId: ScheduleRuleId): String = "generated:${sha256(ruleId.value).take(16)}:"

        fun generatedActivityId(ruleId: ScheduleRuleId, startEpochMillis: Long): String =
            "${generatedRulePrefix(ruleId)}$startEpochMillis"

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

private fun Calendar.copyAndAddDays(days: Int): Calendar = (clone() as Calendar).apply { add(Calendar
    .DAY_OF_YEAR, days) }
private fun Calendar.copyAndAddMinutes(minutes: Int): Calendar = (clone() as Calendar).apply { add(Calendar
    .MINUTE, minutes) }
private val Calendar.studyFlowDayOfWeek: Int
    get() = when (get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> error("Unsupported day of week: ${get(Calendar.DAY_OF_WEEK)}")
    }
