package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.AdaptiveReschedulePlanner
import com.mikeyphw.studyflow.core.domain.ExamReadinessEngine
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.model.AdaptiveReschedulePreview
import com.mikeyphw.studyflow.core.model.MissedActivityHandling
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleGenerationRequest
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyStrategy
import com.mikeyphw.studyflow.core.model.StudyStrategyId
import com.mikeyphw.studyflow.core.model.StudyStrategyType
import java.util.UUID

data class DesktopPlannerAuthoringState(
    val strategy: StudyStrategy?,
    val rules: List<ScheduleRule>,
    val activities: List<PlannedActivity>,
    val adaptivePreview: AdaptiveReschedulePreview?,
)

class DesktopPlannerAuthoringController(
    private val services: DesktopStudyFlowServices,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun state(): DesktopPlannerAuthoringState {
        val plan = services.planContentController.state().selectedPlan ?: return DesktopPlannerAuthoringState(
            strategy = null,
            rules = emptyList(),
            activities = emptyList(),
            adaptivePreview = null,
        )
        val schedule = services.scheduleRepository.currentSchedule(plan.id)
        val analytics = services.analyticsRepository.currentSnapshot(plan.id)
        val readiness = ExamReadinessEngine.calculate(
            plan = plan,
            analytics = analytics,
            schedule = schedule,
            mockExams = services.practiceRepository.currentMockExams(plan.id),
            nowEpochMillis = clock(),
        )
        val preview = if (services.planRepository.currentSettings()[plan.id]?.adaptivePlanningEnabled == false) {
            AdaptiveReschedulePlanner.disabledPreview(plan, analytics, schedule, readiness.scorePercent, clock())
        } else {
            AdaptiveReschedulePlanner.preview(plan, analytics, schedule, readiness.scorePercent, clock())
        }
        return DesktopPlannerAuthoringState(
            strategy = schedule.strategy,
            rules = schedule.scheduleRules,
            activities = schedule.plannedActivities,
            adaptivePreview = preview,
        )
    }

    suspend fun ensureAdaptiveStrategy(): StudyStrategy {
        val plan = requireEditablePlan()
        val current = services.scheduleRepository.currentSchedule(plan.id).strategy
        val strategy = current ?: StudyStrategy(
            id = StudyStrategyId(idFactory()),
            studyPlanId = plan.id,
            type = StudyStrategyType.ADAPTIVE,
            minimumSessionMinutes = 25,
            maximumSessionMinutes = 90,
            theoryRatioPercent = 50,
            questionRatioPercent = 30,
            reviewRatioPercent = 20,
            adaptivePlanningEnabled = true,
            configurationJson = "{}",
        )
        return services.manageStudySchedule.saveStrategy(strategy)
    }

    suspend fun addEveningRule(dayOfWeek: Int = 1, startMinuteOfDay: Int = 18 * 60): ScheduleRule {
        val plan = requireEditablePlan()
        val rules = services.scheduleRepository.currentRules(plan.id)
        return services.manageStudySchedule.saveRule(
            ScheduleRule(
                id = ScheduleRuleId(idFactory()),
                studyPlanId = plan.id,
                subjectId = services.contentRepository
                    .currentSyllabus(plan.id)
                    .subjects
                    .firstOrNull { !it.archived }
                    ?.id,
                type = ScheduleRuleType.RECURRING_BLOCK,
                dayOfWeek = dayOfWeek,
                startMinuteOfDay = startMinuteOfDay,
                durationMinutes = 45,
                recurrenceRule = null,
                locked = false,
                enabled = true,
                position = rules.size,
            ),
        )
    }

    suspend fun addManualActivity(title: String): PlannedActivity {
        val plan = requireEditablePlan()
        val syllabus = services.contentRepository.currentSyllabus(plan.id)
        return services.manageStudySchedule.saveActivity(
            PlannedActivity(
                id = PlannedActivityId(idFactory()),
                studyPlanId = plan.id,
                subjectId = syllabus.subjects.firstOrNull { !it.archived }?.id,
                topicId = syllabus.topics.firstOrNull { !it.archived }?.id,
                materialId = null,
                videoResourceId = null,
                type = StudyActivityType.THEORY,
                title = title.trim(),
                scheduledStartEpochMillis = clock() + 86_400_000L,
                plannedDurationMinutes = 45,
                status = PlannedActivityStatus.PLANNED,
                locked = false,
                completedAtEpochMillis = null,
            ),
        )
    }

    suspend fun generateSevenDays(): Int {
        val plan = requireEditablePlan()
        val schedule = services.scheduleRepository.currentSchedule(plan.id)
        require(schedule.scheduleRules.isNotEmpty()) { "Add at least one schedule rule before generating activities." }
        return services.manageStudySchedule.generateActivities(
            ScheduleGenerationRequest(plan.id, clock(), days = 7, replaceUnlocked = false),
            currentRules = schedule.scheduleRules,
            strategy = schedule.strategy,
        ).generatedActivities
    }

    suspend fun applyAdaptivePreview(): Int {
        val plan = requireEditablePlan()
        val preview = requireNotNull(state().adaptivePreview) { "No adaptive preview is available." }
        return services.scheduleRepository.applyAdaptivePreview(plan.id, preview)
    }

    suspend fun rebalanceMissed(): Int {
        val plan = requireEditablePlan()
        val activities = services.scheduleRepository.currentActivities(plan.id)
        return services.manageStudySchedule.rebalanceMissed(
            plan.id,
            activities,
            MissedActivityHandling.POSTPONE,
        ).updatedActivities
    }
    private fun requireEditablePlan(): com.mikeyphw.studyflow.core.model.StudyPlan {
        val plan = requireNotNull(services.planContentController.state().selectedPlan) { "Select a Study Plan first." }
        require(plan.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED)) {
            "Restore the Study Plan before editing its schedule."
        }
        return plan
    }

}
