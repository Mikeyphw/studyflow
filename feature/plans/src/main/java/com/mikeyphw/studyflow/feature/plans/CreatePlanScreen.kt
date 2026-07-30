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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.model.StudyType

private const val DefaultWeeklyGoal = 20

data class CreatePlanForm(
    val name: String,
    val description: String,
    val studyType: StudyType,
    val examDateIso: String,
    val weeklyGoalHours: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(
    onBack: () -> Unit,
    onCreate: (CreatePlanForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var examDate by rememberSaveable { mutableStateOf("") }
    var weeklyGoal by rememberSaveable { mutableStateOf(DefaultWeeklyGoal.toString()) }
    var studyTypeName by rememberSaveable { mutableStateOf(StudyType.PUBLIC_EXAMINATION.name) }
    val selectedStudyType = StudyType.valueOf(studyTypeName)
    val weeklyGoalValue = weeklyGoal.toIntOrNull()
    val isValid = name.isNotBlank() && weeklyGoalValue != null && weeklyGoalValue in 1..100

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.create_plan_title)) },
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
                text = stringResource(R.string.create_plan_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.plan_name)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.plan_description)) },
                minLines = 3,
            )
            Text(
                text = stringResource(R.string.study_type),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudyType.entries.forEach { studyType ->
                    FilterChip(
                        selected = selectedStudyType == studyType,
                        onClick = { studyTypeName = studyType.name },
                        label = { Text(text = stringResource(studyTypeLabel(studyType))) },
                    )
                }
            }
            OutlinedTextField(
                value = examDate,
                onValueChange = { examDate = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.exam_date_optional)) },
                supportingText = { Text(text = stringResource(R.string.iso_date_hint)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = weeklyGoal,
                onValueChange = { weeklyGoal = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.weekly_goal)) },
                supportingText = { Text(text = stringResource(R.string.weekly_goal_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = weeklyGoalValue == null || weeklyGoalValue !in 1..100,
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        onCreate(
                            CreatePlanForm(
                                name = name,
                                description = description,
                                studyType = selectedStudyType,
                                examDateIso = examDate,
                                weeklyGoalHours = requireNotNull(weeklyGoalValue),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isValid,
                ) {
                    Text(text = stringResource(R.string.create_plan))
                }
            }
        }
    }
}

@StringRes
private fun studyTypeLabel(type: StudyType): Int = when (type) {
    StudyType.PUBLIC_EXAMINATION -> R.string.study_type_public_examination
    StudyType.CERTIFICATION -> R.string.study_type_certification
    StudyType.UNIVERSITY -> R.string.study_type_university
    StudyType.ENTRANCE_EXAMINATION -> R.string.study_type_entrance_examination
    StudyType.FREE_STUDY -> R.string.study_type_free_study
}
