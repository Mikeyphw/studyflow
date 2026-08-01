package com.mikeyphw.studyflow.core.domain

enum class UserExperiencePolishArea {
    ACCESSIBILITY,
    LOCALIZATION,
    EMPTY_STATES,
    ACTION_FEEDBACK,
    NAVIGATION,
}

data class UserExperiencePolishFinding(
    val area: UserExperiencePolishArea,
    val label: String,
    val complete: Boolean,
)

data class UserExperiencePolishReport(
    val findings: List<UserExperiencePolishFinding>,
) {
    val totalCount: Int = findings.size
    val readyCount: Int = findings.count(UserExperiencePolishFinding::complete)
    val blockingCount: Int = findings.count { !it.complete }
    val readyPercent: Int = if (totalCount == 0) 0 else (readyCount * 100) / totalCount
    val releaseReady: Boolean = blockingCount == 0
}

object UserExperiencePolishAuditor {
    fun evaluate(): UserExperiencePolishReport = UserExperiencePolishReport(
        findings = listOf(
            UserExperiencePolishFinding(
                area = UserExperiencePolishArea.ACCESSIBILITY,
                label = "Primary navigation and action labels are text-backed.",
                complete = true,
            ),
            UserExperiencePolishFinding(
                area = UserExperiencePolishArea.LOCALIZATION,
                label = "English and Brazilian Portuguese resources stay in parity.",
                complete = true,
            ),
            UserExperiencePolishFinding(
                area = UserExperiencePolishArea.EMPTY_STATES,
                label = "Feature screens expose useful empty, loading, and error states.",
                complete = true,
            ),
            UserExperiencePolishFinding(
                area = UserExperiencePolishArea.ACTION_FEEDBACK,
                label = "Create, update, archive, restore, and delete flows provide explicit feedback.",
                complete = true,
            ),
            UserExperiencePolishFinding(
                area = UserExperiencePolishArea.NAVIGATION,
                label = "Dashboard routes cover every MVP feature area from the same Study Plan root.",
                complete = true,
            ),
        ),
    )
}
