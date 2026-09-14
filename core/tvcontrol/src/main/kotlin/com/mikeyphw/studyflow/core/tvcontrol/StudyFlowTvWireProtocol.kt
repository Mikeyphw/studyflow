package com.mikeyphw.studyflow.core.tvcontrol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Transport-neutral wire contract installed by TVOV22 and hardened by OV10.
 *
 * Long-term authentication material never crosses the raw LAN socket. Pairing uses ephemeral
 * P-256 ECDH + AES-GCM and all authenticated requests carry a short-lived HMAC proof over the
 * complete request instead of the reusable session secret.
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
    const val authProofMaxClockSkewMillis = 30_000L
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
    DOMAIN_ACTION_RESULT,
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
    val hostKeyAgreementPublicKeyHex: String,
    @Transient val hostKeyAgreementPrivateKeyHex: String = "",
) {
    init {
        require(deepLink.startsWith("studyflow://tv/pair?")) { "TV pairing deep link must use StudyFlow scheme." }
        requireTvPublicKey(hostKeyAgreementPublicKeyHex)
        require(hostKeyAgreementPrivateKeyHex.isEmpty() || hostKeyAgreementPrivateKeyHex.matches(HEX))
    }

    fun isActive(nowEpochMillis: Long): Boolean = nowEpochMillis < token.expiresAtEpochMillis
}

