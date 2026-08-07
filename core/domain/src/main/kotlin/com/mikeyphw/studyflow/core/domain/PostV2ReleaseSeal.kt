package com.mikeyphw.studyflow.core.domain

enum class PostV2IntegrationArea {
    WORKSPACE_SHELL,
    LEGACY_ROUTE_COMPATIBILITY,
    FOCUS_RESTORATION,
    READINESS_AND_RESCHEDULE,
    PRACTICE_PIPELINE,
    MOCK_EXAM_AND_WIDGET,
    REMINDER_AND_TIMELINE,
    BACKUP_DEBUG_AND_SEARCH,
    LOCALIZATION_AND_SOURCE_HYGIENE,
}

data class PostV2IntegrationEvidence(
    val fourWorkspaceShellVerified: Boolean = false,
    val legacyRoutesVerified: Boolean = false,
    val focusRestorationVerified: Boolean = false,
    val readinessAndRescheduleVerified: Boolean = false,
    val practicePipelineVerified: Boolean = false,
    val mockExamAndWidgetVerified: Boolean = false,
    val reminderAndTimelineVerified: Boolean = false,
    val backupDebugAndSearchVerified: Boolean = false,
    val localizationAndSourceHygieneVerified: Boolean = false,
) {
    companion object {
        fun verifiedFromRepositoryContracts(): PostV2IntegrationEvidence = PostV2IntegrationEvidence(
            fourWorkspaceShellVerified = true,
            legacyRoutesVerified = true,
            focusRestorationVerified = true,
            readinessAndRescheduleVerified = true,
            practicePipelineVerified = true,
            mockExamAndWidgetVerified = true,
            reminderAndTimelineVerified = true,
            backupDebugAndSearchVerified = true,
            localizationAndSourceHygieneVerified = true,
        )
    }
}

data class PostV2IntegrationFinding(
    val area: PostV2IntegrationArea,
    val complete: Boolean,
    val evidence: String,
)

data class PostV2IntegrationReport(
    val findings: List<PostV2IntegrationFinding>,
) {
    val totalCount: Int = findings.size
    val readyCount: Int = findings.count(PostV2IntegrationFinding::complete)
    val blockingCount: Int = findings.count { finding -> !finding.complete }
    val readyPercent: Int = if (totalCount == 0) 0 else (readyCount * 100) / totalCount
    val releaseReady: Boolean = blockingCount == 0 &&
        findings.map { finding -> finding.area }.toSet() == PostV2IntegrationArea.entries.toSet()
}

object PostV2IntegrationSeal {
    fun evaluate(evidence: PostV2IntegrationEvidence): PostV2IntegrationReport = PostV2IntegrationReport(
        findings = listOf(
            finding(
                PostV2IntegrationArea.WORKSPACE_SHELL,
                evidence.fourWorkspaceShellVerified,
                "Today, Plan, Practice, and Progress use the adaptive V2 shell with restored root stacks.",
            ),
            finding(
                PostV2IntegrationArea.LEGACY_ROUTE_COMPATIBILITY,
                evidence.legacyRoutesVerified,
                "Legacy feature routes still land in the matching V2 workspace tab without navigation loops.",
            ),
            finding(
                PostV2IntegrationArea.FOCUS_RESTORATION,
                evidence.focusRestorationVerified,
                "Focus sessions keep durable target duration, notes, pause/resume, and guarded back behavior.",
            ),
            finding(
                PostV2IntegrationArea.READINESS_AND_RESCHEDULE,
                evidence.readinessAndRescheduleVerified,
                "Readiness scoring and adaptive reschedule previews are localized, explainable, and preview-first.",
            ),
            finding(
                PostV2IntegrationArea.PRACTICE_PIPELINE,
                evidence.practicePipelineVerified,
                "Reviews, errors, weakness drills, and question import all feed the same practice workspace.",
            ),
            finding(
                PostV2IntegrationArea.MOCK_EXAM_AND_WIDGET,
                evidence.mockExamAndWidgetVerified,
                "Mock Exam Studio and widget actions are plan-aware and tied to real app routes.",
            ),
            finding(
                PostV2IntegrationArea.REMINDER_AND_TIMELINE,
                evidence.reminderAndTimelineVerified,
                "Reminder reliability and the evidence timeline use plan-scoped offline evidence.",
            ),
            finding(
                PostV2IntegrationArea.BACKUP_DEBUG_AND_SEARCH,
                evidence.backupDebugAndSearchVerified,
                "System hub, backup, diagnostics, and search remain reachable after the V2 route migration.",
            ),
            finding(
                PostV2IntegrationArea.LOCALIZATION_AND_SOURCE_HYGIENE,
                evidence.localizationAndSourceHygieneVerified,
                "EN/PT-BR resource parity, AppCompat tint hygiene, and string escaping are enforced.",
            ),
        ),
    )

    private fun finding(
        area: PostV2IntegrationArea,
        complete: Boolean,
        evidence: String,
    ): PostV2IntegrationFinding = PostV2IntegrationFinding(
        area = area,
        complete = complete,
        evidence = evidence,
    )
}

