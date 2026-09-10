package com.mikeyphw.studyflow.core.link

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyFlowAccountBackupGoldenTest {
    @Test
    fun accountBackupReleasePlanGoldenIsSharedWithProtocolDirectory() {
        val root = generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        val protocol = Files.readString(root.resolve("protocol/sflp/v1/golden/account-backup-release-plan.json")).trim()
        val resource = checkNotNull(javaClass.getResource("/sflp/v1/account-backup-release-plan.json"))
        assertEquals(protocol, Files.readString(Path.of(resource.toURI())).trim())
        assertTrue(StudyFlowAccountBackupMessageNames.ReleaseValidationPlan in protocol)
    }
}
