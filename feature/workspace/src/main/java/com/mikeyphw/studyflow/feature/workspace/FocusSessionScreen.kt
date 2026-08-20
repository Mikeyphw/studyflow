package com.mikeyphw.studyflow.feature.workspace

import android.os.SystemClock
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowImmersiveTimer
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSessionControls
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.asStudyFlowLoadState
import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun FocusSessionScreen(
    plan: StudyPlan,
    sessionId: StudySessionId,
    sessionManager: ManageStudySession,
    onFinished: () -> Unit,
    onCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionsFlow = remember(plan.id) { sessionManager.observeSessions(plan.id).asStudyFlowLoadState() }
    val sessionsState by sessionsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val sessions = (sessionsState as? StudyFlowLoadState.Ready<List<StudySession>>)?.value
    val sessionsLoading = sessionsState is StudyFlowLoadState.Loading
    val session = sessions?.firstOrNull { it.id == sessionId }
    val activeSession = session?.takeIf { current ->
        current.status == StudySessionStatus.RUNNING || current.status == StudySessionStatus.PAUSED
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var timerTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var showFinish by rememberSaveable { mutableStateOf(false) }
    var showCancel by rememberSaveable { mutableStateOf(false) }
    var transitionInFlight by remember { mutableStateOf(false) }
    var draftNotes by rememberSaveable(session?.id?.value) { mutableStateOf(session?.notes.orEmpty()) }
    val operationFailed = stringResource(R.string.operation_failed)
    val focusTitle = stringResource(R.string.focus_title)

    LaunchedEffect(session?.status) {
        while (session?.status == StudySessionStatus.RUNNING) {
            timerTick = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    LaunchedEffect(session?.id, draftNotes) {
        val current = session ?: return@LaunchedEffect
        if (draftNotes == current.notes) return@LaunchedEffect
        delay(650)
        try {
            sessionManager.updateNotes(plan.id, current.id, draftNotes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            snackbar.showSnackbar(operationFailed)
        }
    }

    PredictiveBackHandler(enabled = sessionsLoading || shouldInterceptFocusBack(session?.status)) { progress ->
        if (sessionsLoading || transitionInFlight) return@PredictiveBackHandler
        try {
            progress.collect { }
            showCancel = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    Box(modifier = modifier.fillMaxSize().semantics { paneTitle = focusTitle }) {
        if (sessionsLoading) {
            StudyFlowEmptyState(
                title = focusTitle,
                message = stringResource(R.string.session_loading),
                actionLabel = stringResource(R.string.back),
                onAction = {},
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            )
        } else if (activeSession == null) {
            StudyFlowEmptyState(
                title = focusTitle,
                message = stringResource(
                    if (session == null) R.string.session_not_found else R.string.session_already_finished,
                ),
                actionLabel = stringResource(R.string.back),
                onAction = onFinished,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            )
        } else {
            val elapsedSeconds = remember(activeSession, timerTick) {
                sessionManager.liveActiveSeconds(activeSession)
            }
            val targetSeconds = activeSession.targetDurationMinutes?.times(60L)
            val overtimeSeconds = targetSeconds?.let { (elapsedSeconds - it).coerceAtLeast(0L) }
            val remainingSeconds = targetSeconds?.let { (it - elapsedSeconds).coerceAtLeast(0L) }
            val timerSeconds = when {
                targetSeconds == null -> elapsedSeconds
                overtimeSeconds != null && overtimeSeconds > 0L -> overtimeSeconds
                else -> remainingSeconds ?: 0L
            }
            val accessibleTimerSeconds = accessibilityTimerBucket(timerSeconds)
            val timerDescription = when {
                targetSeconds == null -> stringResource(
                    R.string.focus_elapsed,
                    formatDuration(accessibilityTimerBucket(elapsedSeconds)),
                )
                overtimeSeconds != null && overtimeSeconds > 0L -> stringResource(
                    R.string.focus_overtime,
                    formatDuration(accessibilityTimerBucket(overtimeSeconds)),
                )
                else -> stringResource(
                    R.string.focus_remaining,
                    formatDuration(accessibleTimerSeconds),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { showCancel = true },
                        enabled = !transitionInFlight,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cancel_session))
                    }
                    Text(
                        text = focusTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                }
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = activeSession.notes.lineSequence().firstOrNull().orEmpty().ifBlank { plan.name },
                    style = MaterialTheme.typography.headlineMedium,
                )
                StudyFlowImmersiveTimer(
                    timerText = formatDuration(timerSeconds),
                    stateText = timerDescription,
                )
                StudyFlowSessionControls(
                    paused = activeSession.status == StudySessionStatus.PAUSED,
                    pauseLabel = stringResource(R.string.pause),
                    resumeLabel = stringResource(R.string.resume),
                    finishLabel = stringResource(R.string.finish),
                    onToggle = {
                        scope.launch {
                            performFocusMutation(
                                onInFlightChange = { transitionInFlight = it },
                                operation = {
                                    if (activeSession.status == StudySessionStatus.RUNNING) {
                                        sessionManager.pause(plan.id, activeSession.id)
                                    } else {
                                        sessionManager.resume(plan.id, activeSession.id)
                                    }
                                },
                                onFailure = { snackbar.showSnackbar(operationFailed) },
                            )
                        }
                    },
                    onFinish = { showFinish = true },
                    enabled = !transitionInFlight,
                )
                OutlinedTextField(
                    value = draftNotes,
                    onValueChange = { draftNotes = it },
                    label = { Text(stringResource(R.string.session_notes)) },
                    supportingText = { Text(stringResource(R.string.session_notes_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { showCancel = true },
                    enabled = !transitionInFlight,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cancel_session))
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    if (showFinish && activeSession != null) {
        FinishFocusDialog(
            initialNotes = draftNotes,
            onDismiss = { if (!transitionInFlight) showFinish = false },
            enabled = !transitionInFlight,
            onFinish = { pages, answered, correct, concentration, difficulty, notes ->
                scope.launch {
                    performFocusMutation(
                        onInFlightChange = { transitionInFlight = it },
                        operation = {
                            sessionManager.complete(
                                planId = plan.id,
                                sessionId = activeSession.id,
                                pagesCompleted = pages,
                                questionsAnswered = answered,
                                correctAnswers = correct,
                                concentrationRating = concentration,
                                difficultyRating = difficulty,
                                notes = notes,
                            )
                        },
                        onSuccess = {
                            showFinish = false
                            onFinished()
                        },
                        onFailure = { snackbar.showSnackbar(operationFailed) },
                    )
                }
            },
        )
    }
    if (showCancel && activeSession != null) {
        AlertDialog(
            onDismissRequest = { if (!transitionInFlight) showCancel = false },
            title = { Text(stringResource(R.string.cancel_session_title)) },
            text = { Text(stringResource(R.string.cancel_session_message)) },
            confirmButton = {
                Button(
                    enabled = !transitionInFlight,
                    onClick = {
                        scope.launch {
                            performFocusMutation(
                                onInFlightChange = { transitionInFlight = it },
                                operation = { sessionManager.cancel(plan.id, activeSession.id) },
                                onSuccess = {
                                    showCancel = false
                                    onCancelled()
                                },
                                onFailure = { snackbar.showSnackbar(operationFailed) },
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.cancel_session))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCancel = false },
                    enabled = !transitionInFlight,
                ) {
                    Text(stringResource(R.string.keep_studying))
                }
            },
        )
    }
}


private suspend fun performFocusMutation(
    onInFlightChange: (Boolean) -> Unit,
    operation: suspend () -> Unit,
    onSuccess: () -> Unit = {},
    onFailure: suspend () -> Unit,
) {
    onInFlightChange(true)
    try {
        operation()
        onSuccess()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        onFailure()
    } finally {
        onInFlightChange(false)
    }
}

@Composable
private fun FinishFocusDialog(
    initialNotes: String,
    onDismiss: () -> Unit,
    enabled: Boolean,
    onFinish: (Int, Int, Int, Int?, Int?, String) -> Unit,
) {
    var pages by rememberSaveable { mutableStateOf("0") }
    var answered by rememberSaveable { mutableStateOf("0") }
    var correct by rememberSaveable { mutableStateOf("0") }
    var concentration by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable(initialNotes) { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.finish_session_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NumericField(stringResource(R.string.pages), pages) { pages = it }
                NumericField(stringResource(R.string.questions), answered) { answered = it }
                NumericField(stringResource(R.string.correct), correct) { correct = it }
                RatingField(stringResource(R.string.concentration_rating), concentration) {
                    concentration = it
                }
                RatingField(stringResource(R.string.difficulty_rating), difficulty) {
                    difficulty = it
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.session_notes)) },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = enabled,
                onClick = {
                    val pageValue = pages.toIntOrNull() ?: 0
                    val answeredValue = answered.toIntOrNull() ?: 0
                    val correctValue = (correct.toIntOrNull() ?: 0).coerceAtMost(answeredValue)
                    val concentrationValue = concentration.toIntOrNull()?.takeIf { it in 1..5 }
                    val difficultyValue = difficulty.toIntOrNull()?.takeIf { it in 1..5 }
                    onFinish(
                        pageValue,
                        answeredValue,
                        correctValue,
                        concentrationValue,
                        difficultyValue,
                        notes,
                    )
                },
            ) {
                Text(stringResource(R.string.save_finish))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = enabled) {
                Text(stringResource(R.string.keep_studying))
            }
        },
    )
}

@Composable
private fun NumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate -> onValueChange(candidate.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun RatingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            onValueChange(candidate.filter(Char::isDigit).take(1))
        },
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.rating_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

internal fun shouldInterceptFocusBack(status: StudySessionStatus?): Boolean =
    status == StudySessionStatus.RUNNING || status == StudySessionStatus.PAUSED

internal fun accessibilityTimerBucket(totalSeconds: Long): Long = when {
    totalSeconds <= 10L -> totalSeconds
    else -> (totalSeconds / 60L) * 60L
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
