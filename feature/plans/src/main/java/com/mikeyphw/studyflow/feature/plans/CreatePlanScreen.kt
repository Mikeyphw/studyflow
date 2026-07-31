package com.mikeyphw.studyflow.feature.plans

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyType
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

private const val DefaultWeeklyGoal = 20
private const val DefaultSessionMinutes = 45
private const val WizardStepCount = 3

data class PlanEditorForm(
    val name: String,
    val description: String,
    val studyType: StudyType,
    val examDateIso: String,
    val weeklyGoalHours: Int,
    val preferredSessionMinutes: Int,
    val adaptivePlanningEnabled: Boolean,
    val reviewNotificationsEnabled: Boolean,
    val strictExamModeEnabled: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(
    initialPlan: StudyPlan?,
    initialSettings: StudyPlanSettings?,
    onBack: () -> Unit,
    onSave: (PlanEditorForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentStep by rememberSaveable(initialPlan?.id?.value) { mutableStateOf(0) }
    var name by rememberSaveable(initialPlan?.id?.value) { mutableStateOf(initialPlan?.name.orEmpty()) }
    var description by rememberSaveable(initialPlan?.id?.value) {
        mutableStateOf(initialPlan?.description.orEmpty())
    }
    var examDate by rememberSaveable(initialPlan?.id?.value) {
        mutableStateOf(initialPlan?.examDateIso.orEmpty())
    }
    var weeklyGoal by rememberSaveable(initialPlan?.id?.value) {
        mutableStateOf((initialPlan?.weeklyGoalHours ?: DefaultWeeklyGoal).toString())
    }
    var preferredSession by rememberSaveable(initialPlan?.id?.value) {
        mutableStateOf(
            (initialSettings?.preferredSessionMinutes ?: DefaultSessionMinutes).toString(),
        )
    }
    var studyTypeName by rememberSaveable(initialPlan?.id?.value) {
        mutableStateOf((initialPlan?.type ?: StudyType.PUBLIC_EXAMINATION).name)
    }
    var adaptivePlanning by rememberSaveable(initialPlan?.id?.value) {
        mutableStateOf(initialSettings?.adaptivePlanningEnabled ?: true)
    }
    var reviewNotifications by rememberSaveable(initialPlan?.id?.value) {
        mutableStateOf(initialSettings?.reviewNotificationsEnabled ?: true)
    }
    var strictExamMode by rememberSaveable(initialPlan?.id?.value) {
        mutableStateOf(initialSettings?.strictExamModeEnabled ?: false)
    }

    val selectedStudyType = StudyType.valueOf(studyTypeName)
    val weeklyGoalValue = weeklyGoal.toIntOrNull()
    val preferredSessionValue = preferredSession.toIntOrNull()
    val dateValid = isValidIsoDateOrBlank(examDate)
    val objectiveValid = name.isNotBlank()
    val targetValid = weeklyGoalValue in 1..100 && dateValid
    val preferencesValid = preferredSessionValue in 5..240
    val canAdvance = when (currentStep) {
        0 -> objectiveValid
        1 -> targetValid
        else -> preferencesValid
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (initialPlan == null) R.string.create_plan_title else R.string.edit_plan_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.wizard_step, currentStep + 1, WizardStepCount),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            LinearProgressIndicator(
                progress = { (currentStep + 1f) / WizardStepCount },
                modifier = Modifier.fillMaxWidth(),
            )

            when (currentStep) {
                0 -> ObjectiveStep(
                    name = name,
                    onNameChanged = { name = it },
                    description = description,
                    onDescriptionChanged = { description = it },
                    selectedStudyType = selectedStudyType,
                    onStudyTypeChanged = { studyTypeName = it.name },
                )
                1 -> TargetStep(
                    examDate = examDate,
                    onExamDateChanged = { examDate = it },
                    examDateValid = dateValid,
                    weeklyGoal = weeklyGoal,
                    onWeeklyGoalChanged = { weeklyGoal = it.filter(Char::isDigit).take(3) },
                    weeklyGoalValid = weeklyGoalValue in 1..100,
                )
                else -> PreferencesStep(
                    preferredSession = preferredSession,
                    onPreferredSessionChanged = {
                        preferredSession = it.filter(Char::isDigit).take(3)
                    },
                    preferredSessionValid = preferencesValid,
                    adaptivePlanning = adaptivePlanning,
                    onAdaptivePlanningChanged = { adaptivePlanning = it },
                    reviewNotifications = reviewNotifications,
                    onReviewNotificationsChanged = { reviewNotifications = it },
                    strictExamMode = strictExamMode,
                    onStrictExamModeChanged = { strictExamMode = it },
                )
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentStep == 0) onBack() else currentStep -= 1
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (currentStep == 0) R.string.cancel else R.string.previous,
                        ),
                    )
                }
                Button(
                    onClick = {
                        if (currentStep < WizardStepCount - 1) {
                            currentStep += 1
                        } else {
                            onSave(
                                PlanEditorForm(
                                    name = name.trim(),
                                    description = description.trim(),
                                    studyType = selectedStudyType,
                                    examDateIso = examDate.trim(),
                                    weeklyGoalHours = requireNotNull(weeklyGoalValue),
                                    preferredSessionMinutes = requireNotNull(preferredSessionValue),
                                    adaptivePlanningEnabled = adaptivePlanning,
                                    reviewNotificationsEnabled = reviewNotifications,
                                    strictExamModeEnabled = strictExamMode,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canAdvance,
                ) {
                    Text(
                        text = stringResource(
                            if (currentStep < WizardStepCount - 1) R.string.next else R.string.save_plan,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ObjectiveStep(
    name: String,
    onNameChanged: (String) -> Unit,
    description: String,
    onDescriptionChanged: (String) -> Unit,
    selectedStudyType: StudyType,
    onStudyTypeChanged: (StudyType) -> Unit,
) {
    Text(text = stringResource(R.string.wizard_objective_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        text = stringResource(R.string.wizard_objective_message),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = name,
        onValueChange = onNameChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = stringResource(R.string.plan_name)) },
        singleLine = true,
    )
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = stringResource(R.string.plan_description)) },
        minLines = 3,
    )
    Text(text = stringResource(R.string.study_type), style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StudyType.entries.forEach { studyType ->
            FilterChip(
                selected = selectedStudyType == studyType,
                onClick = { onStudyTypeChanged(studyType) },
                label = { Text(text = stringResource(studyTypeLabel(studyType))) },
            )
        }
    }
}

@Composable
private fun TargetStep(
    examDate: String,
    onExamDateChanged: (String) -> Unit,
    examDateValid: Boolean,
    weeklyGoal: String,
    onWeeklyGoalChanged: (String) -> Unit,
    weeklyGoalValid: Boolean,
) {
    Text(text = stringResource(R.string.wizard_target_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        text = stringResource(R.string.wizard_target_message),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = examDate,
        onValueChange = onExamDateChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = stringResource(R.string.exam_date_optional)) },
        supportingText = {
            Text(text = stringResource(if (examDateValid) R.string.iso_date_hint else R.string.invalid_exam_date))
        },
        isError = !examDateValid,
        singleLine = true,
    )
    OutlinedTextField(
        value = weeklyGoal,
        onValueChange = onWeeklyGoalChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = stringResource(R.string.weekly_goal)) },
        supportingText = { Text(text = stringResource(R.string.weekly_goal_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = !weeklyGoalValid,
        singleLine = true,
    )
}

@Composable
private fun PreferencesStep(
    preferredSession: String,
    onPreferredSessionChanged: (String) -> Unit,
    preferredSessionValid: Boolean,
    adaptivePlanning: Boolean,
    onAdaptivePlanningChanged: (Boolean) -> Unit,
    reviewNotifications: Boolean,
    onReviewNotificationsChanged: (Boolean) -> Unit,
    strictExamMode: Boolean,
    onStrictExamModeChanged: (Boolean) -> Unit,
) {
    Text(text = stringResource(R.string.wizard_preferences_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        text = stringResource(R.string.wizard_preferences_message),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = preferredSession,
        onValueChange = onPreferredSessionChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = stringResource(R.string.preferred_session_minutes)) },
        supportingText = { Text(text = stringResource(R.string.preferred_session_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = !preferredSessionValid,
        singleLine = true,
    )
    PreferenceSwitch(
        title = stringResource(R.string.adaptive_planning),
        description = stringResource(R.string.adaptive_planning_description),
        checked = adaptivePlanning,
        onCheckedChange = onAdaptivePlanningChanged,
    )
    PreferenceSwitch(
        title = stringResource(R.string.review_notifications),
        description = stringResource(R.string.review_notifications_description),
        checked = reviewNotifications,
        onCheckedChange = onReviewNotificationsChanged,
    )
    PreferenceSwitch(
        title = stringResource(R.string.strict_exam_mode),
        description = stringResource(R.string.strict_exam_mode_description),
        checked = strictExamMode,
        onCheckedChange = onStrictExamModeChanged,
    )
}

@Composable
internal fun PreferenceSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

internal fun isValidIsoDateOrBlank(value: String): Boolean {
    if (value.isBlank()) return true
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    val position = ParsePosition(0)
    return formatter.parse(value, position) != null && position.index == value.length
}

@StringRes
internal fun studyTypeLabel(type: StudyType): Int = when (type) {
    StudyType.PUBLIC_EXAMINATION -> R.string.study_type_public_examination
    StudyType.CERTIFICATION -> R.string.study_type_certification
    StudyType.UNIVERSITY -> R.string.study_type_university
    StudyType.ENTRANCE_EXAMINATION -> R.string.study_type_entrance_examination
    StudyType.FREE_STUDY -> R.string.study_type_free_study
}