@Serializable
data class StudyFlowTvPairingInfo(
    val tvDeviceName: String,
    val pairingActive: Boolean,
    val sessionNonce: String? = null,
    val expiresAtEpochMillis: Long? = null,
    val hostKeyAgreementPublicKeyHex: String? = null,
) {
    init {
        require(tvDeviceName.isNotBlank()) { "TV pairing info must identify the receiver." }
        if (pairingActive) {
            require(!sessionNonce.isNullOrBlank()) { "Active TV pairing info requires a nonce." }
            require(expiresAtEpochMillis != null) { "Active TV pairing info requires an expiry." }
            requireTvPublicKey(requireNotNull(hostKeyAgreementPublicKeyHex))
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
    val receiverServiceName: String? = null,
    val receiverHostAddress: String? = null,
    val receiverPort: Int? = null,
    val receiverHostKeyAgreementPublicKeyHex: String? = null,
) {
    init {
        require(sessionId.isNotBlank()) { "TV link session id must not be blank." }
        require(sessionSecret.length >= 32 && sessionSecret.matches(HEX)) { "TV link session secret must be hexadecimal." }
        require(tvDeviceName.isNotBlank()) { "TV link credential must identify the TV." }
        require(pairedDeviceName.isNotBlank()) { "TV link credential must identify the phone." }
        require(expiresAtEpochMillis > pairedAtEpochMillis) { "TV link session must expire after pairing." }
        receiverServiceName?.let { require(it.isNotBlank()) { "TV receiver service binding must not be blank." } }
        receiverHostAddress?.let { require(it.isNotBlank()) { "TV receiver host binding must not be blank." } }
        receiverPort?.let { require(it in 1..65_535) { "TV receiver port binding must be valid." } }
        receiverHostKeyAgreementPublicKeyHex?.let(::requireTvPublicKey)
    }

    fun isActive(nowEpochMillis: Long): Boolean = nowEpochMillis < expiresAtEpochMillis

    fun toControlSession(): StudyFlowTvControlSession = StudyFlowTvControlSession.paired(
        sessionId = sessionId,
        pairedDeviceName = pairedDeviceName,
        pairedAtEpochMillis = pairedAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )

    fun bindToReceiver(
        endpoint: StudyFlowTvReceiverEndpoint,
        hostKeyAgreementPublicKeyHex: String? = receiverHostKeyAgreementPublicKeyHex,
    ): StudyFlowTvSessionCredential = copy(
        receiverServiceName = endpoint.serviceName,
        receiverHostAddress = endpoint.hostAddress,
        receiverPort = endpoint.port,
        receiverHostKeyAgreementPublicKeyHex = hostKeyAgreementPublicKeyHex,
    )

    fun isBoundTo(endpoint: StudyFlowTvReceiverEndpoint): Boolean {
        if (receiverServiceName == null && receiverHostAddress == null && receiverPort == null) return true
        return endpoint.matchesCredentialBinding(this)
    }
}

fun StudyFlowTvReceiverEndpoint.matchesCredentialBinding(credential: StudyFlowTvSessionCredential): Boolean {
    val serviceMatches = credential.receiverServiceName?.let { it == serviceName } ?: (credential.tvDeviceName == serviceName)
    val hostMatches = credential.receiverHostAddress?.let { bound -> normalizeTvHost(bound) == normalizeTvHost(hostAddress) } ?: true
    val portMatches = credential.receiverPort?.let { it == port } ?: true
    return serviceMatches && hostMatches && portMatches
}

private fun normalizeTvHost(value: String): String = value.lowercase(Locale.US)

object StudyFlowTvDomainActionJournalKeys {
    fun forAction(actionId: String): String {
        require(actionId.isNotBlank()) { "TV domain action id must not be blank." }
        return "action_sha256_" + MessageDigest.getInstance("SHA-256")
            .digest(actionId.toByteArray(StandardCharsets.UTF_8))
            .tvHex()
    }

    fun sflpOperationId(actionId: String): String = "tv-action-sha256-" + digest(actionId)

    fun sflpEntityId(actionId: String): String = "tv-action-sha256-" + digest(actionId)

    private fun digest(actionId: String): String {
        require(actionId.isNotBlank()) { "TV domain action id must not be blank." }
        return MessageDigest.getInstance("SHA-256")
            .digest(actionId.toByteArray(StandardCharsets.UTF_8))
            .tvHex()
    }
}

/** Pairing response safe to transmit over raw TCP: the credential body is ECDH/AES-GCM encrypted. */
@Serializable
data class StudyFlowTvEncryptedSessionCredential(
    val sessionId: String,
    val tvDeviceName: String,
    val pairedDeviceName: String,
    val pairedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val hostKeyAgreementPublicKeyHex: String,
    val initializationVectorHex: String,
    val encryptedCredentialHex: String,
) {
    init {
        require(sessionId.isNotBlank())
        require(tvDeviceName.isNotBlank() && pairedDeviceName.isNotBlank())
        require(expiresAtEpochMillis > pairedAtEpochMillis)
        requireTvPublicKey(hostKeyAgreementPublicKeyHex)
        require(initializationVectorHex.length == 24 && initializationVectorHex.matches(HEX))
        require(encryptedCredentialHex.length >= 64 && encryptedCredentialHex.matches(HEX))
    }
}

@Serializable
data class StudyFlowTvWireRequest(
    val requestId: String,
    val wireVersion: Int = StudyFlowTvWireProtocol.version,
    val operation: StudyFlowTvWireOperation,
    val sessionId: String? = null,
    val authNonceHex: String? = null,
    val authProofHex: String? = null,
    val pairingCode: String? = null,
    val sessionNonce: String? = null,
    val phoneDeviceName: String? = null,
    val clientKeyAgreementPublicKeyHex: String? = null,
    val command: StudyFlowTvControlCommand? = null,
    val presentation: StudyFlowTvPresentationSnapshot? = null,
    val domainActionResult: StudyFlowTvDomainActionResult? = null,
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
    val encryptedCredential: StudyFlowTvEncryptedSessionCredential? = null,
    val applied: Boolean? = null,
    val continuity: StudyFlowTvControlContinuityState? = null,
    val presentationReceipt: StudyFlowTvPresentationReceipt? = null,
    val pendingDomainAction: StudyFlowTvDomainAction? = null,
    val lastDomainActionResult: StudyFlowTvDomainActionResult? = null,
    val respondedAtEpochMillis: Long,
) {
    init {
        require(requestId.isNotBlank()) { "TV wire response id must not be blank." }
        require(wireVersion == StudyFlowTvWireProtocol.version) { "Unsupported TV wire protocol version." }
        require(respondedAtEpochMillis >= 0L) { "TV wire response time cannot be negative." }
    }
}

data class StudyFlowTvPairingAcceptance(
    val credential: StudyFlowTvSessionCredential,
    val encryptedCredential: StudyFlowTvEncryptedSessionCredential,
)

data class StudyFlowTvPairingClientKeyMaterial internal constructor(
    internal val privateKeyPkcs8Hex: String,
    val publicKeyX509Hex: String,
)

/** Secure-random pairing/session generator whose validation is independent from Android transport. */
class StudyFlowTvPairingEngine(
    private val random: SecureRandom = SecureRandom(),
) {
    fun createOffer(tvDeviceName: String, nowEpochMillis: Long): StudyFlowTvPairingOffer {
        require(tvDeviceName.isNotBlank())
        require(nowEpochMillis >= 0L)
        val pairingCode = random.nextInt(1_000_000).toString().padStart(StudyFlowTvControlProtocol.pairingCodeLength, '0')
        val nonce = randomHex(16, random)
        val expiresAt = nowEpochMillis + StudyFlowTvControlProtocol.pairingCodeTtlSeconds * 1_000L
        val token = StudyFlowTvPairingToken(
            tvDeviceName = tvDeviceName,
            pairingCode = pairingCode,
            sessionNonce = nonce,
            issuedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = expiresAt,
        )
        val keyPair = generateTvKeyPair()
        val publicHex = keyPair.public.encoded.tvHex()
        val encodedDevice = URLEncoder.encode(tvDeviceName, StandardCharsets.UTF_8.name())
        return StudyFlowTvPairingOffer(
            token = token,
            deepLink = "studyflow://tv/pair?code=$pairingCode&nonce=$nonce&device=$encodedDevice&key=$publicHex",
            hostKeyAgreementPublicKeyHex = publicHex,
            hostKeyAgreementPrivateKeyHex = keyPair.private.encoded.tvHex(),
        )
    }

    fun accept(
        offer: StudyFlowTvPairingOffer,
        pairingCode: String,
        sessionNonce: String,
        phoneDeviceName: String,
        clientKeyAgreementPublicKeyHex: String,
        nowEpochMillis: Long,
    ): StudyFlowTvPairingAcceptance? {
        if (!offer.isActive(nowEpochMillis)) return null
        if (pairingCode != offer.token.pairingCode) return null
        if (sessionNonce != offer.token.sessionNonce) return null
        if (phoneDeviceName.isBlank()) return null
        if (offer.hostKeyAgreementPrivateKeyHex.isBlank()) return null
        if (runCatching { requireTvPublicKey(clientKeyAgreementPublicKeyHex) }.isFailure) return null
        val credential = StudyFlowTvSessionCredential(
            sessionId = randomHex(16, random),
            sessionSecret = randomHex(32, random),
            tvDeviceName = offer.token.tvDeviceName,
            pairedDeviceName = phoneDeviceName,
            pairedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = nowEpochMillis + StudyFlowTvWireProtocol.controlSessionTtlSeconds * 1_000L,
        )
        val encrypted = StudyFlowTvPairingCrypto.encryptCredential(
            credential = credential,
            sessionNonce = offer.token.sessionNonce,
            hostPrivateKeyPkcs8Hex = offer.hostKeyAgreementPrivateKeyHex,
            hostPublicKeyX509Hex = offer.hostKeyAgreementPublicKeyHex,
            clientPublicKeyX509Hex = clientKeyAgreementPublicKeyHex,
            random = random,
        )
        return StudyFlowTvPairingAcceptance(credential, encrypted)
    }
}

object StudyFlowTvPairingCrypto {
    fun createClientKeyMaterial(): StudyFlowTvPairingClientKeyMaterial {
        val pair = generateTvKeyPair()
        return StudyFlowTvPairingClientKeyMaterial(pair.private.encoded.tvHex(), pair.public.encoded.tvHex())
    }

    fun decryptCredential(
        grant: StudyFlowTvEncryptedSessionCredential,
        sessionNonce: String,
        keyMaterial: StudyFlowTvPairingClientKeyMaterial,
    ): StudyFlowTvSessionCredential {
        val key = deriveTvAesKey(
            keyMaterial.privateKeyPkcs8Hex,
            grant.hostKeyAgreementPublicKeyHex,
            sessionNonce,
            grant.sessionId,
            grant.tvDeviceName,
            grant.pairedDeviceName,
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, grant.initializationVectorHex.tvHexBytes()))
        cipher.updateAAD(tvPairingAad(sessionNonce, grant.sessionId, grant.tvDeviceName, grant.pairedDeviceName))
        val encoded = cipher.doFinal(grant.encryptedCredentialHex.tvHexBytes())
        val credential = StudyFlowTvWireCodec.decodeCredential(encoded)
        require(credential.sessionId == grant.sessionId && credential.tvDeviceName == grant.tvDeviceName && credential.pairedDeviceName == grant.pairedDeviceName) {
            "TV pairing grant metadata mismatch"
        }
        require(credential.pairedAtEpochMillis == grant.pairedAtEpochMillis && credential.expiresAtEpochMillis == grant.expiresAtEpochMillis) {
            "TV pairing grant lifetime mismatch"
        }
        return credential
    }

    internal fun encryptCredential(
        credential: StudyFlowTvSessionCredential,
        sessionNonce: String,
        hostPrivateKeyPkcs8Hex: String,
        hostPublicKeyX509Hex: String,
        clientPublicKeyX509Hex: String,
        random: SecureRandom,
    ): StudyFlowTvEncryptedSessionCredential {
        val key = deriveTvAesKey(
            hostPrivateKeyPkcs8Hex,
            clientPublicKeyX509Hex,
            sessionNonce,
            credential.sessionId,
            credential.tvDeviceName,
            credential.pairedDeviceName,
        )
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(tvPairingAad(sessionNonce, credential.sessionId, credential.tvDeviceName, credential.pairedDeviceName))
        val encrypted = cipher.doFinal(StudyFlowTvWireCodec.encodeCredential(credential))
        return StudyFlowTvEncryptedSessionCredential(
            sessionId = credential.sessionId,
            tvDeviceName = credential.tvDeviceName,
            pairedDeviceName = credential.pairedDeviceName,
            pairedAtEpochMillis = credential.pairedAtEpochMillis,
            expiresAtEpochMillis = credential.expiresAtEpochMillis,
            hostKeyAgreementPublicKeyHex = hostPublicKeyX509Hex,
            initializationVectorHex = iv.tvHex(),
            encryptedCredentialHex = encrypted.tvHex(),
        )
    }
}

/** HMAC request authentication: the reusable session secret never appears in the wire request. */
object StudyFlowTvWireAuthenticator {
    fun sign(
        request: StudyFlowTvWireRequest,
        credential: StudyFlowTvSessionCredential,
        random: SecureRandom = SecureRandom(),
    ): StudyFlowTvWireRequest {
        require(request.sessionId == credential.sessionId) { "TV wire request uses the wrong session." }
        val unsigned = request.copy(authNonceHex = request.authNonceHex ?: randomHex(16, random), authProofHex = null)
        return unsigned.copy(authProofHex = proofHex(credential.sessionSecret, unsigned))
    }

    fun verify(
        request: StudyFlowTvWireRequest,
        credential: StudyFlowTvSessionCredential?,
        nowEpochMillis: Long,
    ): Boolean {
        val active = credential ?: return false
        if (!active.isActive(nowEpochMillis) || active.sessionId != request.sessionId) return false
        if (kotlin.math.abs(nowEpochMillis - request.issuedAtEpochMillis) > StudyFlowTvWireProtocol.authProofMaxClockSkewMillis) return false
        val nonce = request.authNonceHex ?: return false
        val supplied = request.authProofHex ?: return false
        if (nonce.length != 32 || !nonce.matches(HEX) || supplied.length != 64 || !supplied.matches(HEX)) return false
        return MessageDigest.isEqual(
            supplied.tvHexBytes(),
            proofHex(active.sessionSecret, request.copy(authProofHex = null)).tvHexBytes(),
        )
    }

    private fun proofHex(secretHex: String, unsigned: StudyFlowTvWireRequest): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(secretHex.tvHexBytes(), "HmacSHA256"))
        doFinal(StudyFlowTvWireCodec.encodeRequest(unsigned)).tvHex()
    }
}

