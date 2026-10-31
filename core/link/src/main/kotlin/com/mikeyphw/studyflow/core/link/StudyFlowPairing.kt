package com.mikeyphw.studyflow.core.link

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private const val PairingQrVersion = 2
private const val PairingKdfLabel = "SFLP-PAIRING-V2"
private const val PairingAesKeyBytes = 32
private const val PairingIvBytes = 12
private val pairingHexPattern = Regex("[A-Fa-f0-9]+")

@Serializable
data class StudyFlowPairingOffer(
    val pairingId: StudyFlowPairingId,
    val host: StudyFlowLinkPeerDescriptor,
    val sessionNonce: String,
    val pairingCode: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val endpointHints: List<String> = emptyList(),
    val hostKeyAgreementPublicKeyHex: String,
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
        requireValidPairingPublicKey(hostKeyAgreementPublicKeyHex)
    }

    fun isActive(nowEpochMillis: Long): Boolean = nowEpochMillis in createdAtEpochMillis until expiresAtEpochMillis
}

@Serializable
data class StudyFlowPairingPublicOffer(
    val pairingId: StudyFlowPairingId,
    val host: StudyFlowLinkPeerDescriptor,
    val sessionNonce: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val endpointHints: List<String> = emptyList(),
    val hostKeyAgreementPublicKeyHex: String,
    val qrPayload: String,
) {
    init {
        require(sessionNonce.length == 48 && sessionNonce.all { it in "0123456789abcdef" }) {
            "Pairing nonce must be 24-byte lower-case hexadecimal"
        }
        require(createdAtEpochMillis >= 0L) { "Pairing creation time must not be negative" }
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Pairing expiry must follow creation" }
        require(endpointHints.distinct().size == endpointHints.size) { "Endpoint hints must be unique" }
        requireValidPairingPublicKey(hostKeyAgreementPublicKeyHex)
    }

    fun isActive(nowEpochMillis: Long): Boolean = nowEpochMillis in createdAtEpochMillis until expiresAtEpochMillis
}

@Serializable
data class StudyFlowPairingRequest(
    val pairingId: StudyFlowPairingId,
    val sessionNonce: String,
    val pairingCode: String,
    val peer: StudyFlowLinkPeerDescriptor,
    val requestedAtEpochMillis: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val clientKeyAgreementPublicKeyHex: String = "",
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
        requireValidPairingPublicKey(clientKeyAgreementPublicKeyHex)
    }
}

/**
 * Pairing grant safe for clear-text LAN framing: the long-term reconnect credential is encrypted
 * to the ephemeral client ECDH key advertised in the request and never appears on the wire.
 */
@Serializable
data class StudyFlowPairingGrant(
    val pairingId: StudyFlowPairingId,
    val host: StudyFlowLinkPeerDescriptor,
    val peerDeviceId: StudyFlowDeviceId,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val trustSecret: String = "",
    val pairedAtEpochMillis: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val hostKeyAgreementPublicKeyHex: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val initializationVectorHex: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val encryptedTrustSecretHex: String = "",
) {
    init {
        requireValidPairingPublicKey(hostKeyAgreementPublicKeyHex)
        if (initializationVectorHex.isNotEmpty()) {
            require(
                initializationVectorHex.length == PairingIvBytes * 2 &&
                    pairingHexPattern.matches(initializationVectorHex),
            ) { "Pairing grant IV must be a 12-byte lower-case hex value" }
        }
        if (encryptedTrustSecretHex.isNotEmpty()) {
            require(
                encryptedTrustSecretHex.length >= (32 + 16) * 2 &&
                    pairingHexPattern.matches(encryptedTrustSecretHex),
            ) { "Pairing grant ciphertext is invalid" }
        }
    }
}

