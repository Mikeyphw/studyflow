package com.mikeyphw.studyflow.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.mikeyphw.studyflow.core.domain.BancoDoBrasilAgenteComercialPreset
import com.mikeyphw.studyflow.core.domain.BancoDoBrasilStudyPhasePreset
import com.mikeyphw.studyflow.core.domain.StudyPhaseDefinition
import com.mikeyphw.studyflow.core.domain.StudyPhaseKind
import com.mikeyphw.studyflow.core.domain.StudyPhasePlan
import com.mikeyphw.studyflow.core.domain.StudyPhasePlanCodec
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyScheduleScreen(
    plan: StudyPlan,
    scheduleManager: ManageStudySchedule,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentManager: ManageStudyContent? = null,
    publicExamManager: ManagePublicExam? = null,
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
    var showPhaseDialog by remember { mutableStateOf(false) }
    var strategyName by rememberSaveable(schedule.strategy?.id?.value) {
        mutableStateOf((schedule.strategy?.type ?: StudyStrategyType.FIXED_WEEKLY).name)
    }
    var subjectFilterId by rememberSaveable(plan.id.value) { mutableStateOf("") }
    var typeFilterName by rememberSaveable(plan.id.value) { mutableStateOf("") }
    var statusFilterName by rememberSaveable(plan.id.value) { mutableStateOf("") }
    var provenanceFilterName by rememberSaveable(plan.id.value) { mutableStateOf("") }
    var selectedActivityIds by rememberSaveable(plan.id.value) { mutableStateOf(setOf<String>()) }
    var confirmingBulkDelete by rememberSaveable(plan.id.value) { mutableStateOf(false) }
    var calendarViewName by rememberSaveable(plan.id.value) {
        mutableStateOf(ScheduleCalendarView.THIS_WEEK.name)
    }

    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember(zoneId) { LocalDate.now(zoneId) }
    val calendarView = ScheduleCalendarView.valueOf(calendarViewName)
    val calendarWindow = remember(calendarView, today) { ScheduleCalendarPlanner.window(calendarView, today) }
    val phasePlan = remember(schedule.strategy?.configurationJson) {
        StudyPhasePlanCodec.decode(schedule.strategy?.configurationJson.orEmpty())
    }
    val activePhase = remember(phasePlan) { phasePlan.activeOn(LocalDate.now()) }
    val subjectById = remember(syllabus) { syllabus.subjects.associateBy { it.id.value } }
    val activityFilter = ScheduleActivityFilter(
        subjectId = subjectFilterId.takeIf(String::isNotEmpty)?.let { com.mikeyphw.studyflow.core.model.SubjectId(it) },
        type = typeFilterName.takeIf(String::isNotEmpty)?.let { enumValueOrDefault(it, StudyActivityType.CUSTOM) },
        status = statusFilterName.takeIf(String::isNotEmpty)?.let { enumValueOrDefault(it, PlannedActivityStatus.PLANNED) },
        provenance = provenanceFilterName.takeIf(String::isNotEmpty)?.let { enumValueOrDefault(it, ScheduleActivityProvenance.MANUAL) },
    )
    val criteriaActivities = remember(schedule.plannedActivities, activityFilter) {
        ScheduleIntelligence.filter(schedule.plannedActivities, activityFilter)
    }
    val visibleActivities = remember(criteriaActivities, calendarView, today, zoneId) {
        ScheduleCalendarPlanner.filter(criteriaActivities, calendarView, today, zoneId)
    }
    val dayGroups = remember(visibleActivities, zoneId) { ScheduleIntelligence.groupByDay(visibleActivities, zoneId) }
    val intelligence = remember(schedule.plannedActivities, zoneId) {
        ScheduleIntelligence.inspect(schedule.plannedActivities, zoneId)
    }
    val selectedMutableActivities = remember(schedule.plannedActivities, selectedActivityIds) {
        schedule.plannedActivities.filter { it.id.value in selectedActivityIds && ScheduleIntelligence.isBulkMutable(it) }
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
            item {
                StudyFlowSectionCard(title = stringResource(R.string.schedule_view_title)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ScheduleCalendarView.entries.forEach { candidate ->
                                FilterChip(
                                    selected = calendarView == candidate,
                                    onClick = { calendarViewName = candidate.name },
                                    label = { Text(stringResource(candidate.scheduleViewLabelKey())) },
                                )
                            }
                        }
                        Text(
                            text = calendarWindow.scheduleWindowLabel(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.schedule_view_summary,
                                visibleActivities.size,
                                ScheduleCalendarPlanner.plannedMinutes(visibleActivities),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        launchAction(blockSaved) {
                                            publicExamManager.replaceStudyCycleFromSyllabus(plan.id, syllabus)
                                        }
                                    },
                                    enabled = syllabus.subjects.any { !it.archived },
                                ) {
                                    Text(
                                        stringResource(
                                            if (cycle == null) R.string.build_cycle else R.string.rebuild_cycle,
                                        ),
                                    )
                                }
                                if (cycle != null) {
                                    OutlinedButton(
                                        onClick = {
                                            launchAction(blockSaved) {
                                                publicExamManager.skipCurrentStudyCycleItem(plan.id)
                                            }
                                        },
                                    ) { Text(stringResource(R.string.skip_once)) }
                                }
                            }
                            if (cycle != null) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            launchAction(blockSaved) {
                                                publicExamManager.setStudyCycleEnabled(
                                                    plan.id,
                                                    !(cycle?.cycle?.enabled ?: true),
                                                )
                                            }
                                        },
                                    ) {
                                        Text(
                                            stringResource(
                                                if (cycle?.cycle?.enabled == true) {
                                                    R.string.disable_cycle
                                                } else {
                                                    R.string.enable_cycle
                                                },
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
                StudyFlowSectionCard(title = stringResource(R.string.study_phase_title)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (phasePlan.phases.isEmpty()) {
                            Text(stringResource(R.string.study_phase_not_configured))
                        } else {
                            phasePlan.phases.sortedBy(StudyPhaseDefinition::startsOn).forEach { phase ->
                                val active = phase == activePhase
                                StudyFlowStatusPill(
                                    text = buildString {
                                        append(stringResource(phase.kind.phaseLabelKey()))
                                        append(" · ")
                                        append(phase.startsOn)
                                        append(" → ")
                                        append(phase.endsOn)
                                        if (active) append(" · ").append(stringResource(R.string.study_phase_active))
                                    },
                                )
                                Text(
                                    stringResource(
                                        R.string.study_phase_summary,
                                        phase.theoryRatioPercent,
                                        phase.questionRatioPercent,
                                        phase.reviewRatioPercent,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { showPhaseDialog = true },
                                enabled = schedule.strategy != null,
                            ) { Text(stringResource(R.string.study_phase_edit)) }
                            if (BancoDoBrasilAgenteComercialPreset.matchesReferencePlanName(plan.name)) {
                                OutlinedButton(
                                    onClick = {
                                        schedule.strategy?.let { strategy ->
                                            launchAction(blockSaved) {
                                                scheduleManager.savePhasePlan(strategy, BancoDoBrasilStudyPhasePreset.plan())
                                            }
                                        }
                                    },
                                    enabled = schedule.strategy != null,
                                ) { Text(stringResource(R.string.study_phase_use_bb_reference)) }
                            }
                        }
                        Text(
                            stringResource(R.string.study_phase_allocation_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                StudyFlowSectionCard(title = stringResource(R.string.study_strategy)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                                        strategy = scheduleManager.effectiveStrategyForDate(
                                            schedule.strategy,
                                            LocalDate.now(),
                                        ),
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
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StudyFlowStatusPill(
                            text = pluralStringResource(
                                R.plurals.activity_count,
                                visibleActivities.size,
                                visibleActivities.size,
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
            item {
                StudyFlowSectionCard(title = stringResource(R.string.schedule_filters)) {
                    Text(stringResource(R.string.filter_subject), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = subjectFilterId.isEmpty(),
                            onClick = { subjectFilterId = ""; selectedActivityIds = emptySet() },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                        syllabus.subjects.filter { !it.archived }.forEach { subject ->
                            FilterChip(
                                selected = subjectFilterId == subject.id.value,
                                onClick = { subjectFilterId = subject.id.value; selectedActivityIds = emptySet() },
                                label = { Text(subject.name) },
                            )
                        }
                    }
                    Text(stringResource(R.string.filter_type), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = typeFilterName.isEmpty(),
                            onClick = { typeFilterName = ""; selectedActivityIds = emptySet() },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                        StudyActivityType.entries.forEach { type ->
                            FilterChip(
                                selected = typeFilterName == type.name,
                                onClick = { typeFilterName = type.name; selectedActivityIds = emptySet() },
                                label = { Text(stringResource(type.labelKey())) },
                            )
                        }
                    }
                    Text(stringResource(R.string.filter_status), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = statusFilterName.isEmpty(),
                            onClick = { statusFilterName = ""; selectedActivityIds = emptySet() },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                        PlannedActivityStatus.entries.forEach { status ->
                            FilterChip(
                                selected = statusFilterName == status.name,
                                onClick = { statusFilterName = status.name; selectedActivityIds = emptySet() },
                                label = { Text(stringResource(status.labelKey())) },
                            )
                        }
                    }
                    Text(stringResource(R.string.filter_provenance), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = provenanceFilterName.isEmpty(),
                            onClick = { provenanceFilterName = ""; selectedActivityIds = emptySet() },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                        ScheduleActivityProvenance.entries.forEach { provenance ->
                            FilterChip(
                                selected = provenanceFilterName == provenance.name,
                                onClick = { provenanceFilterName = provenance.name; selectedActivityIds = emptySet() },
                                label = { Text(stringResource(provenance.labelKey())) },
                            )
                        }
                    }
                }
            }
            if (visibleActivities.any(ScheduleIntelligence::isBulkMutable)) {
                item {
                    StudyFlowSectionCard(title = stringResource(R.string.bulk_actions)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedActivityIds = visibleActivities
                                        .filter(ScheduleIntelligence::isBulkMutable)
                                        .mapTo(linkedSetOf()) { it.id.value }
                                },
                            ) { Text(stringResource(R.string.select_visible)) }
                            StudyFlowStatusPill(
                                text = stringResource(R.string.selected_count, selectedMutableActivities.size),
                            )
                            Button(
                                enabled = selectedMutableActivities.isNotEmpty(),
                                onClick = {
                                    val selected = selectedMutableActivities
                                    selectedActivityIds = emptySet()
                                    launchAction(activityCompleted) {
                                        selected.forEach { scheduleManager.completeActivity(plan.id, it.id) }
                                    }
                                },
                            ) { Text(stringResource(R.string.mark_complete)) }
                            OutlinedButton(
                                enabled = selectedMutableActivities.isNotEmpty(),
                                onClick = {
                                    val selected = selectedMutableActivities
                                    selectedActivityIds = emptySet()
                                    launchAction(activityMissed) {
                                        selected.forEach { scheduleManager.markActivityMissed(plan.id, it.id) }
                                    }
                                },
                            ) { Text(stringResource(R.string.mark_missed)) }
                            OutlinedButton(
                                enabled = selectedMutableActivities.isNotEmpty(),
                                onClick = { confirmingBulkDelete = true },
                            ) { Text(stringResource(R.string.delete_selected)) }
                        }
                        Text(
                            stringResource(R.string.bulk_protection_note),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (visibleActivities.isEmpty()) {
                item {
                    StudyFlowEmptyState(
                        title = if (schedule.plannedActivities.isEmpty()) {
                            stringResource(R.string.empty_activities_title)
                        } else {
                            stringResource(R.string.no_filter_matches_title)
                        },
                        message = if (schedule.plannedActivities.isEmpty()) {
                            stringResource(R.string.empty_activities_message)
                        } else {
                            stringResource(R.string.no_filter_matches_message)
                        },
                        actionLabel = stringResource(R.string.add_activity),
                        onAction = { showActivityDialog = true },
                    )
                }
            } else {
                dayGroups.forEach { group ->
                    item(key = "day:${group.date ?: "unscheduled"}") {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = scheduleDayLabel(group.date),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            group.date?.takeIf { it in intelligence.overloadedDates }?.let {
                                StudyFlowStatusPill(text = stringResource(R.string.overloaded_day))
                            }
                        }
                    }
                    items(group.activities, key = { "activity:${it.id.value}" }) { activity ->
                        PlannedActivityCard(
                            activity = activity,
                            subjectLabel = activity.subjectId?.value?.let { subjectById[it]?.name },
                            provenance = ScheduleIntelligence.provenance(activity),
                            conflict = activity.id in intelligence.conflictActivityIds,
                            overloaded = group.date?.let { it in intelligence.overloadedDates } == true,
                            selectable = ScheduleIntelligence.isBulkMutable(activity),
                            selected = activity.id.value in selectedActivityIds,
                            onSelectedChange = { selected ->
                                selectedActivityIds = if (selected) {
                                    selectedActivityIds + activity.id.value
                                } else {
                                    selectedActivityIds - activity.id.value
                                }
                            },
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
    }

    if (confirmingBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmingBulkDelete = false },
            title = { Text(stringResource(R.string.delete_selected_title)) },
            text = { Text(stringResource(R.string.delete_selected_message, selectedMutableActivities.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = selectedMutableActivities
                        confirmingBulkDelete = false
                        selectedActivityIds = emptySet()
                        launchAction(itemDeleted) { selected.forEach { scheduleManager.deleteActivity(plan.id, it.id) } }
                    },
                ) { Text(stringResource(R.string.confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingBulkDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showPhaseDialog) {
        StudyPhaseEditorDialog(
            existing = phasePlan,
            onDismiss = { showPhaseDialog = false },
            onSave = { phase ->
                val strategy = schedule.strategy
                if (strategy == null) {
                    showPhaseDialog = false
                } else {
                    val merged = StudyPhasePlan(
                        (phasePlan.phases.filterNot { it.kind == phase.kind } + phase).sortedBy { it.startsOn },
                    )
                    launchAction(blockSaved) { scheduleManager.savePhasePlan(strategy, merged) }
                    showPhaseDialog = false
                }
            },
        )
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

@OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StudyFlowStatusPill(
                    text = if (rule.enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                )
                if (rule.locked) StudyFlowStatusPill(text = stringResource(R.string.locked))
                DeleteOverflowMenu(onConfirmed = onDelete)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannedActivityCard(
    activity: PlannedActivity,
    subjectLabel: String?,
    provenance: ScheduleActivityProvenance,
    conflict: Boolean,
    overloaded: Boolean,
    selectable: Boolean,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
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
            subjectLabel?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StudyFlowStatusPill(text = stringResource(provenance.labelKey()))
                if (conflict) StudyFlowStatusPill(text = stringResource(R.string.schedule_conflict))
                if (overloaded) StudyFlowStatusPill(text = stringResource(R.string.overloaded_day))
                if (activity.locked) StudyFlowStatusPill(text = stringResource(R.string.locked))
                if (selectable) {
                    Checkbox(checked = selected, onCheckedChange = onSelectedChange)
                    Text(stringResource(R.string.select_activity), style = MaterialTheme.typography.bodySmall)
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    DeleteOverflowMenu(onConfirmed = onDelete)
                }
            }
        }
    }
}

@Composable
private fun DeleteOverflowMenu(onConfirmed: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    expanded = false
                    confirming = true
                },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            )
        }
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

@Composable
private fun ScheduleCalendarWindow.scheduleWindowLabel(): String = when {
    !bounded -> stringResource(R.string.schedule_view_all_dates)
    startsOn == endsOnInclusive -> requireNotNull(startsOn).toString()
    else -> "${requireNotNull(startsOn)} → ${requireNotNull(endsOnInclusive)}"
}

private fun ScheduleCalendarView.scheduleViewLabelKey(): Int = when (this) {
    ScheduleCalendarView.TODAY -> R.string.schedule_view_today
    ScheduleCalendarView.THIS_WEEK -> R.string.schedule_view_this_week
    ScheduleCalendarView.NEXT_WEEK -> R.string.schedule_view_next_week
    ScheduleCalendarView.CALENDAR -> R.string.schedule_view_calendar
}

@Composable
private fun scheduleDayLabel(date: LocalDate?): String {
    if (date == null) return stringResource(R.string.unscheduled)
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
}

private fun ScheduleActivityProvenance.labelKey(): Int = when (this) {
    ScheduleActivityProvenance.MANUAL -> R.string.provenance_manual
    ScheduleActivityProvenance.GENERATED -> R.string.provenance_generated
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
private fun StudyPhaseEditorDialog(
    existing: StudyPhasePlan,
    onDismiss: () -> Unit,
    onSave: (StudyPhaseDefinition) -> Unit,
) {
    var kindName by rememberSaveable { mutableStateOf(StudyPhaseKind.FOUNDATION.name) }
    val existingPhase = existing.phases.firstOrNull { it.kind.name == kindName }
    var startsOn by rememberSaveable(kindName) { mutableStateOf(existingPhase?.startsOn?.toString().orEmpty()) }
    var endsOn by rememberSaveable(kindName) { mutableStateOf(existingPhase?.endsOn?.toString().orEmpty()) }
    var theory by rememberSaveable(kindName) { mutableStateOf((existingPhase?.theoryRatioPercent ?: 50).toString()) }
    var questions by rememberSaveable(kindName) { mutableStateOf((existingPhase?.questionRatioPercent ?: 35).toString()) }
    var reviews by rememberSaveable(kindName) { mutableStateOf((existingPhase?.reviewRatioPercent ?: 15).toString()) }
    var questionGoal by rememberSaveable(kindName) { mutableStateOf(existingPhase?.questionGoal?.toString().orEmpty()) }
    var mockGoal by rememberSaveable(kindName) { mutableStateOf(existingPhase?.mockExamGoal?.toString().orEmpty()) }
    val candidate = runCatching {
        StudyPhaseDefinition(
            kind = StudyPhaseKind.valueOf(kindName),
            startsOn = LocalDate.parse(startsOn),
            endsOn = LocalDate.parse(endsOn),
            theoryRatioPercent = theory.toInt(),
            questionRatioPercent = questions.toInt(),
            reviewRatioPercent = reviews.toInt(),
            questionGoal = questionGoal.takeIf(String::isNotBlank)?.toInt(),
            mockExamGoal = mockGoal.takeIf(String::isNotBlank)?.toInt(),
            allocationIsStudyFlowSuggested = false,
        )
    }.getOrNull()
    val validCandidate = candidate?.takeIf { phase ->
        runCatching {
            StudyPhasePlan((existing.phases.filterNot { it.kind == phase.kind } + phase).sortedBy { it.startsOn })
        }.isSuccess
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_phase_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudyPhaseKind.entries.forEach { kind ->
                        FilterChip(
                            selected = kindName == kind.name,
                            onClick = { kindName = kind.name },
                            label = { Text(stringResource(kind.phaseLabelKey())) },
                        )
                    }
                }
                OutlinedTextField(startsOn, { startsOn = it }, label = { Text(stringResource(R.string.study_phase_start)) })
                OutlinedTextField(endsOn, { endsOn = it }, label = { Text(stringResource(R.string.study_phase_end)) })
                OutlinedTextField(theory, { theory = it }, label = { Text(stringResource(R.string.study_phase_theory_ratio)) })
                OutlinedTextField(questions, { questions = it }, label = { Text(stringResource(R.string.study_phase_question_ratio)) })
                OutlinedTextField(reviews, { reviews = it }, label = { Text(stringResource(R.string.study_phase_review_ratio)) })
                OutlinedTextField(questionGoal, { questionGoal = it }, label = { Text(stringResource(R.string.study_phase_question_goal_label)) })
                OutlinedTextField(mockGoal, { mockGoal = it }, label = { Text(stringResource(R.string.study_phase_mock_goal_label)) })
            }
        },
        confirmButton = {
            Button(enabled = validCandidate != null, onClick = { validCandidate?.let(onSave) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun StudyPhaseKind.phaseLabelKey(): Int = when (this) {
    StudyPhaseKind.FOUNDATION -> R.string.study_phase_foundation
    StudyPhaseKind.EXPANSION -> R.string.study_phase_expansion
    StudyPhaseKind.CONSOLIDATION -> R.string.study_phase_consolidation
    StudyPhaseKind.QUESTIONS_REVIEWS -> R.string.study_phase_questions_reviews
}
