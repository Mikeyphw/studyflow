package com.mikeyphw.studyflow.core.domain

data class ReleaseReadinessPhase(
    val number: Int,
    val title: String,
    val complete: Boolean,
)

data class ReleaseReadinessReport(
    val phases: List<ReleaseReadinessPhase>,
    val polishReport: UserExperiencePolishReport,
    val shellReadiness: AppShellReadinessReport,
    val releaseValidation: com.mikeyphw.studyflow.core.model.ReleaseValidationSeal,
) {
    val completedPhaseCount: Int = phases.count(ReleaseReadinessPhase::complete)
    val totalPhaseCount: Int = phases.size
    val completionPercent: Int = if (totalPhaseCount == 0) 0 else (completedPhaseCount * 100) / totalPhaseCount
    val productSealed: Boolean = phases.all(ReleaseReadinessPhase::complete) &&
        polishReport.releaseReady &&
        shellReadiness.accessibilityBaselineReady &&
        releaseValidation.releaseReady
}

object ReleaseReadinessSeal {
    fun evaluate(
        shellReadiness: AppShellReadinessReport,
        releaseValidation: com.mikeyphw.studyflow.core.model.ReleaseValidationSeal,
        completedPhaseNumbers: Set<Int> = emptySet(),
    ): ReleaseReadinessReport = ReleaseReadinessReport(
        phases = listOf(
            ReleaseReadinessPhase(1, "Foundation and Compose app shell", complete = 1 in completedPhaseNumbers),
            ReleaseReadinessPhase(2, "Domain model and Room database", complete = 2 in completedPhaseNumbers),
            ReleaseReadinessPhase(3, "Complete Study Plan management", complete = 3 in completedPhaseNumbers),
            ReleaseReadinessPhase(4, "Portability and snapshots", complete = 4 in completedPhaseNumbers),
            ReleaseReadinessPhase(5, "Syllabus and content management", complete = 5 in completedPhaseNumbers),
            ReleaseReadinessPhase(6, "Schedule strategy", complete = 6 in completedPhaseNumbers),
            ReleaseReadinessPhase(7, "Study sessions and timer execution", complete = 7 in completedPhaseNumbers),
            ReleaseReadinessPhase(8, "Question bank and practice", complete = 8 in completedPhaseNumbers),
            ReleaseReadinessPhase(9, "Reviews and spaced repetition", complete = 9 in completedPhaseNumbers),
            ReleaseReadinessPhase(10, "Library and study resources", complete = 10 in completedPhaseNumbers),
            ReleaseReadinessPhase(11, "Goals, notes, and progress", complete = 11 in completedPhaseNumbers),
            ReleaseReadinessPhase(12, "Analytics and insights", complete = 12 in completedPhaseNumbers),
            ReleaseReadinessPhase(13, "Settings, backup polish, and app shell seal",
                complete = 13 in completedPhaseNumbers),
            ReleaseReadinessPhase(14, "Polish, accessibility, and UX consistency",
                complete = 14 in completedPhaseNumbers),
            ReleaseReadinessPhase(15, "Final integration seal and regression suite",
                complete = 15 in completedPhaseNumbers),
            ReleaseReadinessPhase(16, "Practice and mock execution engine", complete = 16 in completedPhaseNumbers),
            ReleaseReadinessPhase(17, "Review execution refinements", complete = 17 in completedPhaseNumbers),
            ReleaseReadinessPhase(18, "Resource workflow expansion", complete = 18 in completedPhaseNumbers),
            ReleaseReadinessPhase(19, "Practice execution and remediation", complete = 19 in completedPhaseNumbers),
            ReleaseReadinessPhase(20, "Wrong-answer notebook", complete = 20 in completedPhaseNumbers),
            ReleaseReadinessPhase(21, "Reminder delivery and widgets", complete = 21 in completedPhaseNumbers),
            ReleaseReadinessPhase(22, "Global search and recommendations", complete = 22 in completedPhaseNumbers),
            ReleaseReadinessPhase(23, "Privacy, encryption, and backup hardening",
                complete = 23 in completedPhaseNumbers),
            ReleaseReadinessPhase(24, "Offline checkpoint foundation", complete = 24 in completedPhaseNumbers),
            ReleaseReadinessPhase(25, "Post-MVP gap closure seal", complete = 25 in completedPhaseNumbers),
            ReleaseReadinessPhase(26, "Release validation seal", complete = 26 in completedPhaseNumbers),
            ReleaseReadinessPhase(27, "Runtime smoke workbench", complete = 27 in completedPhaseNumbers),
            ReleaseReadinessPhase(28, "Backup and restore drill seal", complete = 28 in completedPhaseNumbers),
            ReleaseReadinessPhase(29, "Reminder delivery grace and final-user readiness",
                complete = 29 in completedPhaseNumbers),
            ReleaseReadinessPhase(30, "Product truthfulness and user-facing settings cleanup",
                complete = 30 in completedPhaseNumbers),
            ReleaseReadinessPhase(31, "Dashboard action completion and session routing",
                complete = 31 in completedPhaseNumbers),
            ReleaseReadinessPhase(32, "Developer diagnostics de-scaffolding", complete = 32 in completedPhaseNumbers),
            ReleaseReadinessPhase(33, "Offline-first checkpoint completion", complete = 33 in completedPhaseNumbers),
            ReleaseReadinessPhase(34, "Interaction honesty and passive status UI",
                complete = 34 in completedPhaseNumbers),
            ReleaseReadinessPhase(35, "Documentation and artifact cleanup", complete = 35 in completedPhaseNumbers),
            ReleaseReadinessPhase(36, "Release identity and packaging policy", complete = 36 in completedPhaseNumbers),
            ReleaseReadinessPhase(37, "Real-device acceptance seal framework", complete = 37 in completedPhaseNumbers),
            ReleaseReadinessPhase(101, "Debug Workbench D1 diagnostics baseline",
                complete = 101 in completedPhaseNumbers),
            ReleaseReadinessPhase(102, "Debug Workbench D2 timeline and report controls",
                complete = 102 in completedPhaseNumbers),
            ReleaseReadinessPhase(103, "Debug Workbench D3 typed runtime probes",
                complete = 103 in completedPhaseNumbers),
            ReleaseReadinessPhase(104, "Debug Workbench D4 shell layout", complete = 104 in completedPhaseNumbers),
            ReleaseReadinessPhase(105, "Debug Workbench D5 support bundle actions",
                complete = 105 in completedPhaseNumbers),
            ReleaseReadinessPhase(106, "Debug Workbench D6 lab evidence", complete = 106 in completedPhaseNumbers),
            ReleaseReadinessPhase(107, "Debug Workbench D7 self-check history",
                complete = 107 in completedPhaseNumbers),
            ReleaseReadinessPhase(108, "Debug Workbench D8 final diagnostics seal",
                complete = 108 in completedPhaseNumbers),
        ),
        polishReport = UserExperiencePolishAuditor.evaluate(),
        shellReadiness = shellReadiness,
        releaseValidation = releaseValidation,
    )
}