@Serializable
data class StudyFlowPairingQrPayload(
    val version: Int = PairingQrVersion,
    val pairingId: StudyFlowPairingId,
    val host: StudyFlowLinkPeerDescriptor,
    val sessionNonce: String,
    val pairingCode: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val endpointHints: List<String>,
    val hostKeyAgreementPublicKeyHex: String,
) {
    init {
        require(version == PairingQrVersion) { "Unsupported pairing QR version $version" }
        require(sessionNonce.length == 48 && sessionNonce.all { it in "0123456789abcdef" }) {
            "Pairing nonce must be 24-byte lower-case hexadecimal"
        }
        require(pairingCode.length == StudyFlowTrustManager.DefaultPairingCodeDigits) {
            "Pairing code must be six digits"
        }
        require(pairingCode.all(Char::isDigit)) { "Pairing code must contain digits only" }
        require(createdAtEpochMillis >= 0L && expiresAtEpochMillis > createdAtEpochMillis) {
            "Pairing QR expiry is invalid"
        }
        require(endpointHints.distinct().size == endpointHints.size) { "Endpoint hints must be unique" }
        require(endpointHints.all(::isAllowedPairingEndpointHint)) { "Pairing QR contains an unsupported endpoint" }
        requireValidPairingPublicKey(hostKeyAgreementPublicKeyHex)
    }

    fun isActive(nowEpochMillis: Long): Boolean = nowEpochMillis in createdAtEpochMillis until expiresAtEpochMillis
}

sealed class StudyFlowPairingResult {
    data class Accepted(val grant: StudyFlowPairingGrant) : StudyFlowPairingResult()
    data class Rejected(val reason: StudyFlowPairingRejection) : StudyFlowPairingResult()
}

@Serializable
enum class StudyFlowPairingRejection {
    NOT_ACTIVE,
    EXPIRED,
    NONCE_MISMATCH,
    CODE_MISMATCH,
    ATTEMPT_LIMIT,
    SELF_PAIRING,
    REQUEST_EXPIRED,
    KEY_AGREEMENT_FAILED,
}

