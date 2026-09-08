package com.mikeyphw.studyflow.core.tvcontrol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Transport-neutral wire contract installed by TVOV22.
 *
 * Android NSD/socket ownership lives in :core:tvlink. This module owns only the framed JSON schema,
 * short-lived pairing offer, expiring session credential, and authorization rules shared by phone
 * and TV. Raw StudyFlow persistence/database models never enter the wire envelope.
 */
object StudyFlowTvWireProtocol {
    const val version = 1
    const val serviceType = "_studyflow-tv._tcp."
    const val maxFrameBytes = 1_048_576
    const val connectTimeoutMillis = 5_000
    const val socketReadTimeoutMillis = 15_000
    const val mainThreadDispatchTimeoutMillis = 5_000L
    const val controlSessionTtlSeconds = 86_400L
    const val maxPairingAttemptsPerOffer = 10
    const val localPairingDiscoveryAndTransportImplemented = true
    const val rawDatabaseRowsForbidden = true
    const val arbitraryUiTreesForbidden = true
}

@Serializable
enum class StudyFlowTvWireOperation {
    PAIRING_INFO,
    PAIR,
    COMMAND,
    PRESENTATION,
    CONTINUITY,
    REVOKE,
    PING,
}

@Serializable
data class StudyFlowTvReceiverEndpoint(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
) {
    init {
        require(serviceName.isNotBlank()) { "TV receiver service name must not be blank." }
        require(hostAddress.isNotBlank()) { "TV receiver host address must not be blank." }
        require(port in 1..65_535) { "TV receiver port must be valid." }
    }
}

@Serializable
data class StudyFlowTvPairingOffer(
    val token: StudyFlowTvPairingToken,
    val deepLink: String,
) {
    init {
        require(deepLink.startsWith("studyflow://tv/pair?")) { "TV pairing deep link must use StudyFlow scheme." }
    }

    fun isActive(nowEpochMillis: Long): Boolean = nowEpochMillis < token.expiresAtEpochMillis
}

@Serializable
data class StudyFlowTvPairingInfo(
    val tvDeviceName: String,
    val pairingActive: Boolean,
    val sessionNonce: String? = null,
    val expiresAtEpochMillis: Long? = null,
) {
    init {
        require(tvDeviceName.isNotBlank()) { "TV pairing info must identify the receiver." }
        if (pairingActive) {
            require(!sessionNonce.isNullOrBlank()) { "Active TV pairing info requires a nonce." }
            require(expiresAtEpochMillis != null) { "Active TV pairing info requires an expiry." }
        }
    }
}

@Serializable
data class StudyFlowTvSessionCredential(
    val sessionId: String,
    val sessionSecret: String,
    val tvDeviceName: String,
    val pairedDeviceName: String,
    val pairedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    init {
        require(sessionId.isNotBlank()) { "TV link session id must not be blank." }
        require(sessionSecret.length >= 32) { "TV link session secret must be at least 32 characters." }
        require(tvDeviceName.isNotBlank()) { "TV link credential must identify the TV." }
        require(pairedDeviceName.isNotBlank()) { "TV link credential must identify the phone." }
        require(expiresAtEpochMillis > pairedAtEpochMillis) { "TV link session must expire after pairing." }
    }

    fun isActive(nowEpochMillis: Long): Boolean = nowEpochMillis < expiresAtEpochMillis

    fun toControlSession(): StudyFlowTvControlSession = StudyFlowTvControlSession.paired(
        sessionId = sessionId,
        pairedDeviceName = pairedDeviceName,
        pairedAtEpochMillis = pairedAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )
}

@Serializable
data class StudyFlowTvWireRequest(
    val requestId: String,
    val wireVersion: Int = StudyFlowTvWireProtocol.version,
    val operation: StudyFlowTvWireOperation,
    val sessionId: String? = null,
    val sessionSecret: String? = null,
    val pairingCode: String? = null,
    val sessionNonce: String? = null,
    val phoneDeviceName: String? = null,
    val command: StudyFlowTvControlCommand? = null,
    val presentation: StudyFlowTvPresentationSnapshot? = null,
    val issuedAtEpochMillis: Long,
) {
    init {
        require(requestId.isNotBlank()) { "TV wire request id must not be blank." }
        require(wireVersion == StudyFlowTvWireProtocol.version) { "Unsupported TV wire protocol version." }
        require(issuedAtEpochMillis >= 0L) { "TV wire request time cannot be negative." }
    }
}

@Serializable
data class StudyFlowTvWireResponse(
    val requestId: String,
    val wireVersion: Int = StudyFlowTvWireProtocol.version,
    val ok: Boolean,
    val reason: String? = null,
    val pairingInfo: StudyFlowTvPairingInfo? = null,
    val credential: StudyFlowTvSessionCredential? = null,
    val applied: Boolean? = null,
    val continuity: StudyFlowTvControlContinuityState? = null,
    val presentationReceipt: StudyFlowTvPresentationReceipt? = null,
    val respondedAtEpochMillis: Long,
) {
    init {
        require(requestId.isNotBlank()) { "TV wire response id must not be blank." }
        require(wireVersion == StudyFlowTvWireProtocol.version) { "Unsupported TV wire protocol version." }
        require(respondedAtEpochMillis >= 0L) { "TV wire response time cannot be negative." }
    }
}

