package com.mikeyphw.studyflow.core.link

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class StudyFlowPairingOffer(
    val pairingId: StudyFlowPairingId,
    val host: StudyFlowLinkPeerDescriptor,
    val sessionNonce: String,
    val pairingCode: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val endpointHints: List<String> = emptyList(),
) {
    init {
        require(sessionNonce.length == 48 && sessionNonce.all { it in "0123456789abcdef" }) {
            "Pairing nonce must be 24-byte lower-case hexadecimal"
        }
        require(pairingCode.length == StudyFlowTrustManager.DefaultPairingCodeDigits) {
            "Pairing code must be six digits"
        }
        require(pairingCode.all(Char::isDigit)) { "Pairing code must contain digits only" }
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Pairing offer must expire after creation" }
        require(endpointHints.distinct().size == endpointHints.size) { "Endpoint hints must be unique" }
    }

    fun isActive(nowEpochMillis: Long): Boolean = nowEpochMillis in createdAtEpochMillis until expiresAtEpochMillis
}

@Serializable
data class StudyFlowPairingPublicOffer(
    val pairingId: StudyFlowPairingId,
    val host: StudyFlowLinkPeerDescriptor,
    val sessionNonce: String,
    val expiresAtEpochMillis: Long,
    val endpointHints: List<String> = emptyList(),
    val qrPayload: String,
) {
    init {
        require(sessionNonce.length == 48 && sessionNonce.all { it in "0123456789abcdef" }) {
            "Pairing nonce must be 24-byte lower-case hexadecimal"
        }
        require(expiresAtEpochMillis > 0L) { "Pairing expiry must be positive" }
        require(endpointHints.distinct().size == endpointHints.size) { "Endpoint hints must be unique" }
    }
}

@Serializable
data class StudyFlowPairingRequest(
    val pairingId: StudyFlowPairingId,
    val sessionNonce: String,
    val pairingCode: String,
    val peer: StudyFlowLinkPeerDescriptor,
    val requestedAtEpochMillis: Long,
) {
    init {
        require(sessionNonce.length == 48 && sessionNonce.all { it in "0123456789abcdef" }) {
            "Pairing nonce must be 24-byte lower-case hexadecimal"
        }
        require(pairingCode.length == StudyFlowTrustManager.DefaultPairingCodeDigits) {
            "Pairing code must be six digits"
        }
        require(pairingCode.all(Char::isDigit)) { "Pairing code must contain digits only" }
        require(requestedAtEpochMillis >= 0L) { "Pairing request timestamp must not be negative" }
    }
}

@Serializable
data class StudyFlowPairingGrant(
    val pairingId: StudyFlowPairingId,
    val host: StudyFlowLinkPeerDescriptor,
    val peerDeviceId: StudyFlowDeviceId,
    val trustSecret: StudyFlowTrustSecret,
    val pairedAtEpochMillis: Long,
)

@Serializable
data class StudyFlowPairingQrPayload(
    val version: Int = 1,
    val pairingId: StudyFlowPairingId,
    val sessionNonce: String,
    val pairingCode: String,
    val endpointHints: List<String>,
) {
    init {
        require(version == 1) { "Unsupported pairing QR version $version" }
        require(sessionNonce.length == 48 && sessionNonce.all { it in "0123456789abcdef" }) {
            "Pairing nonce must be 24-byte lower-case hexadecimal"
        }
        require(pairingCode.length == StudyFlowTrustManager.DefaultPairingCodeDigits) {
            "Pairing code must be six digits"
        }
        require(pairingCode.all(Char::isDigit)) { "Pairing code must contain digits only" }
        require(endpointHints.distinct().size == endpointHints.size) { "Endpoint hints must be unique" }
    }
}

sealed class StudyFlowPairingResult {
    data class Accepted(val grant: StudyFlowPairingGrant) : StudyFlowPairingResult()
    data class Rejected(val reason: StudyFlowPairingRejection) : StudyFlowPairingResult()
}

