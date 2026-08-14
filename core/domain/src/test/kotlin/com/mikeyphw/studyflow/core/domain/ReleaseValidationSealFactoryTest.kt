package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ReleaseValidationGateKind
import com.mikeyphw.studyflow.core.model.ReleaseValidationGateStatus
import com.mikeyphw.studyflow.core.model.ReleaseValidationProvenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseValidationSealFactoryTest {
    private val allPass = ReleaseValidationSealFactory.requiredKinds().associateWith { kind ->
        ReleaseGateEvidence(ReleaseValidationGateStatus.PASS, "verified:${kind.name}")
    }

    @Test
    fun sourceOnlySealBlocksReleaseUntilEveryRealGateHasPassEvidence() {
        val seal = ReleaseValidationSealFactory.sourceOnlySeal(generatedAtEpochMillis = 10L)
        assertEquals(37, seal.currentPhase)
        assertEquals(seal.currentPhase, seal.validatedPhase)
        assertEquals(ReleaseValidationProvenance.SOURCE_ONLY, seal.provenance)
        assertFalse(seal.releaseReady)
        assertTrue(seal.gates.any { it.kind == ReleaseValidationGateKind.ASSEMBLE && it.blocksRelease })
        assertTrue(seal.gates.any { it.kind == ReleaseValidationGateKind.BUNDLE && it.blocksRelease })
        assertTrue(seal.gates.any { it.kind == ReleaseValidationGateKind.SIGNED_ARTIFACT_IDENTITY && it.blocksRelease })
        assertTrue(seal.gates.any { it.kind == ReleaseValidationGateKind.FULL_JOURNEY && it.blocksRelease })
        assertTrue(seal.gates.any { it.kind == ReleaseValidationGateKind.DEVICE_ACCEPTANCE && it.blocksRelease })
    }

    @Test
    fun evidenceSummaryCannotGrantAuthoritativeReleaseReadiness() {
        val seal = ReleaseValidationSealFactory.evidenceSeal(20L, allPass)
        assertEquals(ReleaseValidationProvenance.EVIDENCE_SUMMARY, seal.provenance)
        assertFalse(seal.releaseReady)
        assertEquals(0, seal.blockerCount)
    }

    @Test
    fun authoritativeVerdictRequiresArtifactAndSignerBinding() {
        val seal = ReleaseValidationSealFactory.authoritativeVerdictSeal(
            generatedAtEpochMillis = 25L,
            validationSession = "release-session",
            artifactSha256 = "a".repeat(64),
            signerCertificateSha256 = listOf("b".repeat(64)),
            evidence = allPass,
        )
        assertEquals(ReleaseValidationProvenance.AUTHORITATIVE_VERDICT, seal.provenance)
        assertTrue(seal.releaseReady)
        assertEquals("a".repeat(64), seal.artifactSha256)
        assertEquals(listOf("b".repeat(64)), seal.signerCertificateSha256)
    }

    @Test
    fun warningFailureAndMissingEvidenceAllBlockAuthoritativeRelease() {
        listOf(ReleaseValidationGateStatus.WARNING, ReleaseValidationGateStatus.FAIL).forEach { status ->
            val seal = ReleaseValidationSealFactory.authoritativeVerdictSeal(
                generatedAtEpochMillis = 30L,
                validationSession = "release-session",
                artifactSha256 = "a".repeat(64),
                signerCertificateSha256 = listOf("b".repeat(64)),
                evidence = allPass +
                    (ReleaseValidationGateKind.LINT to ReleaseGateEvidence(status, "lint did not pass cleanly")),
            )
            assertFalse(seal.releaseReady)
            assertTrue(seal.gates.single { it.kind == ReleaseValidationGateKind.LINT }.blocksRelease)
        }
        val missing = ReleaseValidationSealFactory.authoritativeVerdictSeal(
            generatedAtEpochMillis = 40L,
            validationSession = "release-session",
            artifactSha256 = "a".repeat(64),
            signerCertificateSha256 = listOf("b".repeat(64)),
            evidence = allPass - ReleaseValidationGateKind.DEVICE_ACCEPTANCE,
        )
        assertFalse(missing.releaseReady)
        assertEquals(
            ReleaseValidationGateStatus.BLOCKED,
            missing.gates.single { it.kind == ReleaseValidationGateKind.DEVICE_ACCEPTANCE }.status,
        )
    }
}
