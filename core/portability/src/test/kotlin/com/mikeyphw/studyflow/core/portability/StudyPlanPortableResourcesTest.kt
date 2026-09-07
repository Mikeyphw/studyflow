package com.mikeyphw.studyflow.core.portability

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlanPortableResourcesTest {
    @Test
    fun mimeTypesResolveToStableOpenHandlers() {
        assertEquals(PortableResourceHandler.DOCUMENT, StudyPlanPortableResources.handlerForMimeType("application/pdf"))
        assertEquals(PortableResourceHandler.DOCUMENT, StudyPlanPortableResources.handlerForMimeType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ))
        assertEquals(
            PortableResourceHandler.TEXT,
            StudyPlanPortableResources.handlerForMimeType("text/markdown; charset=utf-8"),
        )
        assertEquals(PortableResourceHandler.IMAGE, StudyPlanPortableResources.handlerForMimeType("image/png"))
        assertEquals(PortableResourceHandler.AUDIO, StudyPlanPortableResources.handlerForMimeType("audio/mpeg"))
        assertEquals(PortableResourceHandler.VIDEO, StudyPlanPortableResources.handlerForMimeType("video/mp4"))
        assertEquals(
            PortableResourceHandler.GENERIC,
            StudyPlanPortableResources.handlerForMimeType("application/octet-stream"),
        )
    }

    @Test
    fun manifestResourcesPreserveFilenameMimeChecksumAndSize() {
        val manifest = StudyPlanPackageManifest(
            applicationVersion = "1",
            exportedAtEpochMillis = 1,
            sourceStudyPlanId = "plan",
            displayName = "Plan",
            exportMode = StudyPlanExportMode.FULL,
            includesPrivateHistory = true,
            includesAnnexMetadata = true,
            includesAnnexPayloads = true,
            annexPayloads = listOf(
                PortableAnnexPayload(
                    annexId = "annex",
                    entryName = "annex-payloads/annex/notes.md",
                    fileName = "notes.md",
                    mimeType = "text/markdown",
                    byteSize = 12,
                    checksumSha256 = "A".repeat(64),
                ),
            ),
            tableRowCounts = mapOf("study_plans" to 1, "annexes" to 1),
        )
        assertEquals(
            listOf(
                StudyPlanPortableResource(
                    fileName = "notes.md",
                    mimeType = "text/markdown",
                    byteSize = 12,
                    checksumSha256 = "a".repeat(64),
                    handler = PortableResourceHandler.TEXT,
                ),
            ),
            StudyPlanPortableResources.forManifest(manifest),
        )
    }
}
