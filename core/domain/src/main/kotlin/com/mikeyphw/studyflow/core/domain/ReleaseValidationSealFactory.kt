package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ReleaseValidationGate
import com.mikeyphw.studyflow.core.model.ReleaseValidationGateKind
import com.mikeyphw.studyflow.core.model.ReleaseValidationGateStatus
import com.mikeyphw.studyflow.core.model.ReleaseValidationProvenance
import com.mikeyphw.studyflow.core.model.ReleaseValidationSeal

data class ReleaseGateEvidence(
    val status: ReleaseValidationGateStatus,
    val evidence: String,
)

object ReleaseValidationSealFactory {
    private const val CurrentPhase = 37
    private val sha256Pattern = Regex("[0-9a-fA-F]{64}")

    fun sourceOnlySeal(generatedAtEpochMillis: Long): ReleaseValidationSeal = buildSeal(
        generatedAtEpochMillis = generatedAtEpochMillis,
        evidence = mapOf(
            ReleaseValidationGateKind.ARTIFACT_MANIFEST to passEvidence(
                "Overlay inventory and fail-closed validation contract are coherent.",
            ),
            ReleaseValidationGateKind.SOURCE_CONTRACTS to passEvidence(
                "Source/static contracts passed; this is not Android artifact acceptance.",
            ),
            ReleaseValidationGateKind.FINAL_STAGING to passEvidence(
                "Release evidence files can be staged without committing generated artifacts or secrets.",
            ),
        ),
        provenance = ReleaseValidationProvenance.SOURCE_ONLY,
    )

    /**
     * Summarizes supplied gate statuses without granting product-release authority.
     * Use [authoritativeVerdictSeal] only after the external final-verdict verifier has bound the
     * exact artifact, signer certificate, validation session, journey, and device acceptance.
     */
    fun evidenceSeal(
        generatedAtEpochMillis: Long,
        evidence: Map<ReleaseValidationGateKind, ReleaseGateEvidence>,
    ): ReleaseValidationSeal = buildSeal(
        generatedAtEpochMillis = generatedAtEpochMillis,
        evidence = evidence,
        provenance = ReleaseValidationProvenance.EVIDENCE_SUMMARY,
    )

    fun authoritativeVerdictSeal(
        generatedAtEpochMillis: Long,
        validationSession: String,
        artifactSha256: String,
        signerCertificateSha256: List<String>,
        evidence: Map<ReleaseValidationGateKind, ReleaseGateEvidence>,
    ): ReleaseValidationSeal {
        require(validationSession.isNotBlank()) { "Authoritative release evidence requires a validation session." }
        require(sha256Pattern.matches(artifactSha256)) { "Authoritative release evidence requires an APK SHA-256." }
        require(signerCertificateSha256.isNotEmpty() && signerCertificateSha256.all(sha256Pattern::matches)) {
            "Authoritative release evidence requires signer-certificate SHA-256 values."
        }
        return buildSeal(
            generatedAtEpochMillis = generatedAtEpochMillis,
            evidence = evidence,
            provenance = ReleaseValidationProvenance.AUTHORITATIVE_VERDICT,
            validationSession = validationSession,
            artifactSha256 = artifactSha256.lowercase(),
            signerCertificateSha256 = signerCertificateSha256.map(String::lowercase),
        )
    }

    fun requiredKinds(): Set<ReleaseValidationGateKind> = ReleaseValidationGateKind.entries.toSet()

    private fun buildSeal(
        generatedAtEpochMillis: Long,
        evidence: Map<ReleaseValidationGateKind, ReleaseGateEvidence>,
        provenance: ReleaseValidationProvenance,
        validationSession: String? = null,
        artifactSha256: String? = null,
        signerCertificateSha256: List<String> = emptyList(),
    ): ReleaseValidationSeal {
        val gates = ReleaseValidationGateKind.entries.map { kind ->
            val supplied = evidence[kind]
            ReleaseValidationGate(
                kind = kind,
                status = supplied?.status ?: ReleaseValidationGateStatus.BLOCKED,
                label = label(kind),
                evidence = supplied?.evidence?.trim().orEmpty().ifBlank { "Required evidence was not supplied." },
            )
        }
        return ReleaseValidationSeal(
            currentPhase = CurrentPhase,
            validatedPhase = CurrentPhase,
            generatedAtEpochMillis = generatedAtEpochMillis,
            gates = gates,
            provenance = provenance,
            validationSession = validationSession,
            artifactSha256 = artifactSha256,
            signerCertificateSha256 = signerCertificateSha256,
        )
    }

    private fun passEvidence(evidence: String) = ReleaseGateEvidence(ReleaseValidationGateStatus.PASS, evidence)

    private fun label(kind: ReleaseValidationGateKind): String = when (kind) {
        ReleaseValidationGateKind.ARTIFACT_MANIFEST -> "Artifact manifest"
        ReleaseValidationGateKind.SOURCE_CONTRACTS -> "Source contracts"
        ReleaseValidationGateKind.GRADLE_CONFIGURATION -> "Gradle configuration"
        ReleaseValidationGateKind.MAIN_COMPILATION -> "Main source compilation"
        ReleaseValidationGateKind.TEST_COMPILATION -> "Test source compilation"
        ReleaseValidationGateKind.UNIT_TESTS -> "Unit tests"
        ReleaseValidationGateKind.ROOM_SCHEMA -> "Room schema and migration validation"
        ReleaseValidationGateKind.RESOURCE_MERGE -> "Resource merge"
        ReleaseValidationGateKind.LINT -> "Lint"
        ReleaseValidationGateKind.ASSEMBLE -> "Release APK assembly"
        ReleaseValidationGateKind.BUNDLE -> "Release app bundle"
        ReleaseValidationGateKind.SIGNED_ARTIFACT_IDENTITY -> "Signed artifact identity"
        ReleaseValidationGateKind.FULL_JOURNEY -> "Full Android journey"
        ReleaseValidationGateKind.DEVICE_ACCEPTANCE -> "Device acceptance"
        ReleaseValidationGateKind.FINAL_STAGING -> "Final staging"
    }
}
