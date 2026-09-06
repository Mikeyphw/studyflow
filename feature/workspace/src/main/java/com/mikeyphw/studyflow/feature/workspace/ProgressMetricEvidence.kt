package com.mikeyphw.studyflow.feature.workspace

enum class ProgressMetricConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

data class EvidenceAwareProgressMetric(
    val percent: Int?,
    val evidenceCount: Int,
    val confidence: ProgressMetricConfidence?,
) {
    init {
        require(evidenceCount >= 0) { "Metric evidence count cannot be negative." }
        require(percent == null || percent in 0..100) { "Metric percent must be between 0 and 100." }
        require((percent == null) == (confidence == null)) {
            "Metric confidence must be absent exactly when the metric value is unavailable."
        }
    }
}

object ProgressMetricEvidence {
    fun accuracy(correctAnswers: Int, answeredQuestions: Int): EvidenceAwareProgressMetric {
        require(answeredQuestions >= 0) { "Answered question count cannot be negative." }
        require(correctAnswers in 0..answeredQuestions) { "Correct answers must be within answered questions." }
        if (answeredQuestions == 0) return unavailable()
        val percent = ((correctAnswers.toDouble() / answeredQuestions.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
        return available(percent, answeredQuestions, mediumAt = 10, highAt = 50)
    }

    fun schedule(percent: Int, observedBlocks: Int): EvidenceAwareProgressMetric =
        observedPercent(percent, observedBlocks, mediumAt = 4, highAt = 12)

    fun goals(percent: Int, observedGoals: Int): EvidenceAwareProgressMetric =
        observedPercent(percent, observedGoals, mediumAt = 2, highAt = 5)

    private fun observedPercent(
        percent: Int,
        evidenceCount: Int,
        mediumAt: Int,
        highAt: Int,
    ): EvidenceAwareProgressMetric {
        require(evidenceCount >= 0) { "Metric evidence count cannot be negative." }
        if (evidenceCount == 0) return unavailable()
        return available(percent.coerceIn(0, 100), evidenceCount, mediumAt, highAt)
    }

    private fun available(
        percent: Int,
        evidenceCount: Int,
        mediumAt: Int,
        highAt: Int,
    ): EvidenceAwareProgressMetric = EvidenceAwareProgressMetric(
        percent = percent,
        evidenceCount = evidenceCount,
        confidence = when {
            evidenceCount >= highAt -> ProgressMetricConfidence.HIGH
            evidenceCount >= mediumAt -> ProgressMetricConfidence.MEDIUM
            else -> ProgressMetricConfidence.LOW
        },
    )

    private fun unavailable(): EvidenceAwareProgressMetric = EvidenceAwareProgressMetric(
        percent = null,
        evidenceCount = 0,
        confidence = null,
    )
}
