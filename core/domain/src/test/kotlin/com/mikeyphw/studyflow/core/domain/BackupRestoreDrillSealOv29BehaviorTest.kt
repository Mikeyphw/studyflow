package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageManifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreDrillSealOv29BehaviorTest {
    @Test
    fun `modern backup coverage rejects packages that omit specialized or annex table families`() {
        val legacyOnly = manifest(mapOf("study_plans" to 1, "topics" to 2, "questions" to 3))
        val modern = manifest(
            mapOf(
                "study_plans" to 1,
                "programming_exercises" to 1,
                "code_snippets" to 1,
                "language_idiom_cards" to 1,
                "language_vocabulary_cards" to 1,
                "material_annexes" to 1,
            ),
        )

        assertFalse(BackupRestoreDrillSeal.modernDataCoverageVerified(legacyOnly))
        assertTrue(BackupRestoreDrillSeal.modernDataCoverageVerified(modern))
    }

    private fun manifest(rows: Map<String, Int>): StudyPlanPackageManifest = StudyPlanPackageManifest(
        applicationVersion = "test",
        exportedAtEpochMillis = 1L,
        sourceStudyPlanId = "plan",
        displayName = "Plan",
        exportMode = StudyPlanExportMode.PRIVATE_STRIPPED,
        includesPrivateHistory = false,
        includesAnnexMetadata = true,
        includesAnnexPayloads = true,
        tableRowCounts = rows,
        metadataPrivacyMode = true,
        redactedSections = emptySet(),
    )
}
