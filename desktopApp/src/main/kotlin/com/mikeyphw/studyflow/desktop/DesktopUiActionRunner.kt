package com.mikeyphw.studyflow.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * OV22 Desktop UI action runner.
 *
 * Normal Compose event callbacks must not block the UI thread with runBlocking.
 * This runner launches repository/domain mutations on a SupervisorJob-backed
 * coroutine scope and reports success/failure through small state-updater
 * callbacks owned by DesktopAppState.
 */
class DesktopUiActionRunner(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    fun launch(
        actionName: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
        action: suspend () -> Unit,
    ) {
        scope.launch {
            runCatching { action() }
                .onSuccess { onSuccess() }
                .onFailure { error -> onFailure(DesktopUiActionFailure(actionName, error)) }
        }
    }
}

class DesktopUiActionFailure(
    actionName: String,
    cause: Throwable,
) : RuntimeException("Desktop action '$actionName' failed: ${cause.message.orEmpty()}", cause)
