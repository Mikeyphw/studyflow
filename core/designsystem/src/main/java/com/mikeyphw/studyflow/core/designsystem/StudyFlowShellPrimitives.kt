package com.mikeyphw.studyflow.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class StudyFlowShellDestination(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: @Composable () -> Unit,
)

object StudyFlowShellDefaults {
    val CompactDockHeight: Dp = 72.dp
    val CompactDockOuterPadding: Dp = 12.dp
    val ExpandedRailWidth: Dp = 80.dp
    val ItemCornerRadius: Dp = 14.dp
    val SelectedIndicatorCornerRadius: Dp = 12.dp
}

@Composable
fun StudyFlowCompactDock(
    destinations: List<StudyFlowShellDestination>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = StudyFlowShellDefaults.CompactDockOuterPadding, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(StudyFlowShellDefaults.CompactDockHeight)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                StudyFlowShellItem(
                    destination = destination,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun StudyFlowExpandedRail(
    destinations: List<StudyFlowShellDestination>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(StudyFlowShellDefaults.ExpandedRailWidth),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "S",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                destinations.forEach { destination ->
                    StudyFlowShellItem(
                        destination = destination,
                        compact = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyFlowShellItem(
    destination: StudyFlowShellDestination,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = rememberStudyFlowMotionEnabled()
    val background by animateColorAsState(
        targetValue = if (destination.selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = studyFlowAnimationSpec(motionEnabled),
        label = "studyflow-shell-item-background",
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (destination.selected) 8.dp else 6.dp,
        animationSpec = studyFlowAnimationSpec(motionEnabled),
        label = "studyflow-shell-item-padding",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (destination.selected) 1.08f else 1f,
        animationSpec = studyFlowAnimationSpec(motionEnabled, StudyFlowMotionTokens.FastMillis),
        label = "studyflow-shell-item-scale",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .semantics { selected = destination.selected }
            .clip(RoundedCornerShape(StudyFlowShellDefaults.ItemCornerRadius))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = destination.onClick,
            )
            .padding(horizontal = 4.dp, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
                .clip(RoundedCornerShape(StudyFlowShellDefaults.SelectedIndicatorCornerRadius))
                .padding(horizontal = if (destination.selected) 10.dp else 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            destination.icon()
        }
        Spacer(modifier = Modifier.height(if (compact) 3.dp else 4.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (destination.selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}
