package com.mikeyphw.studyflow.core.domain

import kotlinx.coroutines.CancellationException

/** Runs a recoverable operation without converting coroutine cancellation or fatal [Error]s. */
suspend fun <T> runRecoverableOperation(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    Result.failure(error)
}
