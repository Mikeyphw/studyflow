package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.WritingCompletionChecklist
import com.mikeyphw.studyflow.core.domain.WritingHistoryEntry
import com.mikeyphw.studyflow.core.domain.WritingRubric
import com.mikeyphw.studyflow.core.domain.WritingSessionNoteCodec
import com.mikeyphw.studyflow.core.domain.WritingSessionRecord
import com.mikeyphw.studyflow.core.domain.WritingWeeklyProgress
import com.mikeyphw.studyflow.core.domain.WritingWorkspaceAnalytics
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.util.UUID

private const val DesktopWritingWeeklyTarget = 1

data class DesktopWritingWorkspaceState(
    val activeSession: StudySession?,
    val record: WritingSessionRecord?,
    val history: List<WritingHistoryEntry>,
    val weeklyProgress: WritingWeeklyProgress,
    val canStart: Boolean,
    val canSave: Boolean,
    val canComplete: Boolean,
)

class DesktopWritingWorkspaceController(
    private val services: DesktopStudyFlowServices,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun state(): DesktopWritingWorkspaceState {
        val plan = services.planContentController.state().selectedPlan
        val anyActive = services.sessionRepository.currentAllSessions().firstOrNull { it.status in ActiveSessionStatuses }
        val activeWriting = anyActive?.takeIf { it.type == StudyActivityType.WRITING }
        val sessions = plan?.id?.let(services.sessionRepository::currentSessions).orEmpty()
        val record = activeWriting?.let { WritingSessionNoteCodec.decode(it.notes) }
        return DesktopWritingWorkspaceState(
            activeSession = activeWriting,
            record = record,
            history = WritingWorkspaceAnalytics.history(sessions),
            weeklyProgress = WritingWorkspaceAnalytics.weeklyProgress(sessions, DesktopWritingWeeklyTarget),
            canStart = plan != null && anyActive == null && plan.status !in BlockedPlanStatuses,
            canSave = activeWriting?.status in ActiveSessionStatuses,
            canComplete = activeWriting?.status in ActiveSessionStatuses,
        )
    }

    suspend fun start(prompt: String): StudySession {
        val plan = requireRunnablePlan()
        require(prompt.isNotBlank()) { "Writing prompt cannot be blank." }
        val session = services.manageStudySession.startQuickSession(
            sessionId = StudySessionId(idFactory()),
            studyPlanId = plan.id,
            title = "Writing Workspace",
            type = StudyActivityType.WRITING,
        )
        return services.manageStudySession.saveWritingDraft(
            planId = plan.id,
            sessionId = session.id,
            prompt = prompt,
            draftText = "",
        )
    }

    suspend fun saveDraft(prompt: String, draftText: String): StudySession {
        val active = requireActiveWriting()
        return services.manageStudySession.saveWritingDraft(active.studyPlanId, active.id, prompt, draftText)
    }

    suspend fun complete(
        prompt: String,
        draftText: String,
        rubric: WritingRubric,
        checklist: WritingCompletionChecklist,
    ): StudySession {
        val active = requireActiveWriting()
        return services.manageStudySession.completeWriting(
            planId = active.studyPlanId,
            sessionId = active.id,
            prompt = prompt,
            draftText = draftText,
            rubric = rubric,
            checklist = checklist,
        )
    }

    private fun requireRunnablePlan() = requireNotNull(services.planContentController.state().selectedPlan) {
        "Select a Study Plan first."
    }.also { plan ->
        require(plan.status !in BlockedPlanStatuses) {
            "Restore or reactivate the Study Plan before starting Writing Workspace."
        }
    }

    private fun requireActiveWriting(): StudySession {
        val active = requireNotNull(services.sessionRepository.currentAllSessions().firstOrNull { it.status in ActiveSessionStatuses }) { "No active Writing Session." }
        require(active.type == StudyActivityType.WRITING) { "The active Study Session is not a Writing Session." }
        require(active.status in ActiveSessionStatuses) { "Writing Session is not active." }
        return active
    }

    private companion object {
        val ActiveSessionStatuses = setOf(StudySessionStatus.RUNNING, StudySessionStatus.PAUSED)
        val BlockedPlanStatuses = setOf(
            StudyPlanStatus.ARCHIVED,
            StudyPlanStatus.TRASHED,
            StudyPlanStatus.COMPLETED,
        )
    }
}