enum class StudyFlowPairingRejection {
    NOT_ACTIVE,
    EXPIRED,
    NONCE_MISMATCH,
    CODE_MISMATCH,
    ATTEMPT_LIMIT,
    SELF_PAIRING,
}

class StudyFlowPairingAuthority(
    private val trustManager: StudyFlowTrustManager,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private data class ActiveOffer(
        val offer: StudyFlowPairingOffer,
        var failedAttempts: Int = 0,
    )

    private val activeOffers = linkedMapOf<StudyFlowPairingId, ActiveOffer>()

    fun createOffer(
        nowEpochMillis: Long,
        endpointHints: List<String> = emptyList(),
        lifetimeMillis: Long = StudyFlowTrustManager.DefaultPairingOfferLifetimeMillis,
    ): StudyFlowPairingPublicOffer {
        require(lifetimeMillis in 1..StudyFlowTrustManager.DefaultPairingOfferLifetimeMillis) {
            "Pairing lifetime is out of range"
        }
        val offer = StudyFlowPairingOffer(
            pairingId = newPairingId(),
            host = trustManager.localIdentity.descriptor,
            sessionNonce = randomHex(24, secureRandom),
            pairingCode = newPairingCode(secureRandom),
            createdAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = nowEpochMillis + lifetimeMillis,
            endpointHints = endpointHints.distinct(),
        )
        synchronized(activeOffers) {
            activeOffers.entries.removeIf { !it.value.offer.isActive(nowEpochMillis) }
            activeOffers[offer.pairingId] = ActiveOffer(offer)
        }
        return offer.toPublicOffer()
    }

    fun activeOffer(pairingId: StudyFlowPairingId, nowEpochMillis: Long): StudyFlowPairingPublicOffer? {
        val active = synchronized(activeOffers) { activeOffers[pairingId] } ?: return null
        if (!active.offer.isActive(nowEpochMillis)) {
            synchronized(activeOffers) { activeOffers.remove(pairingId) }
            return null
        }
        return active.offer.toPublicOffer()
    }

    fun pairingCode(pairingId: StudyFlowPairingId, nowEpochMillis: Long): String? {
        val active = synchronized(activeOffers) { activeOffers[pairingId] } ?: return null
        return active.offer.takeIf { it.isActive(nowEpochMillis) }?.pairingCode
    }

    fun accept(request: StudyFlowPairingRequest, nowEpochMillis: Long): StudyFlowPairingResult {
        val active = synchronized(activeOffers) { activeOffers[request.pairingId] }
            ?: return StudyFlowPairingResult.Rejected(StudyFlowPairingRejection.NOT_ACTIVE)
        val offer = active.offer
        if (!offer.isActive(nowEpochMillis)) {
            synchronized(activeOffers) { activeOffers.remove(request.pairingId) }
            return StudyFlowPairingResult.Rejected(StudyFlowPairingRejection.EXPIRED)
        }
        if (request.peer.deviceId == trustManager.localIdentity.descriptor.deviceId) {
            return StudyFlowPairingResult.Rejected(StudyFlowPairingRejection.SELF_PAIRING)
        }
        if (request.sessionNonce != offer.sessionNonce) {
            return rejectFailedAttempt(request.pairingId, StudyFlowPairingRejection.NONCE_MISMATCH)
        }
        if (request.pairingCode != offer.pairingCode) {
            return rejectFailedAttempt(request.pairingId, StudyFlowPairingRejection.CODE_MISMATCH)
        }
        val secret = StudyFlowTrustSecret(randomHex(32, secureRandom))
        trustManager.remember(request.peer, secret, nowEpochMillis)
        synchronized(activeOffers) { activeOffers.remove(request.pairingId) }
        return StudyFlowPairingResult.Accepted(
            StudyFlowPairingGrant(
                pairingId = request.pairingId,
                host = trustManager.localIdentity.descriptor,
                peerDeviceId = request.peer.deviceId,
                trustSecret = secret,
                pairedAtEpochMillis = nowEpochMillis,
            ),
        )
    }

    fun cancel(pairingId: StudyFlowPairingId): Boolean =
        synchronized(activeOffers) { activeOffers.remove(pairingId) != null }

    private fun rejectFailedAttempt(
        pairingId: StudyFlowPairingId,
        reason: StudyFlowPairingRejection,
    ): StudyFlowPairingResult {
        val attemptLimitReached = synchronized(activeOffers) {
            val state = activeOffers[pairingId] ?: return@synchronized false
            state.failedAttempts += 1
            if (state.failedAttempts >= StudyFlowTrustManager.DefaultPairingAttemptLimit) {
                activeOffers.remove(pairingId)
                true
            } else {
                false
            }
        }
        return StudyFlowPairingResult.Rejected(
            if (attemptLimitReached) StudyFlowPairingRejection.ATTEMPT_LIMIT else reason,
        )
    }
}

