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
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyStrategy
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

/**
 * Canonical local-calendar schedule engine.
 *
 * Generation, adaptive validation and missed-activity recovery all share the same policy evaluator,
 * so DAY_OFF / AVAILABILITY recurrence, plan ownership and overlap rules cannot diverge by path.
 */
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
        requirePlanOwnership(request.studyPlanId, rules, strategy)
        validateRules(rules)
        val startDate = startOfLocalDay(request.startEpochMillis)
        val activeRules = rules
            .filter { it.enabled && it.type in SchedulableRuleTypes }
            .sortedWith(compareBy<ScheduleRule> { it.position }.thenBy { it.id.value })

        val generated = mutableListOf<PlannedActivity>()
        (0 until request.days).forEach dayLoop@{ dayOffset ->
            val day = startDate.copyAndAddDays(dayOffset)
            if (hasActiveDayOff(rules, startDate, day, dayOffset)) return@dayLoop
            activeRules.forEach ruleLoop@{ rule ->
                if (!ruleOccurs(rule, startDate, day, dayOffset)) return@ruleLoop
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
                // An availability rule is a positive allow-list. If the rule does not recur on this
                // week/day, the generated block is skipped rather than silently treating the day as free.
                if (!isWithinAvailability(candidate, rules, startDate, day, dayOffset)) return@ruleLoop
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

    /**
     * Validates a candidate against the exact same local-calendar policy used by generation.
     * [policyRangeStartEpochMillis] is optional for existing callers; when absent we derive a stable
     * anchor from the earliest persisted activity and the candidate itself.
     */
    fun validateCandidate(
        candidate: PlannedActivity,
        allActivities: List<PlannedActivity>,
        rules: List<ScheduleRule>,
        policyRangeStartEpochMillis: Long? = null,
    ) {
        val start = candidate.scheduledStartEpochMillis ?: return
        if (candidate.status !in ActiveScheduleStatuses) return
        require(allActivities.all { it.studyPlanId == candidate.studyPlanId }) {
            "Schedule candidate evidence belongs to another Study Plan."
        }
        requirePlanOwnership(candidate.studyPlanId, rules, strategy = null)
        validateRules(rules)

        val rangeStartMillis = policyRangeStartEpochMillis ?: policyAnchorMillis(allActivities, start)
        val rangeStart = startOfLocalDay(rangeStartMillis)
        val day = startOfLocalDay(start)
        val dayOffset = localDayOffset(rangeStart, day)
        require(dayOffset >= 0) { "Schedule candidate cannot precede the policy recurrence anchor." }
        val localDay = day.studyFlowDayOfWeek
        require(!hasActiveDayOff(rules, rangeStart, day, dayOffset)) {
            "Adaptive schedule change conflicts with a configured day off."
        }
        require(isWithinAvailability(candidate, rules, rangeStart, day, dayOffset)) {
            "Adaptive schedule change falls outside the configured availability window."
        }
        require(allActivities.none { other ->
            other.id != candidate.id && other.status in ActiveScheduleStatuses && other.overlaps(candidate)
        }) { "Adaptive schedule change overlaps another planned activity." }
        require(localDay in 1..7) { "Schedule candidate has an invalid local day." }
    }

    fun rebalanceMissedActivities(
        activities: List<PlannedActivity>,
        rules: List<ScheduleRule>,
        handling: MissedActivityHandling,
        nowEpochMillis: Long,
        policyRangeStartEpochMillis: Long? = null,
    ): List<PlannedActivity> {
        if (activities.isEmpty()) return emptyList()
        val planIds = activities.mapTo(linkedSetOf(), PlannedActivity::studyPlanId)
        require(planIds.size == 1) { "Missed-activity rebalance cannot mix Study Plans." }
        val planId = planIds.single()
        requirePlanOwnership(planId, rules, strategy = null)
        validateRules(rules)

        val missed = activities.filter { activity ->
            !activity.locked && (
                activity.status == PlannedActivityStatus.MISSED ||
                    (activity.status == PlannedActivityStatus.PLANNED && activity.isPastGrace(nowEpochMillis))
                )
        }.sortedBy { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
        if (missed.isEmpty() || handling == MissedActivityHandling.KEEP_UNCHANGED) return emptyList()

        val movableIds = missed.mapTo(mutableSetOf(), PlannedActivity::id)
        val reserved = activities
            .filter { it.id !in movableIds && it.status in ActiveScheduleStatuses }
            .toMutableList()
        val anchor = policyRangeStartEpochMillis ?: policyAnchorMillis(activities, nowEpochMillis)
        var postponeCursor = nowEpochMillis + MissedGraceMillis
        val changes = mutableListOf<PlannedActivity>()

        missed.forEachIndexed { index, activity ->
            val proposed = when (handling) {
                MissedActivityHandling.KEEP_UNCHANGED -> null
                MissedActivityHandling.SKIP -> activity.copy(status = PlannedActivityStatus.SKIPPED)
                MissedActivityHandling.RETURN_TO_BACKLOG -> activity.copy(
                    scheduledStartEpochMillis = null,
                    status = PlannedActivityStatus.PLANNED,
                    completedAtEpochMillis = null,
                )
                MissedActivityHandling.POSTPONE -> {
                    val start = findPolicyCompliantStart(
                        activity = activity,
                        earliestStartEpochMillis = postponeCursor,
                        reservedActivities = reserved,
                        rules = rules,
                        policyRangeStartEpochMillis = anchor,
                    )
                    postponeCursor = start + activity.plannedDurationMinutes * 60_000L + RebalanceGapMillis
                    activity.copy(
                        scheduledStartEpochMillis = start,
                        status = PlannedActivityStatus.PLANNED,
                        completedAtEpochMillis = null,
                    )
                }
                MissedActivityHandling.REDISTRIBUTE -> {
                    val preferred = redistributedLocalStart(activity, nowEpochMillis, index + 1)
                    val start = findPolicyCompliantStart(
                        activity = activity,
                        earliestStartEpochMillis = preferred,
                        reservedActivities = reserved,
                        rules = rules,
                        policyRangeStartEpochMillis = anchor,
                    )
                    activity.copy(
                        scheduledStartEpochMillis = start,
                        status = PlannedActivityStatus.PLANNED,
                        completedAtEpochMillis = null,
                    )
                }
            }
            if (proposed != null) {
                changes += proposed
                if (proposed.status in ActiveScheduleStatuses && proposed.scheduledStartEpochMillis != null) {
                    reserved += proposed
                }
            }
        }
        return changes
    }

    fun rebalanceMissedMutations(
        activities: List<PlannedActivity>,
        rules: List<ScheduleRule>,
        handling: MissedActivityHandling,
        nowEpochMillis: Long,
        policyRangeStartEpochMillis: Long? = null,
    ): List<PlannedActivityRebalanceMutation> {
        val expectedById = activities.associateBy { it.id }
        return rebalanceMissedActivities(
            activities = activities,
            rules = rules,
            handling = handling,
            nowEpochMillis = nowEpochMillis,
            policyRangeStartEpochMillis = policyRangeStartEpochMillis,
        ).map { proposed ->
            PlannedActivityRebalanceMutation(
                expected = requireNotNull(expectedById[proposed.id]) { "Rebalance source activity is missing." },
                proposed = proposed,
            )
        }
    }

    private fun findPolicyCompliantStart(
        activity: PlannedActivity,
        earliestStartEpochMillis: Long,
        reservedActivities: List<PlannedActivity>,
        rules: List<ScheduleRule>,
        policyRangeStartEpochMillis: Long,
    ): Long {
        var candidateStart = earliestStartEpochMillis
        repeat(MaxPlacementAttempts) {
            val candidate = activity.copy(
                scheduledStartEpochMillis = candidateStart,
                status = PlannedActivityStatus.PLANNED,
                completedAtEpochMillis = null,
            )
            if (runCatching {
                    validateCandidate(
                        candidate = candidate,
                        allActivities = reservedActivities + candidate,
                        rules = rules,
                        policyRangeStartEpochMillis = policyRangeStartEpochMillis,
                    )
                }.isSuccess
            ) {
                return candidateStart
            }
            candidateStart = addLocalMinutes(candidateStart, PlacementStepMinutes)
        }
        error("No policy-compliant schedule slot was found within $MaxPlacementDays days.")
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

    private fun hasActiveDayOff(
        rules: List<ScheduleRule>,
        rangeStart: Calendar,
        day: Calendar,
        dayOffset: Int,
    ): Boolean = rules.any { rule ->
        rule.enabled && rule.type == ScheduleRuleType.DAY_OFF && ruleOccurs(rule, rangeStart, day, dayOffset)
    }

    private fun isWithinAvailability(
        activity: PlannedActivity,
        rules: List<ScheduleRule>,
        rangeStart: Calendar,
        day: Calendar,
        dayOffset: Int,
    ): Boolean {
        val configured = rules.filter { rule ->
            rule.enabled && rule.type == ScheduleRuleType.AVAILABILITY && rule.dayOfWeek == day.studyFlowDayOfWeek
        }
        if (configured.isEmpty()) return true
        val activeWindows = configured.filter { rule -> ruleOccurs(rule, rangeStart, day, dayOffset) }
        if (activeWindows.isEmpty()) return false
        val start = activity.scheduledStartEpochMillis ?: return true
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = start }
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val end = minute + activity.plannedDurationMinutes
        return activeWindows.any { window ->
            val windowStart = requireNotNull(window.startMinuteOfDay)
            minute >= windowStart && end <= windowStart + window.durationMinutes
        }
    }

    private fun ruleOccurs(rule: ScheduleRule, rangeStart: Calendar, day: Calendar, dayOffset: Int): Boolean {
        if (dayOffset < 0 || rule.dayOfWeek != day.studyFlowDayOfWeek) return false
        val recurrence = parseRecurrence(rule)
        val matchingOrdinal = (0..dayOffset).count { offset ->
            rangeStart.copyAndAddDays(offset).studyFlowDayOfWeek == rule.dayOfWeek
        } - 1
        if (matchingOrdinal < 0) return false
        return when (recurrence.kind) {
            RecurrenceKind.ONCE -> matchingOrdinal == 0
            RecurrenceKind.WEEKLY, RecurrenceKind.EVERY_N_WEEKS -> {
                if (matchingOrdinal % recurrence.interval != 0) return false
                val occurrenceIndex = matchingOrdinal / recurrence.interval
                recurrence.count == null || occurrenceIndex < recurrence.count
            }
        }
    }

    private fun parseRecurrence(rule: ScheduleRule): Recurrence {
        val raw = rule.recurrenceRule?.trim()?.uppercase()
        if (raw.isNullOrEmpty()) {
            return if (rule.type == ScheduleRuleType.FIXED_BLOCK) {
                Recurrence(RecurrenceKind.ONCE, interval = 1, count = 1)
            } else {
                Recurrence(RecurrenceKind.WEEKLY, interval = 1, count = null)
            }
        }
        if (raw == "ONCE") return Recurrence(RecurrenceKind.ONCE, interval = 1, count = 1)
        if (raw in setOf("WEEKLY", "EVERY_WEEK")) {
            return Recurrence(RecurrenceKind.WEEKLY, interval = 1, count = null)
        }
        Regex("EVERY_(\\d+)_WEEKS").matchEntire(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { interval ->
            require(interval in 1..52) { "Schedule recurrence interval must be between 1 and 52 weeks." }
            return Recurrence(RecurrenceKind.EVERY_N_WEEKS, interval, count = null)
        }
        Regex("EVERY_N_WEEKS:(\\d+)").matchEntire(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { interval ->
            require(interval in 1..52) { "Schedule recurrence interval must be between 1 and 52 weeks." }
            return Recurrence(RecurrenceKind.EVERY_N_WEEKS, interval, count = null)
        }
        if (raw.startsWith("FREQ=WEEKLY")) {
            val segments = raw.split(';')
            require(segments.first() == "FREQ=WEEKLY") {
                "Unsupported schedule recurrence rule: ${rule.recurrenceRule}"
            }
            val values = mutableMapOf<String, String>()
            segments.drop(1).forEach { segment ->
                val pieces = segment.split('=', limit = 2)
                require(pieces.size == 2 && pieces[0] in setOf("INTERVAL", "COUNT")) {
                    "Unsupported schedule recurrence rule: ${rule.recurrenceRule}"
                }
                require(values.put(pieces[0], pieces[1]) == null) {
                    "Duplicate schedule recurrence component: ${pieces[0]}"
                }
            }
            val interval = values["INTERVAL"]?.toIntOrNull() ?: 1
            val count = values["COUNT"]?.toIntOrNull()
            require(interval in 1..52) { "Schedule recurrence interval must be between 1 and 52 weeks." }
            require(count == null || count in 1..366) { "Schedule recurrence count must be between 1 and 366." }
            return Recurrence(
                kind = if (interval == 1) RecurrenceKind.WEEKLY else RecurrenceKind.EVERY_N_WEEKS,
                interval = interval,
                count = count,
            )
        }
        error("Unsupported schedule recurrence rule: ${rule.recurrenceRule}")
    }

    private fun requirePlanOwnership(
        planId: StudyPlanId,
        rules: List<ScheduleRule>,
        strategy: StudyStrategy?,
    ) {
        require(rules.all { it.studyPlanId == planId }) {
            "Schedule rules belong to another Study Plan."
        }
        require(strategy == null || strategy.studyPlanId == planId) {
            "Study strategy belongs to another Study Plan."
        }
    }

    private fun policyAnchorMillis(activities: List<PlannedActivity>, fallback: Long): Long =
        activities.mapNotNull(PlannedActivity::scheduledStartEpochMillis).minOrNull()?.let { minOf(it, fallback) }
            ?: fallback

    private fun localDayOffset(rangeStart: Calendar, day: Calendar): Int {
        var cursor = rangeStart.clone() as Calendar
        if (cursor.timeInMillis == day.timeInMillis) return 0
        var offset = 0
        val forward = cursor.before(day)
        while (cursor.timeInMillis != day.timeInMillis && kotlin.math.abs(offset) <= MaxPolicySpanDays) {
            cursor.add(Calendar.DAY_OF_YEAR, if (forward) 1 else -1)
            offset += if (forward) 1 else -1
        }
        require(cursor.timeInMillis == day.timeInMillis) { "Schedule policy date is outside the supported range." }
        return offset
    }

    private fun addLocalMinutes(epochMillis: Long, minutes: Int): Long = Calendar.getInstance(timeZone).apply {
        timeInMillis = epochMillis
        add(Calendar.MINUTE, minutes)
    }.timeInMillis

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
        if (strategy == null) return if (rule.type == ScheduleRuleType.CYCLE_SLOT) StudyActivityType.CUSTOM
        else StudyActivityType.THEORY
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

    private data class Recurrence(val kind: RecurrenceKind, val interval: Int, val count: Int?)
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
        private const val RebalanceGapMillis = 15L * 60L * 1000L
        private const val PlacementStepMinutes = 15
        private const val MaxPlacementDays = 366
        private const val MaxPlacementAttempts = MaxPlacementDays * 24 * 60 / PlacementStepMinutes
        private const val MaxPolicySpanDays = 3660

        fun generatedRulePrefix(ruleId: ScheduleRuleId): String = "generated:${sha256(ruleId.value).take(16)}:"

        fun generatedActivityId(ruleId: ScheduleRuleId, startEpochMillis: Long): String =
            "${generatedRulePrefix(ruleId)}$startEpochMillis"

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

private fun Calendar.copyAndAddDays(days: Int): Calendar =
    (clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days) }

private fun Calendar.copyAndAddMinutes(minutes: Int): Calendar =
    (clone() as Calendar).apply { add(Calendar.MINUTE, minutes) }
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
