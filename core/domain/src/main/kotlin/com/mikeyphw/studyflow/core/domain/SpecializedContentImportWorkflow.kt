package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyFlowImportPreview
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId

sealed interface SpecializedContentImportApplyResult {
    data class ProgrammingApplied(val snapshot: ProgrammingCourseSnapshot) : SpecializedContentImportApplyResult
    data class LanguageApplied(val snapshot: LanguageLearningSnapshot) : SpecializedContentImportApplyResult
    data object UnsupportedContentMode : SpecializedContentImportApplyResult
}

/**
 * Android-facing specialized import apply workflow.
 *
 * OV26 makes the existing programming/language template preview parsers actionable. This use case is
 * deliberately content-mode gated so Android cannot silently shove specialized rows into the generic
 * syllabus importer or relabel a generic import as programming/language data.
 */
class ApplySpecializedContentImport(
    private val programmingCourse: ManageProgrammingCourse,
    private val languageLearning: ManageLanguageLearning,
) {
    suspend fun apply(
        plan: StudyPlan,
        preview: StudyFlowImportPreview,
    ): SpecializedContentImportApplyResult = when (plan.contentMode) {
        StudyContentMode.PROGRAMMING_COURSE -> SpecializedContentImportApplyResult.ProgrammingApplied(
            programmingCourse.applyImportPreview(plan.id, StudyFlowProgrammingImportPreview(preview)),
        )
        StudyContentMode.LANGUAGE_DECK -> SpecializedContentImportApplyResult.LanguageApplied(
            languageLearning.applyImportPreview(plan.id, StudyFlowLanguageImportPreview(preview)),
        )
        StudyContentMode.EXAM_PLAN,
        StudyContentMode.READING_STUDY,
        StudyContentMode.GENERAL_SKILL,
        -> SpecializedContentImportApplyResult.UnsupportedContentMode
    }
}


/**
 * Executable OV29 contract facade for specialized import behavior.
 *
 * These helpers intentionally delegate to the production managers instead of re-parsing previews in
 * validation scripts. Release gates call this surface to prove programming/language imports are
 * actionable runtime flows rather than source-presence tokens.
 */
object SpecializedContentImportWorkflow {
    suspend fun applyProgrammingCourseImportPreview(
        programmingCourse: ManageProgrammingCourse,
        planId: StudyPlanId,
        preview: StudyFlowImportPreview,
    ): ProgrammingCourseSnapshot = programmingCourse.applyImportPreview(
        planId,
        StudyFlowProgrammingImportPreview(preview),
    )

    suspend fun applyLanguageDeckImportPreview(
        languageLearning: ManageLanguageLearning,
        planId: StudyPlanId,
        preview: StudyFlowImportPreview,
    ): LanguageLearningSnapshot = languageLearning.applyImportPreview(
        planId,
        StudyFlowLanguageImportPreview(preview),
    )

    suspend fun apply(
        programmingCourse: ManageProgrammingCourse,
        languageLearning: ManageLanguageLearning,
        plan: StudyPlan,
        preview: StudyFlowImportPreview,
    ): SpecializedContentImportApplyResult = ApplySpecializedContentImport(
        programmingCourse = programmingCourse,
        languageLearning = languageLearning,
    ).apply(plan, preview)
}
