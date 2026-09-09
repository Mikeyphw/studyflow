package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class StudyFlowLinkProtocolTest {
    @Test
    fun negotiationChoosesHighestOverlappingMinor() {
        val local = StudyFlowLinkProtocolSupport(major = 1, minMinor = 0, maxMinor = 3)
        val remote = StudyFlowLinkProtocolSupport(major = 1, minMinor = 1, maxMinor = 2)

        assertEquals(
            StudyFlowLinkProtocolVersion(major = 1, minor = 2),
            StudyFlowLinkProtocol.negotiate(local, remote),
        )
    }

    @Test
    fun negotiationRejectsDifferentMajor() {
        val local = StudyFlowLinkProtocolSupport(major = 1, minMinor = 0, maxMinor = 0)
        val remote = StudyFlowLinkProtocolSupport(major = 2, minMinor = 0, maxMinor = 0)

        assertNull(StudyFlowLinkProtocol.negotiate(local, remote))
    }

    @Test
    fun responseRequiresCorrelationId() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyFlowLinkEnvelope(
                messageId = StudyFlowMessageId("msg-response-1"),
                workspaceId = StudyFlowWorkspaceId("workspace-main"),
                senderDeviceId = StudyFlowDeviceId("device-desktop-1"),
                messageType = StudyFlowLinkMessageType.RESPONSE,
                name = StudyFlowLinkMessageNames.Hello,
                sentAtEpochMillis = 1L,
            )
        }
    }

    @Test
    fun capabilityNamesAreStableAndUnique() {
        assertEquals(
            StudyFlowLinkCapabilities.V1.size,
            StudyFlowLinkCapabilities.V1.distinct().size,
        )
        assertEquals(
            StudyFlowLinkCapability("workspace.read"),
            StudyFlowLinkCapabilities.WorkspaceRead,
        )
    }
}