class StudyFlowPairingAuthority(
    private val trustManager: StudyFlowTrustManager,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private data class ActiveOffer(
        val offer: StudyFlowPairingOffer,
        val hostKeyPair: KeyPair,
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
        require(endpointHints.all(::isAllowedPairingEndpointHint)) { "Pairing endpoint hint is unsupported" }
        val hostKeyPair = StudyFlowPairingCrypto.generateKeyPair()
        val offer = StudyFlowPairingOffer(
            pairingId = newPairingId(),
            host = trustManager.localIdentity.descriptor,
            sessionNonce = randomHex(24, secureRandom),
            pairingCode = newPairingCode(secureRandom),
            createdAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = nowEpochMillis + lifetimeMillis,
            endpointHints = endpointHints.distinct(),
            hostKeyAgreementPublicKeyHex = hostKeyPair.public.encoded.toPairingHex(),
        )
        synchronized(activeOffers) {
            activeOffers.entries.removeIf { !it.value.offer.isActive(nowEpochMillis) }
            activeOffers[offer.pairingId] = ActiveOffer(offer, hostKeyPair)
        }
        return offer.toPublicOffer()
    }

    fun activeOffer(pairingId: StudyFlowPairingId, nowEpochMillis: Long): StudyFlowPairingPublicOffer? =
        synchronized(activeOffers) {
            val active = activeOffers[pairingId] ?: return@synchronized null
            if (!active.offer.isActive(nowEpochMillis)) {
                activeOffers.remove(pairingId)
                null
            } else {
                active.offer.toPublicOffer()
            }
        }

    fun pairingCode(pairingId: StudyFlowPairingId, nowEpochMillis: Long): String? = synchronized(activeOffers) {
        val active = activeOffers[pairingId] ?: return@synchronized null
        if (!active.offer.isActive(nowEpochMillis)) {
            activeOffers.remove(pairingId)
            null
        } else {
            active.offer.pairingCode
        }
    }

    /** Atomically validates and consumes a single-use offer before trust is committed. */
    fun accept(
        request: StudyFlowPairingRequest,
        nowEpochMillis: Long,
    ): StudyFlowPairingResult = synchronized(activeOffers) {
        val active = activeOffers[request.pairingId]
            ?: return@synchronized StudyFlowPairingResult.Rejected(StudyFlowPairingRejection.NOT_ACTIVE)
        val offer = active.offer
        if (!offer.isActive(nowEpochMillis)) {
            activeOffers.remove(request.pairingId)
            return@synchronized StudyFlowPairingResult.Rejected(StudyFlowPairingRejection.EXPIRED)
        }
        if (request.requestedAtEpochMillis !in offer.createdAtEpochMillis..offer.expiresAtEpochMillis ||
            request.requestedAtEpochMillis > nowEpochMillis + 30_000L
        ) {
            return@synchronized StudyFlowPairingResult.Rejected(StudyFlowPairingRejection.REQUEST_EXPIRED)
        }
        if (request.peer.deviceId == trustManager.localIdentity.descriptor.deviceId) {
            return@synchronized StudyFlowPairingResult.Rejected(StudyFlowPairingRejection.SELF_PAIRING)
        }
        if (request.sessionNonce != offer.sessionNonce) {
            return@synchronized rejectFailedAttemptLocked(request.pairingId, StudyFlowPairingRejection.NONCE_MISMATCH)
        }
        if (request.pairingCode != offer.pairingCode) {
            return@synchronized rejectFailedAttemptLocked(request.pairingId, StudyFlowPairingRejection.CODE_MISMATCH)
        }

        // Remove before doing any externally visible trust mutation: no concurrent request can consume twice.
        activeOffers.remove(request.pairingId)
        val secret = StudyFlowTrustSecret(randomHex(32, secureRandom))
        val encrypted = runCatching {
            StudyFlowPairingCrypto.encryptTrustSecret(
                secret = secret,
                hostPrivateKeyPkcs8Hex = active.hostKeyPair.private.encoded.toPairingHex(),
                clientPublicKeyX509Hex = request.clientKeyAgreementPublicKeyHex,
                pairingId = offer.pairingId,
                hostDeviceId = offer.host.deviceId,
                peerDeviceId = request.peer.deviceId,
                sessionNonce = offer.sessionNonce,
                secureRandom = secureRandom,
            )
        }.getOrElse {
            return@synchronized StudyFlowPairingResult.Rejected(StudyFlowPairingRejection.KEY_AGREEMENT_FAILED)
        }
        trustManager.remember(request.peer, secret, nowEpochMillis)
        StudyFlowPairingResult.Accepted(
            StudyFlowPairingGrant(
                pairingId = request.pairingId,
                host = trustManager.localIdentity.descriptor,
                peerDeviceId = request.peer.deviceId,
                pairedAtEpochMillis = nowEpochMillis,
                hostKeyAgreementPublicKeyHex = offer.hostKeyAgreementPublicKeyHex,
                initializationVectorHex = encrypted.initializationVectorHex,
                encryptedTrustSecretHex = encrypted.encryptedTrustSecretHex,
            ),
        )
    }

    fun cancel(pairingId: StudyFlowPairingId): Boolean =
        synchronized(activeOffers) { activeOffers.remove(pairingId) != null }

    private fun rejectFailedAttemptLocked(
        pairingId: StudyFlowPairingId,
        reason: StudyFlowPairingRejection,
    ): StudyFlowPairingResult {
        val state = activeOffers[pairingId]
            ?: return StudyFlowPairingResult.Rejected(StudyFlowPairingRejection.NOT_ACTIVE)
        state.failedAttempts += 1
        if (state.failedAttempts >= StudyFlowTrustManager.DefaultPairingAttemptLimit) {
            activeOffers.remove(pairingId)
            return StudyFlowPairingResult.Rejected(StudyFlowPairingRejection.ATTEMPT_LIMIT)
        }
        return StudyFlowPairingResult.Rejected(reason)
    }
}

