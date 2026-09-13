package com.mikeyphw.studyflow.feature.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.domain.PublicExamReviewPolicy
import com.mikeyphw.studyflow.core.domain.PublicExamReviewPolicyCodec
import com.mikeyphw.studyflow.core.model.StudyDomainInvariants
import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyTarget
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSettingsScreen(
    plan: StudyPlan,
    settings: StudyPlanSettings,
    onBack: () -> Unit,
    onSave: (StudyPlanSettings) -> Unit,
    modifier: Modifier = Modifier,
    studyTarget: StudyTarget? = null,
    examStages: List<ExamStage> = emptyList(),
    onSavePublicExamProfile: (PublicExamProfileForm) -> Unit = {},
) {
    var preferredSession by rememberSaveable(plan.id.value) {
        mutableStateOf(settings.preferredSessionMinutes.toString())
    }
    var adaptivePlanning by rememberSaveable(plan.id.value) {
        mutableStateOf(settings.adaptivePlanningEnabled)
    }
    var reviewNotifications by rememberSaveable(plan.id.value) {
        mutableStateOf(settings.reviewNotificationsEnabled)
    }
    var strictExamMode by rememberSaveable(plan.id.value) {
        mutableStateOf(settings.strictExamModeEnabled)
    }
    var quietHoursEnabled by rememberSaveable(plan.id.value) {
        mutableStateOf(settings.quietHoursStartMinute != null)
    }
    var quietHoursStart by rememberSaveable(plan.id.value) {
        mutableStateOf(formatMinuteOfDay(settings.quietHoursStartMinute ?: 22 * 60))
    }
    var quietHoursEnd by rememberSaveable(plan.id.value) {
        mutableStateOf(formatMinuteOfDay(settings.quietHoursEndMinute ?: 7 * 60))
    }

    val initialReviewPolicy = PublicExamReviewPolicyCodec.decode(settings.configurationJson)
    var reviewDay1 by rememberSaveable(plan.id.value) {
        mutableStateOf(initialReviewPolicy.intervalsDays.getOrElse(0) { 1 }.toString())
    }
    var reviewDay2 by rememberSaveable(plan.id.value) {
        mutableStateOf(initialReviewPolicy.intervalsDays.getOrElse(1) { 7 }.toString())
    }
    var reviewDay3 by rememberSaveable(plan.id.value) {
        mutableStateOf(initialReviewPolicy.intervalsDays.getOrElse(2) { 30 }.toString())
    }

    val preferredSessionValue = preferredSession.toIntOrNull()
    val quietStartValue = parseMinuteOfDay(quietHoursStart)
    val quietEndValue = parseMinuteOfDay(quietHoursEnd)
    val quietHoursValid = !quietHoursEnabled || (quietStartValue != null && quietEndValue != null)
    val reviewDays = listOf(reviewDay1, reviewDay2, reviewDay3).map { it.toIntOrNull() }
    val reviewPolicyValid = reviewDays.all { it != null && it in 1..365 } &&
        reviewDays.filterNotNull().zipWithNext().all { (left, right) -> right >= left }
    val formValid = preferredSessionValue in StudyDomainInvariants.preferredSessionMinutes && quietHoursValid &&
        (!shouldShowPublicExamProfile(plan.type) || reviewPolicyValid)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_settings_intro, plan.name),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (shouldShowPublicExamProfile(plan.type)) {
                PublicExamProfileSection(
                    plan = plan,
                    target = studyTarget,
                    examStages = examStages,
                    onSave = onSavePublicExamProfile,
                )
            }
            if (shouldShowPublicExamProfile(plan.type)) {
                Text(
                    text = stringResource(R.string.public_exam_review_policy_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.public_exam_review_policy_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(
                        Triple(reviewDay1, R.string.public_exam_review_24h, { value: String -> reviewDay1 = value }),
                        Triple(reviewDay2, R.string.public_exam_review_7d, { value: String -> reviewDay2 = value }),
                        Triple(reviewDay3, R.string.public_exam_review_30d, { value: String -> reviewDay3 = value }),
                    ).forEach { (value, label, update) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { update(it.filter(Char::isDigit).take(3)) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = !reviewPolicyValid,
                            singleLine = true,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = preferredSession,
                onValueChange = { preferredSession = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.preferred_session_minutes)) },
                supportingText = { Text(stringResource(R.string.preferred_session_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = preferredSessionValue !in StudyDomainInvariants.preferredSessionMinutes,
                singleLine = true,
            )
            PreferenceSwitch(
                title = stringResource(R.string.adaptive_planning),
                description = stringResource(R.string.adaptive_planning_description),
                checked = adaptivePlanning,
                onCheckedChange = { adaptivePlanning = it },
            )
            PreferenceSwitch(
                title = stringResource(R.string.review_notifications),
                description = stringResource(R.string.review_notifications_description),
                checked = reviewNotifications,
                onCheckedChange = { reviewNotifications = it },
            )
            PreferenceSwitch(
                title = stringResource(R.string.strict_exam_mode),
                description = stringResource(R.string.strict_exam_mode_description),
                checked = strictExamMode,
                onCheckedChange = { strictExamMode = it },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .toggleable(
                        value = quietHoursEnabled,
                        role = Role.Switch,
                        onValueChange = { quietHoursEnabled = it },
                    ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.quiet_hours), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.quiet_hours_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = quietHoursEnabled,
                    onCheckedChange = null,
                )
            }
            if (quietHoursEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = quietHoursStart,
                        onValueChange = { quietHoursStart = it.take(5) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.quiet_hours_start)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    if (quietStartValue == null) R.string.invalid_time else R.string.time_hint,
                                ),
                            )
                        },
                        isError = quietStartValue == null,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = quietHoursEnd,
                        onValueChange = { quietHoursEnd = it.take(5) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.quiet_hours_end)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    if (quietEndValue == null) R.string.invalid_time else R.string.time_hint,
                                ),
                            )
                        },
                        isError = quietEndValue == null,
                        singleLine = true,
                    )
                }
            }
            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            adaptivePlanningEnabled = adaptivePlanning,
                            reviewNotificationsEnabled = reviewNotifications,
                            strictExamModeEnabled = strictExamMode,
                            preferredSessionMinutes = requireNotNull(preferredSessionValue),
                            quietHoursStartMinute = if (quietHoursEnabled) quietStartValue else null,
                            quietHoursEndMinute = if (quietHoursEnabled) quietEndValue else null,
                            configurationJson = if (shouldShowPublicExamProfile(plan.type)) {
                                PublicExamReviewPolicyCodec.encode(
                                    settings.configurationJson,
                                    PublicExamReviewPolicy(reviewDays.filterNotNull()),
                                )
                            } else {
                                settings.configurationJson
                            },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = formValid,
            ) {
                Text(stringResource(R.string.save_settings))
            }
        }
    }
}

internal fun parseMinuteOfDay(value: String): Int? {
    val match = Regex("^(\\d{2}):(\\d{2})$").matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

internal fun formatMinuteOfDay(value: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    value / 60,
    value % 60,
)
