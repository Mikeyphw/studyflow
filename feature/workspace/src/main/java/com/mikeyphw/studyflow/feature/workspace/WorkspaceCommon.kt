package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowWorkspaceHeader
import com.mikeyphw.studyflow.core.model.StudyPlan

@Composable
internal fun WorkspaceHeader(
    title: String,
    plan: StudyPlan?,
    onSwitchPlan: () -> Unit,
    onSearch: () -> Unit,
    onSystem: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
) {
    StudyFlowWorkspaceHeader(
        title = title,
        planName = plan?.name,
        planSupportingText = stringResource(
            if (plan == null) R.string.select_plan else R.string.switch_plan,
        ),
        onPlanClick = onSwitchPlan,
        onSearchClick = onSearch,
        onSystemClick = onSystem,
        searchContentDescription = stringResource(R.string.search),
        systemContentDescription = stringResource(R.string.more),
        eyebrow = eyebrow,
        modifier = modifier.semantics { paneTitle = title },
    )
}

@Composable
fun WorkspaceNoPlanScreen(
    title: String,
    onCreatePlan: () -> Unit,
    onSwitchPlan: () -> Unit,
    onSearch: () -> Unit,
    onSystem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WorkspaceHeader(
            title = title,
            plan = null,
            onSwitchPlan = onSwitchPlan,
            onSearch = onSearch,
            onSystem = onSystem,
        )
        StudyFlowEmptyState(
            title = stringResource(R.string.no_plan_title),
            message = stringResource(R.string.no_plan_message),
            actionLabel = stringResource(R.string.create_plan),
            onAction = onCreatePlan,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceUtilityScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable (PaddingValues) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        content(
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding() + contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = padding.calculateBottomPadding() + contentPadding.calculateBottomPadding(),
            ),
        )
    }
}

@Composable
internal fun CenteredMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title)
        Text(message)
    }
}
