package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudyFlowOv13PeerLifecycleLanRecoveryTest {
    @Test
    fun startupPolicyRequiresUserEnablementAndLocalNetworkPermissionBeforeSockets() {
        assertEquals(
            "user-disabled",
            StudyFlowPeerLifecyclePolicy(
                userEnabled = false,
                localNetworkPermissionGranted = true,
            ).startupDecision(nowEpochMillis = 10_000L).reason,
        )
        assertEquals(
            "local-network-permission-required",
            StudyFlowPeerLifecyclePolicy(
                userEnabled = true,
                localNetworkPermissionGranted = false,
            ).startupDecision(nowEpochMillis = 10_000L).reason,
        )
        assertTrue(
            StudyFlowPeerLifecyclePolicy(
                userEnabled = true,
                localNetworkPermissionGranted = true,
            ).startupDecision(nowEpochMillis = 10_000L).allowed,
        )
    }

    @Test
    fun transientPeerStartupFailuresRemainRetryableAfterReady() {
        val recovery = StudyFlowPeerLifecycleRecovery(retryDelayMillis = 2_500L)
        val decision = recovery.failureDecision(100_000L, "nsd-bind-failed")

        assertFalse(decision.allowed)
        assertEquals("nsd-bind-failed", decision.reason)
        assertEquals(102_500L, decision.retryAtEpochMillis)
        assertEquals(
            "retry-backoff",
            StudyFlowPeerLifecyclePolicy(
                userEnabled = true,
                localNetworkPermissionGranted = true,
                retryAtEpochMillis = requireNotNull(decision.retryAtEpochMillis),
            ).startupDecision(nowEpochMillis = 101_000L).reason,
        )
    }

    @Test
    fun externallyReachableEndpointFilterRejectsLoopbackWildcardAndPreservesIpv6ScopeIds() {
        assertFalse(StudyFlowLanEndpointResolver.isExternallyReachableHost("127.0.0.1"))
        assertFalse(StudyFlowLanEndpointResolver.isExternallyReachableHost("localhost"))
        assertFalse(StudyFlowLanEndpointResolver.isExternallyReachableHost("0.0.0.0"))
        assertFalse(StudyFlowLanEndpointResolver.isExternallyReachableHost("::1"))

        val scoped = "fe80::1234%wlan0"
        assertTrue(StudyFlowLanEndpointResolver.isExternallyReachableHost(scoped))
        val endpoint = StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, scoped, 45_000)
        assertEquals(scoped, endpoint.host)
        assertTrue(StudyFlowLanEndpointResolver.isExternallyReachableEndpoint(endpoint))
    }

    @Test
    fun readinessCannotClaimReadyWithoutReachableEndpoints() {
        val accepted = StudyFlowPeerReadiness(
            ready = true,
            externallyReachableEndpoints = listOf(
                StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, "192.168.1.10", 45_000),
            ),
            discoveryAttached = true,
        )
        assertTrue(accepted.ready)
        assertNotNull(accepted.externallyReachableEndpoints.single().port)

        val failure = runCatching {
            StudyFlowPeerReadiness(
                ready = true,
                externallyReachableEndpoints = emptyList(),
                discoveryAttached = true,
            )
        }
        assertTrue(requireNotNull(failure.exceptionOrNull()).message!!.contains("externally reachable"))
    }
}
