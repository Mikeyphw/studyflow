package com.mikeyphw.studyflow.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object StudyFlowWorkspaceTokens {
    val ScreenPadding = 20.dp
    val SectionSpacing = 18.dp
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
            .padding(horizontal = StudyFlowWorkspaceTokens.ScreenPadding, vertical = 14.dp),
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
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = StudyFlowWorkspaceTokens.MinimumTouchTarget)
                .clickable(onClick = onPlanClick)
                .semantics { traversalIndex = 3f },
            shape = RoundedCornerShape(StudyFlowWorkspaceTokens.CompactCardRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = planSupportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
        }
    }
}

@Composable
fun StudyFlowPrimaryActionCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    supportingLabel: String? = null,
    secondaryLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StudyFlowWorkspaceTokens.CardRadius),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            supportingLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (secondaryLabel != null && onSecondaryAction != null) {
                StudyFlowResponsivePair(
                    first = { childModifier ->
                        Button(onClick = onAction, modifier = childModifier) {
                            Text(actionLabel)
                        }
                    },
                    second = { childModifier ->
                        OutlinedButton(onClick = onSecondaryAction, modifier = childModifier) {
                            Text(secondaryLabel)
                        }
                    },
                )
            } else {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
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
fun StudyFlowMetricPulse(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    progress: Float? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(StudyFlowWorkspaceTokens.CompactCardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            progress?.let {
                LinearProgressIndicator(
                    progress = { it.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun StudyFlowTimelineRow(
    title: String,
    metadata: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    status: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .heightIn(min = StudyFlowWorkspaceTokens.MinimumTouchTarget)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun StudyFlowCoachCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StudyFlowWorkspaceTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (actionLabel != null && onAction != null) {
                AssistChip(
                    onClick = onAction,
                    label = { Text(actionLabel) },
                    modifier = Modifier.heightIn(min = StudyFlowWorkspaceTokens.MinimumTouchTarget),
                )
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StudyFlowWorkspaceTokens.CompactCardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StudyFlowWorkspaceTokens.CompactCardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column {
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
}

@Composable
fun <T> StudyFlowWorkspaceTabs(
    tabs: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0),
        modifier = modifier.fillMaxWidth(),
        edgePadding = 0.dp,
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                modifier = Modifier.heightIn(min = StudyFlowWorkspaceTokens.MinimumTouchTarget),
                text = {
                    Text(
                        text = label(tab),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

fun LazyListScope.studyFlowSectionHeader(
    key: String,
    title: String,
    actionLabel: String? = null,
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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionLabel)
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
    top = 8.dp,
    end = StudyFlowWorkspaceTokens.ScreenPadding,
    bottom = bottom,
)
