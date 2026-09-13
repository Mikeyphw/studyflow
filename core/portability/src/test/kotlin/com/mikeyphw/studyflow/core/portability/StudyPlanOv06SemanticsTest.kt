package com.mikeyphw.studyflow.core.portability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanOv06SemanticsTest {
    @Test fun schemaAndPurposeContractsAreExplicit() {
        assertEquals(2, StudyPlanPackageSchemaVersion)
        assertEquals(1, StudyPlanPackageMinimumReadableSchemaVersion)
        assertEquals(StudyPlanPackagePurpose.PORTABLE, manifest().packagePurpose)
    }

    @Test fun structureAndTemplateStripProgressAndDeviceState() {
        listOf(StudyPlanExportMode.STRUCTURE_ONLY, StudyPlanExportMode.TEMPLATE).forEach { mode ->
            val options = StudyPlanExportOptions(mode = mode)
            assertTrue(StudyPlanPrivacyPolicy.HistorySection in StudyPlanPrivacyPolicy.redactedSections(options))
            val legislation = StudyPlanPrivacyPolicy.sanitizeRow(
                "legislation_provisions", row("read" to int(1), "important" to int(1)), options,
            )
            assertEquals("0", legislation.values.getValue("read").value)
            val cycle = StudyPlanPrivacyPolicy.sanitizeRow("study_cycles", row("currentItemId" to text("item")), options)
            assertEquals(PortableValueType.NULL, cycle.values.getValue("currentItemId").type)
            val runtime = StudyPlanPrivacyPolicy.sanitizeRow(
                "termux_runtime_preferences",
                row("enabled" to int(1), "workspaceRoot" to text("/private/path"),
                    "defaultRunCommand" to text("python x.py"), "defaultTestCommand" to text("pytest"),
                    "allowedCommandTemplatesJson" to text("[\"private\"]")), options,
            )
            assertEquals("0", runtime.values.getValue("enabled").value)
            assertEquals("~/studyflow/courses", runtime.values.getValue("workspaceRoot").value)
            assertEquals("[]", runtime.values.getValue("allowedCommandTemplatesJson").value)
        }
    }

    @Test fun projectedDiffConsumesProductionRemappedIds() {
        val incoming = payload(table("study_plans", row("id" to text("target"))),
            table("subjects", row("id" to text("target:source"), "studyPlanId" to text("target"))))
        val current = payload(table("study_plans", row("id" to text("target"))),
            table("subjects", row("id" to text("target:old"), "studyPlanId" to text("target"))))
        val diff = StudyPlanImportDiffEngine.diffProjected(incoming,current,StudyPlanImportStrategy.REPLACE_EXISTING)
            .first { it.tableName == "subjects" }
        assertEquals(1,diff.addedRows); assertEquals(1,diff.removedRows)
    }

    private fun manifest() = StudyPlanPackageManifest(
        applicationVersion="ov06", exportedAtEpochMillis=1, sourceStudyPlanId="target", displayName="Plan",
        exportMode=StudyPlanExportMode.FULL, includesPrivateHistory=true, includesAnnexMetadata=false,
        tableRowCounts=mapOf("study_plans" to 1),
    )
    private fun payload(vararg tables: PortableTable) = StudyPlanPackagePayload(
        manifest().copy(tableRowCounts=tables.associate { it.name to it.rows.size }), tables.toList())
    private fun table(name:String,vararg rows:PortableRow)=PortableTable(name,rows.toList())
    private fun row(vararg values:Pair<String,PortableValue>)=PortableRow(values.toMap())
    private fun text(v:String)=PortableValue(PortableValueType.TEXT,v)
    private fun int(v:Int)=PortableValue(PortableValueType.INTEGER,v.toString())
}
