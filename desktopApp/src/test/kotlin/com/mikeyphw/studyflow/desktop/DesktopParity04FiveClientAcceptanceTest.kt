package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.portability.CanonicalPortableJson
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopParity04FiveClientAcceptanceTest {
    @Test
    fun desktopConsumesParity04CorpusAndKeepsTvAuthorityBoundary() {
        val parity = fixture("protocol/parity/v1/five-client-scenarios.json").jsonObject
        assertEquals("PARITY04", parity.getValue("milestone").jsonPrimitive.content)
        assertEquals(10, parity.getValue("directPairs").jsonArray.size)
        assertEquals(12, parity.getValue("scenarios").jsonArray.size)

        val desktop = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.DESKTOP).toSet()
        assertTrue(StudyFlowLinkCapabilities.WorkspaceWrite in desktop)
        assertTrue(StudyFlowLinkCapabilities.DevicePresentationSource in desktop)
        val androidTv = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID_TV).toSet()
        val webTv = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.WEB_TV).toSet()
        listOf(androidTv, webTv).forEach { capabilities ->
            assertTrue(StudyFlowLinkCapabilities.DevicePresentationSink in capabilities)
            assertFalse(StudyFlowLinkCapabilities.WorkspaceWrite in capabilities)
            assertFalse(StudyFlowLinkCapabilities.BackupImport in capabilities)
        }
    }

    @Test
    fun parity04ReusesAllWeb20SemanticsAndCanonicalPortableJson() {
        val web20 = fixture("protocol/semantic/v1/golden-vectors.json").jsonObject
        assertEquals(40, web20.getValue("vectors").jsonArray.size)
        val portable = web20.getValue("vectors").jsonArray
            .first { it.jsonObject.getValue("id").jsonPrimitive.content == "portable-1" }
            .jsonObject
        val input = portable.getValue("input").jsonObject.getValue("value").toString()
        val expected = portable.getValue("expected").jsonObject.getValue("canonical").jsonPrimitive.content
        assertEquals(expected, CanonicalPortableJson.canonicalize(input))
    }

    private fun fixture(relative: String) = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val file = File(dir, relative)
            if (file.isFile) return@run Json.parseToJsonElement(file.readText())
            dir = dir.parentFile ?: dir
        }
        error("Fixture not found: $relative")
    }
}
