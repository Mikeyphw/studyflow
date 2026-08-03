package com.mikeyphw.studyflow.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWorkbenchFinalSealTest {
    @Test
    fun `final seal passes only when every milestone validator privacy and validation check is present`() {
        val report = DebugWorkbenchFinalSealPolicy.evaluate(
            DebugWorkbenchFinalSealEvidence(
                implementedMilestones = DebugWorkbenchFinalSealPolicy.requiredMilestones,
                validatorNames = DebugWorkbenchFinalSealPolicy.requiredValidators,
                privacyChecks = DebugWorkbenchFinalSealPolicy.requiredPrivacyChecks,
                validationChecks = DebugWorkbenchFinalSealPolicy.requiredValidationChecks,
            ),
        )

        assertEquals(DebugWorkbenchFinalSealStatus.PASSED, report.status)
        assertTrue(report.passed)
        assertTrue(report.missingMilestones.isEmpty())
        assertTrue(report.missingValidators.isEmpty())
    }

    @Test
    fun `final seal fails when a validator is missing`() {
        val report = DebugWorkbenchFinalSealPolicy.evaluate(
            DebugWorkbenchFinalSealEvidence(
                implementedMilestones = DebugWorkbenchFinalSealPolicy.requiredMilestones,
                validatorNames = DebugWorkbenchFinalSealPolicy.requiredValidators - "verify_debug_workbench_d8.py",
                privacyChecks = DebugWorkbenchFinalSealPolicy.requiredPrivacyChecks,
                validationChecks = DebugWorkbenchFinalSealPolicy.requiredValidationChecks,
            ),
        )

        assertEquals(DebugWorkbenchFinalSealStatus.FAILED, report.status)
        assertFalse(report.passed)
        assertTrue("verify_debug_workbench_d8.py" in report.missingValidators)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `final seal evidence rejects path-like validator names`() {
        DebugWorkbenchFinalSealEvidence(
            implementedMilestones = DebugWorkbenchFinalSealPolicy.requiredMilestones,
            validatorNames = DebugWorkbenchFinalSealPolicy.requiredValidators + "../validator.py",
            privacyChecks = DebugWorkbenchFinalSealPolicy.requiredPrivacyChecks,
            validationChecks = DebugWorkbenchFinalSealPolicy.requiredValidationChecks,
        )
    }
}
