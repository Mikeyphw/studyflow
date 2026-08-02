package com.mikeyphw.studyflow.core.model

enum class ReleaseValidationGateStatus {
    PASS,
    WARNING,
    BLOCKED,
}

enum class ReleaseValidationGateKind {
    ARTIFACT_MANIFEST,
    SOURCE_CONTRACTS,
    GRADLE_CONFIGURATION,
    MAIN_COMPILATION,
    TEST_COMPILATION,
    UNIT_TESTS,
    ROOM_SCHEMA,
    RESOURCE_MERGE,
    LINT,
    ASSEMBLE,
    DEVICE_SMOKE,
    FINAL_STAGING,
}

data class ReleaseValidationGate(
    val kind: ReleaseValidationGateKind,
    val status: ReleaseValidationGateStatus,
    val label: String,
    val evidence: String,
    val requiredForRelease: Boolean = true,
) {
    val blocksRelease: Boolean = requiredForRelease && status == ReleaseValidationGateStatus.BLOCKED
}

data class ReleaseValidationSeal(
    val currentPhase: Int,
    val validatedPhase: Int,
    val gates: List<ReleaseValidationGate>,
    val generatedAtEpochMillis: Long,
) {
    val releaseReady: Boolean = currentPhase == validatedPhase && gates.none(ReleaseValidationGate::blocksRelease)
    val blockerCount: Int = gates.count(ReleaseValidationGate::blocksRelease)
    val warningCount: Int = gates.count { gate -> gate.status == ReleaseValidationGateStatus.WARNING }
    val passedCount: Int = gates.count { gate -> gate.status == ReleaseValidationGateStatus.PASS }
}
