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
import com.mikeyphw.studyflow.core.designsystem.StudyFlowCompactDock
import com.mikeyphw.studyflow.core.designsystem.StudyFlowExpandedRail
import com.mikeyphw.studyflow.core.designsystem.StudyFlowShellDefaults
import com.mikeyphw.studyflow.core.designsystem.StudyFlowShellDestination
import com.mikeyphw.studyflow.core.navigation.StudyFlowWorkspaceRoot

@Composable
internal fun StudyFlowShell(
    selectedRoot: StudyFlowWorkspaceRoot,
    labels: Map<StudyFlowWorkspaceRoot, String>,
    onNavigate: (StudyFlowWorkspaceRoot) -> Unit,
    modifier: Modifier = Modifier,
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

    if (expanded) {
        Row(modifier = modifier.fillMaxSize()) {
            StudyFlowExpandedRail(destinations = destinations)
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                content()
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = StudyFlowShellDefaults.CompactDockHeight + 28.dp),
            ) {
                content()
            }
            StudyFlowCompactDock(
                destinations = destinations,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
