package com.mikeyphw.studyflow.feature.plans

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.ExamStageType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyType

/** UI-only edit state. Canonical persistence stays in StudyTarget and ExamStage. */
data class PublicExamStageForm(
    val id: ExamStageId?,
    val name: String,
    val type: ExamStageType,
    val scheduledDateIso: String,
    val weight: String,
    val minimumScorePercent: String,
)

data class PublicExamProfileForm(
    val organization: String,
    val role: String,
    val examBoard: String,
    val noticeUrl: String,
    val confirmedExamDateIso: String,
    val expectedExamDateIso: String,
    val openings: String,
    val estimatedCutoffPercent: String,
    val stages: List<PublicExamStageForm>,
)

internal fun shouldShowPublicExamProfile(type: StudyType): Boolean = type == StudyType.PUBLIC_EXAMINATION

internal fun publicExamProfileForm(
    target: StudyTarget?,
    stages: List<ExamStage>,
): PublicExamProfileForm = PublicExamProfileForm(
    organization = target?.organization.orEmpty(),
    role = target?.role.orEmpty(),
    examBoard = target?.examBoard.orEmpty(),
    noticeUrl = target?.noticeUrl.orEmpty(),
    confirmedExamDateIso = target?.confirmedExamDateIso.orEmpty(),
    expectedExamDateIso = target?.expectedExamDateIso.orEmpty(),
    openings = target?.openings?.toString().orEmpty(),
    estimatedCutoffPercent = target?.estimatedCutoffPercent?.toString().orEmpty(),
    stages = stages.sortedBy(ExamStage::position).map { stage ->
        PublicExamStageForm(
            id = stage.id,
            name = stage.name,
            type = stage.type,
            scheduledDateIso = stage.scheduledDateIso.orEmpty(),
            weight = stage.weight.toString(),
            minimumScorePercent = stage.minimumScorePercent?.toString().orEmpty(),
        )
    },
)

internal fun isValidPublicExamProfileForm(form: PublicExamProfileForm): Boolean {
    if (form.organization.isBlank() || form.role.isBlank() || form.examBoard.isBlank()) return false
    if (!isValidIsoDateOrBlank(form.confirmedExamDateIso)) return false
    if (!isValidIsoDateOrBlank(form.expectedExamDateIso)) return false
    if (form.openings.isNotBlank() && (form.openings.toIntOrNull()?.let { it >= 0 } != true)) return false
    if (
        form.estimatedCutoffPercent.isNotBlank() &&
        (form.estimatedCutoffPercent.toIntOrNull()?.let { it in 0..100 } != true)
    ) return false
    return form.stages.all(::isValidPublicExamStageForm)
}

private fun isValidPublicExamStageForm(stage: PublicExamStageForm): Boolean {
    val weight = stage.weight.toDoubleOrNull()
    if (stage.name.isBlank() || weight == null || !weight.isFinite() || weight < 0.0) return false
    if (!isValidIsoDateOrBlank(stage.scheduledDateIso)) return false
    return stage.minimumScorePercent.isBlank() ||
        (stage.minimumScorePercent.toIntOrNull()?.let { it in 0..100 } == true)
}