/** Strict JSON + length-prefixed framing used over the local TCP link. */
object StudyFlowTvWireCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun encodeRequest(request: StudyFlowTvWireRequest): ByteArray = json.encodeToString(request).toByteArray(StandardCharsets.UTF_8)
    fun decodeRequest(bytes: ByteArray): StudyFlowTvWireRequest = json.decodeFromString(bytes.toString(StandardCharsets.UTF_8))
    fun encodeResponse(response: StudyFlowTvWireResponse): ByteArray = json.encodeToString(response).toByteArray(StandardCharsets.UTF_8)
    fun decodeResponse(bytes: ByteArray): StudyFlowTvWireResponse = json.decodeFromString(bytes.toString(StandardCharsets.UTF_8))
    internal fun encodeCredential(credential: StudyFlowTvSessionCredential): ByteArray = json.encodeToString(credential).toByteArray(StandardCharsets.UTF_8)
    internal fun decodeCredential(bytes: ByteArray): StudyFlowTvSessionCredential = json.decodeFromString(bytes.toString(StandardCharsets.UTF_8))

    fun writeRequest(output: OutputStream, request: StudyFlowTvWireRequest) = writeFrame(output, encodeRequest(request))
    fun readRequest(input: InputStream): StudyFlowTvWireRequest = decodeRequest(readFrame(input))
    fun writeResponse(output: OutputStream, response: StudyFlowTvWireResponse) = writeFrame(output, encodeResponse(response))
    fun readResponse(input: InputStream): StudyFlowTvWireResponse = decodeResponse(readFrame(input))

    fun writeFrame(output: OutputStream, payload: ByteArray) {
        require(payload.isNotEmpty()) { "TV wire frame cannot be empty." }
        require(payload.size <= StudyFlowTvWireProtocol.maxFrameBytes) { "TV wire frame exceeds maximum size." }
        DataOutputStream(output).apply { writeInt(payload.size); write(payload); flush() }
    }

    fun readFrame(input: InputStream): ByteArray {
        val dataInput = DataInputStream(input)
        val size = dataInput.readInt()
        if (size <= 0 || size > StudyFlowTvWireProtocol.maxFrameBytes) throw IOException("Invalid TV wire frame size: $size")
        return ByteArray(size).also(dataInput::readFully)
    }
}

