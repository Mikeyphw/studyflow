package com.mikeyphw.studyflow.core.portability

object StudyPlanImportCompatibility {
    val MergeSingletonTables: Set<String> = setOf(
        "study_targets", "study_cycles", "study_strategies", "study_plan_settings",
        "programming_course_profiles", "termux_runtime_preferences", "language_deck_profiles",
    )
    val MergeSkippedTables: Set<String> = MergeSingletonTables
}
