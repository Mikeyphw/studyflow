package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.ExamStageType
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.SubjectId

enum class OnboardingSamplePlanTemplate {
    PUBLIC_EXAM_12_WEEK,
    BB_AGENTE_COMERCIAL_PRE_EDITAL,
    UNIVERSITY_SEMESTER,
    CERTIFICATION_BOOTCAMP,
    FREE_STUDY_HABIT,
}

enum class OnboardingSampleSubjectKind {
    CORE_THEORY,
    BB_PORTUGUESE,
    BB_ENGLISH,
    BB_MATHEMATICS,
    BB_FINANCIAL_MARKET_CURRENT_AFFAIRS,
    BB_FINANCIAL_MATHEMATICS,
    BB_BANKING_KNOWLEDGE,
    BB_INFORMATION_TECHNOLOGY,
    BB_SALES_NEGOTIATION,
    QUESTIONS_AND_CASES,
    REVIEW_AND_FLASHCARDS,
    COURSE_FOUNDATIONS,
    WEEKLY_ASSIGNMENTS,
    LAB_AND_PROJECTS,
    EXAM_OBJECTIVES,
    PRACTICAL_REHEARSAL,
    FREE_READING,
    CREATIVE_PRACTICE,
}

enum class OnboardingSampleTopicKind {
    FOUNDATIONS,
    HIGH_INCIDENCE_TOPICS,
    LEGAL_OR_CONCEPTUAL_DETAILS,
    QUESTION_BANK,
    WRONG_ANSWER_LOG,
    SPACED_REVIEW,
    FLASHCARDS,
    SYLLABUS_MAPPING,
    LECTURE_NOTES,
    EXERCISE_LISTS,
    SEMINAR_PREPARATION,
    LAB_NOTES,
    PROJECT_MILESTONES,
    OFFICIAL_OBJECTIVES,
    PRACTICE_TEST,
    PRACTICAL_CHECKLIST,
    READING_BACKLOG,
    SUMMARY_NOTES,
    PRACTICE_ROUTINE,
    REFLECTION_LOG,
}

enum class OnboardingSampleExamStageKind {
    OBJECTIVE,
    ESSAY,
}

data class OnboardingSampleExamStageBlueprint(
    val kind: OnboardingSampleExamStageKind,
    val type: ExamStageType,
    val weight: Double,
    val minimumScorePercent: Int?,
)

data class OnboardingSampleTopicBlueprint(
    val kind: OnboardingSampleTopicKind,
    val priority: Int,
    val difficulty: Int,
    val estimatedMinutes: Int,
)

data class OnboardingSampleSubjectBlueprint(
    val kind: OnboardingSampleSubjectKind,
    val priority: Int,
    val examWeight: Double,
    val estimatedMinutes: Int,
    val topics: List<OnboardingSampleTopicBlueprint>,
    val examStageKind: OnboardingSampleExamStageKind? = null,
)

data class OnboardingScheduleRuleBlueprint(
    val dayOfWeek: Int,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val subjectKind: OnboardingSampleSubjectKind?,
)

data class OnboardingSamplePlanBlueprint(
    val template: OnboardingSamplePlanTemplate,
    val studyType: StudyType,
    val weeklyGoalHours: Int,
    val preferredSessionMinutes: Int,
    val adaptivePlanningEnabled: Boolean,
    val reviewNotificationsEnabled: Boolean,
    val strictExamModeEnabled: Boolean,
    val localOnly: Boolean,
    val previewFirst: Boolean,
    val userInitiatedOnly: Boolean,
    val subjects: List<OnboardingSampleSubjectBlueprint>,
    val scheduleRules: List<OnboardingScheduleRuleBlueprint>,
    val examStages: List<OnboardingSampleExamStageBlueprint> = emptyList(),
    val publicExam: PublicExamOnboardingBlueprint? = null,
) {
    val subjectCount: Int = subjects.size
    val topicCount: Int = subjects.sumOf { subject -> subject.topics.size }
    val weeklyScheduleMinutes: Int = scheduleRules.sumOf { rule -> rule.durationMinutes }
}

data class OnboardingSamplePlanInput(
    val template: OnboardingSamplePlanTemplate,
    val publicExam: PublicExamOnboardingInput = PublicExamOnboardingInput(),
    val localOnly: Boolean = true,
    val previewFirst: Boolean = true,
    val userInitiatedOnly: Boolean = true,
)

