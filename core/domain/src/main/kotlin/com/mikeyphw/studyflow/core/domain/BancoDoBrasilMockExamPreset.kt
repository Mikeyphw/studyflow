package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.Subject
import kotlin.math.floor

/**
 * Selection counts are derived from the supplied reference point weights so the preset can request
 * exactly 70 objective questions without pretending that those derived counts are official item counts.
 */
enum class BancoDoBrasilQuestionAllocationPolicy {
    DERIVED_FROM_REFERENCE_POINT_WEIGHTS,
}

data class BancoDoBrasilMockExamPresetPreview(
    val blueprint: ExamBlueprint,
    val preview: ExamBlueprintPreview,
    val allocationPolicy: BancoDoBrasilQuestionAllocationPolicy,
    val essayMinimumScorePercent: Int,
) {
    val missingQuestionCount: Int
        get() = preview.missingQuestionCount

    val executable: Boolean
        get() = preview.executable
}

object BancoDoBrasilMockExamPreset {
    const val ExamBoardFilter = "Cesgranrio"
    const val ReferenceFullMockMinutes = 300

    private data class SubjectReference(
        val aliases: Set<String>,
        val weight: Double,
    )

    private val references = listOf(
        SubjectReference(setOf("língua portuguesa", "portuguese"), 15.0),
        SubjectReference(setOf("língua inglesa", "english"), 5.0),
        SubjectReference(setOf("matemática", "mathematics"), 7.5),
        SubjectReference(
            setOf("atualidades do mercado financeiro", "financial market current affairs"),
            5.0,
        ),
        SubjectReference(setOf("matemática financeira", "financial mathematics"), 7.5),
        SubjectReference(setOf("conhecimentos bancários", "banking knowledge"), 15.0),
        SubjectReference(setOf("conhecimentos de informática", "information technology"), 22.5),
        SubjectReference(setOf("vendas e negociação", "sales and negotiation"), 22.5),
    )

    fun buildBlueprint(
        syllabus: StudySyllabus,
        title: String,
        timeLimitMinutes: Int = ReferenceFullMockMinutes,
    ): ExamBlueprint? {
        val subjects = resolveSubjects(syllabus) ?: return null
        val counts = allocateQuestionCounts(references.map(SubjectReference::weight))
        val sections = subjects.mapIndexed { index, subject ->
            ExamBlueprintSection(
                key = subject.name,
                questionCount = counts[index],
                weight = references[index].weight,
                minimumScorePercent = null,
                subjectId = subject.id,
            )
        }
        return ExamBlueprint(
            title = title.trim().ifBlank { "BB Agente Comercial mock" },
            sections = sections,
            timeLimitMinutes = timeLimitMinutes,
            minimumOverallScorePercent = null,
            examBoard = ExamBoardFilter,
            year = null,
            strictExamMode = true,
            scoringPolicy = ExamScoringPolicy(mode = ExamScoringMode.NO_PENALTY),
        )
    }

    fun preview(
        syllabus: StudySyllabus,
        questions: List<Question>,
        title: String,
        timeLimitMinutes: Int = ReferenceFullMockMinutes,
    ): BancoDoBrasilMockExamPresetPreview? {
        val blueprint = buildBlueprint(syllabus, title, timeLimitMinutes) ?: return null
        return BancoDoBrasilMockExamPresetPreview(
            blueprint = blueprint,
            preview = ExamBlueprintPlanner.preview(questions, blueprint),
            allocationPolicy = BancoDoBrasilQuestionAllocationPolicy.DERIVED_FROM_REFERENCE_POINT_WEIGHTS,
            essayMinimumScorePercent = BancoDoBrasilAgenteComercialPreset.EssayMinimumScorePercent,
        )
    }

    private fun resolveSubjects(syllabus: StudySyllabus): List<Subject>? {
        val active = syllabus.subjects.filterNot(Subject::archived)
        val claimed = mutableSetOf<com.mikeyphw.studyflow.core.model.SubjectId>()
        val resolved = references.map { reference ->
            active.firstOrNull { subject ->
                subject.id !in claimed &&
                    normalizedName(subject.name) in reference.aliases.map(::normalizedName) &&
                    kotlin.math.abs(subject.examWeight - reference.weight) < 0.0001
            }?.also { subject -> claimed += subject.id }
        }
        return resolved.filterNotNull().takeIf { it.size == references.size }
    }

    internal fun allocateQuestionCounts(weights: List<Double>): List<Int> {
        require(weights.isNotEmpty())
        require(weights.all { it.isFinite() && it > 0.0 })
        val totalWeight = weights.sum()
        val raw = weights.map { weight ->
            weight / totalWeight * BancoDoBrasilAgenteComercialPreset.ObjectiveQuestionCount
        }
        val counts = raw.map { floor(it).toInt() }.toMutableList()
        var remaining = BancoDoBrasilAgenteComercialPreset.ObjectiveQuestionCount - counts.sum()
        raw.indices
            .sortedWith(compareByDescending<Int> { raw[it] - floor(raw[it]) }.thenBy { it })
            .forEach { index ->
                if (remaining > 0) {
                    counts[index] += 1
                    remaining -= 1
                }
            }
        require(counts.sum() == BancoDoBrasilAgenteComercialPreset.ObjectiveQuestionCount)
        return counts
    }

    private fun normalizedName(value: String): String = value.trim().lowercase()
}
