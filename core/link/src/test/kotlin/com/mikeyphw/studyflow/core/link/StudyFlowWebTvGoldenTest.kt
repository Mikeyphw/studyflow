package com.mikeyphw.studyflow.core.link

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowWebTvGoldenTest {
    @Test
    fun webPeerGoldenVectorIsSharedWithProtocolDirectory() {
        val root = findRepositoryRoot()
        val protocol = root.resolve("protocol/sflp/v1/golden/web-peer-snapshot.json")
        val resource = root.resolve("core/link/src/test/resources/sflp/v1/web-peer-snapshot.json")
        assertTrue(Files.exists(protocol))
        assertTrue(Files.exists(resource))
        val protocolText = Files.readString(protocol)
        val resourceText = Files.readString(resource)
        assertTrue(protocolText.contains("device-web-golden"))
        assertTrue(resourceText.contains("workspace.sync"))
        assertEquals(protocolText, resourceText)
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
