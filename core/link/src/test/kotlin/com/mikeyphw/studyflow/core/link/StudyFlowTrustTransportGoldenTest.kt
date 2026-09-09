package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFlowTrustTransportGoldenTest {
    private val comparisonJson = Json { ignoreUnknownKeys = false }

    @Test
    fun trustAndTransportGoldenObjectsRoundTripSemantically() {
        roundTrip("pairing-request.json", StudyFlowPairingRequest.serializer())
        roundTrip("pairing-grant.json", StudyFlowPairingGrant.serializer())
        roundTrip("reconnect-challenge.json", StudyFlowReconnectChallenge.serializer())
        roundTrip("transport-endpoint.json", StudyFlowTransportEndpoint.serializer())
    }

    private fun <T> roundTrip(name: String, serializer: KSerializer<T>) {
        val source = resourceText(name)
        val decoded = StudyFlowLinkCodec.json.decodeFromString(serializer, source)
        val encoded = StudyFlowLinkCodec.json.encodeToString(serializer, decoded)
        assertEquals(
            comparisonJson.parseToJsonElement(source),
            comparisonJson.parseToJsonElement(encoded),
        )
    }

    private fun resourceText(fileName: String): String {
        val resource = checkNotNull(javaClass.classLoader.getResource("sflp/v1/$fileName"))
        return resource.readText()
    }
}
