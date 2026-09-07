package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyStrategy
import java.time.LocalDate

enum class StudyPhaseKind {
    FOUNDATION,
    EXPANSION,
    CONSOLIDATION,
    QUESTIONS_REVIEWS,
}

data class StudyPhaseDefinition(
    val kind: StudyPhaseKind,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val theoryRatioPercent: Int,
    val questionRatioPercent: Int,
    val reviewRatioPercent: Int,
    val questionGoal: Int? = null,
    val mockExamGoal: Int? = null,
    val allocationIsStudyFlowSuggested: Boolean = false,
) {
    init {
        require(!endsOn.isBefore(startsOn)) { "Study phase end cannot precede its start." }
        require(theoryRatioPercent in 0..100 && questionRatioPercent in 0..100 && reviewRatioPercent in 0..100) {
            "Study phase ratios must be between 0 and 100 percent."
        }
        require(theoryRatioPercent + questionRatioPercent + reviewRatioPercent == 100) {
            "Study phase ratios must total 100 percent."
        }
        require(questionGoal == null || questionGoal >= 0) { "Study phase question goal cannot be negative." }
        require(mockExamGoal == null || mockExamGoal >= 0) { "Study phase mock goal cannot be negative." }
    }

    fun contains(date: LocalDate): Boolean = !date.isBefore(startsOn) && !date.isAfter(endsOn)
}

data class StudyPhasePlan(val phases: List<StudyPhaseDefinition>) {
    init {
        val ordered = phases.sortedBy(StudyPhaseDefinition::startsOn)
        require(ordered.zipWithNext().none { (left, right) -> !right.startsOn.isAfter(left.endsOn) }) {
            "Study phases cannot overlap."
        }
        require(phases.map(StudyPhaseDefinition::kind).distinct().size == phases.size) {
            "A study phase kind may appear only once."
        }
    }

    fun activeOn(date: LocalDate): StudyPhaseDefinition? = phases.firstOrNull { it.contains(date) }
    fun nextAfter(date: LocalDate): StudyPhaseDefinition? =
        phases.filter { it.startsOn.isAfter(date) }.minByOrNull { it.startsOn }
}

object StudyPhasePlanning {
    fun effectiveStrategy(strategy: StudyStrategy?, plan: StudyPhasePlan, date: LocalDate): StudyStrategy? {
        val current = strategy ?: return null
        val phase = plan.activeOn(date) ?: return current
        return current.copy(
            theoryRatioPercent = phase.theoryRatioPercent,
            questionRatioPercent = phase.questionRatioPercent,
            reviewRatioPercent = phase.reviewRatioPercent,
        )
    }
}

/** Stores phase planning in the existing strategy JSON object; no schema/store duplication. */
object StudyPhasePlanCodec {
    private val fieldRegex = Regex("\\\"studyPhasesV1\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")

    fun decode(configurationJson: String): StudyPhasePlan {
        val raw = fieldRegex.find(configurationJson)?.groupValues?.get(1).orEmpty()
        if (raw.isBlank()) return StudyPhasePlan(emptyList())
        val phases = raw.split(';').mapNotNull(::decodePhase)
        return runCatching { StudyPhasePlan(phases) }.getOrElse { StudyPhasePlan(emptyList()) }
    }

    fun encode(configurationJson: String, plan: StudyPhasePlan): String {
        val normalized = configurationJson.trim().ifBlank { "{}" }
        require(normalized.startsWith('{') && normalized.endsWith('}')) {
            "Study strategy configuration must remain a JSON object."
        }
        val raw = plan.phases.sortedBy(StudyPhaseDefinition::startsOn).joinToString(";") { phase ->
            listOf(
                phase.kind.name,
                phase.startsOn,
                phase.endsOn,
                phase.theoryRatioPercent,
                phase.questionRatioPercent,
                phase.reviewRatioPercent,
                phase.questionGoal ?: "",
                phase.mockExamGoal ?: "",
                if (phase.allocationIsStudyFlowSuggested) 1 else 0,
            ).joinToString(",")
        }
        val replacement = "\"studyPhasesV1\":\"$raw\""
        if (fieldRegex.containsMatchIn(normalized)) return normalized.replace(fieldRegex, replacement)
        if (normalized == "{}") return "{$replacement}"
        val body = normalized.substring(1, normalized.lastIndex).trim()
        return if (body.isBlank()) "{$replacement}" else "{$body,$replacement}"
    }

    private fun decodePhase(raw: String): StudyPhaseDefinition? = runCatching {
        val parts = raw.split(',')
        if (parts.size != 9) return null
        StudyPhaseDefinition(
            kind = StudyPhaseKind.valueOf(parts[0]),
            startsOn = LocalDate.parse(parts[1]),
            endsOn = LocalDate.parse(parts[2]),
            theoryRatioPercent = parts[3].toInt(),
            questionRatioPercent = parts[4].toInt(),
            reviewRatioPercent = parts[5].toInt(),
            questionGoal = parts[6].takeIf(String::isNotBlank)?.toInt(),
            mockExamGoal = parts[7].takeIf(String::isNotBlank)?.toInt(),
            allocationIsStudyFlowSuggested = parts[8] == "1",
        )
    }.getOrNull()
}

object BancoDoBrasilStudyPhasePreset {
    /**
     * Dates and question/mock targets come from the supplied Sep–Dec 2026 reference plan.
     * Allocation ratios are StudyFlow planning suggestions and remain editable/non-official.
     */
    fun plan(): StudyPhasePlan = StudyPhasePlan(
        listOf(
            StudyPhaseDefinition(
                StudyPhaseKind.FOUNDATION, LocalDate.parse("2026-09-07"), LocalDate.parse("2026-09-30"),
                theoryRatioPercent = 60, questionRatioPercent = 25, reviewRatioPercent = 15,
                questionGoal = 300, mockExamGoal = 3, allocationIsStudyFlowSuggested = true,
            ),
            StudyPhaseDefinition(
                StudyPhaseKind.EXPANSION, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"),
                theoryRatioPercent = 45, questionRatioPercent = 40, reviewRatioPercent = 15,
                questionGoal = 500, mockExamGoal = 5, allocationIsStudyFlowSuggested = true,
            ),
            StudyPhaseDefinition(
                StudyPhaseKind.CONSOLIDATION, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"),
                theoryRatioPercent = 25, questionRatioPercent = 55, reviewRatioPercent = 20,
                questionGoal = 700, mockExamGoal = 4, allocationIsStudyFlowSuggested = true,
            ),
            StudyPhaseDefinition(
                StudyPhaseKind.QUESTIONS_REVIEWS, LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-31"),
                theoryRatioPercent = 10, questionRatioPercent = 65, reviewRatioPercent = 25,
                questionGoal = 1_000, mockExamGoal = 4, allocationIsStudyFlowSuggested = true,
            ),
        ),
    )
}
