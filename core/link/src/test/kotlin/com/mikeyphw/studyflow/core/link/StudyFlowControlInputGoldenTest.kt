package com.mikeyphw.studyflow.core.link

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudyFlowControlInputGoldenTest {
    @Test
    fun controlAndInputGoldenVectorsAreSharedWithProtocolDirectory() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        listOf("control-command.json", "input-event.json").forEach { name ->
            val protocol = File(root, "protocol/sflp/v1/golden/$name")
            val resource = javaClass.classLoader.getResource("sflp/v1/$name")
            assertNotNull(resource)
            assertEquals(protocol.readText().trim(), File(resource.toURI()).readText().trim())
        }
    }

    @Test
    fun schemaListsStableControlAndInputMessageNames() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val schema = File(root, "protocol/sflp/v1/sflp-control-input.schema.json").readText()
        assertTrue(schema.contains("control.command"))
        assertTrue(schema.contains("input.delegation_offer"))
        assertTrue(schema.contains("input.event"))
    }
}
