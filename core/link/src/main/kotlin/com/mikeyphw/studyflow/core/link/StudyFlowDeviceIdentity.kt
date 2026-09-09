package com.mikeyphw.studyflow.core.link

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val PairingCodeDigits = 6
private const val PairingOfferLifetimeMillis = 120_000L
private const val PairingAttemptLimit = 10
private const val ReconnectChallengeLifetimeMillis = 30_000L
private const val TrustSecretBytes = 32
private const val NonceBytes = 24
private val lowerHexPattern = Regex("[0-9a-f]+")

@Serializable
@JvmInline
value class StudyFlowPairingId(val value: String) {
    init {
        require(value.startsWith("pair-") && value.length <= 80) { "Invalid pairing id" }
    }
}

@Serializable
@JvmInline
value class StudyFlowTrustSecret(val value: String) {
    init {
        require(value.length == TrustSecretBytes * 2) { "Trust secret must be 32 bytes" }
        require(lowerHexPattern.matches(value)) { "Trust secret must be lower-case hexadecimal" }
    }
}

@Serializable
@JvmInline
value class StudyFlowReconnectNonce(val value: String) {
    init {
        require(value.length == NonceBytes * 2) { "Reconnect nonce has an invalid length" }
        require(lowerHexPattern.matches(value)) { "Reconnect nonce must be lower-case hexadecimal" }
    }
}

@Serializable
data class StudyFlowLocalDeviceIdentity(
    val descriptor: StudyFlowLinkPeerDescriptor,
    val createdAtEpochMillis: Long,
) {
    init {
        require(createdAtEpochMillis >= 0L) { "Identity creation timestamp must not be negative" }
    }
}

interface StudyFlowDeviceIdentityStore {
    fun load(): StudyFlowLocalDeviceIdentity?
    fun save(identity: StudyFlowLocalDeviceIdentity)
}

class InMemoryStudyFlowDeviceIdentityStore(
    initial: StudyFlowLocalDeviceIdentity? = null,
) : StudyFlowDeviceIdentityStore {
    private var identity: StudyFlowLocalDeviceIdentity? = initial

    override fun load(): StudyFlowLocalDeviceIdentity? = synchronized(this) { identity }

    override fun save(identity: StudyFlowLocalDeviceIdentity) {
        synchronized(this) { this.identity = identity }
    }
}

class StudyFlowDeviceIdentityManager(
    private val store: StudyFlowDeviceIdentityStore,
    private val newDeviceId: () -> StudyFlowDeviceId = {
        StudyFlowDeviceId("device-${UUID.randomUUID()}")
    },
) {
    fun getOrCreate(
        displayName: String,
        clientKind: StudyFlowLinkClientKind,
        capabilities: List<StudyFlowLinkCapability>,
        nowEpochMillis: Long,
    ): StudyFlowLocalDeviceIdentity {
        require(nowEpochMillis >= 0L) { "Current timestamp must not be negative" }
        val existing = store.load()
        if (existing != null) {
            val refreshed = existing.copy(
                descriptor = existing.descriptor.copy(
                    displayName = displayName,
                    clientKind = clientKind,
                    capabilities = capabilities.distinct(),
                ),
            )
            store.save(refreshed)
            return refreshed
        }
        val identity = StudyFlowLocalDeviceIdentity(
            descriptor = StudyFlowLinkPeerDescriptor(
                deviceId = newDeviceId(),
                displayName = displayName,
                clientKind = clientKind,
                capabilities = capabilities.distinct(),
            ),
            createdAtEpochMillis = nowEpochMillis,
        )
        store.save(identity)
        return identity
    }
}

@Serializable
enum class StudyFlowTrustState {
    @SerialName("trusted")
    TRUSTED,

    @SerialName("revoked")
    REVOKED,
}

@Serializable
data class StudyFlowTrustedPeerRecord(
    val peer: StudyFlowLinkPeerDescriptor,
    val state: StudyFlowTrustState,
    val pairedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long? = null,
    val revokedAtEpochMillis: Long? = null,
    val trustSecret: StudyFlowTrustSecret? = null,
) {
    init {
        require(pairedAtEpochMillis >= 0L) { "Pairing timestamp must not be negative" }
        require(updatedAtEpochMillis >= pairedAtEpochMillis) { "Trust update cannot predate pairing" }
        require(lastSeenAtEpochMillis == null || lastSeenAtEpochMillis >= pairedAtEpochMillis) {
            "Last-seen timestamp cannot predate pairing"
        }
        when (state) {
            StudyFlowTrustState.TRUSTED -> {
                require(revokedAtEpochMillis == null) { "Trusted peer cannot have a revocation timestamp" }
                requireNotNull(trustSecret) { "Trusted peer requires a trust secret" }
            }
            StudyFlowTrustState.REVOKED -> {
                requireNotNull(revokedAtEpochMillis) { "Revoked peer requires a revocation timestamp" }
                require(trustSecret == null) { "Revoked peer must not retain its trust secret" }
            }
        }
    }
}

