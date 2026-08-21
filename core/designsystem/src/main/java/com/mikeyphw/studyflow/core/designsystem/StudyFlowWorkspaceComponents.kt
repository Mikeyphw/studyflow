package com.mikeyphw.studyflow.core.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object StudyFlowWorkspaceTokens {
    val ScreenPadding = 20.dp
    val SectionSpacing = 14.dp
    val CardRadius = 24.dp
    val CompactCardRadius = 18.dp
    val MinimumTouchTarget = 48.dp
    val ContentMaxWidth = 920.dp
}

@Composable
fun StudyFlowWorkspaceHeader(
    title: String,
    planName: String?,
    onPlanClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSystemClick: () -> Unit,
    planSupportingText: String,
    searchContentDescription: String,
    systemContentDescription: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true }
            .padding(horizontal = StudyFlowWorkspaceTokens.ScreenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                eyebrow?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics {
                        heading()
                        traversalIndex = 0f
                    },
                )
            }
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .size(StudyFlowWorkspaceTokens.MinimumTouchTarget)
                    .semantics { traversalIndex = 1f },
            ) {
                Icon(Icons.Outlined.Search, contentDescription = searchContentDescription)
            }
            IconButton(
                onClick = onSystemClick,
                modifier = Modifier
                    .size(StudyFlowWorkspaceTokens.MinimumTouchTarget)
                    .semantics { traversalIndex = 2f },
            ) {
                Icon(Icons.Outlined.MoreVert, contentDescription = systemContentDescription)
            }
        }
        val motionEnabled = rememberStudyFlowMotionEnabled()
        val planInteraction = remember { MutableInteractionSource() }
        val planPressed by planInteraction.collectIsPressedAsState()
        val planScale by animateFloatAsState(
            targetValue = if (planPressed) 0.985f else 1f,
            animationSpec = studyFlowAnimationSpec(motionEnabled, StudyFlowMotionTokens.FastMillis),
            label = "studyflow-plan-switcher-scale",
        )
        val arrowRotation by animateFloatAsState(
            targetValue = if (planPressed) 180f else 0f,
            animationSpec = studyFlowAnimationSpec(motionEnabled, StudyFlowMotionTokens.FastMillis),
            label = "studyflow-plan-switcher-arrow",
        )
        val planVerticalPadding by animateDpAsState(
            targetValue = if (planPressed) 12.dp else 10.dp,
            animationSpec = studyFlowAnimationSpec(motionEnabled, StudyFlowMotionTokens.FastMillis),
            label = "studyflow-plan-switcher-expansion",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = StudyFlowWorkspaceTokens.MinimumTouchTarget)
                .graphicsLayer {
                    scaleX = planScale
                    scaleY = planScale
                }
                .clip(RoundedCornerShape(StudyFlowWorkspaceTokens.CompactCardRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
                .clickable(interactionSource = planInteraction, indication = null, onClick = onPlanClick)
                .semantics { traversalIndex = 3f }
                .padding(horizontal = 14.dp, vertical = planVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = planName.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = planSupportingText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
            )
        }
    }
}

fun shouldStackWorkspacePair(maxWidthDp: Float, fontScale: Float): Boolean =
    maxWidthDp < 360f || fontScale >= 1.5f

@Composable
fun StudyFlowResponsivePair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stack = shouldStackWorkspacePair(
            maxWidthDp = maxWidth.value,
            fontScale = LocalDensity.current.fontScale,
        )
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StudyFlowInlineError(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StudyFlowWorkspaceTokens.CompactCardRadius))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .heightIn(min = StudyFlowWorkspaceTokens.MinimumTouchTarget)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAction)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        )
    }
}

@Composable
fun StudyFlowSubjectAccordion(
    title: String,
    supportingText: String,
    status: String,
    expanded: Boolean,
    expandedStateDescription: String,
    collapsedStateDescription: String,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StudyFlowWorkspaceTokens.CompactCardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = StudyFlowWorkspaceTokens.MinimumTouchTarget)
                    .clickable { onExpandedChange(!expanded) }
                    .semantics {
                        stateDescription = if (expanded) {
                            expandedStateDescription
                        } else {
                            collapsedStateDescription
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
            }
            if (expanded) {
                HorizontalDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    content()
                }
            }
        }
}

fun LazyListScope.studyFlowSectionHeader(
    key: String,
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    studyFlowSectionHeader(
        key = key,
        title = { title },
        actionLabel = actionLabel?.let { label -> { label } },
        onAction = onAction,
    )
}

fun LazyListScope.studyFlowSectionHeader(
    key: String,
    title: @Composable () -> String,
    actionLabel: (@Composable () -> String)? = null,
    onAction: (() -> Unit)? = null,
) {
    item(key = "section:$key", contentType = "workspace-section-header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (actionLabel != null && onAction != null) {
                Row(
                    modifier = Modifier
                        .heightIn(min = StudyFlowWorkspaceTokens.MinimumTouchTarget)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAction)
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionLabel(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun StudyFlowInlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(start = 54.dp))
}

fun workspaceContentPadding(bottom: androidx.compose.ui.unit.Dp = 96.dp): PaddingValues = PaddingValues(
    start = StudyFlowWorkspaceTokens.ScreenPadding,
    top = 4.dp,
    end = StudyFlowWorkspaceTokens.ScreenPadding,
    bottom = bottom,
)