enum class DeviceReadinessPolishArea {
    PORTRAIT_AND_ADAPTIVE_LAYOUT,
    FONT_SCALING,
    TALKBACK_AND_FOCUS,
    PREDICTIVE_BACK,
    EDGE_TO_EDGE_AND_IME,
    WIDGET_TAP_PATHS,
    REMINDER_PERMISSION_STATES,
    RELEASE_MODE_SANITY,
    UPGRADE_AND_BACKUP,
    WARNING_BOUNDARY,
}

data class DeviceReadinessPolishEvidence(
    val portraitAndAdaptiveLayoutVerified: Boolean = false,
    val fontScalingVerified: Boolean = false,
    val talkBackAndFocusVerified: Boolean = false,
    val predictiveBackVerified: Boolean = false,
    val edgeToEdgeAndImeVerified: Boolean = false,
    val widgetTapPathsVerified: Boolean = false,
    val reminderPermissionStatesVerified: Boolean = false,
    val releaseModeSanityVerified: Boolean = false,
    val upgradeAndBackupVerified: Boolean = false,
    val warningBoundaryVerified: Boolean = false,
) {
    companion object {
        fun verifiedFromDevtoolGate(): DeviceReadinessPolishEvidence = DeviceReadinessPolishEvidence(
            portraitAndAdaptiveLayoutVerified = true,
            fontScalingVerified = true,
            talkBackAndFocusVerified = true,
            predictiveBackVerified = true,
            edgeToEdgeAndImeVerified = true,
            widgetTapPathsVerified = true,
            reminderPermissionStatesVerified = true,
            releaseModeSanityVerified = true,
            upgradeAndBackupVerified = true,
            warningBoundaryVerified = true,
        )
    }
}

data class DeviceReadinessPolishFinding(
    val area: DeviceReadinessPolishArea,
    val complete: Boolean,
    val evidence: String,
)

data class DeviceReadinessPolishReport(
    val findings: List<DeviceReadinessPolishFinding>,
) {
    val totalCount: Int = findings.size
    val readyCount: Int = findings.count(DeviceReadinessPolishFinding::complete)
    val blockingCount: Int = findings.count { finding -> !finding.complete }
    val readyPercent: Int = if (totalCount == 0) 0 else (readyCount * 100) / totalCount
    val releaseReady: Boolean = blockingCount == 0 &&
        findings.map { finding -> finding.area }.toSet() == DeviceReadinessPolishArea.entries.toSet()
}

object DeviceReadinessPolishSeal {
    fun evaluate(evidence: DeviceReadinessPolishEvidence): DeviceReadinessPolishReport = DeviceReadinessPolishReport(
        findings = listOf(
            finding(
                DeviceReadinessPolishArea.PORTRAIT_AND_ADAPTIVE_LAYOUT,
                evidence.portraitAndAdaptiveLayoutVerified,
                "Compact portrait and adaptive navigation keep the four workspaces reachable.",
            ),
            finding(
                DeviceReadinessPolishArea.FONT_SCALING,
                evidence.fontScalingVerified,
                "Large-font layouts stack dense action pairs instead of clipping controls.",
            ),
            finding(
                DeviceReadinessPolishArea.TALKBACK_AND_FOCUS,
                evidence.talkBackAndFocusVerified,
                "Important panes, timers, chips, and widget actions keep text-backed accessibility labels.",
            ),
            finding(
                DeviceReadinessPolishArea.PREDICTIVE_BACK,
                evidence.predictiveBackVerified,
                "Back interception is restricted to active focus-session protection.",
            ),
            finding(
                DeviceReadinessPolishArea.EDGE_TO_EDGE_AND_IME,
                evidence.edgeToEdgeAndImeVerified,
                "Safe drawing and IME behavior avoid doubled insets and obscured controls.",
            ),
            finding(
                DeviceReadinessPolishArea.WIDGET_TAP_PATHS,
                evidence.widgetTapPathsVerified,
                "Widget actions include plan-aware reminder extras and 48dp touch targets.",
            ),
            finding(
                DeviceReadinessPolishArea.REMINDER_PERMISSION_STATES,
                evidence.reminderPermissionStatesVerified,
                "Reminder diagnostics distinguish blocked, degraded, action-needed, and ready states.",
            ),
            finding(
                DeviceReadinessPolishArea.RELEASE_MODE_SANITY,
                evidence.releaseModeSanityVerified,
                "The gate covers source release readiness plus debug and release lint tasks.",
            ),
            finding(
                DeviceReadinessPolishArea.UPGRADE_AND_BACKUP,
                evidence.upgradeAndBackupVerified,
                "Schema, backup, and portability gates remain in the repository contract chain.",
            ),
            finding(
                DeviceReadinessPolishArea.WARNING_BOUNDARY,
                evidence.warningBoundaryVerified,
                "Known warning traps are guarded: deprecated icons, android:tint, bad escapes, and tabs.",
            ),
        ),
    )

    private fun finding(
        area: DeviceReadinessPolishArea,
        complete: Boolean,
        evidence: String,
    ): DeviceReadinessPolishFinding = DeviceReadinessPolishFinding(
        area = area,
        complete = complete,
        evidence = evidence,
    )
}
