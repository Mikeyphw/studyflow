package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowJournalFrontier
import com.mikeyphw.studyflow.core.link.StudyFlowReconnectResult
import com.mikeyphw.studyflow.core.link.StudyFlowReconnectStatus
import com.mikeyphw.studyflow.core.link.StudyFlowTransportEndpoint
import com.mikeyphw.studyflow.core.link.StudyFlowTransportScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopOv23RuntimeUxSflpCorrectnessTest {
    @Test
    fun `desktop pairing hints never advertise loopback-only endpoints`() {
        val endpoints = listOf(
            StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, "127.0.0.1", 19001),
            StudyFlowTransportEndpoint(StudyFlowTransportScheme.WEBSOCKET, "localhost", 19002, "/sflp"),
            StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, "192.168.1.44", 19001),
            StudyFlowTransportEndpoint(StudyFlowTransportScheme.WEBSOCKET, "fe80::1%wlan0", 19002, "/sflp"),
        )

        val shareable = DesktopStudyFlowLinkUxPolicy.shareableEndpoints(endpoints).map { it.canonicalUri() }

        assertFalse(shareable.any { it.contains("127.0.0.1") || it.contains("localhost") })
        assertTrue(shareable.any { it.contains("192.168.1.44") })
        assertTrue(shareable.any { it.contains("fe80::1%wlan0") })
    }

    @Test
    fun `manual Desktop SFLP endpoints reject self loopback`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopStudyFlowLinkUxPolicy.validateManualEndpoint(
                StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, "::1", 19001),
            )
        }
        val relay = DesktopStudyFlowLinkUxPolicy.validateManualEndpoint(
            StudyFlowTransportEndpoint(StudyFlowTransportScheme.RELAY, "relay.studyflow.local", path = "/device/abc"),
        )
        assertEquals("relay.studyflow.local", relay.host)
    }

    @Test
    fun `sync summary stays retry-aware instead of claiming global success`() {
        val result = DesktopStudyFlowLinkUxPolicy.syncSummary(
            listOf(
                reconnect(StudyFlowReconnectStatus.COMPLETE),
                reconnect(StudyFlowReconnectStatus.INTERRUPTED, failure = "peer offline"),
            ),
        )

        assertTrue(result.contains("partially complete"))
        assertTrue(result.contains("retryable"))
    }

    @Test
    fun `link capability message distinguishes no endpoints from ready peers`() {
        val noEndpoints = DesktopStudyFlowLinkState(
            localDeviceId = "desktop-1",
            localEndpoints = emptyList(),
            discoveredPeers = emptyList(),
            trustedPeers = emptyList(),
            pairingCode = null,
            pairingQrPayload = null,
            pendingOutgoingOperations = 0,
            presence = emptyList(),
            status = "Listening",
            healthMessage = "pending=0 endpoints=0 trusted=0",
        )
        assertTrue(DesktopStudyFlowLinkUxPolicy.capabilityMessage(noEndpoints).contains("No shareable LAN endpoint"))

        val ready = noEndpoints.copy(
            localEndpoints = listOf("sflp+tcp://192.168.1.44:19001"),
            trustedPeers = listOf(
                DesktopStudyFlowTrustedPeer("phone-1", "Phone", "ANDROID", trusted = true, endpointCount = 1, reachable = true),
            ),
        )
        assertTrue(DesktopStudyFlowLinkUxPolicy.capabilityMessage(ready).contains("ready"))
    }

    private fun reconnect(
        status: StudyFlowReconnectStatus,
        failure: String? = null,
    ): StudyFlowReconnectResult = StudyFlowReconnectResult(
        status = status,
        pushedOperations = if (status == StudyFlowReconnectStatus.COMPLETE) 1 else 0,
        pulledOperations = 0,
        duplicateOperations = 0,
        pushBatches = 0,
        pullBatches = 0,
        acknowledgedQueueEntries = 0,
        localFrontier = StudyFlowJournalFrontier(),
        remoteFrontier = null,
        failure = failure,
    )
}