object StudyOnboardingSamplePlanWizard {
    fun choices(): List<OnboardingSamplePlanBlueprint> = OnboardingSamplePlanTemplate.entries.map { template ->
        build(OnboardingSamplePlanInput(template = template))
    }

    fun build(input: OnboardingSamplePlanInput): OnboardingSamplePlanBlueprint {
        require(input.localOnly) { "Onboarding samples must stay local to this device." }
        require(input.previewFirst) { "Onboarding samples must be previewed before creation." }
        require(input.userInitiatedOnly) { "Onboarding samples must be user initiated." }
        return when (input.template) {
            OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK -> publicExamPlan(input.publicExam)
            OnboardingSamplePlanTemplate.BB_AGENTE_COMERCIAL_PRE_EDITAL -> bbAgenteComercialPlan()
            OnboardingSamplePlanTemplate.UNIVERSITY_SEMESTER -> universityPlan()
            OnboardingSamplePlanTemplate.CERTIFICATION_BOOTCAMP -> certificationPlan()
            OnboardingSamplePlanTemplate.FREE_STUDY_HABIT -> freeStudyPlan()
        }
    }

    fun buildSettings(planId: StudyPlanId, blueprint: OnboardingSamplePlanBlueprint): StudyPlanSettings {
        val defaults = StudyPlanSettings.defaults(planId)
        val configurationJson = blueprint.publicExam?.let { profile ->
            PublicExamReviewPolicyCodec.encode(defaults.configurationJson, profile.reviewPolicy)
        } ?: defaults.configurationJson
        return defaults.copy(
            adaptivePlanningEnabled = blueprint.adaptivePlanningEnabled,
            reviewNotificationsEnabled = blueprint.reviewNotificationsEnabled,
            strictExamModeEnabled = blueprint.strictExamModeEnabled,
            preferredSessionMinutes = blueprint.preferredSessionMinutes,
            configurationJson = configurationJson,
        )
    }

    fun buildScheduleRules(
        planId: StudyPlanId,
        blueprint: OnboardingSamplePlanBlueprint,
        subjectIdsByKind: Map<OnboardingSampleSubjectKind, SubjectId>,
        idFactory: () -> String,
    ): List<ScheduleRule> = blueprint.scheduleRules.mapIndexed { index, rule ->
        ScheduleRule(
            id = ScheduleRuleId(idFactory()),
            studyPlanId = planId,
            subjectId = rule.subjectKind?.let(subjectIdsByKind::get),
            type = ScheduleRuleType.RECURRING_BLOCK,
            dayOfWeek = rule.dayOfWeek,
            startMinuteOfDay = rule.startMinuteOfDay,
            durationMinutes = rule.durationMinutes,
            recurrenceRule = "FREQ=WEEKLY",
            locked = false,
            enabled = true,
            position = index,
        )
    }

    fun buildExamStages(
        planId: StudyPlanId,
        targetId: StudyTargetId,
        blueprint: OnboardingSamplePlanBlueprint,
        stageNames: Map<OnboardingSampleExamStageKind, String>,
        idFactory: () -> String,
    ): Pair<List<ExamStage>, Map<OnboardingSampleExamStageKind, ExamStageId>> {
        val ids = blueprint.examStages.associate { stage -> stage.kind to ExamStageId(idFactory()) }
        val stages = blueprint.examStages.mapIndexed { index, stage ->
            ExamStage(
                id = ids.getValue(stage.kind),
                studyPlanId = planId,
                studyTargetId = targetId,
                name = stageNames.getValue(stage.kind),
                type = stage.type,
                scheduledDateIso = null,
                weight = stage.weight,
                minimumScorePercent = stage.minimumScorePercent,
                position = index,
            )
        }
        return stages to ids
    }

