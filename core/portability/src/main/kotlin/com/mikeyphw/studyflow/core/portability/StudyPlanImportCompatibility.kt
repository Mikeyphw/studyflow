package com.mikeyphw.studyflow.core.portability

object StudyPlanImportCompatibility {
    val MergeSkippedTables: Set<String> = setOf(
        "study_targets",
        "exam_stages",
        "exam_notice_revisions",
        "legislation_documents",
        "legislation_provisions",
        "legislation_evidence_links",
        "study_cycles",
        "study_cycle_items",
        "study_strategies",
        "study_plan_settings",
        "programming_course_profiles",
        "termux_runtime_preferences",
    )
}
