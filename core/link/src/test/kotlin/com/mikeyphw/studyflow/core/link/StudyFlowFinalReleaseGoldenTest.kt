package com.mikeyphw.studyflow.core.link

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyFlowFinalReleaseGoldenTest {
    @Test
    fun finalReleaseGoldenVectorIsSharedWithProtocolDirectory() {
        val root = generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        val protocol = Files.readString(
            root.resolve("protocol/sflp/v1/golden/final-release-validation-plan.json"),
        ).trim()
        val resource = checkNotNull(javaClass.getResource("/sflp/v1/final-release-validation-plan.json"))

        assertEquals(protocol, Files.readString(Path.of(resource.toURI())).trim())
        assertTrue(StudyFlowSflp24FinalReleaseGate.FinalTaskName in protocol)
        assertTrue("verifySflp22Sflp23AccountBackupValidation" in protocol)
    }
}
