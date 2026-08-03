package com.mikeyphw.studyflow.core.diagnostics

/** Final release seal for the StudyFlow Debug Workbench D1 through D8 chain. */
data class DebugWorkbenchFinalSealEvidence(
    val implementedMilestones: Set<DebugWorkbenchMilestone>,
    val validatorNames: Set<String>,
    val privacyChecks: Set<DebugWorkbenchPrivacyCheck>,
    val validationChecks: Set<DebugWorkbenchValidationCheck>,
) {
    init {
        require(validatorNames.none { name -> '/' in name || name.contains("\\") || name.contains("..") }) {
            "Validator names must be simple filenames."
        }
    }
}

enum class DebugWorkbenchMilestone {
    D1_RECORDER_PRIVACY,
    D2_SHELL_TIMELINE_REPORTS,
    D3_TYPED_PROBE_REGISTRY,
    D4_REMINDER_NOTIFICATION_LAB,
    D5_DATABASE_PORTABILITY_LAB,
    D6_STUDY_ENGINE_LAB,
    D7_RUNTIME_SELF_CHECK_SUPPORT_SEAL,
    D8_FINAL_RELEASE_SEAL,
}

enum class DebugWorkbenchPrivacyCheck {
    NO_LOGCAT_READER,
    NO_AUTOMATIC_UPLOAD,
    NO_RAW_STUDY_CONTENT,
    NO_DATABASE_OR_ANNEX_EXPORT,
    SUPPORT_BUNDLE_ALLOWLIST,
    SANITIZED_CLIPBOARD_OUTPUT,
}

enum class DebugWorkbenchValidationCheck {
    HISTORICAL_PHASE_VALIDATORS,
    DEBUG_WORKBENCH_VALIDATORS,
    RESOURCE_PARITY,
    FORMATTING_SCAN,
    COMPILE_WIRING_SCAN,
    PROMISE_AUDIT,
}

enum class DebugWorkbenchFinalSealStatus {
    PASSED,
    FAILED,
}

data class DebugWorkbenchFinalSealReport(
    val status: DebugWorkbenchFinalSealStatus,
    val missingMilestones: Set<DebugWorkbenchMilestone> = emptySet(),
    val missingValidators: Set<String> = emptySet(),
    val missingPrivacyChecks: Set<DebugWorkbenchPrivacyCheck> = emptySet(),
    val missingValidationChecks: Set<DebugWorkbenchValidationCheck> = emptySet(),
) {
    init {
        require(missingValidators.none { name -> '/' in name || name.contains("\\") || name.contains("..") }) {
            "Final seal must not expose path-like validator names."
        }
    }

    val passed: Boolean = status == DebugWorkbenchFinalSealStatus.PASSED
}

object DebugWorkbenchFinalSealPolicy {
    val requiredMilestones: Set<DebugWorkbenchMilestone> = DebugWorkbenchMilestone.entries.toSet()

    val requiredValidators: Set<String> = setOf(
        "verify_debug_workbench_d1_r2.py",
        "verify_debug_workbench_d2.py",
        "verify_debug_workbench_d3.py",
        "verify_debug_workbench_d2_d3_corrective_seal.py",
        "verify_debug_workbench_d4.py",
        "verify_debug_workbench_d5.py",
        "verify_debug_workbench_d6.py",
        "verify_debug_workbench_d7.py",
        "verify_debug_workbench_d8.py",
    )

    val requiredPrivacyChecks: Set<DebugWorkbenchPrivacyCheck> = DebugWorkbenchPrivacyCheck.entries.toSet()
    val requiredValidationChecks: Set<DebugWorkbenchValidationCheck> = DebugWorkbenchValidationCheck.entries.toSet()

    fun evaluate(evidence: DebugWorkbenchFinalSealEvidence): DebugWorkbenchFinalSealReport {
        val missingMilestones = requiredMilestones - evidence.implementedMilestones
        val missingValidators = requiredValidators - evidence.validatorNames
        val missingPrivacyChecks = requiredPrivacyChecks - evidence.privacyChecks
        val missingValidationChecks = requiredValidationChecks - evidence.validationChecks
        val failed = missingMilestones.isNotEmpty() ||
            missingValidators.isNotEmpty() ||
            missingPrivacyChecks.isNotEmpty() ||
            missingValidationChecks.isNotEmpty()
        return DebugWorkbenchFinalSealReport(
            status = if (failed) DebugWorkbenchFinalSealStatus.FAILED else DebugWorkbenchFinalSealStatus.PASSED,
            missingMilestones = missingMilestones,
            missingValidators = missingValidators,
            missingPrivacyChecks = missingPrivacyChecks,
            missingValidationChecks = missingValidationChecks,
        )
    }
}
