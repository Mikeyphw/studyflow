package com.mikeyphw.studyflow.core.model

/**
 * Canonical product-level validation bounds shared by models and managers.
 *
 * Keep persistence constructors and mutation managers on these exact contracts so a value accepted
 * by one layer cannot fail later when the same object crosses another layer.
 */
object StudyDomainInvariants {
    const val MIN_SESSION_MINUTES = 5
    const val MAX_SESSION_MINUTES = 720
    const val MAX_PREFERRED_SESSION_MINUTES = 240
    const val MAX_STRATEGY_MINIMUM_MINUTES = 480

    val sessionMinutes: IntRange = MIN_SESSION_MINUTES..MAX_SESSION_MINUTES
    val preferredSessionMinutes: IntRange = MIN_SESSION_MINUTES..MAX_PREFERRED_SESSION_MINUTES

    fun requireSessionMinutes(value: Int, label: String = "Session duration") {
        require(value in sessionMinutes) {
            "$label must be between $MIN_SESSION_MINUTES and $MAX_SESSION_MINUTES minutes."
        }
    }

    fun requireOptionalSessionMinutes(value: Int?, label: String = "Session duration") {
        if (value != null) requireSessionMinutes(value, label)
    }

    fun requireStrategyDurations(minimumMinutes: Int, maximumMinutes: Int) {
        require(minimumMinutes in MIN_SESSION_MINUTES..MAX_STRATEGY_MINIMUM_MINUTES) {
            "Minimum session duration must be between $MIN_SESSION_MINUTES and $MAX_STRATEGY_MINIMUM_MINUTES minutes."
        }
        require(maximumMinutes in minimumMinutes..MAX_SESSION_MINUTES) {
            "Maximum session duration must be between the minimum and $MAX_SESSION_MINUTES minutes."
        }
    }

    fun requireMaterialTotalUnits(totalUnits: Int?) {
        require(totalUnits == null || totalUnits > 0) { "Total units must be positive when present." }
    }
}
