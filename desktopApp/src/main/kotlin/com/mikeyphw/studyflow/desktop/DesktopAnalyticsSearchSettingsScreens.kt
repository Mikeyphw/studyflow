package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DesktopAnalyticsScreen(
    state: DesktopAnalyticsScreenState,
    progress: DesktopProgressReadinessState? = null,
    actionMessage: String = "",
    onApplySprint: () -> Unit = {},
    onExportReport: () -> Unit = {},
) {
    if (state.status == DesktopScreenStatus.EMPTY) {
        DesktopStateMessage(
            title = "No analytics yet",
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
                label = "Study time",
                value = "${state.studiedHoursSevenDays}h",
                detail = "${state.completedSessions} completed sessions",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Adherence",
                value = "${state.scheduleAdherencePercent}%",
                detail = "Schedule completion",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Practice",
                value = "${state.questionAccuracyPercent}%",
                detail = "${state.answeredQuestions} answered questions",
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = "Library",
                value = "${state.libraryProgressPercent}%",
                detail = "Resource progress",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Consistency",
                value = "${state.consistencyScorePercent}%",
                detail = "Last seven days",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Reviews",
                value = state.dueReviewCount.toString(),
                detail = "${state.overdueReviewCount} overdue",
                modifier = Modifier.weight(1f),
            )
        }

        DesktopSectionCard(title = "Weak areas") {
            state.weakAreas.forEach { area ->
                Text("• ${area.label}: ${area.masteryPercent}% mastery, priority ${area.priority}")
            }
        }

        DesktopSectionCard(title = "Insights") {
            state.insights.forEach { insight ->
                Text("• ${insight.kind.name.replace('_', ' ')} — ${insight.severity.name.lowercase()}")
            }
        }

        progress?.takeIf { it.available }?.let { advanced ->
            val readiness = advanced.readiness
            DesktopSectionCard(title = "Exam readiness") {
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

            DesktopSectionCard(title = "Weekly coach") {
                val review = advanced.weeklyReview
                Text(
                    "${review?.studiedMinutes ?: 0} min · ${review?.evidenceCount ?: 0} evidence event(s) · " +
                        "${review?.actionCount ?: 0} action(s)",
                )
                review?.items?.forEach { item ->
                    Text("• ${item.kind.name.replace('_', ' ')} — ${item.tone.name.lowercase()}")
                }
            }

            DesktopSectionCard(title = "Content health") {
                val cleanup = advanced.cleanupReport
                Text(
                    "Health: ${cleanup?.healthScorePercent ?: 100}% · ${cleanup?.issueCount ?: 0} issue(s) · " +
                        "${cleanup?.actionCount ?: 0} action item(s)",
                )
                cleanup?.issues?.take(6)?.forEach { issue ->
                    Text("• ${issue.kind.name.replace('_', ' ')}: ${issue.count} (${issue.severity.name.lowercase()})")
                }
                Text("Cleanup remains preview-only; DOV19 never performs destructive cleanup automatically.")
            }

            DesktopSectionCard(title = "Exam runbook") {
                val runbook = advanced.runbook
                Text(
                    "${runbook?.state?.name?.replace('_', ' ') ?: "unavailable"} · " +
                        "${runbook?.daysUntilExam?.let { "$it day(s)" } ?: "no confirmed exam countdown"}",
                )
                runbook?.items?.forEach { item ->
                    Text("• ${item.window.name.replace('_', ' ')} — ${item.action.name.replace('_', ' ')}")
                }
            }

            DesktopSectionCard(title = "Seven-day study sprint") {
                val sprint = advanced.sprintPreview
                Text(
                    "Focus: ${sprint?.focus?.name?.replace('_', ' ') ?: "none"} · " +
                        "${sprint?.candidateCount ?: 0} candidate(s) · ${sprint?.totalMinutes ?: 0} min",
                )
                Text("Executable changes: ${sprint?.executableChangeCount ?: 0}")
                Button(onClick = onApplySprint, enabled = sprint?.executable == true) {
                    Text("Apply approved sprint preview")
                }
            }

            DesktopSectionCard(title = "Exam report") {
                val report = advanced.examReport
                Text(
                    "Evidence: ${report?.evidenceCount ?: 0} · Practice tests: ${report?.practiceTestCount ?: 0} · " +
                        "Mock exams: ${report?.mockExamCount ?: 0}",
                )
                Text(
                    "Cleanup health: ${report?.cleanupHealthScorePercent ?: 100}% · " +
                        "Next actions: ${report?.nextActions?.size ?: 0}",
                )
                Button(onClick = onExportReport, enabled = report != null) { Text("Export local HTML report") }
                Text("Reports omit freeform study notes and stay inside the Desktop workspace until DOV23.")
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
            label = { Text("Search StudyFlow") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopSectionCard(title = "Results", modifier = Modifier.weight(2f)) {
                if (state.results.isEmpty()) {
                    Text("Type at least two characters to search plans, materials, activities, and questions.")
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
            DesktopSectionCard(title = "Recommendations", modifier = Modifier.weight(1f)) {
                if (state.recommendations.isEmpty()) {
                    Text("No recommendations yet.")
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

        DesktopSectionCard(title = "Recent searches") {
            if (state.recentSearches.isEmpty()) {
                Text("No recent searches yet.")
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
                label = "Default plan",
                value = state.defaultPlanLabel,
                detail = "Stored in desktop preview preferences",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Theme",
                value = state.themeMode.name.replace('_', ' '),
                detail = "Dynamic color: ${state.dynamicColorEnabled}",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Language",
                value = state.language.name.replace('_', ' '),
                detail = "Desktop-safe AppPreferences",
                modifier = Modifier.weight(1f),
            )
        }

        DesktopSectionCard(title = "Desktop settings") {
            Text("Settings use core:model AppPreferences and remain in memory until the filesystem overlay.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCycleTheme) { Text("Cycle theme") }
                Button(onClick = onCycleLanguage) { Text("Cycle language") }
            }
        }

        DesktopSectionCard(title = "Portability and encrypted backup") {
            Text(
                "Portable .studyplan export/import uses the shared structure schema. " +
                    "Encrypted workspace backup preserves the complete Desktop workspace.",
            )
            OutlinedTextField(
                value = portabilityPath,
                onValueChange = onPortabilityPathChanged,
                label = { Text("Import/export file path") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = portabilityPassword,
                onValueChange = onPortabilityPasswordChanged,
                label = { Text("Backup password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onExportPortable, enabled = portabilityPath.isNotBlank()) {
                    Text("Export portable structure")
                }
                Button(onClick = onImportPortable, enabled = portabilityPath.isNotBlank()) {
                    Text("Import portable structure")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCreateEncryptedBackup,
                    enabled = portabilityPath.isNotBlank() && portabilityPassword.length >= 8,
                ) {
                    Text("Create encrypted backup")
                }
                Button(
                    onClick = onRestoreEncryptedBackup,
                    enabled = portabilityPath.isNotBlank() && portabilityPassword.length >= 8,
                ) {
                    Text("Restore encrypted backup")
                }
                Button(onClick = onMigrateWorkspace) { Text("Migrate workspace schema") }
            }
            Text("Restore is rollback-protected and requires an application restart before continuing work.")
            if (portabilityActionMessage.isNotBlank()) Text(portabilityActionMessage, fontWeight = FontWeight.Bold)
        }

        DesktopSectionCard(title = "StudyFlow Link") {
            if (linkState == null) {
                Text("SFLP peer integration is unavailable in this desktop composition root.")
            } else {
                Text("Device: ${linkState.localDeviceId}", fontWeight = FontWeight.Bold)
                Text("Status: ${linkState.status}")
                Text("Pending offline operations: ${linkState.pendingOutgoingOperations}")
                linkState.localEndpoints.forEach { endpoint -> Text("• $endpoint") }
                if (linkActionMessage.isNotBlank()) Text(linkActionMessage)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onCreatePairingOffer) { Text("Create pairing offer") }
                    Button(onClick = onSyncPeers) { Text("Sync trusted peers") }
                }
                linkState.pairingCode?.let { code -> Text("Pairing code: $code", fontWeight = FontWeight.Bold) }
                linkState.pairingQrPayload?.let { payload ->
                    Text("QR payload: $payload", maxLines = 3, overflow = TextOverflow.Ellipsis)
                }

                OutlinedTextField(
                    value = linkQrInput,
                    onValueChange = onLinkQrChanged,
                    label = { Text("Paste StudyFlow pairing QR payload") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = onPairFromQr, enabled = linkQrInput.isNotBlank()) {
                    Text("Pair from QR payload")
                }

                OutlinedTextField(
                    value = linkManualEndpoint,
                    onValueChange = onManualEndpointChanged,
                    label = { Text("Manual peer endpoint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = onRememberEndpoint, enabled = linkManualEndpoint.isNotBlank()) {
                    Text("Remember endpoint")
                }
            }
        }

        if (linkState != null) {
            DesktopSectionCard(title = "Trusted StudyFlow peers") {
                if (linkState.trustedPeers.isEmpty()) {
                    Text("No trusted peers yet.")
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
                                Text("Revoke")
                            }
                            Button(onClick = { onForgetPeer(peer.deviceId) }) { Text("Forget") }
                        }
                    }
                }
            }

            DesktopSectionCard(title = "Discovered StudyFlow peers") {
                if (linkState.discoveredPeers.isEmpty()) {
                    Text("No active LAN/manual peers discovered.")
                } else {
                    linkState.discoveredPeers.forEach { peer ->
                        Text("${peer.displayName} • ${peer.clientKind} • ${peer.source}", fontWeight = FontWeight.Bold)
                        peer.endpoints.forEach { endpoint -> Text("• $endpoint") }
                    }
                }
            }
        }

        DesktopSectionCard(title = "Allowed boundary modules") {
            state.boundaryModules.forEach { module -> Text("• $module") }
        }

        DesktopSectionCard(title = "Deferred desktop integrations") {
            Text("These are intentionally not implemented until native desktop adapters are introduced:")
            state.deferredDesktopIntegrations.forEach { item ->
                Text("• $item", fontWeight = FontWeight.Bold)
            }
        }
    }
}