@Composable
internal fun PublicExamProfileSection(
    plan: StudyPlan,
    target: StudyTarget?,
    examStages: List<ExamStage>,
    onSave: (PublicExamProfileForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stageStamp = examStages.joinToString(separator = "|") { stage ->
        listOf(
            stage.id.value,
            stage.name,
            stage.type.name,
            stage.scheduledDateIso,
            stage.weight,
            stage.minimumScorePercent,
        ).joinToString(":")
    }
    var form by remember(plan.id.value, target?.updatedAtEpochMillis, stageStamp) {
        mutableStateOf(publicExamProfileForm(target, examStages))
    }
    val valid = isValidPublicExamProfileForm(form)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.public_exam_profile_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.public_exam_profile_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (target == null) {
                Text(
                    text = stringResource(R.string.public_exam_profile_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (
                form.confirmedExamDateIso.isBlank() &&
                form.expectedExamDateIso.isBlank() &&
                plan.examDateIso == null
            ) {
                Text(
                    text = stringResource(R.string.public_exam_date_unknown),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PublicExamTextField(
                value = form.organization,
                onValueChange = { form = form.copy(organization = it) },
                label = R.string.public_exam_organization,
            )
            PublicExamTextField(
                value = form.role,
                onValueChange = { form = form.copy(role = it) },
                label = R.string.public_exam_role,
            )
            PublicExamTextField(
                value = form.examBoard,
                onValueChange = { form = form.copy(examBoard = it) },
                label = R.string.public_exam_board,
            )
            PublicExamTextField(
                value = form.noticeUrl,
                onValueChange = { form = form.copy(noticeUrl = it) },
                label = R.string.public_exam_notice_reference,
            )
            PublicExamDateField(
                value = form.confirmedExamDateIso,
                onValueChange = { form = form.copy(confirmedExamDateIso = it) },
                label = R.string.public_exam_confirmed_date,
            )
            PublicExamDateField(
                value = form.expectedExamDateIso,
                onValueChange = { form = form.copy(expectedExamDateIso = it) },
                label = R.string.public_exam_expected_date,
            )
            PublicExamNumberField(
                value = form.openings,
                onValueChange = { form = form.copy(openings = it) },
                label = R.string.public_exam_openings,
            )
            PublicExamNumberField(
                value = form.estimatedCutoffPercent,
                onValueChange = { form = form.copy(estimatedCutoffPercent = it) },
                label = R.string.public_exam_estimated_cutoff,
            )

            HorizontalDivider()
            Text(
                text = stringResource(R.string.public_exam_stages_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (form.stages.isEmpty()) {
                Text(
                    text = stringResource(R.string.public_exam_stages_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            form.stages.forEachIndexed { index, stage ->
                PublicExamStageEditor(
                    stage = stage,
                    index = index,
                    onChange = { updated ->
                        form = form.copy(
                            stages = form.stages.toMutableList().also { stages -> stages[index] = updated },
                        )
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    form = form.copy(
                        stages = form.stages + PublicExamStageForm(
                            id = null,
                            name = "",
                            type = ExamStageType.OBJECTIVE,
                            scheduledDateIso = "",
                            weight = "1.0",
                            minimumScorePercent = "",
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.public_exam_add_stage))
            }
            Button(onClick = { onSave(form) }, enabled = valid) {
                Text(stringResource(R.string.public_exam_save_profile))
            }
            if (!valid) {
                Text(
                    text = stringResource(R.string.public_exam_validation_hint),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PublicExamStageEditor(
    stage: PublicExamStageForm,
    index: Int,
    onChange: (PublicExamStageForm) -> Unit,
) {
    var typeMenuExpanded by remember(index, stage.id?.value) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.public_exam_stage_number, index + 1),
                style = MaterialTheme.typography.labelLarge,
            )
            PublicExamTextField(
                value = stage.name,
                onValueChange = { onChange(stage.copy(name = it)) },
                label = R.string.public_exam_stage_name,
            )
            Column {
                Text(
                    text = stringResource(R.string.public_exam_stage_type),
                    style = MaterialTheme.typography.labelMedium,
                )
                OutlinedButton(onClick = { typeMenuExpanded = true }) {
                    Text(stringResource(stage.type.titleRes()))
                }
                DropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                ) {
                    ExamStageType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(stringResource(type.titleRes())) },
                            onClick = {
                                onChange(stage.copy(type = type))
                                typeMenuExpanded = false
                            },
                        )
                    }
                }
            }
            PublicExamDateField(
                value = stage.scheduledDateIso,
                onValueChange = { onChange(stage.copy(scheduledDateIso = it)) },
                label = R.string.public_exam_stage_date,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = stage.weight,
                    onValueChange = { value -> onChange(stage.copy(weight = value.filterNumericDecimal())) },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.public_exam_stage_weight)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = stage.minimumScorePercent,
                    onValueChange = { value ->
                        onChange(stage.copy(minimumScorePercent = value.filter(Char::isDigit)))
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.public_exam_stage_minimum)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun PublicExamTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        singleLine = true,
    )
}

@Composable
private fun PublicExamDateField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(10)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        supportingText = { Text(stringResource(R.string.public_exam_date_format_hint)) },
        isError = !isValidIsoDateOrBlank(value),
        singleLine = true,
    )
}

@Composable
private fun PublicExamNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

private fun String.filterNumericDecimal(): String {
    var dotSeen = false
    return filter { char ->
        when {
            char.isDigit() -> true
            char == '.' && !dotSeen -> {
                dotSeen = true
                true
            }
            else -> false
        }
    }
}

@StringRes
private fun ExamStageType.titleRes(): Int = when (this) {
    ExamStageType.OBJECTIVE -> R.string.public_exam_stage_type_objective
    ExamStageType.WRITTEN -> R.string.public_exam_stage_type_written
    ExamStageType.ESSAY -> R.string.public_exam_stage_type_essay
    ExamStageType.PRACTICAL -> R.string.public_exam_stage_type_practical
    ExamStageType.PHYSICAL -> R.string.public_exam_stage_type_physical
    ExamStageType.ORAL -> R.string.public_exam_stage_type_oral
    ExamStageType.TITLES -> R.string.public_exam_stage_type_titles
    ExamStageType.OTHER -> R.string.public_exam_stage_type_other
}
