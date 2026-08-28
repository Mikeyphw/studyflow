package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyGoalId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyStrategy
import com.mikeyphw.studyflow.core.model.StudyStrategyId
import com.mikeyphw.studyflow.core.model.StudyStrategyType
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyTargetId
import java.time.LocalDate

enum class PublicExamCyclePreference {
    STUDY_CYCLE,
    FIXED_WEEKLY,
    ADAPTIVE,
}

enum class PublicExamGoalPreset {
    WEEKLY_STUDY_TIME,
    WEEKLY_QUESTIONS,
    ACCURACY,
    MONTHLY_MOCKS,
}

enum class PublicExamImportTemplateKind {
    EDITAL,
    QUESTIONS,
    LEGISLATION,
}

data class PublicExamImportTemplate(
    val kind: PublicExamImportTemplateKind,
    val repositoryPath: String,
    val localOnly: Boolean = true,
)

object PublicExamOnboardingTemplates {
    val importTemplates: List<PublicExamImportTemplate> = listOf(
        PublicExamImportTemplate(PublicExamImportTemplateKind.EDITAL, "samples/import/public_exam_edital.txt"),
        PublicExamImportTemplate(PublicExamImportTemplateKind.QUESTIONS, "samples/import/public_exam_questions.csv"),
        PublicExamImportTemplate(PublicExamImportTemplateKind.LEGISLATION, "samples/import/public_exam_legislation.md"),
    )
}

data class PublicExamOnboardingInput(
    val organization: String = "",
    val role: String = "",
    val examBoard: String = "",
    val noticeUrl: String = "",
    val examDateIso: String = "",
    val weeklyAvailabilityHours: Int = 18,
    val cyclePreference: PublicExamCyclePreference = PublicExamCyclePreference.STUDY_CYCLE,
    val reviewDays: List<Int> = listOf(1, 7, 30),
    val weeklyQuestionGoal: Int = 200,
    val accuracyGoalPercent: Int = 80,
    val monthlyMockGoal: Int = 2,
) {
    init {
        require(weeklyAvailabilityHours in 1..100) { "Weekly availability must be between 1 and 100 hours." }
        PublicExamReviewPolicy(reviewDays)
        require(weeklyQuestionGoal in 0..100_000) { "Weekly question goal is invalid." }
        require(accuracyGoalPercent in 0..100) { "Accuracy goal must be between 0 and 100." }
        require(monthlyMockGoal in 0..100) { "Monthly mock goal is invalid." }
        val required = listOf(organization.trim(), role.trim(), examBoard.trim())
        require(required.all(String::isBlank) || required.none(String::isBlank)) {
            "Organization, role and exam board must be filled together or left blank."
        }
        examDateIso.trim().takeIf(String::isNotEmpty)?.let { date ->
            require(runCatching { LocalDate.parse(date) }.isSuccess) { "Exam date must use YYYY-MM-DD." }
        }
    }
}

data class PublicExamOnboardingBlueprint(
    val organization: String,
    val role: String,
    val examBoard: String,
    val noticeUrl: String?,
    val examDateIso: String?,
    val weeklyAvailabilityHours: Int,
    val cyclePreference: PublicExamCyclePreference,
    val reviewPolicy: PublicExamReviewPolicy,
    val weeklyQuestionGoal: Int,
    val accuracyGoalPercent: Int,
    val monthlyMockGoal: Int,
    val importTemplates: List<PublicExamImportTemplate> = PublicExamOnboardingTemplates.importTemplates,
) {
    val hasTargetProfile: Boolean = organization.isNotBlank() && role.isNotBlank() && examBoard.isNotBlank()
}

object PublicExamOnboardingBuilder {
    fun build(input: PublicExamOnboardingInput): PublicExamOnboardingBlueprint = PublicExamOnboardingBlueprint(
        organization = input.organization.trim(),
        role = input.role.trim(),
        examBoard = input.examBoard.trim(),
        noticeUrl = input.noticeUrl.trim().ifBlank { null },
        examDateIso = input.examDateIso.trim().ifBlank { null },
        weeklyAvailabilityHours = input.weeklyAvailabilityHours,
        cyclePreference = input.cyclePreference,
        reviewPolicy = PublicExamReviewPolicy(input.reviewDays),
        weeklyQuestionGoal = input.weeklyQuestionGoal,
        accuracyGoalPercent = input.accuracyGoalPercent,
        monthlyMockGoal = input.monthlyMockGoal,
    )