interface StudyFlowTrustStore {
    fun get(deviceId: StudyFlowDeviceId): StudyFlowTrustedPeerRecord?
    fun list(): List<StudyFlowTrustedPeerRecord>
    fun put(record: StudyFlowTrustedPeerRecord)
    fun remove(deviceId: StudyFlowDeviceId): Boolean
}

class InMemoryStudyFlowTrustStore(
    records: Iterable<StudyFlowTrustedPeerRecord> = emptyList(),
) : StudyFlowTrustStore {
    private val byDeviceId = linkedMapOf<StudyFlowDeviceId, StudyFlowTrustedPeerRecord>()

    init {
        records.forEach { byDeviceId[it.peer.deviceId] = it }
    }

    override fun get(deviceId: StudyFlowDeviceId): StudyFlowTrustedPeerRecord? =
        synchronized(this) { byDeviceId[deviceId] }

    override fun list(): List<StudyFlowTrustedPeerRecord> =
        synchronized(this) { byDeviceId.values.sortedBy { it.peer.deviceId.value } }

    override fun put(record: StudyFlowTrustedPeerRecord) {
        synchronized(this) { byDeviceId[record.peer.deviceId] = record }
    }

    override fun remove(deviceId: StudyFlowDeviceId): Boolean =
        synchronized(this) { byDeviceId.remove(deviceId) != null }
}

@Serializable
data class StudyFlowReconnectChallenge(
    val challengerDeviceId: StudyFlowDeviceId,
    val peerDeviceId: StudyFlowDeviceId,
    val nonce: StudyFlowReconnectNonce,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    init {
        require(expiresAtEpochMillis > issuedAtEpochMillis) { "Reconnect challenge must expire after issue" }
    }

    fun isActive(nowEpochMillis: Long): Boolean = nowEpochMillis in issuedAtEpochMillis until expiresAtEpochMillis
}

@Serializable
data class StudyFlowReconnectProof(
    val proverDeviceId: StudyFlowDeviceId,
    val nonce: StudyFlowReconnectNonce,
    val proofHex: String,
) {
    init {
        require(proofHex.length == 64 && lowerHexPattern.matches(proofHex)) {
            "Reconnect proof must be a SHA-256 HMAC"
        }
    }
}

