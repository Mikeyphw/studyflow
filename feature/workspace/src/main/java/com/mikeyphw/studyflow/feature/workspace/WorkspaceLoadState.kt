package com.mikeyphw.studyflow.feature.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikeyphw.studyflow.core.designsystem.StudyFlowErrorPane
import com.mikeyphw.studyflow.core.designsystem.StudyFlowLoadingPane
import com.mikeyphw.studyflow.core.model.StudyFlowLoadState

@Composable
internal fun workspaceLoadGate(
    states: List<StudyFlowLoadState<*>>,
    onRetry: () -> Unit,
    modifier: Modifier,
): Boolean {
    if (states.any { state -> state is StudyFlowLoadState.Failed }) {
        StudyFlowErrorPane(
            message = stringResource(R.string.operation_failed),
            onRetry = onRetry,
            modifier = modifier,
        )
        return false
    }
    if (states.any { state -> state !is StudyFlowLoadState.Ready<*> }) {
        StudyFlowLoadingPane(modifier = modifier)
        return false
    }
    return true
}

@Suppress("UNCHECKED_CAST")
internal fun <T> StudyFlowLoadState<T>.readyValue(): T =
    (this as StudyFlowLoadState.Ready<T>).value
