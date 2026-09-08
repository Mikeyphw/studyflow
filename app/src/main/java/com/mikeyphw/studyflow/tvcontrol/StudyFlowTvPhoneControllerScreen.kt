package com.mikeyphw.studyflow.tvcontrol

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.R
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.core.tvlink.StudyFlowTvLocalNetworkPermission
import com.mikeyphw.studyflow.core.tvlink.StudyFlowTvLocalTransportClient
import com.mikeyphw.studyflow.core.tvlink.StudyFlowTvReceiverDiscovery

@Composable
internal fun StudyFlowTvPhoneControllerScreen(
    discovery: StudyFlowTvReceiverDiscovery,
    client: StudyFlowTvLocalTransportClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val gateway = remember(discovery, client) { DefaultStudyFlowTvPhoneLinkGateway(discovery, client) }
    val controller = remember(gateway) { StudyFlowTvPhoneController(gateway) }
    val state by controller.state.collectAsStateWithLifecycle()
    var pairingCode by rememberSaveable { mutableStateOf("") }
    val phoneName = remember {
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android phone" }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted || StudyFlowTvLocalNetworkPermission.isGranted(context)) controller.startDiscovery()
    }

    DisposableEffect(controller) {
        if (StudyFlowTvLocalNetworkPermission.isGranted(context)) controller.startDiscovery()
        onDispose { controller.close() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.tv_controller_title), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.tv_controller_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.tv_controller_back)) }

        StudyFlowSectionCard(title = stringResource(R.string.tv_controller_discovery_title)) {
            Text(
                stringResource(R.string.tv_controller_link_status, state.linkStatus.name),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!StudyFlowTvLocalNetworkPermission.isGranted(context)) {
                Text(stringResource(R.string.tv_controller_permission_message))
                Button(
                    onClick = { permissionLauncher.launch(StudyFlowTvLocalNetworkPermission.permission) },
                ) {
                    Text(stringResource(R.string.tv_controller_grant_permission))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = controller::startDiscovery, enabled = !state.busy) {
                        Text(stringResource(R.string.tv_controller_scan))
                    }
                    OutlinedButton(onClick = controller::stopDiscovery, enabled = !state.busy) {
                        Text(stringResource(R.string.tv_controller_stop_scan))
                    }
                }
            }
            if (state.receivers.isEmpty()) {
                Text(stringResource(R.string.tv_controller_no_receivers))
            } else {
                state.receivers.forEach { endpoint ->
                    val selected = endpoint == state.selectedReceiver
                    OutlinedButton(
                        onClick = { controller.selectReceiver(endpoint) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (selected) {
                                stringResource(R.string.tv_controller_receiver_selected, endpoint.serviceName)
                            } else {
                                endpoint.serviceName
                            },
                        )
                    }
                }
            }
        }

        StudyFlowSectionCard(title = stringResource(R.string.tv_controller_pairing_title)) {
            val selected = state.selectedReceiver
            Text(
                selected?.let { stringResource(R.string.tv_controller_selected_receiver, it.serviceName) }
                    ?: stringResource(R.string.tv_controller_select_receiver_first),
            )
            state.credential?.let { credential ->
                Text(
                    stringResource(R.string.tv_controller_paired_with, credential.tvDeviceName),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = controller::checkPairing,
                    enabled = selected != null && !state.busy,
                ) { Text(stringResource(R.string.tv_controller_check_pairing)) }
                state.pairingActive?.let { active ->
                    Text(
                        if (active) stringResource(R.string.tv_controller_pairing_ready)
                        else stringResource(R.string.tv_controller_pairing_not_ready),
                    )
                }
            }
            if (!state.paired) {
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { value -> pairingCode = value.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tv_controller_pairing_code)) },
                    supportingText = { Text(stringResource(R.string.tv_controller_pairing_code_help)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                Button(
                    onClick = { controller.pair(pairingCode, phoneName) },
                    enabled = selected != null && pairingCode.length == 6 && !state.busy,
                ) { Text(stringResource(R.string.tv_controller_pair)) }
            }
            if (state.paired) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = controller::refreshContinuity, enabled = selected != null && !state.busy) {
                        Text(stringResource(R.string.tv_controller_refresh_state))
                    }
                    OutlinedButton(onClick = controller::revoke, enabled = selected != null && !state.busy) {
                        Text(stringResource(R.string.tv_controller_revoke))
                    }
                }
                OutlinedButton(onClick = controller::forgetSavedSession, enabled = !state.busy) {
                    Text(stringResource(R.string.tv_controller_forget_local))
                }
            }
        }

        state.continuity?.let { continuity ->
            StudyFlowSectionCard(title = stringResource(R.string.tv_controller_current_tv_title)) {
                Text(stringResource(R.string.tv_controller_current_surface, continuity.activeSurfaceTitle))
                Text(stringResource(R.string.tv_controller_playback_state, continuity.playbackState.name))
                Text(stringResource(R.string.tv_controller_revision, continuity.commandRevision))
            }
        }

        if (state.paired && state.selectedReceiver != null) {
            StudyFlowSectionCard(title = stringResource(R.string.tv_controller_navigation_title)) {
                StudyFlowTvSurface.entries.forEach { surface ->
                    Button(
                        onClick = { controller.open(surface) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(surfaceLabel(surface))
                    }
                }
            }

            StudyFlowSectionCard(title = stringResource(R.string.tv_controller_session_controls_title)) {
                ControllerActionRow(
                    firstLabel = stringResource(R.string.tv_controller_start),
                    onFirst = { controller.send(StudyFlowTvSurface.STUDY_SESSION, StudyFlowTvControlAction.START) },
                    secondLabel = stringResource(R.string.tv_controller_pause),
                    onSecond = { controller.send(StudyFlowTvSurface.STUDY_SESSION, StudyFlowTvControlAction.PAUSE) },
                    enabled = !state.busy,
                )
                ControllerActionRow(
                    firstLabel = stringResource(R.string.tv_controller_resume),
                    onFirst = { controller.send(StudyFlowTvSurface.STUDY_SESSION, StudyFlowTvControlAction.RESUME) },
                    secondLabel = stringResource(R.string.tv_controller_complete),
                    onSecond = { controller.send(StudyFlowTvSurface.STUDY_SESSION, StudyFlowTvControlAction.COMPLETE) },
                    enabled = !state.busy,
                )
                ControllerActionRow(
                    firstLabel = stringResource(R.string.tv_controller_previous),
                    onFirst = { controller.send(StudyFlowTvSurface.STUDY_SESSION, StudyFlowTvControlAction.PREVIOUS) },
                    secondLabel = stringResource(R.string.tv_controller_next),
                    onSecond = { controller.send(StudyFlowTvSurface.STUDY_SESSION, StudyFlowTvControlAction.NEXT) },
                    enabled = !state.busy,
                )
            }

            StudyFlowSectionCard(title = stringResource(R.string.tv_controller_question_controls_title)) {
                ControllerActionRow(
                    firstLabel = stringResource(R.string.tv_controller_previous),
                    onFirst = { controller.send(StudyFlowTvSurface.QUESTION_SET, StudyFlowTvControlAction.PREVIOUS) },
                    secondLabel = stringResource(R.string.tv_controller_next),
                    onSecond = { controller.send(StudyFlowTvSurface.QUESTION_SET, StudyFlowTvControlAction.NEXT) },
                    enabled = !state.busy,
                )
                (0..3).chunked(2).forEach { pair ->
                    ControllerActionRow(
                        firstLabel = stringResource(R.string.tv_controller_answer_choice, ('A'.code + pair[0]).toChar().toString()),
                        onFirst = {
                            controller.send(
                                StudyFlowTvSurface.QUESTION_SET,
                                StudyFlowTvControlAction.SELECT_ANSWER,
                                optionId = pair[0].toString(),
                            )
                        },
                        secondLabel = stringResource(R.string.tv_controller_answer_choice, ('A'.code + pair[1]).toChar().toString()),
                        onSecond = {
                            controller.send(
                                StudyFlowTvSurface.QUESTION_SET,
                                StudyFlowTvControlAction.SELECT_ANSWER,
                                optionId = pair[1].toString(),
                            )
                        },
                        enabled = !state.busy,
                    )
                }
                ControllerActionRow(
                    firstLabel = stringResource(R.string.tv_controller_explanation),
                    onFirst = { controller.send(StudyFlowTvSurface.QUESTION_SET, StudyFlowTvControlAction.SHOW_EXPLANATION) },
                    secondLabel = stringResource(R.string.tv_controller_mark_review),
                    onSecond = { controller.send(StudyFlowTvSurface.QUESTION_SET, StudyFlowTvControlAction.MARK_FOR_REVIEW) },
                    enabled = !state.busy,
                )
            }

            StudyFlowSectionCard(title = stringResource(R.string.tv_controller_mock_controls_title)) {
                ControllerActionRow(
                    firstLabel = stringResource(R.string.tv_controller_start),
                    onFirst = { controller.send(StudyFlowTvSurface.MOCK_EXAM, StudyFlowTvControlAction.START) },
                    secondLabel = stringResource(R.string.tv_controller_pause),
                    onSecond = { controller.send(StudyFlowTvSurface.MOCK_EXAM, StudyFlowTvControlAction.PAUSE) },
                    enabled = !state.busy,
                )
                ControllerActionRow(
                    firstLabel = stringResource(R.string.tv_controller_resume),
                    onFirst = { controller.send(StudyFlowTvSurface.MOCK_EXAM, StudyFlowTvControlAction.RESUME) },
                    secondLabel = stringResource(R.string.tv_controller_submit),
                    onSecond = { controller.send(StudyFlowTvSurface.MOCK_EXAM, StudyFlowTvControlAction.SUBMIT) },
                    enabled = !state.busy,
                )
                ControllerActionRow(
                    firstLabel = stringResource(R.string.tv_controller_previous),
                    onFirst = { controller.send(StudyFlowTvSurface.MOCK_EXAM, StudyFlowTvControlAction.PREVIOUS) },
                    secondLabel = stringResource(R.string.tv_controller_next),
                    onSecond = { controller.send(StudyFlowTvSurface.MOCK_EXAM, StudyFlowTvControlAction.NEXT) },
                    enabled = !state.busy,
                )
                Button(
                    onClick = { controller.send(StudyFlowTvSurface.MOCK_EXAM, StudyFlowTvControlAction.REVIEW_RESULTS) },
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.tv_controller_review_results)) }
            }

            StudyFlowSectionCard(title = stringResource(R.string.tv_controller_review_controls_title)) {
                ControllerActionRow(
                    firstLabel = stringResource(R.string.tv_controller_previous),
                    onFirst = { controller.send(StudyFlowTvSurface.REVIEW, StudyFlowTvControlAction.PREVIOUS) },
                    secondLabel = stringResource(R.string.tv_controller_next),
                    onSecond = { controller.send(StudyFlowTvSurface.REVIEW, StudyFlowTvControlAction.NEXT) },
                    enabled = !state.busy,
                )
                Button(
                    onClick = { controller.send(StudyFlowTvSurface.REVIEW, StudyFlowTvControlAction.COMPLETE) },
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.tv_controller_complete)) }
            }

            OutlinedButton(
                onClick = {
                    val surface = state.continuity?.currentSurface ?: StudyFlowTvSurface.HOME
                    controller.send(surface, StudyFlowTvControlAction.BACK)
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tv_controller_back_on_tv)) }
        }

        state.lastMessage?.let { message ->
            Text(
                text = stringResource(R.string.tv_controller_last_result, message),
                color = if (message in setOf("paired", "command-applied", "continuity-refreshed", "pairing-revoked")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Text(
            text = stringResource(R.string.tv_controller_cast_deferred),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ControllerActionRow(
    firstLabel: String,
    onFirst: () -> Unit,
    secondLabel: String,
    onSecond: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onFirst, enabled = enabled, modifier = Modifier.weight(1f)) { Text(firstLabel) }
        Button(onClick = onSecond, enabled = enabled, modifier = Modifier.weight(1f)) { Text(secondLabel) }
    }
}

@Composable
private fun surfaceLabel(surface: StudyFlowTvSurface): String = when (surface) {
    StudyFlowTvSurface.HOME -> stringResource(R.string.tv_controller_surface_home)
    StudyFlowTvSurface.TODAY -> stringResource(R.string.tv_controller_surface_today)
    StudyFlowTvSurface.STUDY_SESSION -> stringResource(R.string.tv_controller_surface_session)
    StudyFlowTvSurface.QUESTION_SET -> stringResource(R.string.tv_controller_surface_questions)
    StudyFlowTvSurface.REVIEW -> stringResource(R.string.tv_controller_surface_review)
    StudyFlowTvSurface.MOCK_EXAM -> stringResource(R.string.tv_controller_surface_mock)
    StudyFlowTvSurface.PROGRESS -> stringResource(R.string.tv_controller_surface_progress)
    StudyFlowTvSurface.SCHEDULE -> stringResource(R.string.tv_controller_surface_schedule)
    StudyFlowTvSurface.LIBRARY -> stringResource(R.string.tv_controller_surface_library)
    StudyFlowTvSurface.SETTINGS -> stringResource(R.string.tv_controller_surface_settings)
}
