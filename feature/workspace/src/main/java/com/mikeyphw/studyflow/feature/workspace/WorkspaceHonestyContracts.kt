package com.mikeyphw.studyflow.feature.workspace

import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import com.mikeyphw.studyflow.core.model.StudySessionStatus

internal data class WorkspaceStreamPartition<T>(
    val required: StudyFlowLoadState<T>,
    val optional: List<StudyFlowLoadState<*>>,
) {
    val requiredReady: Boolean = required is StudyFlowLoadState.Ready<T>
    val optionalFailures: Int = optional.count { it is StudyFlowLoadState.Failed }
    val optionalLoading: Int = optional.count { it !is StudyFlowLoadState.Ready<*> && it !is StudyFlowLoadState.Failed }
}

internal fun shouldBlockWorkspaceForOptionalStreams(optionalStates: List<StudyFlowLoadState<*>>): Boolean = false

internal fun shouldInterceptFocusBackWhileLoading(
    sessionsLoading: Boolean,
    sessionStatus: StudySessionStatus?,
): Boolean = !sessionsLoading && shouldInterceptFocusBack(sessionStatus)

@Suppress("UNCHECKED_CAST")
internal fun <T> StudyFlowLoadState<T>.readyOrFallback(fallback: T): T =
    (this as? StudyFlowLoadState.Ready<T>)?.value ?: fallback
