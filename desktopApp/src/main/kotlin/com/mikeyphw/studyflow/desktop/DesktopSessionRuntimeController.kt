package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.util.UUID

data class DesktopSessionRuntimeState(
    val activeSession: StudySession?,
    val nextActivityTitle: String?,
    val canStart: Boolean,
    val canPause: Boolean,
    val canResume: Boolean,
    val canFinish: Boolean,
)

class DesktopSessionRuntimeController(
    private val services: DesktopStudyFlowServices,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun state(): DesktopSessionRuntimeState {
        val plan = services.planContentController.state().selectedPlan
        val active = plan?.id?.let { planId -> services.manageStudySession.activeSession(planId) }
        val next = plan?.id?.let(services.scheduleRepository::currentActivities)
            ?.firstOrNull { it.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED) }
        return DesktopSessionRuntimeState(
            activeSession = active,
            nextActivityTitle = next?.title,
            canStart = plan != null && active == null,
            canPause = active?.status == StudySessionStatus.RUNNING,
            canResume = active?.status == StudySessionStatus.PAUSED,
            canFinish = active?.status in setOf(StudySessionStatus.RUNNING, StudySessionStatus.PAUSED),
        )
    }

    suspend fun startNextActivity(): StudySession {
        val plan = requireRunnablePlan()
        val activity = services.scheduleRepository.currentActivities(plan.id)
            .firstOrNull { it.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED) }
            ?: error("No planned or missed activity is available.")
        return services.manageStudySession.startFromActivity(StudySessionId(idFactory()), activity)
    }

    suspend fun startQuickSession(title: String = "Quick study session"): StudySession {
        val plan = requireRunnablePlan()
        return services.manageStudySession.startQuickSession(
            sessionId = StudySessionId(idFactory()),
            studyPlanId = plan.id,
            title = title,
            type = StudyActivityType.CUSTOM,
        )
    }

    suspend fun pause(): StudySession {
        val active = requireActive()
        return services.manageStudySession.pause(active.studyPlanId, active.id)
    }

    suspend fun resume(): StudySession {
        val active = requireActive()
        return services.manageStudySession.resume(active.studyPlanId, active.id)
    }

    suspend fun complete(): StudySession {
        val active = requireActive()
        return services.manageStudySession.complete(
            planId = active.studyPlanId,
            sessionId = active.id,
            pagesCompleted = active.pagesCompleted,
            questionsAnswered = active.questionsAnswered,
            correctAnswers = active.correctAnswers,
            concentrationRating = active.concentrationRating,
            difficultyRating = active.difficultyRating,
            notes = active.notes,
        )
    }

    suspend fun cancel(): StudySession {
        val active = requireActive()
        return services.manageStudySession.cancel(active.studyPlanId, active.id)
    }

    private fun requireRunnablePlan(): com.mikeyphw.studyflow.core.model.StudyPlan {
        val plan = requireNotNull(services.planContentController.state().selectedPlan) { "Select a Study Plan first." }
        require(plan.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED, StudyPlanStatus.COMPLETED)) {
            "Restore or reactivate the Study Plan before starting a Study Session."
        }
        return plan
    }

    private suspend fun requireActive(): StudySession = requireNotNull(services.manageStudySession.activeSession()) {
        "No active Study Session."
    }
}
