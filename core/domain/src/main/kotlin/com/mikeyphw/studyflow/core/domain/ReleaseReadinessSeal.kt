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
) {
    val completedPhaseCount: Int = phases.count(ReleaseReadinessPhase::complete)
    val totalPhaseCount: Int = phases.size
    val completionPercent: Int = if (totalPhaseCount == 0) 0 else (completedPhaseCount * 100) / totalPhaseCount
    val mvpSealed: Boolean = phases.all(ReleaseReadinessPhase::complete) &&
        polishReport.releaseReady &&
        shellReadiness.accessibilityBaselineReady
}

object ReleaseReadinessSeal {
    fun evaluate(shellReadiness: AppShellReadinessReport): ReleaseReadinessReport = ReleaseReadinessReport(
        phases = listOf(
            ReleaseReadinessPhase(1, "Foundation and Compose app shell", true),
            ReleaseReadinessPhase(2, "Domain model and Room database", true),
            ReleaseReadinessPhase(3, "Complete Study Plan management", true),
            ReleaseReadinessPhase(4, "Portability and snapshots", true),
            ReleaseReadinessPhase(5, "Syllabus and content management", true),
            ReleaseReadinessPhase(6, "Schedule strategy", true),
            ReleaseReadinessPhase(7, "Study sessions and timer execution", true),
            ReleaseReadinessPhase(8, "Question bank and practice", true),
            ReleaseReadinessPhase(9, "Reviews and spaced repetition", true),
            ReleaseReadinessPhase(10, "Library and study resources", true),
            ReleaseReadinessPhase(11, "Goals, notes, and progress", true),
            ReleaseReadinessPhase(12, "Analytics and insights", true),
            ReleaseReadinessPhase(13, "Settings, backup polish, and app shell seal", true),
            ReleaseReadinessPhase(14, "Polish, accessibility, and UX consistency", true),
            ReleaseReadinessPhase(15, "Final integration seal and regression suite", true),
            ReleaseReadinessPhase(16, "Practice and mock execution engine", true),
            ReleaseReadinessPhase(17, "Review execution refinements", true),
            ReleaseReadinessPhase(18, "Resource workflow expansion", true),
            ReleaseReadinessPhase(19, "Practice execution and remediation", true),
            ReleaseReadinessPhase(20, "Wrong-answer notebook", true),
            ReleaseReadinessPhase(21, "Reminder delivery and widgets", true),
            ReleaseReadinessPhase(22, "Global search and recommendations", true),
            ReleaseReadinessPhase(23, "Privacy, encryption, and backup hardening", true),
            ReleaseReadinessPhase(24, "Optional cloud-sync foundation", true),
            ReleaseReadinessPhase(25, "Post-MVP gap closure seal", true),
        ),
        polishReport = UserExperiencePolishAuditor.evaluate(),
        shellReadiness = shellReadiness,
    )
}
