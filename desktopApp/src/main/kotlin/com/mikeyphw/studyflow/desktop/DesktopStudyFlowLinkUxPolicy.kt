package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowReconnectResult
import com.mikeyphw.studyflow.core.link.StudyFlowReconnectStatus
import com.mikeyphw.studyflow.core.link.StudyFlowTransportEndpoint
import com.mikeyphw.studyflow.core.link.StudyFlowTransportScheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Desktop-facing SFLP policy for endpoints and user-visible status copy.
 *
 * The shared SFLP runtime is intentionally transport-generic; this boundary owns
 * Desktop UX correctness so Settings never advertises loopback-only pairing,
 * never accepts self-loopback manual peers, and never says a sync succeeded when
 * at least one peer was interrupted or deferred.
 */
object DesktopStudyFlowLinkUxPolicy {
    private val expiryFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())

    fun shareableEndpoints(endpoints: List<StudyFlowTransportEndpoint>): List<StudyFlowTransportEndpoint> = endpoints
        .filter { endpoint -> endpoint.scheme in shareableSchemes }
        .filterNot { endpoint -> endpoint.host.isLoopbackOrWildcardHost() }
        .distinctBy { endpoint -> endpoint.canonicalUri() }

    fun validateManualEndpoint(endpoint: StudyFlowTransportEndpoint): StudyFlowTransportEndpoint {
        require(endpoint.scheme in manualPeerSchemes) {
            "Desktop SFLP accepts TCP, WebSocket, secure WebSocket, or relay peer endpoints."
        }
        require(!endpoint.host.isLoopbackOrWildcardHost()) {
            "Desktop manual endpoints must name a reachable peer, not this process loopback."
        }
        return endpoint
    }

    fun formatPairingExpiry(expiresAtEpochMillis: Long): String =
        expiryFormatter.format(Instant.ofEpochMilli(expiresAtEpochMillis))

    fun syncSummary(results: List<StudyFlowReconnectResult>): String {
        if (results.isEmpty()) return "No trusted peers were eligible for sync"
        val complete = results.count { it.status == StudyFlowReconnectStatus.COMPLETE && it.failure == null }
        val retryable = results.count {
            it.status in setOf(StudyFlowReconnectStatus.DEFERRED_BY_BACKOFF, StudyFlowReconnectStatus.INTERRUPTED) ||
                it.failure != null
        }
        val terminal = results.count { it.status == StudyFlowReconnectStatus.TERMINAL_FAILURE }
        val transferred = results.sumOf { it.pushedOperations + it.pulledOperations }
        return when {
            retryable == 0 && terminal == 0 -> "Sync complete for $complete peer(s), $transferred operation(s) exchanged"
            complete > 0 -> "Sync partially complete: $complete peer(s) updated, $retryable retryable, $terminal terminal"
            else -> "Sync did not complete: $retryable retryable peer(s), $terminal terminal peer(s)"
        }
    }

    fun capabilityMessage(state: DesktopStudyFlowLinkState): String = when {
        state.localEndpoints.isEmpty() ->
            "No shareable LAN endpoint is available; use a reachable manual/relay peer endpoint."
        state.trustedPeers.isEmpty() ->
            "Create a pairing offer or pair from another StudyFlow device before syncing."
        state.trustedPeers.none { it.reachable } ->
            "Trusted peers are remembered, but none currently has a reachable endpoint."
        else ->
            "Desktop SFLP is ready; sync results remain retry-aware instead of assuming success."
    }

    private fun String.isLoopbackOrWildcardHost(): Boolean {
        val normalized = trim().removeSurrounding("[", "]").lowercase()
        if (normalized == "localhost" || normalized == "::1" || normalized == "0:0:0:0:0:0:0:1") return true
        if (normalized == "0.0.0.0" || normalized == "::" || normalized == "0:0:0:0:0:0:0:0") return true
        if (normalized.startsWith("127.")) return true
        return false
    }

    private val shareableSchemes = setOf(
        StudyFlowTransportScheme.TCP,
        StudyFlowTransportScheme.WEBSOCKET,
        StudyFlowTransportScheme.WEBSOCKET_SECURE,
        StudyFlowTransportScheme.RELAY,
    )
    private val manualPeerSchemes = shareableSchemes
}
