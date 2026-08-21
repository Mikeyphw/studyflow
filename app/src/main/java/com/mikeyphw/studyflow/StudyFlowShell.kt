package com.mikeyphw.studyflow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowActiveSessionBar
import com.mikeyphw.studyflow.core.designsystem.StudyFlowCompactDock
import com.mikeyphw.studyflow.core.designsystem.StudyFlowExpandedRail
import com.mikeyphw.studyflow.core.designsystem.StudyFlowMotionTokens
import com.mikeyphw.studyflow.core.designsystem.StudyFlowPracticeTokens
import com.mikeyphw.studyflow.core.designsystem.StudyFlowShellDefaults
import com.mikeyphw.studyflow.core.designsystem.StudyFlowShellDestination
import com.mikeyphw.studyflow.core.designsystem.rememberStudyFlowMotionEnabled
import com.mikeyphw.studyflow.core.designsystem.studyFlowAnimationSpec
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
    val motionEnabled = rememberStudyFlowMotionEnabled()
    val sessionInset by animateDpAsState(
        targetValue = if (activeSession != null) {
            StudyFlowPracticeTokens.SessionBarMinHeight + 28.dp
        } else {
            0.dp
        },
        animationSpec = studyFlowAnimationSpec(motionEnabled, StudyFlowMotionTokens.StandardMillis),
        label = "studyflow-shell-session-inset",
    )
    val sessionEnter = if (motionEnabled) {
        fadeIn(studyFlowAnimationSpec(true)) + scaleIn(
            initialScale = 0.96f,
            animationSpec = studyFlowAnimationSpec(true),
        )
    } else {
        EnterTransition.None
    }
    val sessionExit = if (motionEnabled) {
        fadeOut(studyFlowAnimationSpec(true)) + scaleOut(
            targetScale = 0.96f,
            animationSpec = studyFlowAnimationSpec(true),
        )
    } else {
        ExitTransition.None
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        val shellSafeArea = Modifier.windowInsetsPadding(WindowInsets.statusBars)
        if (expanded) {
            Row(modifier = shellSafeArea.fillMaxSize()) {
                StudyFlowExpandedRail(destinations = destinations)
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = sessionInset)) {
                        content()
                    }
                    StudyFlowExpandedSessionBar(
                        activeSession = activeSession,
                        activeSessionElapsed = activeSessionElapsed,
                        activeSessionStatusLabel = activeSessionStatusLabel,
                        pauseLabel = pauseLabel,
                        resumeLabel = resumeLabel,
                        onOpenActiveSession = onOpenActiveSession,
                        onToggleActiveSession = onToggleActiveSession,
                        enter = sessionEnter,
                        exit = sessionExit,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        } else {
            val bottomInset by animateDpAsState(
                targetValue = if (activeSession != null) {
                    StudyFlowPracticeTokens.SessionBarMinHeight + 20.dp
                } else {
                    StudyFlowShellDefaults.CompactDockHeight + 20.dp
                },
                animationSpec = studyFlowAnimationSpec(motionEnabled, StudyFlowMotionTokens.StandardMillis),
                label = "studyflow-shell-bottom-inset",
            )
            Box(modifier = shellSafeArea.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                    content()
                }
                AnimatedContent(
                    targetState = activeSession != null,
                    transitionSpec = { sessionEnter togetherWith sessionExit },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    label = "studyflow-session-dock-morph",
                ) { sessionVisible ->
                    val session = activeSession
                    if (sessionVisible && session != null) {
                        StudyFlowActiveSessionBar(
                            title = session.notes.lineSequence().firstOrNull().orEmpty()
                                .ifBlank { activeSessionStatusLabel },
                            elapsed = activeSessionElapsed,
                            statusLabel = activeSessionStatusLabel,
                            toggleLabel = if (session.status == StudySessionStatus.PAUSED) {
                                resumeLabel
                            } else {
                                pauseLabel
                            },
                            paused = session.status == StudySessionStatus.PAUSED,
                            onOpen = onOpenActiveSession,
                            onToggle = onToggleActiveSession,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    } else {
                        StudyFlowCompactDock(destinations = destinations)
                    }
                }
            }
        }
    }
}


@Composable
private fun StudyFlowExpandedSessionBar(
    activeSession: StudySession?,
    activeSessionElapsed: String,
    activeSessionStatusLabel: String,
    pauseLabel: String,
    resumeLabel: String,
    onOpenActiveSession: () -> Unit,
    onToggleActiveSession: () -> Unit,
    enter: EnterTransition,
    exit: ExitTransition,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = activeSession != null,
        enter = enter,
        exit = exit,
        modifier = modifier,
    ) {
        activeSession?.let { session ->
            StudyFlowActiveSessionBar(
                title = session.notes.lineSequence().firstOrNull().orEmpty()
                    .ifBlank { activeSessionStatusLabel },
                elapsed = activeSessionElapsed,
                statusLabel = activeSessionStatusLabel,
                toggleLabel = if (session.status == StudySessionStatus.PAUSED) {
                    resumeLabel
                } else {
                    pauseLabel
                },
                paused = session.status == StudySessionStatus.PAUSED,
                onOpen = onOpenActiveSession,
                onToggle = onToggleActiveSession,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}
