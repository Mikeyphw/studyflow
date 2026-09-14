package com.mikeyphw.studyflow.core.tvcontrol

import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * OV21 shared admission boundary for every TV control path.
 *
 * Local TCP, browser relay, cast synchronization, domain-action acknowledgement, revoke, and
 * TV attention ingress all produce the same signed plane envelope before a receiver mutates any
 * visible TV state. The envelope is deliberately transport-neutral: it does not replace the
 * authenticated wire proof, but it binds the proof to the intended receiver, session, operation,
 * cast/presentation scope, and one concrete payload hash.
 */
object StudyFlowTvUnifiedControlPlaneContract {
    const val overlay = "OV21"
    const val signedPlaneEnvelopeRequired = true
    const val localTcpAndBrowserRelaySharePlanePolicy = true
    const val sessionReceiverBindingRequired = true
    const val commandPresentationContinuityDomainRevokeCovered = true
    const val castPresentationScopeRequired = true
    const val stopCastCarriesCastScope = true
    const val attentionIngressUsesCanonicalPlane = true
    const val bodyHashExcludesAuthProofAndEnvelope = true
    const val rawControllerTokenForbidden = true
    const val bearerRelayTokenForbidden = true
    const val maxEnvelopeLifetimeMillis = 30_000L
}

@Serializable
enum class StudyFlowTvPlaneOperation {
    PAIRING_INFO,
    PAIR,
    COMMAND,
    PRESENTATION,
    CONTINUITY,
    DOMAIN_ACTION_RESULT,
    REVOKE,
    ATTENTION,
    PING,
}

@Serializable
data class StudyFlowTvPlaneEnvelope(
    val messageId: String,
    val protocolVersion: Int = StudyFlowTvControlProtocol.version,
    val operation: StudyFlowTvPlaneOperation,
    val sessionId: String? = null,
    val receiverServiceName: String? = null,
    val receiverHostAddress: String? = null,
    val receiverPort: Int? = null,
    val castScopeId: String? = null,
    val presentationSnapshotId: String? = null,
    val attentionOccurrenceId: String? = null,
    val domainActionId: String? = null,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val bodySha256Hex: String,
) {
    init {
        require(messageId.isNotBlank()) { "TV plane envelope id must not be blank." }
        require(protocolVersion == StudyFlowTvControlProtocol.version) { "Unsupported TV plane version." }
        require(issuedAtEpochMillis >= 0L) { "TV plane issue time cannot be negative." }
        require(expiresAtEpochMillis > issuedAtEpochMillis) { "TV plane envelope must expire after issue." }
        require(expiresAtEpochMillis - issuedAtEpochMillis <= StudyFlowTvUnifiedControlPlaneContract.maxEnvelopeLifetimeMillis) {
            "TV plane envelope lifetime must be bounded."
        }
        require(bodySha256Hex.length == 64 && bodySha256Hex.all { it in '0'..'9' || it in 'a'..'f' }) {
            "TV plane envelope must carry a SHA-256 payload hash."
        }
        receiverServiceName?.let { require(it.isNotBlank()) { "TV plane receiver service cannot be blank." } }
        receiverHostAddress?.let { require(it.isNotBlank()) { "TV plane receiver host cannot be blank." } }
        receiverPort?.let { require(it in 1..65_535) { "TV plane receiver port must be valid." } }
        castScopeId?.let { require(it.isNotBlank()) { "TV plane cast scope cannot be blank." } }
        presentationSnapshotId?.let { require(it.isNotBlank()) { "TV plane presentation snapshot cannot be blank." } }
        attentionOccurrenceId?.let { require(it.isNotBlank()) { "TV plane attention occurrence cannot be blank." } }
        domainActionId?.let { require(it.isNotBlank()) { "TV plane domain action cannot be blank." } }
    }

    fun isFresh(nowEpochMillis: Long): Boolean = nowEpochMillis in issuedAtEpochMillis..expiresAtEpochMillis
}

data class StudyFlowTvPlaneDecision(
    val accepted: Boolean,
    val reason: String? = null,
) {
    companion object {
        val Accepted = StudyFlowTvPlaneDecision(true)
        fun reject(reason: String) = StudyFlowTvPlaneDecision(false, reason)
    }
}

object StudyFlowTvUnifiedControlPlane {
    fun attachEnvelope(
        request: StudyFlowTvWireRequest,
        credential: StudyFlowTvSessionCredential,
        endpoint: StudyFlowTvReceiverEndpoint,
        nowEpochMillis: Long = request.issuedAtEpochMillis,
    ): StudyFlowTvWireRequest {
        val unsigned = request.copy(authNonceHex = null, authProofHex = null, planeEnvelope = null)
        val operation = operationFor(request.operation)
        val command = request.command
        val presentation = request.presentation
        val result = request.domainActionResult
        val castScope = when {
            presentation != null -> presentation.studyPlanId
            command?.action == StudyFlowTvControlAction.STOP_CAST -> command.targetId ?: command.surface.name
            else -> null
        }
        return request.copy(
            planeEnvelope = StudyFlowTvPlaneEnvelope(
                messageId = request.requestId,
                operation = operation,
                sessionId = credential.sessionId,
                receiverServiceName = endpoint.serviceName,
                receiverHostAddress = endpoint.hostAddress,
                receiverPort = endpoint.port,
                castScopeId = castScope,
                presentationSnapshotId = presentation?.snapshotId,
                domainActionId = result?.actionId,
                issuedAtEpochMillis = nowEpochMillis,
                expiresAtEpochMillis = nowEpochMillis + StudyFlowTvUnifiedControlPlaneContract.maxEnvelopeLifetimeMillis,
                bodySha256Hex = sha256Hex(StudyFlowTvWireCodec.encodeRequest(unsigned)),
            ),
        )
    }