/** Secure-random pairing/session generator whose validation is independent from Android transport. */
class StudyFlowTvPairingEngine(
    private val random: SecureRandom = SecureRandom(),
) {
    fun createOffer(tvDeviceName: String, nowEpochMillis: Long): StudyFlowTvPairingOffer {
        require(tvDeviceName.isNotBlank())
        require(nowEpochMillis >= 0L)
        val pairingCode = random.nextInt(1_000_000).toString().padStart(StudyFlowTvControlProtocol.pairingCodeLength, '0')
        val nonce = randomHex(16)
        val expiresAt = nowEpochMillis + StudyFlowTvControlProtocol.pairingCodeTtlSeconds * 1_000L
        val token = StudyFlowTvPairingToken(
            tvDeviceName = tvDeviceName,
            pairingCode = pairingCode,
            sessionNonce = nonce,
            issuedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = expiresAt,
        )
        val encodedDevice = URLEncoder.encode(tvDeviceName, StandardCharsets.UTF_8.name())
        return StudyFlowTvPairingOffer(
            token = token,
            deepLink = "studyflow://tv/pair?code=$pairingCode&nonce=$nonce&device=$encodedDevice",
        )
    }

    fun accept(
        offer: StudyFlowTvPairingOffer,
        pairingCode: String,
        sessionNonce: String,
        phoneDeviceName: String,
        nowEpochMillis: Long,
    ): StudyFlowTvSessionCredential? {
        if (!offer.isActive(nowEpochMillis)) return null
        if (pairingCode != offer.token.pairingCode) return null
        if (sessionNonce != offer.token.sessionNonce) return null
        if (phoneDeviceName.isBlank()) return null
        return StudyFlowTvSessionCredential(
            sessionId = randomHex(16),
            sessionSecret = randomHex(32),
            tvDeviceName = offer.token.tvDeviceName,
            pairedDeviceName = phoneDeviceName,
            pairedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = nowEpochMillis + StudyFlowTvWireProtocol.controlSessionTtlSeconds * 1_000L,
        )
    }

    fun isAuthorized(
        credential: StudyFlowTvSessionCredential?,
        sessionId: String?,
        sessionSecret: String?,
        nowEpochMillis: Long,
    ): Boolean = credential != null &&
        credential.isActive(nowEpochMillis) &&
        credential.sessionId == sessionId &&
        constantTimeEquals(credential.sessionSecret, sessionSecret)

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return buildString(byteCount * 2) {
            bytes.forEach { byte -> append("%02x".format(byte.toInt() and 0xff)) }
        }
    }

    private fun constantTimeEquals(expected: String, actual: String?): Boolean {
        if (actual == null || expected.length != actual.length) return false
        var difference = 0
        expected.indices.forEach { index ->
            difference = difference or (expected[index].code xor actual[index].code)
        }
        return difference == 0
    }
}

/** Strict JSON + length-prefixed framing used over the local TCP link. */
object StudyFlowTvWireCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encodeRequest(request: StudyFlowTvWireRequest): ByteArray =
        json.encodeToString(request).toByteArray(StandardCharsets.UTF_8)

    fun decodeRequest(bytes: ByteArray): StudyFlowTvWireRequest =
        json.decodeFromString(bytes.toString(StandardCharsets.UTF_8))

    fun encodeResponse(response: StudyFlowTvWireResponse): ByteArray =
        json.encodeToString(response).toByteArray(StandardCharsets.UTF_8)

    fun decodeResponse(bytes: ByteArray): StudyFlowTvWireResponse =
        json.decodeFromString(bytes.toString(StandardCharsets.UTF_8))

    fun writeRequest(output: OutputStream, request: StudyFlowTvWireRequest) =
        writeFrame(output, encodeRequest(request))

    fun readRequest(input: InputStream): StudyFlowTvWireRequest = decodeRequest(readFrame(input))

    fun writeResponse(output: OutputStream, response: StudyFlowTvWireResponse) =
        writeFrame(output, encodeResponse(response))

    fun readResponse(input: InputStream): StudyFlowTvWireResponse = decodeResponse(readFrame(input))

    fun writeFrame(output: OutputStream, payload: ByteArray) {
        require(payload.isNotEmpty()) { "TV wire frame cannot be empty." }
        require(payload.size <= StudyFlowTvWireProtocol.maxFrameBytes) { "TV wire frame exceeds maximum size." }
        DataOutputStream(output).apply {
            writeInt(payload.size)
            write(payload)
            flush()
        }
    }

    fun readFrame(input: InputStream): ByteArray {
        val dataInput = DataInputStream(input)
        val size = dataInput.readInt()
        if (size <= 0 || size > StudyFlowTvWireProtocol.maxFrameBytes) {
            throw IOException("Invalid TV wire frame size: $size")
        }
        return ByteArray(size).also(dataInput::readFully)
    }
}