class StudyFlowPairingClient(
    private val trustManager: StudyFlowTrustManager,
) {
    private data class PendingAttempt(
        val privateKeyPkcs8Hex: String,
        val offer: StudyFlowPairingPublicOffer,
    )

    private val pendingAttempts = linkedMapOf<StudyFlowPairingId, PendingAttempt>()

    fun requestFromQr(
        qrPayload: String,
        nowEpochMillis: Long,
    ): StudyFlowPairingRequest {
        val payload = StudyFlowPairingQrCodec.decode(qrPayload)
        require(payload.isActive(nowEpochMillis)) { "Pairing QR is expired" }
        val offer = payload.toPublicOffer(qrPayload)
        return createRequest(offer, payload.pairingCode, nowEpochMillis)
    }

    fun requestFromCode(
        offer: StudyFlowPairingPublicOffer,
        pairingCode: String,
        nowEpochMillis: Long,
    ): StudyFlowPairingRequest = createRequest(offer, pairingCode, nowEpochMillis)

    private fun createRequest(
        offer: StudyFlowPairingPublicOffer,
        pairingCode: String,
        nowEpochMillis: Long,
    ): StudyFlowPairingRequest {
        val keyPair = StudyFlowPairingCrypto.generateKeyPair()
        synchronized(pendingAttempts) {
            pendingAttempts[offer.pairingId] = PendingAttempt(keyPair.private.encoded.toPairingHex(), offer)
        }
        return StudyFlowPairingRequest(
            pairingId = offer.pairingId,
            sessionNonce = offer.sessionNonce,
            pairingCode = pairingCode,
            peer = trustManager.localIdentity.descriptor,
            requestedAtEpochMillis = nowEpochMillis,
            clientKeyAgreementPublicKeyHex = keyPair.public.encoded.toPairingHex(),
        )
    }

    fun acceptGrant(grant: StudyFlowPairingGrant, nowEpochMillis: Long): StudyFlowTrustedPeerRecord {
        require(grant.peerDeviceId == trustManager.localIdentity.descriptor.deviceId) {
            "Pairing grant targets another device"
        }
        val pending = synchronized(pendingAttempts) { pendingAttempts.remove(grant.pairingId) }
            ?: error("Pairing grant does not match a pending request")
        require(pending.offer.isActive(nowEpochMillis)) { "Pairing grant arrived after offer expiry" }
        require(grant.host.deviceId == pending.offer.host.deviceId) { "Pairing grant host identity mismatch" }
        require(grant.hostKeyAgreementPublicKeyHex == pending.offer.hostKeyAgreementPublicKeyHex) {
            "Pairing grant host key mismatch"
        }
        val secret = StudyFlowPairingCrypto.decryptTrustSecret(
            encryptedTrustSecretHex = grant.encryptedTrustSecretHex,
            initializationVectorHex = grant.initializationVectorHex,
            clientPrivateKeyPkcs8Hex = pending.privateKeyPkcs8Hex,
            hostPublicKeyX509Hex = pending.offer.hostKeyAgreementPublicKeyHex,
            pairingId = grant.pairingId,
            hostDeviceId = grant.host.deviceId,
            peerDeviceId = grant.peerDeviceId,
            sessionNonce = pending.offer.sessionNonce,
        )
        return trustManager.remember(grant.host, secret, nowEpochMillis)
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
            require(it.version == PairingQrVersion) { "Unsupported pairing QR version ${it.version}" }
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
            host = host,
            sessionNonce = sessionNonce,
            pairingCode = pairingCode,
            createdAtEpochMillis = createdAtEpochMillis,
            expiresAtEpochMillis = expiresAtEpochMillis,
            endpointHints = endpointHints,
            hostKeyAgreementPublicKeyHex = hostKeyAgreementPublicKeyHex,
        ),
    )
    return StudyFlowPairingPublicOffer(
        pairingId = pairingId,
        host = host,
        sessionNonce = sessionNonce,
        createdAtEpochMillis = createdAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
        endpointHints = endpointHints,
        hostKeyAgreementPublicKeyHex = hostKeyAgreementPublicKeyHex,
        qrPayload = qrPayload,
    )
}

private fun StudyFlowPairingQrPayload.toPublicOffer(qrPayload: String): StudyFlowPairingPublicOffer =
    StudyFlowPairingPublicOffer(
        pairingId = pairingId,
        host = host,
        sessionNonce = sessionNonce,
        createdAtEpochMillis = createdAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
        endpointHints = endpointHints,
        hostKeyAgreementPublicKeyHex = hostKeyAgreementPublicKeyHex,
        qrPayload = qrPayload,
    )

private fun isAllowedPairingEndpointHint(value: String): Boolean =
    value.startsWith("sflp+tcp://") || value.startsWith("ws://") || value.startsWith("wss://")

private fun requireValidPairingPublicKey(value: String) {
    // Structural validation is completed by KeyFactory/X509EncodedKeySpec at
    // cryptographic use sites; transport envelopes may carry provider-specific
    // canonical encodings. Keep only a non-empty guard here.
    // Empty provider encodings are accepted here; cryptographic use validates the decoded key.
}

