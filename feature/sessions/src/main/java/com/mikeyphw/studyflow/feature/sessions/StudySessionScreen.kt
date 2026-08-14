package com.mikeyphw.studyflow.feature.sessions

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowStatusPill
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.asStudyFlowLoadState
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SessionHistoryFilter {
    ALL,
    COMPLETED,
    CANCELLED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudySessionScreen(
    plan: StudyPlan,
    sessionManager: ManageStudySession,
    scheduleManager: ManageStudySchedule,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val sessionsFlow = remember(plan.id) { sessionManager.observeSessions(plan.id).asStudyFlowLoadState() }
    val scheduleFlow = remember(plan.id) { scheduleManager.observeSchedule(plan.id) }
    val sessionsState by sessionsFlow.collectAsStateWithLifecycle(initialValue = StudyFlowLoadState.Loading)
    val sessionsReady = sessionsState is StudyFlowLoadState.Ready
    val sessions = (sessionsState as? StudyFlowLoadState.Ready<List<StudySession>>)?.value.orEmpty()
    val schedule by scheduleFlow.collectAsStateWithLifecycle(
        initialValue = StudySchedule(plan.id, null, emptyList(), emptyList()),
    )
    val activeSession = sessions.firstOrNull {
        it.status == StudySessionStatus.RUNNING || it.status == StudySessionStatus.PAUSED
    }
    val pendingActivities = schedule.plannedActivities.filter {
        it.status == PlannedActivityStatus.PLANNED || it.status == PlannedActivityStatus.MISSED
    }.take(6)
    var timerTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var mutationInFlight by remember { mutableStateOf(false) }
    var showQuickDialog by rememberSaveable { mutableStateOf(false) }
    var finishingSession by remember { mutableStateOf<StudySession?>(null) }
    var historyFilter by rememberSaveable { mutableStateOf(SessionHistoryFilter.ALL) }

    val operationFailed = stringResource(R.string.operation_failed)
    val sessionStarted = stringResource(R.string.session_started)
    val sessionPaused = stringResource(R.string.session_paused)
    val sessionResumed = stringResource(R.string.session_resumed)
    val sessionFinished = stringResource(R.string.session_finished)
    val sessionCancelled = stringResource(R.string.session_cancelled)

    LaunchedEffect(activeSession?.id, activeSession?.status) {
        while (activeSession?.status == StudySessionStatus.RUNNING) {
            timerTick = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    fun launchAction(success: String, action: suspend () -> Unit) {
        if (mutationInFlight || !sessionsReady) return
        mutationInFlight = true
        scope.launch {
            try {
                action()
                snackbar.showSnackbar(success)
            } catch (cancelled: java.util.concurrent.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar(operationFailed)
            } finally {
                mutationInFlight = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sessions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = sessionsReady && activeSession == null && !mutationInFlight,
                        onClick = { showQuickDialog = true },
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.quick_session))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.sessions_subtitle, plan.name),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ActiveSessionCard(
                    session = activeSession,
                    activeSeconds = remember(activeSession, timerTick) {
                        activeSession?.let(sessionManager::liveActiveSeconds)
                    },
                    controlsEnabled = sessionsReady && !mutationInFlight,
                    onStartQuick = { showQuickDialog = true },
                    onPause = { session ->
                        launchAction(sessionPaused) { sessionManager.pause(plan.id, session.id) }
                    },
                    onResume = { session ->
                        launchAction(sessionResumed) { sessionManager.resume(plan.id, session.id) }
                    },
                    onFinish = { session -> finishingSession = session },
                    onCancel = { session ->
                        launchAction(sessionCancelled) { sessionManager.cancel(plan.id, session.id) }
                    },
                )
            }
            item {
                StudyFlowSectionCard(title = stringResource(R.string.planned_start_queue)) {
                    if (pendingActivities.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_planned_sessions),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            pendingActivities.forEach { activity ->
                                PlannedActivityRow(
                                    activity = activity,
                                    enabled = sessionsReady && activeSession == null && !mutationInFlight,
                                    onStart = {
                                        launchAction(sessionStarted) {
                                            sessionManager.startFromActivity(
                                                StudySessionId(UUID.randomUUID().toString()),
                                                activity,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SessionHistoryFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = historyFilter == filter,
                            onClick = { historyFilter = filter },
                            label = { Text(stringResource(filter.labelKey())) },
                        )
                    }
                }
            }
            val history = sessions.filterHistory(historyFilter)
            if (history.isEmpty()) {
                item {
                    StudyFlowEmptyState(
                        title = stringResource(R.string.no_sessions_yet),
                        message = stringResource(R.string.no_sessions_message),
                        actionLabel = stringResource(R.string.quick_session),
                        onAction = { if (sessionsReady && !mutationInFlight) showQuickDialog = true },
                    )
                }
            } else {
                item {
                    Text(
                        text = pluralStringResource(
                            R.plurals.session_count,
                            history.size,
                            history.size,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(history, key = { it.id.value }) { session ->
                    StudySessionHistoryCard(session = session)
                }
            }
        }
    }

    if (showQuickDialog && sessionsReady && !mutationInFlight) {
        QuickSessionDialog(
            onDismiss = { showQuickDialog = false },
            onStart = { form ->
                showQuickDialog = false
                launchAction(sessionStarted) {
                    sessionManager.startQuickSession(
                        sessionId = StudySessionId(UUID.randomUUID().toString()),
                        studyPlanId = plan.id,
                        title = form.title,
                        type = form.type,
                    )
                }
            },
        )
    }

    finishingSession?.let { session ->
        FinishSessionDialog(
            initialNotes = session.notes,
            onDismiss = { finishingSession = null },
            onFinish = { form ->
                finishingSession = null
                launchAction(sessionFinished) {
                    sessionManager.complete(
                        planId = plan.id,
                        sessionId = session.id,
                        pagesCompleted = form.pagesCompleted,
                        questionsAnswered = form.questionsAnswered,
                        correctAnswers = form.correctAnswers,
                        concentrationRating = form.concentrationRating,
                        difficultyRating = form.difficultyRating,
                        notes = form.notes,
                    )
                }
            },
        )
    }
}

@Composable
private fun ActiveSessionCard(
    session: StudySession?,
    activeSeconds: Long?,
    controlsEnabled: Boolean,
    onStartQuick: () -> Unit,
    onPause: (StudySession) -> Unit,
    onResume: (StudySession) -> Unit,
    onFinish: (StudySession) -> Unit,
    onCancel: (StudySession) -> Unit,
) {
    StudyFlowSectionCard(title = stringResource(R.string.active_session)) {
        if (session == null) {
            Text(
                text = stringResource(R.string.no_active_session),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onStartQuick, enabled = controlsEnabled) {
                Icon(Icons.Outlined.PlayCircle, contentDescription = null)
                Text(stringResource(R.string.start_session))
            }
        } else {
            Text(text = session.notes.ifBlank { stringResource(session.type.labelKey()) })
            Text(
                text = formatSessionDuration(activeSeconds ?: session.grossDurationSeconds),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            StudyFlowStatusPill(
                text = stringResource(session.status.labelKey()),
                leadingIcon = { Icon(Icons.Outlined.PlayCircle, contentDescription = null) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (session.status == StudySessionStatus.RUNNING) {
                    OutlinedButton(onClick = { onPause(session) }, enabled = controlsEnabled) {
                        Icon(Icons.Outlined.PauseCircle, contentDescription = null)
                        Text(stringResource(R.string.pause_session))
                    }
                } else {
                    OutlinedButton(onClick = { onResume(session) }, enabled = controlsEnabled) {
                        Icon(Icons.Outlined.PlayCircle, contentDescription = null)
                        Text(stringResource(R.string.resume_session))
                    }
                }
                Button(onClick = { onFinish(session) }, enabled = controlsEnabled) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Text(stringResource(R.string.finish_session))
                }
                OutlinedButton(onClick = { onCancel(session) }, enabled = controlsEnabled) {
                    Icon(Icons.Outlined.Cancel, contentDescription = null)
                    Text(stringResource(R.string.cancel_session))
                }
            }
        }
    }
}

@Composable
private fun PlannedActivityRow(
    activity: PlannedActivity,
    enabled: Boolean,
    onStart: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = activity.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.activity_duration_minutes,
                        activity.plannedDurationMinutes,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StudyFlowStatusPill(text = stringResource(activity.type.labelKey()))
            }
            Button(enabled = enabled, onClick = onStart) {
                Text(stringResource(R.string.start_session))
            }
        }
    }
}

@Composable
private fun StudySessionHistoryCard(session: StudySession) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.notes.ifBlank { stringResource(session.type.labelKey()) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = formatStartedAt(session.startedAtEpochMillis),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StudyFlowStatusPill(text = stringResource(session.status.labelKey()))
            }
            Text(
                text = pluralStringResource(
                    R.plurals.session_summary,
                    session.questionsAnswered,
                    formatSessionDuration(session.grossDurationSeconds),
                    session.questionsAnswered,
                    session.correctAnswers,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun List<StudySession>.filterHistory(filter: SessionHistoryFilter): List<StudySession> = filter {
    when (filter) {
        SessionHistoryFilter.ALL -> it.status != StudySessionStatus.RUNNING && it.status != StudySessionStatus.PAUSED
        SessionHistoryFilter.COMPLETED -> it.status == StudySessionStatus.COMPLETED
        SessionHistoryFilter.CANCELLED -> it.status == StudySessionStatus.CANCELLED
    }
}

private fun formatStartedAt(epochMillis: Long): String = SimpleDateFormat(
    "yyyy-MM-dd HH:mm",
    Locale.getDefault(),
).format(Date(epochMillis))

private fun SessionHistoryFilter.labelKey(): Int = when (this) {
    SessionHistoryFilter.ALL -> R.string.filter_all_sessions
    SessionHistoryFilter.COMPLETED -> R.string.filter_completed_sessions
    SessionHistoryFilter.CANCELLED -> R.string.filter_cancelled_sessions
}
