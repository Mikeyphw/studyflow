package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFlowLinkCodecGoldenTest {
    private val comparisonJson = Json { ignoreUnknownKeys = false }

    @Test
    fun goldenMessagesRoundTripSemantically() {
        listOf(
            "hello-request.json",
            "hello-response.json",
            "capabilities-event.json",
            "incompatible-protocol-error.json",
        ).forEach { fileName ->
            val source = resourceText(fileName)
            val decoded = StudyFlowLinkCodec.decode(source)
            val encoded = StudyFlowLinkCodec.encode(decoded)
            assertEquals(
                comparisonJson.parseToJsonElement(source),
                comparisonJson.parseToJsonElement(encoded),
            )
        }
    }

    @Test
    fun peerDescriptorPayloadUsesTheCanonicalShape() {
        val peer = StudyFlowLinkPeerDescriptor(
            deviceId = StudyFlowDeviceId("device-desktop-1"),
            displayName = "Study PC",
            clientKind = StudyFlowLinkClientKind.DESKTOP,
            capabilities = listOf(
                StudyFlowLinkCapabilities.WorkspaceRead,
                StudyFlowLinkCapabilities.WorkspaceWrite,
                StudyFlowLinkCapabilities.DeviceControl,
            ),
        )
        val payload = StudyFlowLinkCodec.encodePayload(StudyFlowLinkPeerDescriptor.serializer(), peer)
        val decoded = StudyFlowLinkCodec.decodePayload(StudyFlowLinkPeerDescriptor.serializer(), payload)

        assertEquals(peer, decoded)
    }

    private fun resourceText(fileName: String): String {
        val path = "sflp/v1/$fileName"
        val resource = checkNotNull(javaClass.classLoader.getResource(path)) { "Missing resource $path" }
        return resource.readText()
    }
}
