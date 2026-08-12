package com.mikeyphw.studyflow.core.model

sealed interface StudyFlowLoadState<out T> {
    data object Loading : StudyFlowLoadState<Nothing>

    data class Ready<T>(
        val value: T,
    ) : StudyFlowLoadState<T>

    data object Missing : StudyFlowLoadState<Nothing>

    data class Failed(
        val causeType: String,
    ) : StudyFlowLoadState<Nothing>
}
