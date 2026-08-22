package com.mikeyphw.studyflow.core.domain

enum class ReleaseCandidatePolishArea {
    SETTINGS_POLISH,
    EMPTY_ERROR_LOADING_STATES,
    ACCESSIBILITY_LABELS,
    RESOURCE_PARITY,
    TRUST_COPY,
    FIRST_RUN_UPGRADE_NOTES,
    FINAL_ROADMAP_INDEX,
}

data class ReleaseCandidatePolishEvidence(
    val settingsPolishVerified: Boolean = false,
    val emptyErrorLoadingStatesReviewed: Boolean = false,
    val accessibilityLabelsReviewed: Boolean = false,
    val resourceParityVerified: Boolean = false,
    val trustCopyReviewed: Boolean = false,
    val firstRunUpgradeNotesPublished: Boolean = false,
    val finalRoadmapIndexPublished: Boolean = false,
) {
    companion object {
        fun verifiedForReleaseCandidateDocs(): ReleaseCandidatePolishEvidence = ReleaseCandidatePolishEvidence(
            settingsPolishVerified = true,
            emptyErrorLoadingStatesReviewed = true,
            accessibilityLabelsReviewed = true,
            resourceParityVerified = true,
            trustCopyReviewed = true,
            firstRunUpgradeNotesPublished = true,
            finalRoadmapIndexPublished = true,
        )
    }
}

data class ReleaseCandidatePolishFinding(
    val area: ReleaseCandidatePolishArea,
    val label: String,
    val complete: Boolean,
    val evidence: String,
)

data class ReleaseCandidatePolishReport(
    val findings: List<ReleaseCandidatePolishFinding>,
) {
    val totalCount: Int = findings.size
    val readyCount: Int = findings.count(ReleaseCandidatePolishFinding::complete)
    val blockingCount: Int = findings.count { finding -> !finding.complete }
    val readyPercent: Int = if (totalCount == 0) 0 else (readyCount * 100) / totalCount
    val releaseReady: Boolean = blockingCount == 0 &&
        findings.map { finding -> finding.area }.toSet() == ReleaseCandidatePolishArea.entries.toSet()
}

object ReleaseCandidatePolishAuditor {
    fun evaluate(
        evidence: ReleaseCandidatePolishEvidence = ReleaseCandidatePolishEvidence(),
    ): ReleaseCandidatePolishReport = ReleaseCandidatePolishReport(
        findings = listOf(
            finding(
                ReleaseCandidatePolishArea.SETTINGS_POLISH,
                "Settings groups backup, reminders, privacy, sync, runtime smoke, and release polish.",
                evidence.settingsPolishVerified,
                "Manual/visual Settings pass recorded for all release-facing groups.",
            ),
            finding(
                ReleaseCandidatePolishArea.EMPTY_ERROR_LOADING_STATES,
                "Core screens use explicit loading, empty, and recoverable error states.",
                evidence.emptyErrorLoadingStatesReviewed,
                "Manual screen-state pass recorded; not inferred from static marker text.",
            ),
            finding(
                ReleaseCandidatePolishArea.ACCESSIBILITY_LABELS,
                "Navigation, destructive actions, widget, and toolbar affordances keep text labels.",
                evidence.accessibilityLabelsReviewed,
                "Manual accessibility-label pass recorded for user-visible affordances.",
            ),
            finding(
                ReleaseCandidatePolishArea.RESOURCE_PARITY,
                "English and Brazilian Portuguese release-candidate copy stay in resource parity.",
                evidence.resourceParityVerified,
                "Resource parity verifier confirms EN/PT-BR strings for the release card.",
            ),
            finding(
                ReleaseCandidatePolishArea.TRUST_COPY,
                "Privacy, sync, reminder, and backup copy avoids unsupported cloud or exact-alarm claims.",
                evidence.trustCopyReviewed,
                "Trust-copy review avoids fake cloud-provider, account-identity, or exact-alarm claims.",
            ),
            finding(
                ReleaseCandidatePolishArea.FIRST_RUN_UPGRADE_NOTES,
                "First-run and upgrade notes explain local-first optional sync defaults and safe backup drills.",
                evidence.firstRunUpgradeNotesPublished,
                "First-run and upgrade notes are present and reviewed.",
            ),
            finding(
                ReleaseCandidatePolishArea.FINAL_ROADMAP_INDEX,
                "The final roadmap index records every phase through the release-candidate pass.",
                evidence.finalRoadmapIndexPublished,
                "Final roadmap index exists through Phase 29.",
            ),
        ),
    )

    private fun finding(
        area: ReleaseCandidatePolishArea,
        label: String,
        complete: Boolean,
        evidence: String,
    ): ReleaseCandidatePolishFinding = ReleaseCandidatePolishFinding(
        area = area,
        label = label,
        complete = complete,
        evidence = evidence,
    )
}