class StudyFlowPairingClient(
    private val trustManager: StudyFlowTrustManager,
) {
    fun requestFromQr(
        qrPayload: String,
        nowEpochMillis: Long,
    ): StudyFlowPairingRequest {
        val payload = StudyFlowPairingQrCodec.decode(qrPayload)
        return StudyFlowPairingRequest(
            pairingId = payload.pairingId,
            sessionNonce = payload.sessionNonce,
            pairingCode = payload.pairingCode,
            peer = trustManager.localIdentity.descriptor,
            requestedAtEpochMillis = nowEpochMillis,
        )
    }

    fun requestFromCode(
        offer: StudyFlowPairingPublicOffer,
        pairingCode: String,
        nowEpochMillis: Long,
    ): StudyFlowPairingRequest = StudyFlowPairingRequest(
        pairingId = offer.pairingId,
        sessionNonce = offer.sessionNonce,
        pairingCode = pairingCode,
        peer = trustManager.localIdentity.descriptor,
        requestedAtEpochMillis = nowEpochMillis,
    )

    fun acceptGrant(grant: StudyFlowPairingGrant, nowEpochMillis: Long): StudyFlowTrustedPeerRecord {
        require(grant.peerDeviceId == trustManager.localIdentity.descriptor.deviceId) {
            "Pairing grant targets another device"
        }
        return trustManager.remember(grant.host, grant.trustSecret, nowEpochMillis)
    }
}

object StudyFlowPairingQrCodec {
    private const val SchemePrefix = "studyflow://pair?data="

    fun encode(payload: StudyFlowPairingQrPayload): String {
        val json = StudyFlowLinkCodec.json.encodeToString(payload)
        return SchemePrefix + URLEncoder.encode(json, Charsets.UTF_8.name())
    }

    fun decode(encoded: String): StudyFlowPairingQrPayload {
        require(encoded.startsWith(SchemePrefix)) { "Unsupported StudyFlow pairing QR payload" }
        val json = URLDecoder.decode(encoded.removePrefix(SchemePrefix), Charsets.UTF_8.name())
        return StudyFlowLinkCodec.json.decodeFromString<StudyFlowPairingQrPayload>(json).also {
            require(it.version == 1) { "Unsupported pairing QR version ${it.version}" }
        }
    }
}

object StudyFlowPairingMessageNames {
    const val Offer = "link.pairing_offer"
    const val Pair = "link.pair"
    const val Grant = "link.pairing_grant"
    const val ReconnectChallenge = "link.reconnect_challenge"
    const val ReconnectProof = "link.reconnect_proof"
    const val Revoke = "link.revoke"
}

private fun StudyFlowPairingOffer.toPublicOffer(): StudyFlowPairingPublicOffer {
    val qrPayload = StudyFlowPairingQrCodec.encode(
        StudyFlowPairingQrPayload(
            pairingId = pairingId,
            sessionNonce = sessionNonce,
            pairingCode = pairingCode,
            endpointHints = endpointHints,
        ),
    )
    return StudyFlowPairingPublicOffer(
        pairingId = pairingId,
        host = host,
        sessionNonce = sessionNonce,
        expiresAtEpochMillis = expiresAtEpochMillis,
        endpointHints = endpointHints,
        qrPayload = qrPayload,
    )
}
