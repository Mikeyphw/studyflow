package com.mikeyphw.studyflow.core.portability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanImportDiffEngineTest {
    @Test
    fun createAsNewMarksEveryIncomingRowAsAdded() {
        val incoming = payload(
            "source",
            table("study_plans", row("id" to "source")),
            table("subjects", row("id" to "s1", "studyPlanId" to "source")),
        )
        val diffs = StudyPlanImportDiffEngine.diff(incoming, null, StudyPlanImportStrategy.CREATE_AS_NEW, null)
        assertEquals(2, diffs.sumOf { it.addedRows })
        assertEquals(0, diffs.sumOf { it.removedRows + it.changedRows })
    }

    @Test
    fun replaceDetectsAddedRemovedChangedAndUnchangedRows() {
        val incoming = payload(
            "source",
            table("study_plans", row("id" to "source", "name" to "Plan")),
            table("subjects",
                row("id" to "same", "studyPlanId" to "source", "name" to "Same"),
                row("id" to "changed", "studyPlanId" to "source", "name" to "New name"),
                row("id" to "added", "studyPlanId" to "source", "name" to "Added"),
            ),
        )
        val current = payload(
            "target",
            table("study_plans", row("id" to "target", "name" to "Plan")),
            table("subjects",
                row("id" to "same", "studyPlanId" to "target", "name" to "Same"),
                row("id" to "changed", "studyPlanId" to "target", "name" to "Old name"),
                row("id" to "removed", "studyPlanId" to "target", "name" to "Removed"),
            ),
        )
        val diff = StudyPlanImportDiffEngine.diff(
            incoming, current, StudyPlanImportStrategy.REPLACE_EXISTING, "target",
        ).first { it.tableName == "subjects" }
        assertEquals(1, diff.addedRows)
        assertEquals(1, diff.removedRows)
        assertEquals(1, diff.changedRows)
        assertEquals(1, diff.unchangedRows)
    }

    @Test
    fun mergeMarksProtectedTablesIgnoredAndEligibleRowsAdded() {
        val incoming = payload(
            "source",
            table("study_plans", row("id" to "source")),
            table("study_targets", row("id" to "target-1", "studyPlanId" to "source")),
            table("subjects", row("id" to "subject-1", "studyPlanId" to "source")),
        )
        val current = payload("target", table("study_plans", row("id" to "target")))
        val diffs = StudyPlanImportDiffEngine.diff(
            incoming, current, StudyPlanImportStrategy.MERGE_COMPATIBLE, "target",
        )
        assertEquals(1, diffs.first { it.tableName == "study_targets" }.ignoredRows)
        assertEquals(1, diffs.first { it.tableName == "subjects" }.addedRows)
        assertTrue("study_targets" in StudyPlanImportCompatibility.MergeSkippedTables)
    }

    private fun payload(planId: String, vararg tables: PortableTable) = StudyPlanPackagePayload(
        manifest = StudyPlanPackageManifest(
            applicationVersion = "1",
            exportedAtEpochMillis = 1,
            sourceStudyPlanId = planId,
            displayName = "Plan",
            exportMode = StudyPlanExportMode.FULL,
            includesPrivateHistory = true,
            includesAnnexMetadata = tables.any { it.name == "annexes" },
            tableRowCounts = tables.associate { it.name to it.rows.size },
        ),
        tables = tables.toList(),
    )

    private fun table(name: String, vararg rows: PortableRow) = PortableTable(name, rows.toList())

    private fun row(vararg values: Pair<String, String>) = PortableRow(
        values.associate { (name, value) -> name to PortableValue(PortableValueType.TEXT, value) },
    )
}
