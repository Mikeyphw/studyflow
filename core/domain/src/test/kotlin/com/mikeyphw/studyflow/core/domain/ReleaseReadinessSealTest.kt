package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.ReleaseValidationGateStatus
import com.mikeyphw.studyflow.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseReadinessSealTest {
    private val shell = AppShellReadinessAuditor.evaluate(
        AppShellReadinessInput(
            language = AppLanguage.SYSTEM_DEFAULT,
            themeMode = ThemeMode.SYSTEM_DEFAULT,
            dynamicColorEnabled = false,
            hasDefaultPlan = true,
        ),
    )
    private val allPhaseNumbers = (1..37).toSet() + (101..108).toSet()

    @Test
    fun historicalPhasesStayUnprovenUntilExplicitCompletionEvidenceIsSupplied() {
        val report = ReleaseReadinessSeal.evaluate(shell, ReleaseValidationSealFactory.sourceOnlySeal(1L))
        assertEquals(45, report.totalPhaseCount)
        assertEquals(0, report.completedPhaseCount)
        assertEquals(0, report.completionPercent)
        assertTrue(report.phases.none(ReleaseReadinessPhase::complete))
        assertFalse(report.productSealed)
    }

    @Test
    fun summaryPassStringsCannotSealProduct() {
        val evidence = ReleaseValidationSealFactory.requiredKinds().associateWith { kind ->
            ReleaseGateEvidence(ReleaseValidationGateStatus.PASS, "verified:${kind.name}")
        }
        val report = ReleaseReadinessSeal.evaluate(
            shellReadiness = shell,
            releaseValidation = ReleaseValidationSealFactory.evidenceSeal(2L, evidence),
            completedPhaseNumbers = allPhaseNumbers,
        )
        assertFalse(report.releaseValidation.releaseReady)
        assertEquals(100, report.completionPercent)
        assertFalse(report.productSealed)
    }

    @Test
    fun productSealsOnlyForArtifactBoundAuthoritativeVerdictAndCompletedPhases() {
        val evidence = ReleaseValidationSealFactory.requiredKinds().associateWith { kind ->
            ReleaseGateEvidence(ReleaseValidationGateStatus.PASS, "verified:${kind.name}")
        }
        val report = ReleaseReadinessSeal.evaluate(
            shellReadiness = shell,
            releaseValidation = ReleaseValidationSealFactory.authoritativeVerdictSeal(
                generatedAtEpochMillis = 3L,
                validationSession = "acceptance-session",
                artifactSha256 = "a".repeat(64),
                signerCertificateSha256 = listOf("b".repeat(64)),
                evidence = evidence,
            ),
            completedPhaseNumbers = allPhaseNumbers,
        )
        assertTrue(report.releaseValidation.releaseReady)
        assertTrue(report.productSealed)
    }
}
