package com.mikeyphw.studyflow.core.model

enum class ReleaseValidationGateStatus {
    PASS,
    WARNING,
    BLOCKED,
    FAIL,
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
    BUNDLE,
    SIGNED_ARTIFACT_IDENTITY,
    FULL_JOURNEY,
    DEVICE_ACCEPTANCE,
    FINAL_STAGING,
}

enum class ReleaseValidationProvenance {
    SOURCE_ONLY,
    EVIDENCE_SUMMARY,
    AUTHORITATIVE_VERDICT,
}

data class ReleaseValidationGate(
    val kind: ReleaseValidationGateKind,
    val status: ReleaseValidationGateStatus,
    val label: String,
    val evidence: String,
    val requiredForRelease: Boolean = true,
) {
    val blocksRelease: Boolean = requiredForRelease && status != ReleaseValidationGateStatus.PASS
}

data class ReleaseValidationSeal(
    val currentPhase: Int,
    val validatedPhase: Int,
    val gates: List<ReleaseValidationGate>,
    val generatedAtEpochMillis: Long,
    val provenance: ReleaseValidationProvenance = ReleaseValidationProvenance.SOURCE_ONLY,
    val validationSession: String? = null,
    val artifactSha256: String? = null,
    val signerCertificateSha256: List<String> = emptyList(),
) {
    private val artifactBound: Boolean = !validationSession.isNullOrBlank() &&
        artifactSha256.isSha256() &&
        signerCertificateSha256.isNotEmpty() &&
        signerCertificateSha256.all(String::isSha256)

    val releaseReady: Boolean = provenance == ReleaseValidationProvenance.AUTHORITATIVE_VERDICT &&
        artifactBound &&
        currentPhase == validatedPhase &&
        gates.isNotEmpty() &&
        gates.filter(ReleaseValidationGate::requiredForRelease).all { gate ->
            gate.status == ReleaseValidationGateStatus.PASS
        }
    val blockerCount: Int = gates.count(ReleaseValidationGate::blocksRelease)
    val warningCount: Int = gates.count { gate -> gate.status == ReleaseValidationGateStatus.WARNING }
    val failureCount: Int = gates.count { gate -> gate.status == ReleaseValidationGateStatus.FAIL }
    val passedCount: Int = gates.count { gate -> gate.status == ReleaseValidationGateStatus.PASS }

    private fun String?.isSha256(): Boolean = this?.matches(Regex("[0-9a-fA-F]{64}")) == true
}
