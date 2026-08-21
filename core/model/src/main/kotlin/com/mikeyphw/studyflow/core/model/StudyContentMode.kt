package com.mikeyphw.studyflow.core.model

/**
 * High-level learning mode for a StudyFlow plan. The mode changes labels,
 * templates and future capabilities without changing the existing study graph.
 */
enum class StudyContentMode {
    EXAM_PLAN,
    PROGRAMMING_COURSE,
    LANGUAGE_DECK,
    READING_STUDY,
    GENERAL_SKILL,
}

data class StudyContentModeCapabilities(
    val supportsCodeBlocks: Boolean,
    val supportsTermuxRuntime: Boolean,
    val supportsCodingExercises: Boolean,
)

val StudyContentMode.capabilities: StudyContentModeCapabilities
    get() = when (this) {
        StudyContentMode.PROGRAMMING_COURSE -> StudyContentModeCapabilities(
            supportsCodeBlocks = true,
            supportsTermuxRuntime = false,
            supportsCodingExercises = true,
        )
        StudyContentMode.EXAM_PLAN,
        StudyContentMode.LANGUAGE_DECK,
        StudyContentMode.READING_STUDY,
        StudyContentMode.GENERAL_SKILL,
        -> StudyContentModeCapabilities(
            supportsCodeBlocks = false,
            supportsTermuxRuntime = false,
            supportsCodingExercises = false,
        )
    }

fun contentModeOrDefault(rawName: String?): StudyContentMode =
    StudyContentMode.entries.firstOrNull { mode -> mode.name == rawName } ?: StudyContentMode.EXAM_PLAN

fun defaultContentModeForStudyType(type: StudyType): StudyContentMode = when (type) {
    StudyType.PUBLIC_EXAMINATION,
    StudyType.CERTIFICATION,
    StudyType.UNIVERSITY,
    StudyType.ENTRANCE_EXAMINATION,
    -> StudyContentMode.EXAM_PLAN
    StudyType.FREE_STUDY -> StudyContentMode.GENERAL_SKILL
}
