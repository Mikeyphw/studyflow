package com.mikeyphw.studyflow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowActiveSessionBar
import com.mikeyphw.studyflow.core.designsystem.StudyFlowCompactDock
import com.mikeyphw.studyflow.core.designsystem.StudyFlowExpandedRail
import com.mikeyphw.studyflow.core.designsystem.StudyFlowPracticeTokens
import com.mikeyphw.studyflow.core.designsystem.StudyFlowShellDefaults
import com.mikeyphw.studyflow.core.designsystem.StudyFlowShellDestination
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.navigation.StudyFlowWorkspaceRoot

@Composable
internal fun StudyFlowShell(
    selectedRoot: StudyFlowWorkspaceRoot,
    labels: Map<StudyFlowWorkspaceRoot, String>,
    onNavigate: (StudyFlowWorkspaceRoot) -> Unit,
    modifier: Modifier = Modifier,
    activeSession: StudySession? = null,
    activeSessionElapsed: String = "",
    activeSessionStatusLabel: String = "",
    pauseLabel: String = "",
    resumeLabel: String = "",
    onOpenActiveSession: () -> Unit = {},
    onToggleActiveSession: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val expanded = LocalConfiguration.current.screenWidthDp >= 600
    val destinations = StudyFlowWorkspaceRoot.entries.map { root ->
        StudyFlowShellDestination(
            key = root.name,
            label = labels.getValue(root),
            selected = root == selectedRoot,
            onClick = { if (root != selectedRoot) onNavigate(root) },
            icon = {
                Icon(
                    imageVector = when (root) {
                        StudyFlowWorkspaceRoot.TODAY -> Icons.Outlined.Home
                        StudyFlowWorkspaceRoot.PLAN -> Icons.Outlined.CalendarMonth
                        StudyFlowWorkspaceRoot.PRACTICE -> Icons.Outlined.Quiz
                        StudyFlowWorkspaceRoot.PROGRESS -> Icons.Outlined.Insights
                    },
                    contentDescription = labels.getValue(root),
                    tint = if (root == selectedRoot) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
        )
    }
    val sessionInset = if (activeSession != null) {
        StudyFlowPracticeTokens.SessionBarMinHeight + 28.dp
    } else {
        0.dp
    }

    if (expanded) {
        Row(modifier = modifier.fillMaxSize()) {
            StudyFlowExpandedRail(destinations = destinations)
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = sessionInset)) {
                    content()
                }
                activeSession?.let { session ->
                    StudyFlowActiveSessionBar(
                        title = session.notes.lineSequence().firstOrNull().orEmpty().ifBlank { activeSessionStatusLabel },
                        elapsed = activeSessionElapsed,
                        statusLabel = activeSessionStatusLabel,
                        toggleLabel = if (session.status == StudySessionStatus.PAUSED) resumeLabel else pauseLabel,
                        paused = session.status == StudySessionStatus.PAUSED,
                        onOpen = onOpenActiveSession,
                        onToggle = onToggleActiveSession,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    } else {
        val bottomInset = if (activeSession != null) {
            StudyFlowPracticeTokens.SessionBarMinHeight + 28.dp
        } else {
            StudyFlowShellDefaults.CompactDockHeight + 28.dp
        }
        Box(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                content()
            }
            if (activeSession == null) {
                StudyFlowCompactDock(
                    destinations = destinations,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else {
                StudyFlowActiveSessionBar(
                    title = activeSession.notes.lineSequence().firstOrNull().orEmpty().ifBlank { activeSessionStatusLabel },
                    elapsed = activeSessionElapsed,
                    statusLabel = activeSessionStatusLabel,
                    toggleLabel = if (activeSession.status == StudySessionStatus.PAUSED) resumeLabel else pauseLabel,
                    paused = activeSession.status == StudySessionStatus.PAUSED,
                    onOpen = onOpenActiveSession,
                    onToggle = onToggleActiveSession,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}
