package com.mikeyphw.studyflow.feature.plans

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowEmptyState
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal enum class PlanCollectionFilter {
    CURRENT,
    ARCHIVED,
    TRASH,
}

enum class StudyPlanAction {
    EDIT,
    DUPLICATE,
    SET_DEFAULT,
    CLEAR_DEFAULT,
    ACTIVATE,
    PAUSE,
    ARCHIVE,
    RESTORE_ARCHIVED,
    MOVE_TO_TRASH,
    RESTORE_FROM_TRASH,
    DELETE_PERMANENTLY,
}

@Composable
fun PlanListScreen(
    plans: List<StudyPlan>,
    defaultPlanId: StudyPlanId?,
    onPlanSelected: (StudyPlan) -> Unit,
    onCreatePlan: () -> Unit,
    onPlanAction: (StudyPlan, StudyPlanAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilterName by rememberSaveable { mutableStateOf(PlanCollectionFilter.CURRENT.name) }
    val selectedFilter = PlanCollectionFilter.valueOf(selectedFilterName)
    val visiblePlans = plansForFilter(plans, selectedFilter)

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreatePlan) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.create_plan),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.plans_title),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            text = stringResource(R.string.plans_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanCollectionFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilterName = filter.name },
                                label = { Text(stringResource(filterLabel(filter))) },
                            )
                        }
                    }
                }
            }

            if (visiblePlans.isEmpty()) {
                item {
                    val title = when (selectedFilter) {
                        PlanCollectionFilter.CURRENT -> R.string.empty_plans_title
                        PlanCollectionFilter.ARCHIVED -> R.string.empty_archived_title
                        PlanCollectionFilter.TRASH -> R.string.empty_trash_title
                    }
                    val message = when (selectedFilter) {
                        PlanCollectionFilter.CURRENT -> R.string.empty_plans_message
                        PlanCollectionFilter.ARCHIVED -> R.string.empty_archived_message
                        PlanCollectionFilter.TRASH -> R.string.empty_trash_message
                    }
                    StudyFlowEmptyState(
                        title = stringResource(title),
                        message = stringResource(message),
                        actionLabel = if (selectedFilter == PlanCollectionFilter.CURRENT) {
                            stringResource(R.string.create_plan)
                        } else {
                            null
                        },
                        onAction = if (selectedFilter == PlanCollectionFilter.CURRENT) onCreatePlan else null,
                    )
                }
            } else {
                items(visiblePlans, key = { it.id.value }) { plan ->
                    StudyPlanCard(
                        plan = plan,
                        isDefault = defaultPlanId == plan.id,
                        onClick = { onPlanSelected(plan) },
                        onAction = { action -> onPlanAction(plan, action) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyPlanCard(
    plan: StudyPlan,
    isDefault: Boolean,
    onClick: () -> Unit,
    onAction: (StudyPlanAction) -> Unit,
) {
    var menuExpanded by rememberSaveable(plan.id.value) { mutableStateOf(false) }
    var confirmationActionName by rememberSaveable(plan.id.value) { mutableStateOf<String?>(null) }
    val confirmationAction = confirmationActionName?.let(StudyPlanAction::valueOf)

    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(statusLabel(plan.status)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isDefault) {
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.default_plan)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Star, contentDescription = null)
                                },
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.progress_percent, plan.progressPercent),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.more_actions),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        planActions(plan, isDefault).forEach { action ->
                            DropdownMenuItem(
                                text = { Text(stringResource(actionLabel(action))) },
                                onClick = {
                                    menuExpanded = false
                                    if (action == StudyPlanAction.MOVE_TO_TRASH ||
                                        action == StudyPlanAction.DELETE_PERMANENTLY
                                    ) {
                                        confirmationActionName = action.name
                                    } else {
                                        onAction(action)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            LinearProgressIndicator(
                progress = { plan.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.weekly_goal_hours,
                        plan.weeklyGoalHours,
                        plan.weeklyGoalHours,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = examCountdownLabel(plan.examDateIso),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.next_activity_unconfigured),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (plan.status != StudyPlanStatus.TRASHED) {
                Button(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
                    Text(text = stringResource(R.string.open_plan))
                }
            }
        }
    }

    if (confirmationAction != null) {
        val permanent = confirmationAction == StudyPlanAction.DELETE_PERMANENTLY
        AlertDialog(
            onDismissRequest = { confirmationActionName = null },
            icon = if (permanent) {
                { Icon(Icons.Outlined.DeleteForever, contentDescription = null) }
            } else {
                null
            },
            title = {
                Text(
                    stringResource(
                        if (permanent) R.string.delete_confirmation_title else R.string.trash_confirmation_title,
                        plan.name,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (permanent) R.string.delete_confirmation_message else R.string.trash_confirmation_message,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmationActionName = null
                        onAction(confirmationAction)
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmationActionName = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

internal fun plansForFilter(
    plans: List<StudyPlan>,
    filter: PlanCollectionFilter,
): List<StudyPlan> = plans.filter { plan ->
    when (filter) {
        PlanCollectionFilter.CURRENT -> plan.status !in setOf(
            StudyPlanStatus.ARCHIVED,
            StudyPlanStatus.TRASHED,
        )
        PlanCollectionFilter.ARCHIVED -> plan.status == StudyPlanStatus.ARCHIVED
        PlanCollectionFilter.TRASH -> plan.status == StudyPlanStatus.TRASHED
    }
}

internal fun planActions(
    plan: StudyPlan,
    isDefault: Boolean,
): List<StudyPlanAction> = when (plan.status) {
    StudyPlanStatus.TRASHED -> listOf(
        StudyPlanAction.RESTORE_FROM_TRASH,
        StudyPlanAction.DELETE_PERMANENTLY,
    )
    StudyPlanStatus.ARCHIVED -> buildList {
        add(StudyPlanAction.RESTORE_ARCHIVED)
        add(StudyPlanAction.DUPLICATE)
        if (isDefault) add(StudyPlanAction.CLEAR_DEFAULT)
        add(StudyPlanAction.MOVE_TO_TRASH)
    }
    else -> buildList {
        add(StudyPlanAction.EDIT)
        add(StudyPlanAction.DUPLICATE)
        add(if (isDefault) StudyPlanAction.CLEAR_DEFAULT else StudyPlanAction.SET_DEFAULT)
        if (plan.status == StudyPlanStatus.ACTIVE) {
            add(StudyPlanAction.PAUSE)
        } else {
            add(StudyPlanAction.ACTIVATE)
        }
        add(StudyPlanAction.ARCHIVE)
        add(StudyPlanAction.MOVE_TO_TRASH)
    }
}

@Composable
private fun examCountdownLabel(examDateIso: String?): String {
    val days = examDateIso?.let(::daysUntilExam) ?: return stringResource(R.string.no_exam_date)
    return when {
        days > 0 -> pluralStringResource(R.plurals.days_until_exam, days.toInt(), days.toInt())
        days == 0L -> stringResource(R.string.exam_today)
        else -> stringResource(R.string.exam_date_passed)
    }
}

internal fun daysUntilExam(
    examDateIso: String,
    nowEpochMillis: Long = System.currentTimeMillis(),
): Long? {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    val position = ParsePosition(0)
    val parsed = formatter.parse(examDateIso, position) ?: return null
    if (position.index != examDateIso.length) return null
    val today = Date(nowEpochMillis - Math.floorMod(nowEpochMillis, TimeUnit.DAYS.toMillis(1)))
    val exam = Date(parsed.time - Math.floorMod(parsed.time, TimeUnit.DAYS.toMillis(1)))
    return TimeUnit.MILLISECONDS.toDays(exam.time - today.time)
}

@StringRes
private fun filterLabel(filter: PlanCollectionFilter): Int = when (filter) {
    PlanCollectionFilter.CURRENT -> R.string.filter_current
    PlanCollectionFilter.ARCHIVED -> R.string.filter_archived
    PlanCollectionFilter.TRASH -> R.string.filter_trash
}

@StringRes
private fun actionLabel(action: StudyPlanAction): Int = when (action) {
    StudyPlanAction.EDIT -> R.string.edit_plan
    StudyPlanAction.DUPLICATE -> R.string.duplicate_plan
    StudyPlanAction.SET_DEFAULT -> R.string.set_as_default
    StudyPlanAction.CLEAR_DEFAULT -> R.string.remove_default
    StudyPlanAction.ACTIVATE -> R.string.activate_plan
    StudyPlanAction.PAUSE -> R.string.pause_plan
    StudyPlanAction.ARCHIVE -> R.string.archive_plan
    StudyPlanAction.RESTORE_ARCHIVED,
    StudyPlanAction.RESTORE_FROM_TRASH,
    -> R.string.restore_plan
    StudyPlanAction.MOVE_TO_TRASH -> R.string.move_to_trash
    StudyPlanAction.DELETE_PERMANENTLY -> R.string.delete_permanently
}

@StringRes
internal fun statusLabel(status: StudyPlanStatus): Int = when (status) {
    StudyPlanStatus.DRAFT -> R.string.status_draft
    StudyPlanStatus.ACTIVE -> R.string.status_active
    StudyPlanStatus.PAUSED -> R.string.status_paused
    StudyPlanStatus.COMPLETED -> R.string.status_completed
    StudyPlanStatus.ARCHIVED -> R.string.status_archived
    StudyPlanStatus.TRASHED -> R.string.status_trashed
}
