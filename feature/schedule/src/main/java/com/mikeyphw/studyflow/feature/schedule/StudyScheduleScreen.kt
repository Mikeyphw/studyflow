package com.mikeyphw.studyflow.feature.schedule

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
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.mikeyphw.studyflow.core.domain.runRecoverableOperation
import com.mikeyphw.studyflow.core.domain.ManagePublicExam
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.model.MissedActivityHandling
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleGenerationRequest
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyStrategy
import com.mikeyphw.studyflow.core.model.StudyStrategyId
import com.mikeyphw.studyflow.core.model.StudyStrategyType
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScheduleScreen(
    plan: StudyPlan,
    scheduleManager: ManageStudySchedule,
    contentManager: ManageStudyContent? = null,
    publicExamManager: ManagePublicExam? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scheduleFlow = remember(plan.id) { scheduleManager.observeSchedule(plan.id) }
    val schedule by scheduleFlow.collectAsStateWithLifecycle(
        initialValue = StudySchedule(plan.id, null, emptyList(), emptyList()),
    )
    val syllabusFlow = remember(plan.id, contentManager) {
        contentManager?.observeSyllabus(plan.id) ?: kotlinx.coroutines.flow.flowOf(
            StudySyllabus(plan.id, emptyList(), emptyList()),
        )
    }
    val syllabus by syllabusFlow.collectAsStateWithLifecycle(
        initialValue = StudySyllabus(plan.id, emptyList(), emptyList()),
    )
    val cycleFlow = remember(plan.id, publicExamManager) {
        publicExamManager?.observeStudyCycle(plan.id) ?: kotlinx.coroutines.flow.flowOf(null)
    }
    val cycle by cycleFlow.collectAsStateWithLifecycle(initialValue = null)
    var showRuleDialog by remember { mutableStateOf(false) }
    var showActivityDialog by remember { mutableStateOf(false) }
    var strategyName by rememberSaveable(schedule.strategy?.id?.value) {
        mutableStateOf((schedule.strategy?.type ?: StudyStrategyType.FIXED_WEEKLY).name)
    }

    val operationFailed = stringResource(R.string.operation_failed)
    val blockSaved = stringResource(R.string.block_saved)
    val activitySaved = stringResource(R.string.activity_saved)
    val activityCompleted = stringResource(R.string.activity_completed)
    val activityMissed = stringResource(R.string.activity_missed)
    val itemDeleted = stringResource(R.string.item_deleted)
    val weekGenerated = stringResource(R.string.week_generated)
    val missedRebalanced = stringResource(R.string.missed_rebalanced)

    fun launchAction(success: String, action: suspend () -> Unit) {
        scope.launch {
            runRecoverableOperation { action() }
                .onSuccess { snackbar.showSnackbar(success) }
                .onFailure { snackbar.showSnackbar(operationFailed) }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showRuleDialog = true }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.EventNote,
                            contentDescription = stringResource(R.string.add_block),
                        )
                    }
                    IconButton(onClick = { showActivityDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_activity))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showActivityDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_activity))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.schedule_subtitle, plan.name),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (plan.type == StudyType.PUBLIC_EXAMINATION && publicExamManager != null) {
                item {
                    StudyFlowSectionCard(title = stringResource(R.string.study_cycle_title)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val current = cycle?.orderedItems?.firstOrNull { it.id == cycle?.cycle?.currentItemId }
                            Text(
                                if (cycle == null) {
                                    stringResource(R.string.study_cycle_not_configured)
                                } else {
                                    stringResource(
                                        R.string.study_cycle_status,
                                        cycle?.orderedItems?.size ?: 0,
                                        (current?.position ?: 0) + 1,
                                    )
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        launchAction(blockSaved) {
                                            publicExamManager.replaceStudyCycleFromSyllabus(plan.id, syllabus)
                                        }
                                    },
                                    enabled = syllabus.subjects.any { !it.archived },
                                ) { Text(stringResource(if (cycle == null) R.string.build_cycle else R.string.rebuild_cycle)) }
                                if (cycle != null) {
                                    OutlinedButton(
                                        onClick = {
                                            launchAction(blockSaved) { publicExamManager.skipCurrentStudyCycleItem(plan.id) }
                                        },
                                    ) { Text(stringResource(R.string.skip_once)) }
                                }
                            }
                            if (cycle != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            launchAction(blockSaved) {
                                                publicExamManager.setStudyCycleEnabled(plan.id, !(cycle?.cycle?.enabled ?: true))
                                            }
                                        },
                                    ) {
                                        Text(
                                            stringResource(
                                                if (cycle?.cycle?.enabled == true) R.string.disable_cycle else R.string.enable_cycle,
                                            ),
                                        )
                                    }
                                    current?.let { item ->
                                        OutlinedButton(
                                            onClick = {
                                                launchAction(blockSaved) {
                                                    publicExamManager.moveStudyCycleItemToEnd(plan.id, item.id)
                                                }
                                            },
                                        ) { Text(stringResource(R.string.move_current_to_end)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                StudyFlowSectionCard(title = stringResource(R.string.study_strategy)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StudyStrategyType.entries.take(4).forEach { candidate ->
                            FilterChip(
                                selected = strategyName == candidate.name,
                                onClick = {
                                    strategyName = candidate.name
                                    val strategy = schedule.strategy?.copy(type = candidate) ?: StudyStrategy(
                                        id = StudyStrategyId(UUID.randomUUID().toString()),
                                        studyPlanId = plan.id,
                                        type = candidate,
                                        minimumSessionMinutes = 25,
                                        maximumSessionMinutes = 120,
                                        theoryRatioPercent = 50,
                                        questionRatioPercent = 30,
                                        reviewRatioPercent = 20,
                                        adaptivePlanningEnabled = candidate == StudyStrategyType.ADAPTIVE,
                                        configurationJson = "{}",
                                    )
                                    launchAction(blockSaved) { scheduleManager.saveStrategy(strategy) }
                                },
                                label = { Text(stringResource(candidate.labelKey())) },
                            )
                        }
                    }
                }
            }
            item {
                StudyFlowSectionCard(title = stringResource(R.string.weekly_blocks)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { showRuleDialog = true }) {
                            Icon(Icons.AutoMirrored.Outlined.EventNote, contentDescription = null)
                            Text(stringResource(R.string.add_block))
                        }
                        OutlinedButton(
                            onClick = {
                                val start = startOfTodayEpochMillis()
                                launchAction(weekGenerated) {
                                    scheduleManager.generateActivities(
                                        request = ScheduleGenerationRequest(
                                            plan.id,
                                            start,
                                            days = 7,
                                            replaceUnlocked = true,
                                        ),
                                        currentRules = schedule.scheduleRules,
                                        strategy = schedule.strategy,
                                    )
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.AutoFixHigh, contentDescription = null)
                            Text(stringResource(R.string.generate_week))
                        }
                    }
                }
            }
            if (schedule.scheduleRules.isEmpty()) {
                item {
                    StudyFlowEmptyState(
                        title = stringResource(R.string.empty_blocks_title),
                        message = stringResource(R.string.empty_blocks_message),
                        actionLabel = stringResource(R.string.add_block),
                        onAction = { showRuleDialog = true },
                    )
                }
            } else {
                items(schedule.scheduleRules, key = { it.id.value }) { rule ->
                    ScheduleRuleCard(
                        rule = rule,
                        onDelete = {
                            launchAction(itemDeleted) { scheduleManager.deleteRule(plan.id, rule.id) }
                        },
                    )
                }
            }
            item {
                StudyFlowSectionCard(title = stringResource(R.string.planned_activities)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StudyFlowStatusPill(
                            text = pluralStringResource(
                                R.plurals.activity_count,
                                schedule.plannedActivities.size,
                                schedule.plannedActivities.size,
                            ),
                            leadingIcon = { Icon(Icons.Outlined.PendingActions, contentDescription = null) },
                        )
                        OutlinedButton(
                            onClick = {
                                launchAction(missedRebalanced) {
                                    scheduleManager.rebalanceMissed(
                                        plan.id,
                                        schedule.plannedActivities,
                                        MissedActivityHandling.RETURN_TO_BACKLOG,
                                    )
                                }
                            },
                        ) {
                            Text(stringResource(R.string.rebalance_missed))
                        }
                    }
                }
            }
            if (schedule.plannedActivities.isEmpty()) {
                item {
                    StudyFlowEmptyState(
                        title = stringResource(R.string.empty_activities_title),
                        message = stringResource(R.string.empty_activities_message),
                        actionLabel = stringResource(R.string.add_activity),
                        onAction = { showActivityDialog = true },
                    )
                }
            } else {
                items(schedule.plannedActivities, key = { it.id.value }) { activity ->
                    PlannedActivityCard(
                        activity = activity,
                        onComplete = {
                            launchAction(activityCompleted) { scheduleManager.completeActivity(plan.id, activity.id) }
                        },
                        onMissed = {
                            launchAction(activityMissed) { scheduleManager.markActivityMissed(plan.id, activity.id) }
                        },
                        onDelete = {
                            launchAction(itemDeleted) { scheduleManager.deleteActivity(plan.id, activity.id) }
                        },
                    )
                }
            }
        }
    }

    if (showRuleDialog) {
        ScheduleRuleDialog(
            onDismiss = { showRuleDialog = false },
            onSave = { draft ->
                showRuleDialog = false
                val rule = ScheduleRule(
                    id = ScheduleRuleId(UUID.randomUUID().toString()),
                    studyPlanId = plan.id,
                    subjectId = null,
                    type = ScheduleRuleType.RECURRING_BLOCK,
                    dayOfWeek = draft.dayOfWeek,
                    startMinuteOfDay = draft.startMinuteOfDay,
                    durationMinutes = draft.durationMinutes,
                    recurrenceRule = null,
                    locked = draft.locked,
                    enabled = draft.enabled,
                    position = schedule.scheduleRules.size,
                )
                launchAction(blockSaved) { scheduleManager.saveRule(rule) }
            },
        )
    }

    if (showActivityDialog) {
        PlannedActivityDialog(
            onDismiss = { showActivityDialog = false },
            onSave = { draft ->
                showActivityDialog = false
                val activity = PlannedActivity(
                    id = PlannedActivityId(UUID.randomUUID().toString()),
                    studyPlanId = plan.id,
                    subjectId = null,
                    topicId = null,
                    materialId = null,
                    videoResourceId = null,
                    type = draft.type,
                    title = draft.title,
                    scheduledStartEpochMillis = null,
                    plannedDurationMinutes = draft.durationMinutes,
                    status = PlannedActivityStatus.PLANNED,
                    locked = draft.locked,
                    completedAtEpochMillis = null,
                )
                launchAction(activitySaved) { scheduleManager.saveActivity(activity) }
            },
        )
    }
}

