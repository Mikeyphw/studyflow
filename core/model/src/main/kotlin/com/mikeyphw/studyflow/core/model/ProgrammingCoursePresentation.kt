package com.mikeyphw.studyflow.core.model

data class ProgrammingCourseUiLabels(
    val moduleLabel: String,
    val lessonLabel: String,
    val exerciseLabel: String,
    val projectLabel: String,
    val resourceLabel: String,
    val progressLabel: String,
)

data class ProgrammingCourseDashboardSummary(
    val moduleCount: Int,
    val exerciseCount: Int,
    val projectCount: Int,
    val weakConceptCount: Int,
    val termuxRuntimeReady: Boolean,
) {
    init {
        require(moduleCount >= 0) { "Module count cannot be negative." }
        require(exerciseCount >= 0) { "Exercise count cannot be negative." }
        require(projectCount >= 0) { "Project count cannot be negative." }
        require(weakConceptCount >= 0) { "Weak concept count cannot be negative." }
    }
}

val DefaultProgrammingCourseUiLabels = ProgrammingCourseUiLabels(
    moduleLabel = "Modules",
    lessonLabel = "Lessons",
    exerciseLabel = "Exercises",
    projectLabel = "Projects",
    resourceLabel = "Resources",
    progressLabel = "Skill progress",
)

fun labelsForContentMode(mode: StudyContentMode): ProgrammingCourseUiLabels? = when (mode) {
    StudyContentMode.PROGRAMMING_COURSE -> DefaultProgrammingCourseUiLabels
    StudyContentMode.EXAM_PLAN,
    StudyContentMode.LANGUAGE_DECK,
    StudyContentMode.READING_STUDY,
    StudyContentMode.GENERAL_SKILL,
    -> null
}
