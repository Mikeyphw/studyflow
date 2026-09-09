package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object StudyFlowLinkCodec {
    val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(envelope: StudyFlowLinkEnvelope): String = json.encodeToString(envelope)

    fun decode(encoded: String): StudyFlowLinkEnvelope =
        json.decodeFromString<StudyFlowLinkEnvelope>(encoded).also(::requireSupportedMajor)

    fun <T> encodePayload(
        serializer: SerializationStrategy<T>,
        value: T,
    ): JsonObject = json.encodeToJsonElement(serializer, value).jsonObject

    fun <T> decodePayload(
        deserializer: DeserializationStrategy<T>,
        payload: JsonObject,
    ): T = json.decodeFromJsonElement(deserializer, payload)

    private fun requireSupportedMajor(envelope: StudyFlowLinkEnvelope) {
        require(envelope.protocol.major == StudyFlowLinkProtocol.Current.major) {
            "Unsupported SFLP protocol major ${envelope.protocol.major}"
        }
    }
}