    private fun publicExamPlan(input: PublicExamOnboardingInput): OnboardingSamplePlanBlueprint {
        val profile = PublicExamOnboardingBuilder.build(input)
        return OnboardingSamplePlanBlueprint(
        template = OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK,
        studyType = StudyType.PUBLIC_EXAMINATION,
        weeklyGoalHours = profile.weeklyAvailabilityHours,
        preferredSessionMinutes = 50,
        adaptivePlanningEnabled = true,
        reviewNotificationsEnabled = true,
        strictExamModeEnabled = true,
        localOnly = true,
        previewFirst = true,
        userInitiatedOnly = true,
        subjects = listOf(
            subject(OnboardingSampleSubjectKind.CORE_THEORY, 5, 4.0, 1_200) {
                topic(OnboardingSampleTopicKind.FOUNDATIONS, 5, 3, 300)
                topic(OnboardingSampleTopicKind.HIGH_INCIDENCE_TOPICS, 5, 4, 480)
                topic(OnboardingSampleTopicKind.LEGAL_OR_CONCEPTUAL_DETAILS, 4, 4, 420)
            },
            subject(OnboardingSampleSubjectKind.QUESTIONS_AND_CASES, 5, 3.0, 900) {
                topic(OnboardingSampleTopicKind.QUESTION_BANK, 5, 4, 520)
                topic(OnboardingSampleTopicKind.WRONG_ANSWER_LOG, 5, 5, 380)
            },
            subject(OnboardingSampleSubjectKind.REVIEW_AND_FLASHCARDS, 4, 2.0, 720) {
                topic(OnboardingSampleTopicKind.SPACED_REVIEW, 5, 3, 420)
                topic(OnboardingSampleTopicKind.FLASHCARDS, 4, 3, 300)
            },
        ),
        scheduleRules = listOf(
            rule(1, 19 * 60, 90, OnboardingSampleSubjectKind.CORE_THEORY),
            rule(3, 19 * 60, 90, OnboardingSampleSubjectKind.QUESTIONS_AND_CASES),
            rule(5, 19 * 60, 75, OnboardingSampleSubjectKind.REVIEW_AND_FLASHCARDS),
            rule(6, 9 * 60, 120, OnboardingSampleSubjectKind.QUESTIONS_AND_CASES),
        ),
        publicExam = profile,
    )
    }

    private fun bbAgenteComercialPlan(): OnboardingSamplePlanBlueprint {
        val profile = PublicExamOnboardingBuilder.build(
            PublicExamOnboardingInput(
                organization = "Banco do Brasil",
                role = "Escriturário — Agente Comercial",
                examBoard = "Fundação Cesgranrio",
                noticeUrl = BancoDoBrasilAgenteComercialPreset.ReferenceNoticeUrl,
                examDateIso = "",
                editalImportChoice = PublicExamEditalImportChoice.LATER,
                weeklyAvailabilityHours = 19,
                cyclePreference = PublicExamCyclePreference.FIXED_WEEKLY,
                reviewDays = listOf(1, 7, 30),
                weeklyQuestionGoal = 0,
                accuracyGoalPercent = 80,
                monthlyMockGoal = 0,
                coverageGoalDateIso = "",
            ),
        )
        val objective = OnboardingSampleExamStageKind.OBJECTIVE
        val phasedProfile = profile.copy(phasePlan = BancoDoBrasilStudyPhasePreset.plan())
        return OnboardingSamplePlanBlueprint(
            template = OnboardingSamplePlanTemplate.BB_AGENTE_COMERCIAL_PRE_EDITAL,
            studyType = StudyType.PUBLIC_EXAMINATION,
            weeklyGoalHours = 19,
            preferredSessionMinutes = 60,
            adaptivePlanningEnabled = false,
            reviewNotificationsEnabled = true,
            strictExamModeEnabled = true,
            localOnly = true,
            previewFirst = true,
            userInitiatedOnly = true,
            subjects = listOf(
                bbSubject(OnboardingSampleSubjectKind.BB_PORTUGUESE, 15.0, objective),
                bbSubject(OnboardingSampleSubjectKind.BB_ENGLISH, 5.0, objective),
                bbSubject(OnboardingSampleSubjectKind.BB_MATHEMATICS, 7.5, objective),
                bbSubject(OnboardingSampleSubjectKind.BB_FINANCIAL_MARKET_CURRENT_AFFAIRS, 5.0, objective),
                bbSubject(OnboardingSampleSubjectKind.BB_FINANCIAL_MATHEMATICS, 7.5, objective),
                bbSubject(OnboardingSampleSubjectKind.BB_BANKING_KNOWLEDGE, 15.0, objective),
                bbSubject(OnboardingSampleSubjectKind.BB_INFORMATION_TECHNOLOGY, 22.5, objective),
                bbSubject(OnboardingSampleSubjectKind.BB_SALES_NEGOTIATION, 22.5, objective),
            ),
            scheduleRules = listOf(
                rule(1, 9 * 60, 90, OnboardingSampleSubjectKind.BB_PORTUGUESE),
                rule(1, 10 * 60 + 40, 90, OnboardingSampleSubjectKind.BB_INFORMATION_TECHNOLOGY),
                rule(2, 9 * 60, 90, OnboardingSampleSubjectKind.BB_SALES_NEGOTIATION),
                rule(2, 10 * 60 + 40, 90, OnboardingSampleSubjectKind.BB_BANKING_KNOWLEDGE),
                rule(3, 9 * 60, 90, OnboardingSampleSubjectKind.BB_MATHEMATICS),
                rule(3, 10 * 60 + 40, 90, OnboardingSampleSubjectKind.BB_INFORMATION_TECHNOLOGY),
                rule(4, 9 * 60, 90, OnboardingSampleSubjectKind.BB_SALES_NEGOTIATION),
                rule(4, 10 * 60 + 40, 90, OnboardingSampleSubjectKind.BB_BANKING_KNOWLEDGE),
                rule(5, 9 * 60, 60, OnboardingSampleSubjectKind.BB_FINANCIAL_MATHEMATICS),
                rule(5, 10 * 60 + 10, 60, OnboardingSampleSubjectKind.BB_FINANCIAL_MARKET_CURRENT_AFFAIRS),
                rule(5, 11 * 60 + 20, 60, OnboardingSampleSubjectKind.BB_ENGLISH),
                rule(6, 9 * 60, 120, null),
                rule(6, 11 * 60 + 10, 60, null),
                rule(6, 12 * 60 + 20, 40, null),
                rule(6, 13 * 60 + 10, 20, null),
            ),
            examStages = listOf(
                OnboardingSampleExamStageBlueprint(
                    kind = objective,
                    type = ExamStageType.OBJECTIVE,
                    weight = 100.0,
                    minimumScorePercent = null,
                ),
                OnboardingSampleExamStageBlueprint(
                    kind = OnboardingSampleExamStageKind.ESSAY,
                    type = ExamStageType.ESSAY,
                    weight = 0.0,
                    minimumScorePercent = 70,
                ),
            ),
            publicExam = phasedProfile,
        )
    }

