package com.mikeyphw.studyflow.core.link

/** Core, platform-neutral decision boundary for starting local SFLP transports. */
data class StudyFlowPeerStartDecision(
    val allowed: Boolean,
    val reason: String? = null,
    val retryAtEpochMillis: Long? = null,
) {
    init {
        require(allowed || !reason.isNullOrBlank()) { "Rejected peer startup decisions must carry a reason." }
    }
}

data class StudyFlowPeerLifecyclePolicy(
    val userEnabled: Boolean,
    val localNetworkPermissionGranted: Boolean,
    val retryAtEpochMillis: Long? = null,
) {
    fun startupDecision(nowEpochMillis: Long): StudyFlowPeerStartDecision = when {
        !userEnabled -> StudyFlowPeerStartDecision(false, "user-disabled")
        !localNetworkPermissionGranted -> StudyFlowPeerStartDecision(false, "local-network-permission-required")
        retryAtEpochMillis != null && nowEpochMillis < retryAtEpochMillis ->
            StudyFlowPeerStartDecision(false, "retry-backoff", retryAtEpochMillis)
        else -> StudyFlowPeerStartDecision(true)
    }
}

data class StudyFlowPeerReadiness(
    val ready: Boolean,
    val externallyReachableEndpoints: List<StudyFlowTransportEndpoint>,
    val discoveryAttached: Boolean,
    val lastFailure: String? = null,
) {
    init {
        require(externallyReachableEndpoints.all(StudyFlowLanEndpointResolver::isExternallyReachableEndpoint)) {
            "Peer readiness cannot advertise loopback, wildcard, or local-only endpoints."
        }
        require(!ready || externallyReachableEndpoints.isNotEmpty()) {
            "A ready peer must expose at least one externally reachable endpoint."
        }
    }
}

class StudyFlowPeerLifecycleRecovery(
    private val retryDelayMillis: Long = DefaultRetryDelayMillis,
) {
    init {
        require(retryDelayMillis in 500L..300_000L) { "Peer lifecycle retry delay is out of range." }
    }

    fun failureDecision(nowEpochMillis: Long, failure: String): StudyFlowPeerStartDecision =
        StudyFlowPeerStartDecision(
            allowed = false,
            reason = failure.ifBlank { "peer-startup-failed" },
            retryAtEpochMillis = nowEpochMillis + retryDelayMillis,
        )

    companion object {
        const val DefaultRetryDelayMillis: Long = 2_000L
    }
}