    fun attentionEnvelope(
        messageId: String,
        sessionId: String?,
        sourceDeviceId: String,
        occurrenceId: String,
        nowEpochMillis: Long,
        payloadBytes: ByteArray,
    ): StudyFlowTvPlaneEnvelope = StudyFlowTvPlaneEnvelope(
        messageId = messageId,
        operation = StudyFlowTvPlaneOperation.ATTENTION,
        sessionId = sessionId,
        receiverServiceName = sourceDeviceId,
        attentionOccurrenceId = occurrenceId,
        issuedAtEpochMillis = nowEpochMillis,
        expiresAtEpochMillis = nowEpochMillis + StudyFlowTvUnifiedControlPlaneContract.maxEnvelopeLifetimeMillis,
        bodySha256Hex = sha256Hex(payloadBytes),
    )

    fun authorizeAuthenticatedRequest(
        request: StudyFlowTvWireRequest,
        credential: StudyFlowTvSessionCredential,
        nowEpochMillis: Long,
    ): StudyFlowTvPlaneDecision {
        val envelope = request.planeEnvelope ?: return StudyFlowTvPlaneDecision.reject("tv-plane-envelope-required")
        if (!envelope.isFresh(nowEpochMillis)) return StudyFlowTvPlaneDecision.reject("tv-plane-envelope-expired")
        if (envelope.messageId != request.requestId) return StudyFlowTvPlaneDecision.reject("tv-plane-message-mismatch")
        if (envelope.operation != operationFor(request.operation)) return StudyFlowTvPlaneDecision.reject("tv-plane-operation-mismatch")
        if (envelope.sessionId != credential.sessionId || request.sessionId != credential.sessionId) {
            return StudyFlowTvPlaneDecision.reject("tv-plane-session-mismatch")
        }
        if (!envelope.matchesCredentialBinding(credential)) {
            return StudyFlowTvPlaneDecision.reject("tv-plane-receiver-binding-mismatch")
        }
        val unsigned = request.copy(authNonceHex = null, authProofHex = null, planeEnvelope = null)
        if (envelope.bodySha256Hex != sha256Hex(StudyFlowTvWireCodec.encodeRequest(unsigned))) {
            return StudyFlowTvPlaneDecision.reject("tv-plane-payload-hash-mismatch")
        }
        val command = request.command
        val presentation = request.presentation
        val domainResult = request.domainActionResult
        if (request.operation == StudyFlowTvWireOperation.PRESENTATION) {
            if (presentation == null) return StudyFlowTvPlaneDecision.reject("tv-plane-presentation-required")
            if (envelope.castScopeId != presentation.studyPlanId) return StudyFlowTvPlaneDecision.reject("tv-plane-cast-scope-mismatch")
            if (envelope.presentationSnapshotId != presentation.snapshotId) {
                return StudyFlowTvPlaneDecision.reject("tv-plane-presentation-snapshot-mismatch")
            }
        }
        if (command?.action == StudyFlowTvControlAction.STOP_CAST && envelope.castScopeId.isNullOrBlank()) {
            return StudyFlowTvPlaneDecision.reject("tv-plane-stop-cast-scope-required")
        }
        if (request.operation == StudyFlowTvWireOperation.DOMAIN_ACTION_RESULT) {
            if (domainResult == null) return StudyFlowTvPlaneDecision.reject("tv-plane-domain-result-required")
            if (envelope.domainActionId != domainResult.actionId) return StudyFlowTvPlaneDecision.reject("tv-plane-domain-action-mismatch")
        }
        return StudyFlowTvPlaneDecision.Accepted
    }

    fun operationFor(operation: StudyFlowTvWireOperation): StudyFlowTvPlaneOperation = when (operation) {
        StudyFlowTvWireOperation.PAIRING_INFO -> StudyFlowTvPlaneOperation.PAIRING_INFO
        StudyFlowTvWireOperation.PAIR -> StudyFlowTvPlaneOperation.PAIR
        StudyFlowTvWireOperation.COMMAND -> StudyFlowTvPlaneOperation.COMMAND
        StudyFlowTvWireOperation.PRESENTATION -> StudyFlowTvPlaneOperation.PRESENTATION
        StudyFlowTvWireOperation.CONTINUITY -> StudyFlowTvPlaneOperation.CONTINUITY
        StudyFlowTvWireOperation.DOMAIN_ACTION_RESULT -> StudyFlowTvPlaneOperation.DOMAIN_ACTION_RESULT
        StudyFlowTvWireOperation.REVOKE -> StudyFlowTvPlaneOperation.REVOKE
        StudyFlowTvWireOperation.PING -> StudyFlowTvPlaneOperation.PING
    }

    private fun StudyFlowTvPlaneEnvelope.matchesCredentialBinding(credential: StudyFlowTvSessionCredential): Boolean {
        val serviceOk = receiverServiceName?.let { service ->
            credential.receiverServiceName?.let { it == service } ?: (credential.tvDeviceName == service)
        } ?: false
        val hostOk = receiverHostAddress?.let { requested ->
            credential.receiverHostAddress?.let { bound -> bound.normalizedHost() == requested.normalizedHost() } ?: true
        } ?: credential.receiverHostAddress == null
        val portOk = receiverPort?.let { requested -> credential.receiverPort?.let { it == requested } ?: true } ?: credential.receiverPort == null
        return serviceOk && hostOk && portOk
    }

    private fun String.normalizedHost(): String = lowercase(Locale.US)
    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
        "%02x".format(it.toInt() and 0xff)
    }
}