    private fun universityPlan() = OnboardingSamplePlanBlueprint(
        template = OnboardingSamplePlanTemplate.UNIVERSITY_SEMESTER,
        studyType = StudyType.UNIVERSITY,
        weeklyGoalHours = 12,
        preferredSessionMinutes = 45,
        adaptivePlanningEnabled = true,
        reviewNotificationsEnabled = true,
        strictExamModeEnabled = false,
        localOnly = true,
        previewFirst = true,
        userInitiatedOnly = true,
        subjects = listOf(
            subject(OnboardingSampleSubjectKind.COURSE_FOUNDATIONS, 4, 3.0, 720) {
                topic(OnboardingSampleTopicKind.SYLLABUS_MAPPING, 4, 2, 180)
                topic(OnboardingSampleTopicKind.LECTURE_NOTES, 4, 3, 360)
            },
            subject(OnboardingSampleSubjectKind.WEEKLY_ASSIGNMENTS, 5, 4.0, 840) {
                topic(OnboardingSampleTopicKind.EXERCISE_LISTS, 5, 4, 480)
                topic(OnboardingSampleTopicKind.SEMINAR_PREPARATION, 3, 3, 360)
            },
            subject(OnboardingSampleSubjectKind.LAB_AND_PROJECTS, 4, 3.0, 780) {
                topic(OnboardingSampleTopicKind.LAB_NOTES, 4, 3, 300)
                topic(OnboardingSampleTopicKind.PROJECT_MILESTONES, 5, 4, 480)
            },
        ),
        scheduleRules = listOf(
            rule(2, 18 * 60, 75, OnboardingSampleSubjectKind.COURSE_FOUNDATIONS),
            rule(4, 18 * 60, 90, OnboardingSampleSubjectKind.WEEKLY_ASSIGNMENTS),
            rule(6, 10 * 60, 120, OnboardingSampleSubjectKind.LAB_AND_PROJECTS),
        ),
    )