@Composable
private fun ScheduleRuleCard(
    rule: ScheduleRule,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(dayLabelKey(rule.dayOfWeek)), style = MaterialTheme.typography.titleMedium)
            Text(
                text = pluralStringResource(
                    R.plurals.duration_label,
                    rule.durationMinutes,
                    rule.timeLabel(),
                    rule.durationMinutes,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudyFlowStatusPill(
                    text = if (rule.enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                )
                if (rule.locked) StudyFlowStatusPill(text = stringResource(R.string.locked))
                ConfirmingDeleteButton(onConfirmed = onDelete)
            }
        }
    }
}

@Composable
private fun PlannedActivityCard(
    activity: PlannedActivity,
    onComplete: () -> Unit,
    onMissed: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = activity.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(
                    R.string.activity_status_label,
                    activity.timeLabel(),
                    activity.plannedDurationMinutes,
                    stringResource(activity.status.labelKey()),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)) {
                    Button(onClick = onComplete) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Text(stringResource(R.string.mark_complete))
                    }
                    OutlinedButton(onClick = onMissed) {
                        Text(stringResource(R.string.mark_missed))
                    }
                }
                if (activity.status != PlannedActivityStatus.IN_PROGRESS) {
                    ConfirmingDeleteButton(onConfirmed = onDelete)
                }
            }
        }
    }
}

private fun startOfTodayEpochMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis


private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, defaultValue: T): T =
    enumValues<T>().firstOrNull { value -> value.name == name } ?: defaultValue


@Composable
private fun ConfirmingDeleteButton(onConfirmed: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { confirming = true }) {
        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
        Text(stringResource(R.string.delete))
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onConfirmed() }) {
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
