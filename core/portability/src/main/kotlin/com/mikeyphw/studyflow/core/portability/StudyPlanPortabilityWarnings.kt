package com.mikeyphw.studyflow.core.portability

object StudyPlanPortabilityWarnings {
    fun forManifest(manifest: StudyPlanPackageManifest): List<StudyPlanPortabilityWarning> = buildList {
        if (manifest.includesAnnexMetadata && !manifest.includesAnnexPayloads) {
            add(StudyPlanPortabilityWarning(StudyPlanPortabilityWarningType.ANNEX_METADATA_ONLY))
        }
        if (manifest.includesAnnexPayloads) {
            add(StudyPlanPortabilityWarning(StudyPlanPortabilityWarningType.ANNEX_PAYLOADS_INCLUDED))
        }
        if (manifest.exportMode == StudyPlanExportMode.TEMPLATE) {
            add(StudyPlanPortabilityWarning(StudyPlanPortabilityWarningType.TEMPLATE_WITHOUT_HISTORY))
        }
        if (manifest.redactedSections.isNotEmpty()) {
            add(
                StudyPlanPortabilityWarning(
                    type = StudyPlanPortabilityWarningType.PRIVACY_REDACTIONS,
                    redactedSections = manifest.redactedSections,
                ),
            )
        }
    }
}
