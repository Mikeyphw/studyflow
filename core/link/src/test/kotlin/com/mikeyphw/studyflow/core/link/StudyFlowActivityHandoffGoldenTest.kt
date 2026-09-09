package com.mikeyphw.studyflow.core.link

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowActivityHandoffGoldenTest {
    @Test
    fun activityHandoffGoldenVectorIsSharedWithProtocolDirectory() {
        val root = findRepositoryRoot()
        val protocol = root.resolve("protocol/sflp/v1/golden/activity-handoff-offer.json")
        val resource = root.resolve("core/link/src/test/resources/sflp/v1/activity-handoff-offer.json")
        assertTrue(Files.exists(protocol))
        assertTrue(Files.exists(resource))
        assertEquals(Files.readString(protocol), Files.readString(resource))
        assertTrue(Files.readString(protocol).contains("activity.handoff_offer"))
    }

    @Test
    fun webTvReceiverGoldenVectorIsSharedWithProtocolDirectory() {
        val root = findRepositoryRoot()
        val protocol = root.resolve("protocol/sflp/v1/golden/web-tv-receiver-offer.json")
        val resource = root.resolve("core/link/src/test/resources/sflp/v1/web-tv-receiver-offer.json")
        assertTrue(Files.exists(protocol))
        assertTrue(Files.exists(resource))
        assertEquals(Files.readString(protocol), Files.readString(resource))
        assertTrue(Files.readString(protocol).contains("studyflow.sflp.web_tv.receiver.v1"))
    }

    private fun findRepositoryRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts")) &&
                Files.exists(current.resolve("protocol/sflp/v1"))
            ) {
                return current
            }
            current = current.parent
        }
        return Paths.get("").toAbsolutePath()
    }
}
