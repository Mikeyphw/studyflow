package com.mikeyphw.studyflow.tv.optimization

enum class StudyFlowTvReleaseBudgetBand {
    TARGET,
    ABOVE_TARGET,
    HARD_CEILING_EXCEEDED,
}

data class StudyFlowTvReleaseOptimizationSnapshot(
    val releaseBytes: Long,
    val minified: Boolean,
    val resourcesShrunk: Boolean,
    val mappingPresent: Boolean,
) {
    init {
        require(releaseBytes >= 0L) { "TV release size cannot be negative." }
    }
}

data class StudyFlowTvReleaseOptimizationResult(
    val budgetBand: StudyFlowTvReleaseBudgetBand,
    val optimized: Boolean,
    val acceptableForTvRelease: Boolean,
)

/** Pure release-budget policy used by TVOV18 tests and mirrored by the artifact verifier. */
object StudyFlowTvReleaseOptimizationPolicy {
    const val overlay = "TVOV18"
    const val releaseOptimizationSealed = true
    const val r8Required = true
    const val resourceShrinkingRequired = true
    const val optimizedDefaultRulesRequired = true
    const val mappingEvidenceRequired = true
    const val targetMiB = 25
    const val hardCeilingMiB = 35
    const val bytesPerMiB = 1024L * 1024L

    fun evaluate(snapshot: StudyFlowTvReleaseOptimizationSnapshot): StudyFlowTvReleaseOptimizationResult {
        val budgetBand = when {
            snapshot.releaseBytes <= targetMiB * bytesPerMiB -> StudyFlowTvReleaseBudgetBand.TARGET
            snapshot.releaseBytes <= hardCeilingMiB * bytesPerMiB -> StudyFlowTvReleaseBudgetBand.ABOVE_TARGET
            else -> StudyFlowTvReleaseBudgetBand.HARD_CEILING_EXCEEDED
        }
        val optimized = snapshot.minified && snapshot.resourcesShrunk && snapshot.mappingPresent
        return StudyFlowTvReleaseOptimizationResult(
            budgetBand = budgetBand,
            optimized = optimized,
            acceptableForTvRelease = optimized && budgetBand == StudyFlowTvReleaseBudgetBand.TARGET,
        )
    }
}
