package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.CodeSnippet
import com.mikeyphw.studyflow.core.model.CodeSnippetId
import com.mikeyphw.studyflow.core.model.CodingExercise
import com.mikeyphw.studyflow.core.model.CodingExerciseId
import com.mikeyphw.studyflow.core.model.MiniProject
import com.mikeyphw.studyflow.core.model.MiniProjectId
import com.mikeyphw.studyflow.core.model.ProgrammingCourseProfile
import com.mikeyphw.studyflow.core.model.ProgrammingLanguage
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.TermuxRuntimePolicy
import com.mikeyphw.studyflow.core.model.TermuxRuntimePreference
import kotlinx.coroutines.flow.Flow

/**
 * Canonical production repository for Android Programming Course data.
 *
 * OV26 deliberately exposes persisted Room-backed state instead of deriving programming panels
 * from schedule or generic question metrics. Termux execution itself remains owned by OV27, but
 * every profile/exercise/template row shown by the product flow is durable here.
 */
interface ProgrammingCourseRepository {
    fun observeSnapshot(planId: StudyPlanId): Flow<ProgrammingCourseSnapshot>

    suspend fun saveProfile(profile: ProgrammingCourseProfile): ProgrammingCourseProfile
    suspend fun saveCodeSnippet(snippet: CodeSnippet): CodeSnippet
    suspend fun saveCodingExercise(exercise: CodingExercise): CodingExercise
    suspend fun saveMiniProject(project: MiniProject): MiniProject
    suspend fun saveTermuxRuntimePreference(preference: TermuxRuntimePreference): TermuxRuntimePreference
    suspend fun applyImportPreview(planId: StudyPlanId, preview: StudyFlowProgrammingImportPreview): ProgrammingCourseSnapshot
    suspend fun deleteCodeSnippet(planId: StudyPlanId, snippetId: CodeSnippetId)
    suspend fun deleteCodingExercise(planId: StudyPlanId, exerciseId: CodingExerciseId)
    suspend fun deleteMiniProject(planId: StudyPlanId, projectId: MiniProjectId)
}

data class ProgrammingCourseSnapshot(
    val studyPlanId: StudyPlanId,
    val profile: ProgrammingCourseProfile?,
    val codeSnippets: List<CodeSnippet>,
    val codingExercises: List<CodingExercise>,
    val miniProjects: List<MiniProject>,
    val termuxRuntimePreference: TermuxRuntimePreference,
) {
    val moduleCount: Int = codeSnippets
        .mapNotNull { snippet -> snippet.topicId?.value ?: snippet.subjectId?.value }
        .distinct()
        .size
    val exerciseCount: Int = codingExercises.size
    val projectCount: Int = miniProjects.size
    val completedExerciseCount: Int = codingExercises.count { exercise ->
        exercise.status.name == "COMPLETED" || exercise.status.name == "RAN_SUCCESSFULLY"
    }
    val completedProjectCount: Int = miniProjects.count { project -> project.status.name == "COMPLETED" }
    val termuxRuntimeReady: Boolean = termuxRuntimePreference.enabled &&
        termuxRuntimePreference.allowedCommandTemplates.isNotEmpty()
}

data class StudyFlowProgrammingImportPreview(
    val sourcePreview: com.mikeyphw.studyflow.core.model.StudyFlowImportPreview,
) {
    init {
        require(sourcePreview.canImport) { "Programming import preview must be valid before applying." }
        require(sourcePreview.rows.all { row ->
            row.kind == com.mikeyphw.studyflow.core.model.StudyFlowImportRowKind.PROGRAMMING_EXERCISE ||
                row.kind == com.mikeyphw.studyflow.core.model.StudyFlowImportRowKind.PROGRAMMING_PROJECT
        }) { "Programming import preview contains non-programming rows." }
    }
}

class ManageProgrammingCourse(
    private val repository: ProgrammingCourseRepository,
) {
    fun observeSnapshot(planId: StudyPlanId): Flow<ProgrammingCourseSnapshot> = repository.observeSnapshot(planId)

    fun defaultTermuxPolicy(language: ProgrammingLanguage): TermuxRuntimePolicy =
        TermuxRuntimePolicy.defaultForProgrammingCourse(language)

    suspend fun saveProfile(profile: ProgrammingCourseProfile): ProgrammingCourseProfile =
        repository.saveProfile(profile)

    suspend fun saveCodeSnippet(snippet: CodeSnippet): CodeSnippet = repository.saveCodeSnippet(snippet)

    suspend fun saveCodingExercise(exercise: CodingExercise): CodingExercise = repository.saveCodingExercise(exercise)

    suspend fun saveMiniProject(project: MiniProject): MiniProject = repository.saveMiniProject(project)

    suspend fun saveTermuxRuntimePreference(preference: TermuxRuntimePreference): TermuxRuntimePreference =
        repository.saveTermuxRuntimePreference(preference)

    suspend fun applyImportPreview(
        planId: StudyPlanId,
        preview: StudyFlowProgrammingImportPreview,
    ): ProgrammingCourseSnapshot = repository.applyImportPreview(planId, preview)
}
