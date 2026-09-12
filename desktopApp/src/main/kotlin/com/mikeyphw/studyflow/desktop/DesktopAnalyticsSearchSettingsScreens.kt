package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyGoalId

@Composable
fun DesktopAnalyticsScreen(
    state: DesktopAnalyticsScreenState,
    progress: DesktopProgressReadinessState? = null,
    actionMessage: String = "",
    goalTitle: String = "",
    goalTarget: String = "",
    goalType: GoalType = GoalType.STUDY_TIME,
    goalPeriod: GoalPeriod = GoalPeriod.WEEKLY,
    goalActionMessage: String = "",
    onGoalTitleChanged: (String) -> Unit = {},
    onGoalTargetChanged: (String) -> Unit = {},
    onCycleGoalType: () -> Unit = {},
    onCycleGoalPeriod: () -> Unit = {},
    onCreateGoal: () -> Unit = {},
    onIncrementGoal: (StudyGoalId) -> Unit = {},
    onArchiveGoal: (StudyGoalId) -> Unit = {},
    onRestoreGoal: (StudyGoalId) -> Unit = {},
    onDeleteGoal: (StudyGoalId) -> Unit = {},
    onApplySprint: () -> Unit = {},
    onExportReport: () -> Unit = {},
) {
    if (state.status == DesktopScreenStatus.EMPTY) {
        DesktopStateMessage(
            title = desktopText("No analytics yet"),
            message = "Analytics are wired, but the desktop repositories do not contain Study Plan data.",
        )
        return
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = desktopText("Study time"),
                value = "${state.studiedHoursSevenDays}h",
                detail = "${state.completedSessions} completed sessions",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = desktopText("Adherence"),
                value = "${state.scheduleAdherencePercent}%",
                detail = "Schedule completion",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = desktopText("Practice"),
                value = "${state.questionAccuracyPercent}%",
                detail = "${state.answeredQuestions} answered questions",
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = desktopText("Library"),
                value = "${state.libraryProgressPercent}%",
                detail = "Resource progress",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = desktopText("Consistency"),
                value = "${state.consistencyScorePercent}%",
                detail = "Last seven days",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = desktopText("Reviews"),
                value = state.dueReviewCount.toString(),
                detail = "${state.overdueReviewCount} overdue",
                modifier = Modifier.weight(1f),
            )
        }

        DesktopSectionCard(title = desktopText("Weak areas")) {
            state.weakAreas.forEach { area ->
                Text("• ${area.label}: ${area.masteryPercent}% mastery, priority ${area.priority}")
            }
        }

        DesktopSectionCard(title = desktopText("Insights")) {
            state.insights.forEach { insight ->
                Text("• ${insight.kind.name.replace('_', ' ')} — ${insight.severity.name.lowercase()}")
            }
        }

        progress?.takeIf { it.available }?.let { advanced ->
            val readiness = advanced.readiness

            DesktopSectionCard(title = desktopText("Goals")) {
                OutlinedTextField(
                    value = goalTitle,
                    onValueChange = onGoalTitleChanged,
                    label = { Text(desktopText("Goal title")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = goalTarget,
                    onValueChange = onGoalTargetChanged,
                    label = { Text(desktopText("Target value")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onCycleGoalType) {
                        Text(desktopText("Type") + ": " + goalType.name.replace('_', ' '))
                    }
                    Button(onClick = onCycleGoalPeriod) {
                        Text(desktopText("Period") + ": " + goalPeriod.name.replace('_', ' '))
                    }
                    Button(
                        onClick = onCreateGoal,
                        enabled = goalTitle.isNotBlank() && goalTarget.toDoubleOrNull() != null,
                    ) {
                        Text(desktopText("Create goal"))
                    }
                }
                if (advanced.goals.isEmpty()) {
                    Text(desktopText("No goals yet."))
                } else {
                    advanced.goals.forEach { goal ->
                        DesktopStateMessage(
                            title = goal.title,
                            message = "${goal.type.name.replace('_', ' ')} · " +
                                "${goal.currentValue}/${goal.targetValue} · ${goal.period.name.lowercase()}",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (goal.type == GoalType.CUSTOM && !goal.archived) {
                                Button(onClick = { onIncrementGoal(goal.id) }) { Text(desktopText("+1")) }
                            }
                            if (goal.archived) {
                                Button(onClick = { onRestoreGoal(goal.id) }) { Text(desktopText("Restore")) }
                            } else {
                                Button(onClick = { onArchiveGoal(goal.id) }) { Text(desktopText("Archive")) }
                            }
                            Button(onClick = { onDeleteGoal(goal.id) }) { Text(desktopText("Delete")) }
                        }
                    }
                }
                if (goalActionMessage.isNotBlank()) Text(goalActionMessage, fontWeight = FontWeight.Bold)
            }

            DesktopSectionCard(title = desktopText("Exam readiness")) {
                Text(
                    "Score: ${readiness?.scorePercent?.let { "$it%" } ?: "insufficient evidence"} · " +
                        "Band: ${readiness?.band?.name?.replace('_', ' ') ?: "unscored"}",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Evidence: ${readiness?.evidenceSampleCount ?: 0} samples across " +
                        "${readiness?.evidenceDimensionCount ?: 0} dimensions",
                )
                readiness?.signals?.take(5)?.forEach { signal ->
                    Text("• ${signal.kind.name.replace('_', ' ')}: ${signal.scorePercent?.let { "$it%" } ?: "n/a"}")
                }
            }

            DesktopSectionCard(title = desktopText("Weekly coach")) {
                val review = advanced.weeklyReview
                Text(
                    "${review?.studiedMinutes ?: 0} min · ${review?.evidenceCount ?: 0} evidence event(s) · " +
                        "${review?.actionCount ?: 0} action(s)",
                )
                review?.items?.forEach { item ->
                    Text("• ${item.kind.name.replace('_', ' ')} — ${item.tone.name.lowercase()}")
                }
            }

            DesktopSectionCard(title = desktopText("Content health")) {
                val cleanup = advanced.cleanupReport
                Text(
                    "Health: ${cleanup?.healthScorePercent ?: 100}% · ${cleanup?.issueCount ?: 0} issue(s) · " +
                        "${cleanup?.actionCount ?: 0} action item(s)",
                )
                cleanup?.issues?.take(6)?.forEach { issue ->
                    Text("• ${issue.kind.name.replace('_', ' ')}: ${issue.count} (${issue.severity.name.lowercase()})")
                }
                Text(
                    desktopText(
                        "Cleanup remains preview-only; DOV19 never performs destructive cleanup automatically.",
                    ),
                )
            }

            DesktopSectionCard(title = desktopText("Exam runbook")) {
                val runbook = advanced.runbook
                Text(
                    "${runbook?.state?.name?.replace('_', ' ') ?: "unavailable"} · " +
                        "${runbook?.daysUntilExam?.let { "$it day(s)" } ?: "no confirmed exam countdown"}",
                )
                runbook?.items?.forEach { item ->
                    Text("• ${item.window.name.replace('_', ' ')} — ${item.action.name.replace('_', ' ')}")
                }
            }

            DesktopSectionCard(title = desktopText("Seven-day study sprint")) {
                val sprint = advanced.sprintPreview
                Text(
                    "Focus: ${sprint?.focus?.name?.replace('_', ' ') ?: "none"} · " +
                        "${sprint?.candidateCount ?: 0} candidate(s) · ${sprint?.totalMinutes ?: 0} min",
                )
                Text("Executable changes: ${sprint?.executableChangeCount ?: 0}")
                Button(onClick = onApplySprint, enabled = sprint?.executable == true) {
                    Text(desktopText("Apply approved sprint preview"))
                }
            }

            DesktopSectionCard(title = desktopText("Exam report")) {
                val report = advanced.examReport
                Text(
                    "Evidence: ${report?.evidenceCount ?: 0} · Practice tests: ${report?.practiceTestCount ?: 0} · " +
                        "Mock exams: ${report?.mockExamCount ?: 0}",
                )
                Text(
                    "Cleanup health: ${report?.cleanupHealthScorePercent ?: 100}% · " +
                        "Next actions: ${report?.nextActions?.size ?: 0}",
                )
                Button(onClick = onExportReport, enabled = report != null) {
                    Text(desktopText("Export local HTML report"))
                }
                Text(
                    desktopText(
                        "Reports omit freeform study notes and stay inside the Desktop workspace.",
                    ),
                )
            }
            if (actionMessage.isNotBlank()) Text(actionMessage, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DesktopSearchScreen(
    state: DesktopSearchScreenState,
    onQueryChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            label = { Text(desktopText("Search StudyFlow")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopSectionCard(title = desktopText("Results"), modifier = Modifier.weight(2f)) {
                if (state.results.isEmpty()) {
                    Text(
                        desktopText(
                            "Type at least two characters to search plans, materials, activities, and questions.",
                        ),
                    )
                } else {
                    DesktopScrollableList(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.52f)) {
                        items(state.results.size) { index ->
                            val result = state.results[index]
                            DesktopStateMessage(
                                title = result.title,
                                message = "${result.typeLabel} • ${result.subtitle} • score ${result.score}",
                            )
                        }
                    }
                }
            }
            DesktopSectionCard(title = desktopText("Recommendations"), modifier = Modifier.weight(1f)) {
                if (state.recommendations.isEmpty()) {
                    Text(desktopText("No recommendations yet."))
                } else {
                    state.recommendations.forEach { recommendation ->
                        Text(
                            text = "• ${recommendation.title}: ${recommendation.destinationLabel} " +
                                "(${recommendation.reasonLabel})",
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        DesktopSectionCard(title = desktopText("Recent searches")) {
            if (state.recentSearches.isEmpty()) {
                Text(desktopText("No recent searches yet."))
            } else {
                Text(state.recentSearches.joinToString(" • "))
            }
        }
    }
}

@Composable
fun DesktopSettingsScreen(
    state: DesktopSettingsScreenState,
    onCycleTheme: () -> Unit,
    onCycleLanguage: () -> Unit,
    onToggleReminders: () -> Unit,
    onToggleNativeNotifications: () -> Unit,
    onToggleTrayDashboard: () -> Unit,
    onToggleReminderPause: () -> Unit,
    onReconcileAttention: () -> Unit,
    onTestNotification: () -> Unit,
    onToggleReminderCategory: (DesktopReminderCategoryPreference) -> Unit,
    linkState: DesktopStudyFlowLinkState?,
    linkQrInput: String,
    linkManualEndpoint: String,
    linkActionMessage: String,
    onCreatePairingOffer: () -> Unit,
    onLinkQrChanged: (String) -> Unit,
    onPairFromQr: () -> Unit,
    onManualEndpointChanged: (String) -> Unit,
    onRememberEndpoint: () -> Unit,
    onSyncPeers: () -> Unit,
    onRevokePeer: (String) -> Unit,
    onForgetPeer: (String) -> Unit,
    portabilityPath: String,
    portabilityPassword: String,
    portabilityActionMessage: String,
    onPortabilityPathChanged: (String) -> Unit,
    onPortabilityPasswordChanged: (String) -> Unit,
    onChoosePortabilityImport: () -> Unit,
    onChoosePortabilityExport: () -> Unit,
    onExportPortable: () -> Unit,
    onImportPortable: () -> Unit,
    onCreateEncryptedBackup: () -> Unit,
    onRestoreEncryptedBackup: () -> Unit,
    onMigrateWorkspace: () -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = desktopText("Default plan"),
                value = state.defaultPlanLabel,
                detail = "Stored in desktop preview preferences",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = desktopText("Theme"),
                value = desktopThemeModeLabel(state.themeMode),
                detail = "Dynamic color: ${state.dynamicColorEnabled}",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = desktopText("Language"),
                value = state.language.name.replace('_', ' '),
                detail = "Desktop-safe AppPreferences",
                modifier = Modifier.weight(1f),
            )
        }

        DesktopSectionCard(title = desktopText("Desktop settings")) {
            Text(desktopText("Settings use core:model AppPreferences and persist in the Desktop workspace."))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCycleTheme) { Text(desktopText("Cycle theme")) }
                Button(onClick = onCycleLanguage) { Text(desktopText("Cycle language")) }
            }
        }

        DesktopSectionCard(title = desktopText("Desktop reminders and tray")) {
            val attention = state.attention
            Text(desktopText(attention.capabilityMessage))
            Text(
                "Runtime: ${if (attention.running) "running" else "stopped"} • " +
                    "scheduled ${attention.scheduledCount} • ledger ${attention.ledgerEntryCount} • " +
                    "startup/recovery ${attention.recoveryCount}",
            )
            Text("Next reminder: ${attention.nextReminderLabel}")
            Text("Last delivered: ${attention.lastDeliveredLabel}")
            Text(
                "Native notifications: ${if (attention.nativeNotificationsSupported) "supported" else "unavailable"} • " +
                    "Tray: ${if (attention.traySupported) "supported" else "unavailable"}",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onToggleReminders, enabled = attention.available) {
                    Text(desktopText(if (attention.remindersEnabled) "Disable reminders" else "Enable reminders"))
                }
                Button(onClick = onToggleReminderPause, enabled = attention.available && attention.remindersEnabled) {
                    Text(desktopText(if (attention.paused) "Resume reminders" else "Pause reminders"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onToggleNativeNotifications, enabled = attention.available) {
                    Text(desktopText(if (attention.nativeNotificationsEnabled) "Disable native notifications" else "Enable native notifications"))
                }
                OutlinedButton(onClick = onToggleTrayDashboard, enabled = attention.available) {
                    Text(desktopText(if (attention.trayDashboardEnabled) "Hide tray dashboard" else "Show tray dashboard"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReconcileAttention, enabled = attention.available) {
                    Text(desktopText("Reconcile reminders now"))
                }
                OutlinedButton(onClick = onTestNotification, enabled = attention.available) {
                    Text(desktopText("Send test notification"))
                }
            }
            Text(desktopText("Reminder categories"), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onToggleReminderCategory(DesktopReminderCategoryPreference.PLANNED) }, enabled = attention.available) {
                    Text("Study ${if (attention.plannedStudyEnabled) "on" else "off"}")
                }
                OutlinedButton(onClick = { onToggleReminderCategory(DesktopReminderCategoryPreference.REVIEWS) }, enabled = attention.available) {
                    Text("Reviews ${if (attention.dueReviewEnabled) "on" else "off"}")
                }
                OutlinedButton(onClick = { onToggleReminderCategory(DesktopReminderCategoryPreference.MISSED) }, enabled = attention.available) {
                    Text("Missed ${if (attention.missedActivityEnabled) "on" else "off"}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onToggleReminderCategory(DesktopReminderCategoryPreference.SUMMARY) }, enabled = attention.available) {
                    Text("Summary ${if (attention.dailySummaryEnabled) "on" else "off"}")
                }
                OutlinedButton(onClick = { onToggleReminderCategory(DesktopReminderCategoryPreference.SESSIONS) }, enabled = attention.available) {
                    Text("Sessions ${if (attention.sessionTimerEnabled) "on" else "off"}")
                }
                OutlinedButton(onClick = { onToggleReminderCategory(DesktopReminderCategoryPreference.EXAM) }, enabled = attention.available) {
                    Text("Exam ${if (attention.examCountdownEnabled) "on" else "off"}")
                }
            }
            Text(
                desktopText(
                    "Desktop reminders run while StudyFlow is running. Launch-on-login is not enabled automatically.",
                ),
            )
        }

        DesktopSectionCard(title = desktopText("Portability and encrypted backup")) {
            Text(
                "Portable .studyplan export/import uses the shared structure schema. " +
                    "Encrypted workspace backup preserves the complete Desktop workspace.",
            )
            OutlinedTextField(
                value = portabilityPath,
                onValueChange = onPortabilityPathChanged,
                label = { Text(desktopText("Import/export file path")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onChoosePortabilityImport) { Text(desktopText("Choose import file")) }
                OutlinedButton(onClick = onChoosePortabilityExport) { Text(desktopText("Choose export file")) }
            }
            OutlinedTextField(
                value = portabilityPassword,
                onValueChange = onPortabilityPasswordChanged,
                label = { Text(desktopText("Backup password")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onExportPortable, enabled = portabilityPath.isNotBlank()) {
                    Text(desktopText("Export portable structure"))
                }
                Button(onClick = onImportPortable, enabled = portabilityPath.isNotBlank()) {
                    Text(desktopText("Import portable structure"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCreateEncryptedBackup,
                    enabled = portabilityPath.isNotBlank() && portabilityPassword.length >= 8,
                ) {
                    Text(desktopText("Create encrypted backup"))
                }
                Button(
                    onClick = onRestoreEncryptedBackup,
                    enabled = portabilityPath.isNotBlank() && portabilityPassword.length >= 8,
                ) {
                    Text(desktopText("Restore encrypted backup"))
                }
                Button(onClick = onMigrateWorkspace) { Text(desktopText("Migrate workspace schema")) }
            }
            Text(
                desktopText(
                    "Restore is rollback-protected and requires an application restart before continuing work.",
                ),
            )
            if (portabilityActionMessage.isNotBlank()) Text(portabilityActionMessage, fontWeight = FontWeight.Bold)
        }

        DesktopSectionCard(title = desktopText("StudyFlow Link")) {
            if (linkState == null) {
                Text(desktopText("SFLP peer integration is unavailable in this desktop composition root."))
            } else {
                Text("Device: ${linkState.localDeviceId}", fontWeight = FontWeight.Bold)
                Text("Status: ${linkState.status}")
                Text("Pending offline operations: ${linkState.pendingOutgoingOperations}")
                linkState.localEndpoints.forEach { endpoint -> Text("• $endpoint") }
                if (linkActionMessage.isNotBlank()) Text(linkActionMessage)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onCreatePairingOffer) { Text(desktopText("Create pairing offer")) }
                    Button(onClick = onSyncPeers) { Text(desktopText("Sync trusted peers")) }
                }
                linkState.pairingCode?.let { code -> Text("Pairing code: $code", fontWeight = FontWeight.Bold) }
                linkState.pairingQrPayload?.let { payload ->
                    Text("QR payload: $payload", maxLines = 3, overflow = TextOverflow.Ellipsis)
                }

                OutlinedTextField(
                    value = linkQrInput,
                    onValueChange = onLinkQrChanged,
                    label = { Text(desktopText("Paste StudyFlow pairing QR payload")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = onPairFromQr, enabled = linkQrInput.isNotBlank()) {
                    Text(desktopText("Pair from QR payload"))
                }

                OutlinedTextField(
                    value = linkManualEndpoint,
                    onValueChange = onManualEndpointChanged,
                    label = { Text(desktopText("Manual peer endpoint")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = onRememberEndpoint, enabled = linkManualEndpoint.isNotBlank()) {
                    Text(desktopText("Remember endpoint"))
                }
            }
        }

        if (linkState != null) {
            DesktopSectionCard(title = desktopText("Trusted StudyFlow peers")) {
                if (linkState.trustedPeers.isEmpty()) {
                    Text(desktopText("No trusted peers yet."))
                } else {
                    linkState.trustedPeers.forEach { peer ->
                        Text(
                            "${peer.displayName} • ${peer.clientKind} • " +
                                if (peer.trusted) "trusted" else "revoked",
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${peer.endpointCount} remembered endpoint(s) • ${peer.deviceId}")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { onRevokePeer(peer.deviceId) }, enabled = peer.trusted) {
                                Text(desktopText("Revoke"))
                            }
                            Button(onClick = { onForgetPeer(peer.deviceId) }) { Text(desktopText("Forget")) }
                        }
                    }
                }
            }

            DesktopSectionCard(title = desktopText("Live StudyFlow presence")) {
                Text(desktopText("Presence is ephemeral and optional; sync correctness never depends on it."))
                if (linkState.presence.isEmpty()) {
                    Text(desktopText("No live peer presence is currently visible."))
                } else {
                    linkState.presence.forEach { presence ->
                        Text(
                            "${presence.displayName} • ${presence.clientKind} • ${presence.status.lowercase()}",
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${presence.instanceId} • ${presence.deviceId}")
                        if (presence.activityKind != null) {
                            Text(
                                "Resumable activity: ${presence.activityKind.lowercase()}" +
                                    (presence.activityId?.let { " • $it" } ?: ""),
                            )
                        }
                    }
                }
            }

            DesktopSectionCard(title = desktopText("Discovered StudyFlow peers")) {
                if (linkState.discoveredPeers.isEmpty()) {
                    Text(desktopText("No active LAN/manual peers discovered."))
                } else {
                    linkState.discoveredPeers.forEach { peer ->
                        Text("${peer.displayName} • ${peer.clientKind} • ${peer.source}", fontWeight = FontWeight.Bold)
                        peer.endpoints.forEach { endpoint -> Text("• $endpoint") }
                    }
                }
            }
        }

        DesktopSectionCard(title = desktopText("Allowed boundary modules")) {
            state.boundaryModules.forEach { module -> Text("• $module") }
        }

        DesktopSectionCard(title = desktopText("Deferred desktop integrations")) {
            Text(desktopText("These are intentionally not implemented until native desktop adapters are introduced:"))
            state.deferredDesktopIntegrations.forEach { item ->
                Text("• $item", fontWeight = FontWeight.Bold)
            }
        }
    }
}
