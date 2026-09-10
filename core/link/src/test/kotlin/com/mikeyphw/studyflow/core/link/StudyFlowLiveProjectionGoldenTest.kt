package com.mikeyphw.studyflow.core.link

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowLiveProjectionGoldenTest {
    @Test
    fun liveSessionGoldenVectorIsSharedWithProtocolDirectory() {
        val root = findRepositoryRoot()
        val protocol = root.resolve("protocol/sflp/v1/golden/live-session-event.json")
        val resource = root.resolve("core/link/src/test/resources/sflp/v1/live-session-event.json")
        assertTrue(Files.exists(protocol))
        assertTrue(Files.exists(resource))
        assertEquals(Files.readString(protocol), Files.readString(resource))
        val envelope = StudyFlowLinkCodec.decode(Files.readString(protocol))
        assertEquals(StudyFlowLiveSessionMessageNames.Cursor, envelope.name)
        val event = StudyFlowLinkCodec.decodePayload(StudyFlowLiveSessionEvent.serializer(), envelope.payload)
        assertEquals(StudyFlowLiveSessionId("session-live"), event.sessionId)
    }

    @Test
    fun presentationFrameGoldenVectorIsSharedWithProtocolDirectory() {
        val root = findRepositoryRoot()
        val protocol = root.resolve("protocol/sflp/v1/golden/presentation-projection-frame.json")
        val resource = root.resolve("core/link/src/test/resources/sflp/v1/presentation-projection-frame.json")
        assertTrue(Files.exists(protocol))
        assertTrue(Files.exists(resource))
        assertEquals(Files.readString(protocol), Files.readString(resource))
        val envelope = StudyFlowLinkCodec.decode(Files.readString(protocol))
        assertEquals(StudyFlowPresentationProjectionMessageNames.Frame, envelope.name)
        val frame = StudyFlowLinkCodec.decodePayload(StudyFlowPresentationFrame.serializer(), envelope.payload)
        assertEquals(StudyFlowProjectionId("projection-main"), frame.projectionId)
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
