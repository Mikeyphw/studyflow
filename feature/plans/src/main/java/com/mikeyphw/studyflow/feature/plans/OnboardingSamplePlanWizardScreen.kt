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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanBlueprint
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanTemplate
import com.mikeyphw.studyflow.core.domain.OnboardingSampleSubjectKind
import com.mikeyphw.studyflow.core.domain.OnboardingSampleTopicKind
import com.mikeyphw.studyflow.core.domain.StudyOnboardingSamplePlanWizard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSamplePlanWizardScreen(
    onBack: () -> Unit,
    onCreateSample: (OnboardingSamplePlanTemplate) -> Unit,
    creating: Boolean = false,
    creationFailed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val choices = StudyOnboardingSamplePlanWizard.choices()
    var selectedTemplateName by rememberSaveable {
        mutableStateOf(OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK.name)
    }
    val selectedTemplate = enumValueOrDefault(
        selectedTemplateName,
        OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK,
    )
    val selectedChoice = choices.first { choice -> choice.template == selectedTemplate }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.onboarding_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OnboardingIntro()
            }
            items(choices, key = { choice -> choice.template.name }) { choice ->
                OnboardingTemplateCard(
                    choice = choice,
                    selected = choice.template == selectedTemplate,
                    onSelect = { selectedTemplateName = choice.template.name },
                )
            }
            item {
                OnboardingPreview(choice = selectedChoice)
            }
            if (creationFailed) {
                item {
                    Text(
                        text = stringResource(R.string.onboarding_create_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !creating,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { onCreateSample(selectedChoice.template) },
                        enabled = !creating,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.onboarding_create_sample))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.onboarding_heading),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.onboarding_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingTemplateCard(
    choice: OnboardingSamplePlanBlueprint,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ElevatedCard(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.School, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(choice.template.titleRes()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(choice.template.descriptionRes()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(
                    selected = selected,
                    onClick = onSelect,
                    label = {
                        Text(
                            stringResource(
                                if (selected) R.string.onboarding_selected else R.string.onboarding_select,
                            ),
                        )
                    },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OnboardingMetric(
                    label = pluralStringResource(
                        R.plurals.onboarding_subjects,
                        choice.subjectCount,
                        choice.subjectCount,
                    ),
                )
                OnboardingMetric(
                    label = pluralStringResource(
                        R.plurals.onboarding_topics,
                        choice.topicCount,
                        choice.topicCount,
                    ),
                )
                OnboardingMetric(
                    label = pluralStringResource(
                        R.plurals.weekly_goal_hours,
                        choice.weeklyGoalHours,
                        choice.weeklyGoalHours,
                    ),
                )
            }
        }
    }
}

@Composable
private fun OnboardingMetric(label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun OnboardingPreview(choice: OnboardingSamplePlanBlueprint) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_preview_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.onboarding_preview_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OnboardingWorkloadPreview(choice = choice)
            choice.subjects.forEach { subject ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(subject.kind.titleRes()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    subject.topics.take(3).forEach { topic ->
                        val topicTitle = stringResource(topic.kind.titleRes())
                        Text(
                            text = stringResource(R.string.onboarding_topic_bullet, topicTitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
            OnboardingSchedulePreview(choice = choice)
            Text(
                text = stringResource(R.string.onboarding_local_only_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}


@Composable
private fun OnboardingWorkloadPreview(choice: OnboardingSamplePlanBlueprint) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.onboarding_workload_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                R.string.onboarding_workload_subjects_topics,
                choice.subjectCount,
                choice.topicCount,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.onboarding_workload_minutes_week,
                choice.weeklyScheduleMinutes,
                choice.preferredSessionMinutes,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingSchedulePreview(choice: OnboardingSamplePlanBlueprint) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null)
            Text(
                text = pluralStringResource(
                    R.plurals.onboarding_schedule_blocks,
                    choice.scheduleRules.size,
                    choice.scheduleRules.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = stringResource(R.string.onboarding_schedule_title),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        choice.scheduleRules.forEach { rule ->
            val subjectLabel = rule.subjectKind?.let { subject ->
                stringResource(subject.titleRes())
            } ?: stringResource(R.string.onboarding_schedule_flexible)
            Text(
                text = stringResource(
                    R.string.onboarding_schedule_block,
                    stringResource(dayOfWeekRes(rule.dayOfWeek)),
                    formatMinuteOfDay(rule.startMinuteOfDay),
                    rule.durationMinutes,
                    subjectLabel,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
private fun dayOfWeekRes(dayOfWeek: Int): Int = when (dayOfWeek) {
    1 -> R.string.onboarding_day_monday
    2 -> R.string.onboarding_day_tuesday
    3 -> R.string.onboarding_day_wednesday
    4 -> R.string.onboarding_day_thursday
    5 -> R.string.onboarding_day_friday
    6 -> R.string.onboarding_day_saturday
    else -> R.string.onboarding_day_sunday
}


@StringRes
fun OnboardingSamplePlanTemplate.titleRes(): Int = when (this) {
    OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK -> R.string.onboarding_template_public_exam_title
    OnboardingSamplePlanTemplate.UNIVERSITY_SEMESTER -> R.string.onboarding_template_university_title
    OnboardingSamplePlanTemplate.CERTIFICATION_BOOTCAMP -> R.string.onboarding_template_certification_title
    OnboardingSamplePlanTemplate.FREE_STUDY_HABIT -> R.string.onboarding_template_free_study_title
}

@StringRes
fun OnboardingSamplePlanTemplate.descriptionRes(): Int = when (this) {
    OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK -> R.string.onboarding_template_public_exam_description
    OnboardingSamplePlanTemplate.UNIVERSITY_SEMESTER -> R.string.onboarding_template_university_description
    OnboardingSamplePlanTemplate.CERTIFICATION_BOOTCAMP -> R.string.onboarding_template_certification_description
    OnboardingSamplePlanTemplate.FREE_STUDY_HABIT -> R.string.onboarding_template_free_study_description
}

@StringRes
fun OnboardingSamplePlanTemplate.planNameRes(): Int = when (this) {
    OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK -> R.string.onboarding_template_public_exam_plan_name
    OnboardingSamplePlanTemplate.UNIVERSITY_SEMESTER -> R.string.onboarding_template_university_plan_name
    OnboardingSamplePlanTemplate.CERTIFICATION_BOOTCAMP -> R.string.onboarding_template_certification_plan_name
    OnboardingSamplePlanTemplate.FREE_STUDY_HABIT -> R.string.onboarding_template_free_study_plan_name
}

@StringRes
fun OnboardingSamplePlanTemplate.planDescriptionRes(): Int = when (this) {
    OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK -> R.string.onboarding_template_public_exam_plan_description
    OnboardingSamplePlanTemplate.UNIVERSITY_SEMESTER -> R.string.onboarding_template_university_plan_description
    OnboardingSamplePlanTemplate.CERTIFICATION_BOOTCAMP -> R.string.onboarding_template_certification_plan_description
    OnboardingSamplePlanTemplate.FREE_STUDY_HABIT -> R.string.onboarding_template_free_study_plan_description
}

@StringRes
fun OnboardingSampleSubjectKind.titleRes(): Int = when (this) {
    OnboardingSampleSubjectKind.CORE_THEORY -> R.string.onboarding_subject_core_theory
    OnboardingSampleSubjectKind.QUESTIONS_AND_CASES -> R.string.onboarding_subject_questions_cases
    OnboardingSampleSubjectKind.REVIEW_AND_FLASHCARDS -> R.string.onboarding_subject_review_flashcards
    OnboardingSampleSubjectKind.COURSE_FOUNDATIONS -> R.string.onboarding_subject_course_foundations
    OnboardingSampleSubjectKind.WEEKLY_ASSIGNMENTS -> R.string.onboarding_subject_weekly_assignments
    OnboardingSampleSubjectKind.LAB_AND_PROJECTS -> R.string.onboarding_subject_lab_projects
    OnboardingSampleSubjectKind.EXAM_OBJECTIVES -> R.string.onboarding_subject_exam_objectives
    OnboardingSampleSubjectKind.PRACTICAL_REHEARSAL -> R.string.onboarding_subject_practical_rehearsal
    OnboardingSampleSubjectKind.FREE_READING -> R.string.onboarding_subject_free_reading
    OnboardingSampleSubjectKind.CREATIVE_PRACTICE -> R.string.onboarding_subject_creative_practice
}

@StringRes
fun OnboardingSampleTopicKind.titleRes(): Int = when (this) {
    OnboardingSampleTopicKind.FOUNDATIONS -> R.string.onboarding_topic_foundations
    OnboardingSampleTopicKind.HIGH_INCIDENCE_TOPICS -> R.string.onboarding_topic_high_incidence
    OnboardingSampleTopicKind.LEGAL_OR_CONCEPTUAL_DETAILS -> R.string.onboarding_topic_details
    OnboardingSampleTopicKind.QUESTION_BANK -> R.string.onboarding_topic_question_bank
    OnboardingSampleTopicKind.WRONG_ANSWER_LOG -> R.string.onboarding_topic_wrong_answers
    OnboardingSampleTopicKind.SPACED_REVIEW -> R.string.onboarding_topic_spaced_review
    OnboardingSampleTopicKind.FLASHCARDS -> R.string.onboarding_topic_flashcards
    OnboardingSampleTopicKind.SYLLABUS_MAPPING -> R.string.onboarding_topic_syllabus_mapping
    OnboardingSampleTopicKind.LECTURE_NOTES -> R.string.onboarding_topic_lecture_notes
    OnboardingSampleTopicKind.EXERCISE_LISTS -> R.string.onboarding_topic_exercise_lists
    OnboardingSampleTopicKind.SEMINAR_PREPARATION -> R.string.onboarding_topic_seminar
    OnboardingSampleTopicKind.LAB_NOTES -> R.string.onboarding_topic_lab_notes
    OnboardingSampleTopicKind.PROJECT_MILESTONES -> R.string.onboarding_topic_project_milestones
    OnboardingSampleTopicKind.OFFICIAL_OBJECTIVES -> R.string.onboarding_topic_official_objectives
    OnboardingSampleTopicKind.PRACTICE_TEST -> R.string.onboarding_topic_practice_test
    OnboardingSampleTopicKind.PRACTICAL_CHECKLIST -> R.string.onboarding_topic_practical_checklist
    OnboardingSampleTopicKind.READING_BACKLOG -> R.string.onboarding_topic_reading_backlog
    OnboardingSampleTopicKind.SUMMARY_NOTES -> R.string.onboarding_topic_summary_notes
    OnboardingSampleTopicKind.PRACTICE_ROUTINE -> R.string.onboarding_topic_practice_routine
    OnboardingSampleTopicKind.REFLECTION_LOG -> R.string.onboarding_topic_reflection_log
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, defaultValue: T): T =
    enumValues<T>().firstOrNull { value -> value.name == name } ?: defaultValue