    fun buildTarget(
        planId: StudyPlanId,
        blueprint: PublicExamOnboardingBlueprint,
        idFactory: () -> String,
        nowEpochMillis: Long,
    ): StudyTarget? = blueprint.takeIf(PublicExamOnboardingBlueprint::hasTargetProfile)?.let { profile ->
        StudyTarget(
            id = StudyTargetId(idFactory()),
            studyPlanId = planId,
            organization = profile.organization,
            role = profile.role,
            examBoard = profile.examBoard,
            noticeUrl = profile.noticeUrl,
            confirmedExamDateIso = profile.examDateIso,
            expectedExamDateIso = null,
            openings = null,
            estimatedCutoffPercent = null,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )
    }

    fun buildStrategy(
        planId: StudyPlanId,
        blueprint: PublicExamOnboardingBlueprint,
        idFactory: () -> String,
    ): StudyStrategy = StudyStrategy(
        id = StudyStrategyId(idFactory()),
        studyPlanId = planId,
        type = when (blueprint.cyclePreference) {
            PublicExamCyclePreference.STUDY_CYCLE -> StudyStrategyType.STUDY_CYCLE
            PublicExamCyclePreference.FIXED_WEEKLY -> StudyStrategyType.FIXED_WEEKLY
            PublicExamCyclePreference.ADAPTIVE -> StudyStrategyType.ADAPTIVE
        },
        minimumSessionMinutes = 25,
        maximumSessionMinutes = 120,
        theoryRatioPercent = 40,
        questionRatioPercent = 40,
        reviewRatioPercent = 20,
        adaptivePlanningEnabled = blueprint.cyclePreference == PublicExamCyclePreference.ADAPTIVE,
        configurationJson = "{}",
    )

    fun buildGoals(
        planId: StudyPlanId,
        blueprint: PublicExamOnboardingBlueprint,
        goalTitles: Map<PublicExamGoalPreset, String>,
        startsOnIso: String,
        idFactory: () -> String,
    ): List<StudyGoal> = buildList {
        addGoal(
            planId,
            GoalType.STUDY_TIME,
            GoalPeriod.WEEKLY,
            goalTitles.getValue(PublicExamGoalPreset.WEEKLY_STUDY_TIME),
            blueprint.weeklyAvailabilityHours * 60.0,
            startsOnIso,
            idFactory,
        )
        if (blueprint.weeklyQuestionGoal > 0) {
            addGoal(
                planId,
                GoalType.QUESTION_COUNT,
                GoalPeriod.WEEKLY,
                goalTitles.getValue(PublicExamGoalPreset.WEEKLY_QUESTIONS),
                blueprint.weeklyQuestionGoal.toDouble(),
                startsOnIso,
                idFactory,
            )
        }
        if (blueprint.accuracyGoalPercent > 0) {
            addGoal(
                planId,
                GoalType.ACCURACY,
                GoalPeriod.WEEKLY,
                goalTitles.getValue(PublicExamGoalPreset.ACCURACY),
                blueprint.accuracyGoalPercent.toDouble(),
                startsOnIso,
                idFactory,
            )
        }
        if (blueprint.monthlyMockGoal > 0) {
            addGoal(
                planId,
                GoalType.MOCK_EXAM_COUNT,
                GoalPeriod.MONTHLY,
                goalTitles.getValue(PublicExamGoalPreset.MONTHLY_MOCKS),
                blueprint.monthlyMockGoal.toDouble(),
                startsOnIso,
                idFactory,
            )
        }
    }

    private fun MutableList<StudyGoal>.addGoal(
        planId: StudyPlanId,
        type: GoalType,
        period: GoalPeriod,
        title: String,
        target: Double,
        startsOnIso: String,
        idFactory: () -> String,
    ) {
        add(
            StudyGoal(
                id = StudyGoalId(idFactory()),
                studyPlanId = planId,
                subjectId = null,
                topicId = null,
                type = type,
                period = period,
                title = title,
                targetValue = target,
                currentValue = 0.0,
                startsOnIso = startsOnIso,
                endsOnIso = null,
                completedAtEpochMillis = null,
                archived = false,
            ),
        )
    }
}