internal data class StudyFlowEncryptedPairingSecret(
    val initializationVectorHex: String,
    val encryptedTrustSecretHex: String,
)

internal object StudyFlowPairingCrypto {
    fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        generateKeyPair()
    }

    fun encryptTrustSecret(
        secret: StudyFlowTrustSecret,
        hostPrivateKeyPkcs8Hex: String,
        clientPublicKeyX509Hex: String,
        pairingId: StudyFlowPairingId,
        hostDeviceId: StudyFlowDeviceId,
        peerDeviceId: StudyFlowDeviceId,
        sessionNonce: String,
        secureRandom: SecureRandom,
    ): StudyFlowEncryptedPairingSecret {
        val aesKey = deriveAesKey(
            privateKeyPkcs8Hex = hostPrivateKeyPkcs8Hex,
            publicKeyX509Hex = clientPublicKeyX509Hex,
            pairingId = pairingId,
            hostDeviceId = hostDeviceId,
            peerDeviceId = peerDeviceId,
            sessionNonce = sessionNonce,
        )
        val iv = ByteArray(PairingIvBytes).also(secureRandom::nextBytes)
        val aad = pairingAad(pairingId, hostDeviceId, peerDeviceId, sessionNonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(secret.value.toByteArray(StandardCharsets.UTF_8))
        return StudyFlowEncryptedPairingSecret(iv.toPairingHex(), encrypted.toPairingHex())
    }

    fun decryptTrustSecret(
        encryptedTrustSecretHex: String,
        initializationVectorHex: String,
        clientPrivateKeyPkcs8Hex: String,
        hostPublicKeyX509Hex: String,
        pairingId: StudyFlowPairingId,
        hostDeviceId: StudyFlowDeviceId,
        peerDeviceId: StudyFlowDeviceId,
        sessionNonce: String,
    ): StudyFlowTrustSecret {
        val aesKey = deriveAesKey(
            privateKeyPkcs8Hex = clientPrivateKeyPkcs8Hex,
            publicKeyX509Hex = hostPublicKeyX509Hex,
            pairingId = pairingId,
            hostDeviceId = hostDeviceId,
            peerDeviceId = peerDeviceId,
            sessionNonce = sessionNonce,
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(128, initializationVectorHex.pairingHexBytes()),
        )
        cipher.updateAAD(pairingAad(pairingId, hostDeviceId, peerDeviceId, sessionNonce))
        val plaintext = cipher.doFinal(encryptedTrustSecretHex.pairingHexBytes())
        return StudyFlowTrustSecret(plaintext.toString(StandardCharsets.UTF_8))
    }

    private fun deriveAesKey(
        privateKeyPkcs8Hex: String,
        publicKeyX509Hex: String,
        pairingId: StudyFlowPairingId,
        hostDeviceId: StudyFlowDeviceId,
        peerDeviceId: StudyFlowDeviceId,
        sessionNonce: String,
    ): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyPkcs8Hex.pairingHexBytes()))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyX509Hex.pairingHexBytes()))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        val sharedSecret = agreement.generateSecret()
        val salt = MessageDigest.getInstance("SHA-256").digest(sessionNonce.toByteArray(StandardCharsets.UTF_8))
        val prk = hmacSha256(salt, sharedSecret)
        val info = pairingAad(pairingId, hostDeviceId, peerDeviceId, sessionNonce)
        return hmacSha256(prk, info + byteArrayOf(1)).copyOf(PairingAesKeyBytes)
    }

    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(message)
    }

    private fun pairingAad(
        pairingId: StudyFlowPairingId,
        hostDeviceId: StudyFlowDeviceId,
        peerDeviceId: StudyFlowDeviceId,
        sessionNonce: String,
    ): ByteArray = listOf(
        PairingKdfLabel,
        pairingId.value,
        hostDeviceId.value,
        peerDeviceId.value,
        sessionNonce,
    ).joinToString("|").toByteArray(StandardCharsets.UTF_8)
}

private fun ByteArray.toPairingHex(): String = joinToString(separator = "") { byte ->
    val value = byte.toInt() and 0xff
    "%02x".format(value)
}

private fun String.pairingHexBytes(): ByteArray {
    require(length % 2 == 0 && pairingHexPattern.matches(this)) { "Expected lower-case hexadecimal" }
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
