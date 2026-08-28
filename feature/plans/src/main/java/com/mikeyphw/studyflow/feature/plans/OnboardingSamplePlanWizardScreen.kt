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
import androidx.compose.material3.OutlinedTextField
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
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanInput
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanTemplate
import com.mikeyphw.studyflow.core.domain.OnboardingSampleSubjectKind
import com.mikeyphw.studyflow.core.domain.OnboardingSampleTopicKind
import com.mikeyphw.studyflow.core.domain.PublicExamCyclePreference
import com.mikeyphw.studyflow.core.domain.PublicExamGoalPreset
import com.mikeyphw.studyflow.core.domain.PublicExamOnboardingInput
import com.mikeyphw.studyflow.core.domain.StudyOnboardingSamplePlanWizard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSamplePlanWizardScreen(
    onBack: () -> Unit,
    onCreateSample: (OnboardingSamplePlanInput) -> Unit,
    modifier: Modifier = Modifier,
    creating: Boolean = false,
    creationFailed: Boolean = false,
) {
    val choices = StudyOnboardingSamplePlanWizard.choices()
    var selectedTemplateName by rememberSaveable {
        mutableStateOf(OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK.name)
    }
    val selectedTemplate = enumValueOrDefault(
        selectedTemplateName,
        OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK,
    )
    val selectedBaseChoice = choices.first { choice -> choice.template == selectedTemplate }
    var organization by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("") }
    var examBoard by rememberSaveable { mutableStateOf("") }
    var noticeUrl by rememberSaveable { mutableStateOf("") }
    var examDate by rememberSaveable { mutableStateOf("") }
    var availabilityHours by rememberSaveable { mutableStateOf("18") }
    var cyclePreferenceName by rememberSaveable { mutableStateOf(PublicExamCyclePreference.STUDY_CYCLE.name) }
    var reviewDay1 by rememberSaveable { mutableStateOf("1") }
    var reviewDay2 by rememberSaveable { mutableStateOf("7") }
    var reviewDay3 by rememberSaveable { mutableStateOf("30") }
    var weeklyQuestions by rememberSaveable { mutableStateOf("200") }
    var accuracyGoal by rememberSaveable { mutableStateOf("80") }
    var monthlyMocks by rememberSaveable { mutableStateOf("2") }
    val publicExamInput = runCatching {
        PublicExamOnboardingInput(
            organization = organization,
            role = role,
            examBoard = examBoard,
            noticeUrl = noticeUrl,
            examDateIso = examDate,
            weeklyAvailabilityHours = availabilityHours.toInt(),
            cyclePreference = enumValueOrDefault(
                cyclePreferenceName,
                PublicExamCyclePreference.STUDY_CYCLE,
            ),
            reviewDays = listOf(reviewDay1.toInt(), reviewDay2.toInt(), reviewDay3.toInt()),
            weeklyQuestionGoal = weeklyQuestions.toInt(),
            accuracyGoalPercent = accuracyGoal.toInt(),
            monthlyMockGoal = monthlyMocks.toInt(),
        )
    }.getOrNull()
    val selectedChoice = if (selectedTemplate == OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK) {
        publicExamInput?.let { input ->
            StudyOnboardingSamplePlanWizard.build(
                OnboardingSamplePlanInput(template = selectedTemplate, publicExam = input),
            )
        } ?: selectedBaseChoice
    } else {
        selectedBaseChoice
    }

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
            if (selectedTemplate == OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK) {
                item {
                    PublicExamOnboardingFields(
                        organization = organization,
                        onOrganizationChange = { organization = it },
                        role = role,
                        onRoleChange = { role = it },
                        examBoard = examBoard,
                        onExamBoardChange = { examBoard = it },
                        noticeUrl = noticeUrl,
                        onNoticeUrlChange = { noticeUrl = it },
                        examDate = examDate,
                        onExamDateChange = { examDate = it },
                        availabilityHours = availabilityHours,
                        onAvailabilityHoursChange = { availabilityHours = it },
                        cyclePreferenceName = cyclePreferenceName,
                        onCyclePreferenceChange = { cyclePreferenceName = it.name },
                        reviewDay1 = reviewDay1,
                        onReviewDay1Change = { reviewDay1 = it },
                        reviewDay2 = reviewDay2,
                        onReviewDay2Change = { reviewDay2 = it },
                        reviewDay3 = reviewDay3,
                        onReviewDay3Change = { reviewDay3 = it },
                        weeklyQuestions = weeklyQuestions,
                        onWeeklyQuestionsChange = { weeklyQuestions = it },
                        accuracyGoal = accuracyGoal,
                        onAccuracyGoalChange = { accuracyGoal = it },
                        monthlyMocks = monthlyMocks,
                        onMonthlyMocksChange = { monthlyMocks = it },
                        valid = publicExamInput != null,
                    )
                }
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
                        onClick = {
                            onCreateSample(
                                OnboardingSamplePlanInput(
                                    template = selectedChoice.template,
                                    publicExam = publicExamInput ?: PublicExamOnboardingInput(),
                                ),
                            )
                        },
                        enabled = !creating && (
                            selectedChoice.template != OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK ||
                                publicExamInput != null
                            ),
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
private fun PublicExamOnboardingFields(
    organization: String,
    onOrganizationChange: (String) -> Unit,
    role: String,
    onRoleChange: (String) -> Unit,
    examBoard: String,
    onExamBoardChange: (String) -> Unit,
    noticeUrl: String,
    onNoticeUrlChange: (String) -> Unit,
    examDate: String,
    onExamDateChange: (String) -> Unit,
    availabilityHours: String,
    onAvailabilityHoursChange: (String) -> Unit,
    cyclePreferenceName: String,
    onCyclePreferenceChange: (PublicExamCyclePreference) -> Unit,
    reviewDay1: String,
    onReviewDay1Change: (String) -> Unit,
    reviewDay2: String,
    onReviewDay2Change: (String) -> Unit,
    reviewDay3: String,
    onReviewDay3Change: (String) -> Unit,
    weeklyQuestions: String,
    onWeeklyQuestionsChange: (String) -> Unit,
    accuracyGoal: String,
    onAccuracyGoalChange: (String) -> Unit,
    monthlyMocks: String,
    onMonthlyMocksChange: (String) -> Unit,
    valid: Boolean,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.onboarding_exam_setup_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.onboarding_exam_setup_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = organization,
                onValueChange = onOrganizationChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_exam_organization)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = role,
                onValueChange = onRoleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_exam_role)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = examBoard,
                onValueChange = onExamBoardChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_exam_board)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = noticeUrl,
                onValueChange = onNoticeUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_exam_notice_url)) },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = examDate,
                    onValueChange = onExamDateChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.onboarding_exam_date)) },
                    supportingText = { Text("YYYY-MM-DD") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = availabilityHours,
                    onValueChange = onAvailabilityHoursChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.onboarding_exam_availability)) },
                    singleLine = true,
                )
            }
            Text(stringResource(R.string.onboarding_cycle_preference), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PublicExamCyclePreference.entries.forEach { preference ->
                    FilterChip(
                        selected = preference.name == cyclePreferenceName,
                        onClick = { onCyclePreferenceChange(preference) },
                        label = { Text(stringResource(preference.labelRes())) },
                    )
                }
            }
            Text(stringResource(R.string.onboarding_review_policy), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    reviewDay1 to onReviewDay1Change,
                    reviewDay2 to onReviewDay2Change,
                    reviewDay3 to onReviewDay3Change,
                ).forEachIndexed { index, (value, onChange) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = onChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.onboarding_review_day, index + 1)) },
                        singleLine = true,
                    )
                }
            }
            Text(stringResource(R.string.onboarding_goal_presets), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = weeklyQuestions,
                onValueChange = onWeeklyQuestionsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_goal_questions)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = accuracyGoal,
                onValueChange = onAccuracyGoalChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_goal_accuracy)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = monthlyMocks,
                onValueChange = onMonthlyMocksChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_goal_mocks)) },
                singleLine = true,
            )
            Text(
                text = stringResource(
                    if (valid) R.string.onboarding_exam_setup_valid else R.string.onboarding_exam_setup_invalid,
                ),
                color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(R.string.onboarding_import_templates_local),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@androidx.annotation.StringRes
private fun PublicExamCyclePreference.labelRes(): Int = when (this) {
    PublicExamCyclePreference.STUDY_CYCLE -> R.string.onboarding_cycle_study_cycle
    PublicExamCyclePreference.FIXED_WEEKLY -> R.string.onboarding_cycle_fixed_weekly
    PublicExamCyclePreference.ADAPTIVE -> R.string.onboarding_cycle_adaptive
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
            if (choice.template == OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK) {
                Text(
                    text = stringResource(R.string.onboarding_public_exam_profile_entry),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                pluralStringResource(
                    R.plurals.onboarding_workload_subjects,
                    choice.subjectCount,
                    choice.subjectCount,
                ),
                pluralStringResource(
                    R.plurals.onboarding_workload_topics,
                    choice.topicCount,
                    choice.topicCount,
                ),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.onboarding_workload_minutes_week,
                choice.weeklyScheduleMinutes.toString(),
                choice.preferredSessionMinutes.toString(),
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
fun PublicExamGoalPreset.titleRes(): Int = when (this) {
    PublicExamGoalPreset.WEEKLY_STUDY_TIME -> R.string.onboarding_goal_title_study_time
    PublicExamGoalPreset.WEEKLY_QUESTIONS -> R.string.onboarding_goal_title_questions
    PublicExamGoalPreset.ACCURACY -> R.string.onboarding_goal_title_accuracy
    PublicExamGoalPreset.MONTHLY_MOCKS -> R.string.onboarding_goal_title_mocks
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
