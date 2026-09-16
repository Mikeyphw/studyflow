package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.ExamNoticeImportParser
import com.mikeyphw.studyflow.core.domain.ExamNoticeImportSource
import com.mikeyphw.studyflow.core.domain.ExamNoticeRevisionPlanner
import com.mikeyphw.studyflow.core.domain.LegislationImportFormat
import com.mikeyphw.studyflow.core.domain.ManagePublicExam
import com.mikeyphw.studyflow.core.domain.PublicExamDashboardBuilder
import com.mikeyphw.studyflow.core.domain.PublicExamDashboardSnapshot
import com.mikeyphw.studyflow.core.domain.PublicExamFinalStretchPlanner
import com.mikeyphw.studyflow.core.domain.PublicExamFinalStretchStrategy
import com.mikeyphw.studyflow.core.domain.SyllabusImportFormat
import com.mikeyphw.studyflow.core.model.ExamNoticeRevision
import com.mikeyphw.studyflow.core.model.LegislationStudySnapshot
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyCycleSnapshot
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyTargetId
import kotlinx.coroutines.flow.first
import com.mikeyphw.studyflow.core.model.StudyType
import java.util.UUID

data class DesktopPublicExamState(
    val plan: StudyPlan?,
    val target: StudyTarget?,
    val editalCoveragePercent: Int,
    val revisions: List<ExamNoticeRevision>,
    val legislation: LegislationStudySnapshot?,
    val cycle: StudyCycleSnapshot?,
    val dashboard: PublicExamDashboardSnapshot?,
    val finalStretch: PublicExamFinalStretchStrategy?,
)

class DesktopPublicExamController(
    private val services: DesktopStudyFlowServices,
    private val repository: DesktopPublicExamRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val manager = ManagePublicExam(repository, services.manageStudySession)

    fun state(): DesktopPublicExamState {
        val plan = services.planContentController.state().selectedPlan
        if (plan == null) return DesktopPublicExamState(null, null, 0, emptyList(), null, null, null, null)
        val syllabus = services.contentRepository.currentSyllabus(plan.id)
        val target = services.contentRepository.currentState().targets.firstOrNull { it.studyPlanId == plan.id }
        val revisions = repository.currentRevisions(plan.id)
        val legislation = repository.currentLegislation(plan.id)
        val cycle = repository.currentCycle(plan.id)
        val dashboard = PublicExamDashboardBuilder.build(
            plan = plan,
            target = target,
            syllabus = syllabus,
            questions = services.practiceRepository.currentQuestions(plan.id),
            reviews = services.reviewRepository.currentReviews(plan.id),
            errors = services.reviewRepository.currentErrors(plan.id),
            sessions = services.sessionRepository.currentSessions(plan.id),
            mockExams = services.practiceRepository.currentMockExams(plan.id),
            nowEpochMillis = clock(),
        )
        return DesktopPublicExamState(
            plan = plan,
            target = target,
            editalCoveragePercent = ExamNoticeRevisionPlanner.coverage(syllabus).coveragePercent,
            revisions = revisions,
            legislation = legislation,
            cycle = cycle,
            dashboard = dashboard,
            finalStretch = PublicExamFinalStretchPlanner.plan(dashboard.metrics.daysUntilExam),
        )
    }

    suspend fun saveTarget(
        organization: String,
        role: String,
        examBoard: String,
        noticeUrl: String,
        examDateIso: String,
    ): StudyTarget {
        val plan = requireMutablePlan()
        require(organization.isNotBlank() && role.isNotBlank() && examBoard.isNotBlank()) {
            "Organization, role and exam board are required."
        }
        if (plan.type != StudyType.PUBLIC_EXAMINATION || plan.contentMode != StudyContentMode.EXAM_PLAN) {
            services.planRepository.replace(
                plan.copy(
                    type = StudyType.PUBLIC_EXAMINATION,
                    contentMode = StudyContentMode.EXAM_PLAN,
                    updatedAtEpochMillis = clock(),
                ),
            )
        }
        val previous = services.contentRepository.currentState().targets.firstOrNull { it.studyPlanId == plan.id }
        val now = clock()
        val target = StudyTarget(
            id = previous?.id ?: StudyTargetId(idFactory()),
            studyPlanId = plan.id,
            organization = organization.trim(),
            role = role.trim(),
            examBoard = examBoard.trim(),
            noticeUrl = noticeUrl.trim().ifBlank { null },
            confirmedExamDateIso = examDateIso.trim().ifBlank { null },
            expectedExamDateIso = null,
            openings = previous?.openings,
            estimatedCutoffPercent = previous?.estimatedCutoffPercent,
            createdAtEpochMillis = previous?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
        )
        return services.manageStudyContent.saveStudyTarget(target)
    }

    suspend fun applyEdital(displayName: String, rawText: String): ExamNoticeRevision {
        val plan = requirePublicExamPlan()
        val syllabus = services.contentRepository.currentSyllabus(plan.id)
        val parsed = ExamNoticeImportParser.preview(
            planId = plan.id,
            source = ExamNoticeImportSource(
                displayName = displayName.trim().ifBlank { "Desktop edital" },
                format = SyllabusImportFormat.STRUCTURED_TEXT,
                text = rawText,
            ),
            existing = syllabus,
        )
        val preview = manager.prepareExamNoticeRevision(plan.id, parsed, syllabus)
        return manager.applyExamNoticeRevision(preview)
    }

    suspend fun importLegislation(title: String, rawText: String): LegislationStudySnapshot {
        val plan = requirePublicExamPlan()
        val preview = manager.previewLegislationImport(
            title = title.trim().ifBlank { "Legislation" },
            sourceReference = "desktop-local",
            rawText = rawText,
            format = LegislationImportFormat.STRUCTURED_TEXT,
        )
        return manager.applyLegislationImport(plan.id, preview)
    }

    suspend fun buildCycle(targetDurationMinutes: Int = 45): StudyCycleSnapshot {
        val plan = requirePublicExamPlan()
        return manager.replaceStudyCycleFromSyllabus(
            planId = plan.id,
            syllabus = services.contentRepository.currentSyllabus(plan.id),
            targetDurationMinutes = targetDurationMinutes,
        )
    }

    suspend fun startCurrentCycleItem(): StudySession? {
        val plan = requirePublicExamPlan()
        return manager.startCurrentStudyCycleItem(plan.id)
    }

    suspend fun skipCycleItem(): StudyCycleSnapshot? {
        val plan = requirePublicExamPlan()
        return manager.skipCurrentStudyCycleItem(plan.id)
    }

    suspend fun toggleFirstProvisionRead(): LegislationStudySnapshot {
        val plan = requirePublicExamPlan()
        val snapshot = manager.observeLegislation(plan.id).first()
        val provision = snapshot.provisions.firstOrNull() ?: error("Import legislation first.")
        return manager.setLegislationProvisionRead(plan.id, provision.id, !provision.read)
    }

    private fun requirePublicExamPlan(): StudyPlan = requireMutablePlan().also { plan ->
        require(plan.type == StudyType.PUBLIC_EXAMINATION) { "Save a Concurso Público target first." }
    }

    private fun requireMutablePlan(): StudyPlan = requireNotNull(services.planContentController.state().selectedPlan) {
        "Select a Study Plan first."
    }.also { plan ->
        require(plan.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED, StudyPlanStatus.COMPLETED)) {
            "Restore or reactivate the Study Plan before changing Concurso Público data."
        }
    }
}