class StudyFlowTrustManager(
    val localIdentity: StudyFlowLocalDeviceIdentity,
    private val store: StudyFlowTrustStore,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val pendingReconnectChallenges = linkedMapOf<StudyFlowReconnectNonce, StudyFlowReconnectChallenge>()

    fun remember(
        peer: StudyFlowLinkPeerDescriptor,
        secret: StudyFlowTrustSecret,
        nowEpochMillis: Long,
    ): StudyFlowTrustedPeerRecord {
        require(peer.deviceId != localIdentity.descriptor.deviceId) { "A device cannot trust itself as a peer" }
        val existing = store.get(peer.deviceId)
        val record = StudyFlowTrustedPeerRecord(
            peer = peer,
            state = StudyFlowTrustState.TRUSTED,
            pairedAtEpochMillis = existing?.takeIf { it.state == StudyFlowTrustState.TRUSTED }
                ?.pairedAtEpochMillis ?: nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            lastSeenAtEpochMillis = nowEpochMillis,
            trustSecret = secret,
        )
        store.put(record)
        return record
    }

    fun trustedPeers(): List<StudyFlowTrustedPeerRecord> =
        store.list().filter { it.state == StudyFlowTrustState.TRUSTED }

    fun rememberedPeers(): List<StudyFlowTrustedPeerRecord> = store.list()

    fun refreshPeer(
        peer: StudyFlowLinkPeerDescriptor,
        nowEpochMillis: Long,
    ): StudyFlowTrustedPeerRecord? {
        val current = store.get(peer.deviceId)?.takeIf { it.state == StudyFlowTrustState.TRUSTED } ?: return null
        val refreshed = current.copy(
            peer = peer,
            updatedAtEpochMillis = nowEpochMillis,
            lastSeenAtEpochMillis = nowEpochMillis,
        )
        store.put(refreshed)
        return refreshed
    }

    fun revoke(peerDeviceId: StudyFlowDeviceId, nowEpochMillis: Long): StudyFlowTrustedPeerRecord? {
        val current = store.get(peerDeviceId) ?: return null
        val revoked = current.copy(
            state = StudyFlowTrustState.REVOKED,
            updatedAtEpochMillis = nowEpochMillis,
            revokedAtEpochMillis = nowEpochMillis,
            trustSecret = null,
        )
        store.put(revoked)
        return revoked
    }

    fun forget(peerDeviceId: StudyFlowDeviceId): Boolean = store.remove(peerDeviceId)

    fun issueReconnectChallenge(
        peerDeviceId: StudyFlowDeviceId,
        nowEpochMillis: Long,
        lifetimeMillis: Long = ReconnectChallengeLifetimeMillis,
    ): StudyFlowReconnectChallenge {
        require(lifetimeMillis in 1..PairingOfferLifetimeMillis) { "Reconnect lifetime is out of range" }
        require(store.get(peerDeviceId)?.state == StudyFlowTrustState.TRUSTED) { "Peer is not trusted" }
        val challenge = StudyFlowReconnectChallenge(
            challengerDeviceId = localIdentity.descriptor.deviceId,
            peerDeviceId = peerDeviceId,
            nonce = StudyFlowReconnectNonce(randomHex(NonceBytes, secureRandom)),
            issuedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = nowEpochMillis + lifetimeMillis,
        )
        synchronized(pendingReconnectChallenges) {
            pendingReconnectChallenges[challenge.nonce] = challenge
        }
        return challenge
    }

    fun createReconnectProof(
        challenge: StudyFlowReconnectChallenge,
        nowEpochMillis: Long,
    ): StudyFlowReconnectProof {
        require(challenge.peerDeviceId == localIdentity.descriptor.deviceId) { "Challenge targets another peer" }
        require(challenge.isActive(nowEpochMillis)) { "Reconnect challenge is not active" }
        val record = store.get(challenge.challengerDeviceId)
            ?.takeIf { it.state == StudyFlowTrustState.TRUSTED }
            ?: error("Challenger is not trusted")
        val secret = requireNotNull(record.trustSecret)
        return StudyFlowReconnectProof(
            proverDeviceId = localIdentity.descriptor.deviceId,
            nonce = challenge.nonce,
            proofHex = hmacHex(secret, reconnectTranscript(challenge)),
        )
    }

    fun verifyReconnectProof(
        proof: StudyFlowReconnectProof,
        nowEpochMillis: Long,
    ): Boolean {
        val challenge = synchronized(pendingReconnectChallenges) {
            pendingReconnectChallenges.remove(proof.nonce)
        } ?: return false
        if (!challenge.isActive(nowEpochMillis)) return false
        if (challenge.challengerDeviceId != localIdentity.descriptor.deviceId) return false
        if (challenge.peerDeviceId != proof.proverDeviceId) return false
        val record = store.get(proof.proverDeviceId)
            ?.takeIf { it.state == StudyFlowTrustState.TRUSTED }
            ?: return false
        val secret = record.trustSecret ?: return false
        val expected = hmacHex(secret, reconnectTranscript(challenge))
        if (!constantTimeEquals(expected, proof.proofHex)) return false
        store.put(
            record.copy(
                updatedAtEpochMillis = nowEpochMillis,
                lastSeenAtEpochMillis = nowEpochMillis,
            ),
        )
        return true
    }

    companion object {
        const val DefaultPairingOfferLifetimeMillis: Long = PairingOfferLifetimeMillis
        const val DefaultPairingAttemptLimit: Int = PairingAttemptLimit
        const val DefaultPairingCodeDigits: Int = PairingCodeDigits
    }
}

internal fun randomHex(byteCount: Int, secureRandom: SecureRandom): String {
    val bytes = ByteArray(byteCount)
    secureRandom.nextBytes(bytes)
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal fun newPairingCode(secureRandom: SecureRandom): String =
    secureRandom.nextInt(1_000_000).toString().padStart(PairingCodeDigits, '0')

internal fun newPairingId(): StudyFlowPairingId = StudyFlowPairingId("pair-${UUID.randomUUID()}")

private fun reconnectTranscript(challenge: StudyFlowReconnectChallenge): String =
    listOf(
        "SFLP-RECONNECT-V1",
        challenge.challengerDeviceId.value,
        challenge.peerDeviceId.value,
        challenge.nonce.value,
        challenge.expiresAtEpochMillis.toString(),
    ).joinToString("|")

private fun hmacHex(secret: StudyFlowTrustSecret, message: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(hexToBytes(secret.value), "HmacSHA256"))
    return mac.doFinal(message.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun hexToBytes(value: String): ByteArray =
    ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }

private fun constantTimeEquals(expectedHex: String, actualHex: String): Boolean =
    MessageDigest.isEqual(expectedHex.toByteArray(Charsets.US_ASCII), actualHex.toByteArray(Charsets.US_ASCII))
