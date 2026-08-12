package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

fun <T> Flow<T>.asStudyFlowLoadState(): Flow<StudyFlowLoadState<T>> =
    map<T, StudyFlowLoadState<T>> { value -> StudyFlowLoadState.Ready(value) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(StudyFlowLoadState.Failed(error::class.java.simpleName.ifBlank { "Exception" }))
        }

fun <T : Any> Flow<T?>.asStudyFlowOptionalLoadState(): Flow<StudyFlowLoadState<T>> =
    map<T?, StudyFlowLoadState<T>> { value ->
        if (value == null) StudyFlowLoadState.Missing else StudyFlowLoadState.Ready(value)
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(StudyFlowLoadState.Failed(error::class.java.simpleName.ifBlank { "Exception" }))
    }