    private fun certificationPlan() = OnboardingSamplePlanBlueprint(
        template = OnboardingSamplePlanTemplate.CERTIFICATION_BOOTCAMP,
        studyType = StudyType.CERTIFICATION,
        weeklyGoalHours = 10,
        preferredSessionMinutes = 40,
        adaptivePlanningEnabled = true,
        reviewNotificationsEnabled = true,
        strictExamModeEnabled = true,
        localOnly = true,
        previewFirst = true,
        userInitiatedOnly = true,
        subjects = listOf(
            subject(OnboardingSampleSubjectKind.EXAM_OBJECTIVES, 5, 5.0, 900) {
                topic(OnboardingSampleTopicKind.OFFICIAL_OBJECTIVES, 5, 3, 360)
                topic(OnboardingSampleTopicKind.HIGH_INCIDENCE_TOPICS, 5, 4, 540)
            },
            subject(OnboardingSampleSubjectKind.PRACTICAL_REHEARSAL, 5, 5.0, 840) {
                topic(OnboardingSampleTopicKind.PRACTICE_TEST, 5, 5, 480)
                topic(OnboardingSampleTopicKind.PRACTICAL_CHECKLIST, 4, 4, 360)
            },
        ),
        scheduleRules = listOf(
            rule(1, 20 * 60, 60, OnboardingSampleSubjectKind.EXAM_OBJECTIVES),
            rule(3, 20 * 60, 60, OnboardingSampleSubjectKind.PRACTICAL_REHEARSAL),
            rule(6, 11 * 60, 120, OnboardingSampleSubjectKind.PRACTICAL_REHEARSAL),
        ),
    )

    private fun freeStudyPlan() = OnboardingSamplePlanBlueprint(
        template = OnboardingSamplePlanTemplate.FREE_STUDY_HABIT,
        studyType = StudyType.FREE_STUDY,
        weeklyGoalHours = 6,
        preferredSessionMinutes = 30,
        adaptivePlanningEnabled = false,
        reviewNotificationsEnabled = true,
        strictExamModeEnabled = false,
        localOnly = true,
        previewFirst = true,
        userInitiatedOnly = true,
        subjects = listOf(
            subject(OnboardingSampleSubjectKind.FREE_READING, 3, 2.0, 420) {
                topic(OnboardingSampleTopicKind.READING_BACKLOG, 3, 2, 240)
                topic(OnboardingSampleTopicKind.SUMMARY_NOTES, 3, 2, 180)
            },
            subject(OnboardingSampleSubjectKind.CREATIVE_PRACTICE, 4, 3.0, 480) {
                topic(OnboardingSampleTopicKind.PRACTICE_ROUTINE, 4, 3, 300)
                topic(OnboardingSampleTopicKind.REFLECTION_LOG, 3, 2, 180)
            },
        ),
        scheduleRules = listOf(
            rule(2, 20 * 60, 45, OnboardingSampleSubjectKind.FREE_READING),
            rule(5, 20 * 60, 45, OnboardingSampleSubjectKind.CREATIVE_PRACTICE),
            rule(7, 10 * 60, 60, null),
        ),
    )

    private fun bbSubject(
        kind: OnboardingSampleSubjectKind,
        examWeight: Double,
        examStageKind: OnboardingSampleExamStageKind,
    ) = OnboardingSampleSubjectBlueprint(
        kind = kind,
        priority = 3,
        examWeight = examWeight,
        estimatedMinutes = 0,
        topics = emptyList(),
        examStageKind = examStageKind,
    )

    private fun subject(
        kind: OnboardingSampleSubjectKind,
        priority: Int,
        examWeight: Double,
        estimatedMinutes: Int,
        block: MutableList<OnboardingSampleTopicBlueprint>.() -> Unit,
    ) = OnboardingSampleSubjectBlueprint(
        kind = kind,
        priority = priority,
        examWeight = examWeight,
        estimatedMinutes = estimatedMinutes,
        topics = buildList(block),
    )

    private fun MutableList<OnboardingSampleTopicBlueprint>.topic(
        kind: OnboardingSampleTopicKind,
        priority: Int,
        difficulty: Int,
        estimatedMinutes: Int,
    ) {
        add(OnboardingSampleTopicBlueprint(kind, priority, difficulty, estimatedMinutes))
    }

    private fun rule(
        dayOfWeek: Int,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        subjectKind: OnboardingSampleSubjectKind?,
    ) = OnboardingScheduleRuleBlueprint(dayOfWeek, startMinuteOfDay, durationMinutes, subjectKind)
}