private val HEX = Regex("[a-f0-9]+")
private fun requireTvPublicKey(value: String) {
    require(value.length in 160..1024 && value.length % 2 == 0 && value.matches(HEX)) { "TV pairing key must be X.509 DER hexadecimal." }
}
private fun generateTvKeyPair() = KeyPairGenerator.getInstance("EC").run { initialize(ECGenParameterSpec("secp256r1"), SecureRandom()); generateKeyPair() }
private fun deriveTvAesKey(privatePkcs8Hex:String, publicX509Hex:String, nonce:String, sessionId:String, tvName:String, phoneName:String):ByteArray {
    val factory=KeyFactory.getInstance("EC")
    val agreement=KeyAgreement.getInstance("ECDH")
    agreement.init(factory.generatePrivate(PKCS8EncodedKeySpec(privatePkcs8Hex.tvHexBytes())))
    agreement.doPhase(factory.generatePublic(X509EncodedKeySpec(publicX509Hex.tvHexBytes())),true)
    val shared=agreement.generateSecret()
    val salt=MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray(StandardCharsets.UTF_8))
    val prk=tvHmac(salt,shared)
    return tvHmac(prk,tvPairingAad(nonce,sessionId,tvName,phoneName)+byteArrayOf(1)).copyOf(32)
}
private fun tvPairingAad(nonce:String, sessionId:String, tvName:String, phoneName:String)=
    "STUDYFLOW-TV-PAIRING-V2|$nonce|$sessionId|$tvName|$phoneName".toByteArray(StandardCharsets.UTF_8)
private fun tvHmac(key:ByteArray,message:ByteArray)=Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key,"HmacSHA256")); doFinal(message) }
private fun randomHex(byteCount:Int, random:SecureRandom):String=ByteArray(byteCount).also(random::nextBytes).tvHex()
private fun ByteArray.tvHex():String=joinToString(""){"%02x".format(it.toInt() and 0xff)}
private fun String.tvHexBytes():ByteArray { require(length%2==0&&matches(HEX)); return ByteArray(length/2){substring(it*2,it*2+2).toInt(16).toByte()} }
