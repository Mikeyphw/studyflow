package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DefaultRouteHopLimit = 8
private const val DefaultSeenMessageCapacity = 4096

@Serializable
enum class StudyFlowPeerConnectionState {
    @SerialName("connecting")
    CONNECTING,

    @SerialName("connected")
    CONNECTED,

    @SerialName("degraded")
    DEGRADED,

    @SerialName("disconnected")
    DISCONNECTED,
}

@Serializable
data class StudyFlowPeerHealth(
    val state: StudyFlowPeerConnectionState,
    val lastSuccessAtEpochMillis: Long? = null,
    val lastFailureAtEpochMillis: Long? = null,
    val roundTripMillis: Long? = null,
    val consecutiveFailures: Int = 0,
) {
    init {
        require(lastSuccessAtEpochMillis == null || lastSuccessAtEpochMillis >= 0L) {
            "Peer success timestamp must not be negative"
        }
        require(lastFailureAtEpochMillis == null || lastFailureAtEpochMillis >= 0L) {
            "Peer failure timestamp must not be negative"
        }
        require(roundTripMillis == null || roundTripMillis >= 0L) {
            "Peer round-trip time must not be negative"
        }
        require(consecutiveFailures >= 0) { "Peer failure count must not be negative" }
    }

    fun isUsable(): Boolean = state == StudyFlowPeerConnectionState.CONNECTED ||
        state == StudyFlowPeerConnectionState.DEGRADED
}

class StudyFlowPeerHealthTracker(
    initial: StudyFlowPeerHealth = StudyFlowPeerHealth(StudyFlowPeerConnectionState.CONNECTING),
) {
    private var health = initial

    fun snapshot(): StudyFlowPeerHealth = synchronized(this) { health }

    fun recordSuccess(nowEpochMillis: Long, roundTripMillis: Long?): StudyFlowPeerHealth = synchronized(this) {
        require(nowEpochMillis >= 0L) { "Peer success timestamp must not be negative" }
        require(roundTripMillis == null || roundTripMillis >= 0L) { "Round-trip time must not be negative" }
        health = StudyFlowPeerHealth(
            state = StudyFlowPeerConnectionState.CONNECTED,
            lastSuccessAtEpochMillis = nowEpochMillis,
            lastFailureAtEpochMillis = health.lastFailureAtEpochMillis,
            roundTripMillis = roundTripMillis,
            consecutiveFailures = 0,
        )
        health
    }

    fun recordFailure(nowEpochMillis: Long): StudyFlowPeerHealth = synchronized(this) {
        require(nowEpochMillis >= 0L) { "Peer failure timestamp must not be negative" }
        val failures = health.consecutiveFailures + 1
        health = health.copy(
            state = if (failures >= 3) {
                StudyFlowPeerConnectionState.DISCONNECTED
            } else {
                StudyFlowPeerConnectionState.DEGRADED
            },
            lastFailureAtEpochMillis = nowEpochMillis,
            consecutiveFailures = failures,
        )
        health
    }

    fun markConnecting(): StudyFlowPeerHealth = synchronized(this) {
        health = health.copy(state = StudyFlowPeerConnectionState.CONNECTING)
        health
    }

    fun markDisconnected(): StudyFlowPeerHealth = synchronized(this) {
        health = health.copy(state = StudyFlowPeerConnectionState.DISCONNECTED)
        health
    }
}

@Serializable
enum class StudyFlowRouteKind {
    @SerialName("direct")
    DIRECT,

    @SerialName("learned")
    LEARNED,

    @SerialName("relay")
    RELAY,
}

@Serializable
data class StudyFlowPeerRoute(
    val targetDeviceId: StudyFlowDeviceId,
    val nextHopDeviceId: StudyFlowDeviceId,
    val endpoint: StudyFlowTransportEndpoint,
    val hopCount: Int,
    val kind: StudyFlowRouteKind,
    val health: StudyFlowPeerHealth,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(hopCount in 1..DefaultRouteHopLimit) { "Route hop count is outside SFLP bounds" }
        require(updatedAtEpochMillis >= 0L) { "Route update timestamp must not be negative" }
        if (kind == StudyFlowRouteKind.DIRECT) {
            require(targetDeviceId == nextHopDeviceId) { "A direct route must target its next hop" }
            require(hopCount == 1) { "A direct route must have exactly one hop" }
        }
    }
}

@Serializable
data class StudyFlowWorkspacePeer(
    val descriptor: StudyFlowLinkPeerDescriptor,
    val lastSeenAtEpochMillis: Long,
) {
    init {
        require(lastSeenAtEpochMillis >= 0L) { "Peer last-seen timestamp must not be negative" }
    }
}

class StudyFlowWorkspaceTopology(
    val workspaceId: StudyFlowWorkspaceId,
    val localDeviceId: StudyFlowDeviceId,
) {
    private val peers = linkedMapOf<StudyFlowDeviceId, StudyFlowWorkspacePeer>()
    private val routes = linkedMapOf<String, StudyFlowPeerRoute>()

    fun upsertPeer(peer: StudyFlowWorkspacePeer) {
        require(peer.descriptor.deviceId != localDeviceId) { "Local device must not be registered as a peer" }
        synchronized(this) { peers[peer.descriptor.deviceId] = peer }
    }

    fun removePeer(deviceId: StudyFlowDeviceId) = synchronized(this) {
        peers.remove(deviceId)
        routes.entries.removeIf { (_, route) ->
            route.targetDeviceId == deviceId || route.nextHopDeviceId == deviceId
        }
    }

    fun peer(deviceId: StudyFlowDeviceId): StudyFlowWorkspacePeer? = synchronized(this) { peers[deviceId] }

    fun peers(): List<StudyFlowWorkspacePeer> = synchronized(this) {
        peers.values.sortedBy { it.descriptor.deviceId.value }
    }

    fun upsertRoute(route: StudyFlowPeerRoute) {
        require(route.targetDeviceId != localDeviceId) { "A route must not target the local device" }
        require(route.nextHopDeviceId != localDeviceId) { "A route must not loop immediately to the local device" }
        val key = routeKey(route)
        synchronized(this) { routes[key] = route }
    }

    fun routesTo(deviceId: StudyFlowDeviceId): List<StudyFlowPeerRoute> = synchronized(this) {
        routes.values
            .filter { it.targetDeviceId == deviceId }
            .sortedWith(routeComparator)
    }

    fun bestRouteTo(
        deviceId: StudyFlowDeviceId,
        excludedNextHops: Set<StudyFlowDeviceId> = emptySet(),
    ): StudyFlowPeerRoute? = synchronized(this) {
        routes.values
            .asSequence()
            .filter { it.targetDeviceId == deviceId }
            .filter { it.nextHopDeviceId !in excludedNextHops }
            .filter { it.health.isUsable() }
            .minWithOrNull(routeComparator)
    }

    fun forwardingRoutes(
        excludedDeviceIds: Set<StudyFlowDeviceId>,
    ): List<StudyFlowPeerRoute> = synchronized(this) {
        routes.values
            .asSequence()
            .filter { it.kind == StudyFlowRouteKind.DIRECT }
            .filter { it.nextHopDeviceId !in excludedDeviceIds }
            .filter { it.health.isUsable() }
            .sortedWith(routeComparator)
            .distinctBy { it.nextHopDeviceId }
            .toList()
    }

    fun updateNextHopHealth(
        nextHopDeviceId: StudyFlowDeviceId,
        health: StudyFlowPeerHealth,
        nowEpochMillis: Long,
    ) = synchronized(this) {
        require(nowEpochMillis >= 0L) { "Route update timestamp must not be negative" }
        routes.keys.toList().forEach { key ->
            val route = requireNotNull(routes[key])
            if (route.nextHopDeviceId == nextHopDeviceId) {
                routes[key] = route.copy(health = health, updatedAtEpochMillis = nowEpochMillis)
            }
        }
    }

    private fun routeKey(route: StudyFlowPeerRoute): String = buildString {
        append(route.targetDeviceId.value)
        append('|')
        append(route.nextHopDeviceId.value)
        append('|')
        append(route.endpoint.canonicalUri())
    }

    private companion object {
        val routeComparator = compareBy<StudyFlowPeerRoute>(
            { connectionRank(it.health.state) },
            { routeKindRank(it.kind) },
            StudyFlowPeerRoute::hopCount,
            { it.health.consecutiveFailures },
            { it.health.roundTripMillis ?: Long.MAX_VALUE },
            { it.nextHopDeviceId.value },
            { it.endpoint.canonicalUri() },
        )

        fun connectionRank(state: StudyFlowPeerConnectionState): Int = when (state) {
            StudyFlowPeerConnectionState.CONNECTED -> 0
            StudyFlowPeerConnectionState.DEGRADED -> 1
            StudyFlowPeerConnectionState.CONNECTING -> 2
            StudyFlowPeerConnectionState.DISCONNECTED -> 3
        }

        fun routeKindRank(kind: StudyFlowRouteKind): Int = when (kind) {
            StudyFlowRouteKind.DIRECT -> 0
            StudyFlowRouteKind.LEARNED -> 1
            StudyFlowRouteKind.RELAY -> 2
        }
    }
}

@Serializable
data class StudyFlowRouteTrace(
    val sourceDeviceId: StudyFlowDeviceId,
    val visitedDeviceIds: List<StudyFlowDeviceId>,
    val hopLimit: Int = DefaultRouteHopLimit,
) {
    init {
        require(hopLimit in 1..32) { "Route hop limit must be between 1 and 32" }
        require(visitedDeviceIds.isNotEmpty()) { "Route trace must contain its source" }
        require(visitedDeviceIds.first() == sourceDeviceId) { "Route trace must start at its source" }
        require(visitedDeviceIds.distinct().size == visitedDeviceIds.size) { "Route trace must not contain loops" }
        require(visitedDeviceIds.size <= hopLimit) { "Route trace exceeds its hop limit" }
    }

    val lastHopDeviceId: StudyFlowDeviceId get() = visitedDeviceIds.last()

    fun canVisit(deviceId: StudyFlowDeviceId): Boolean =
        deviceId !in visitedDeviceIds && visitedDeviceIds.size < hopLimit

    fun forwardedBy(deviceId: StudyFlowDeviceId): StudyFlowRouteTrace {
        require(canVisit(deviceId)) { "Route trace would loop or exceed its hop limit" }
        return copy(visitedDeviceIds = visitedDeviceIds + deviceId)
    }

    companion object {
        fun begin(
            sourceDeviceId: StudyFlowDeviceId,
            hopLimit: Int = DefaultRouteHopLimit,
        ): StudyFlowRouteTrace = StudyFlowRouteTrace(sourceDeviceId, listOf(sourceDeviceId), hopLimit)
    }
}

@Serializable
data class StudyFlowRoutedEnvelope(
    val trace: StudyFlowRouteTrace,
    val envelope: StudyFlowLinkEnvelope,
) {
    init {
        require(trace.sourceDeviceId == envelope.senderDeviceId) {
            "Routed envelope source must match the original envelope sender"
        }
    }
}

class StudyFlowMessageDeduplicator(
    private val capacity: Int = DefaultSeenMessageCapacity,
) {
    private val seen = linkedSetOf<StudyFlowMessageId>()

    init {
        require(capacity >= 1) { "Message dedupe capacity must be at least one" }
    }

    fun accept(messageId: StudyFlowMessageId): Boolean = synchronized(this) {
        if (messageId in seen) return false
        seen += messageId
        while (seen.size > capacity) {
            seen.remove(seen.first())
        }
        true
    }

    fun contains(messageId: StudyFlowMessageId): Boolean = synchronized(this) { messageId in seen }

    fun size(): Int = synchronized(this) { seen.size }
}

class StudyFlowTopologyRouter(
    private val topology: StudyFlowWorkspaceTopology,
    private val deduplicator: StudyFlowMessageDeduplicator = StudyFlowMessageDeduplicator(),
) {
    fun originate(envelope: StudyFlowLinkEnvelope): StudyFlowRoutedEnvelope {
        require(envelope.workspaceId == topology.workspaceId) { "Envelope belongs to another workspace" }
        require(envelope.senderDeviceId == topology.localDeviceId) { "Only local envelopes can originate here" }
        check(deduplicator.accept(envelope.messageId)) { "Message ID was already routed" }
        return StudyFlowRoutedEnvelope(StudyFlowRouteTrace.begin(topology.localDeviceId), envelope)
    }

    fun acceptIncoming(
        routed: StudyFlowRoutedEnvelope,
        fromDeviceId: StudyFlowDeviceId,
    ): StudyFlowRoutedEnvelope? {
        require(routed.envelope.workspaceId == topology.workspaceId) { "Envelope belongs to another workspace" }
        if (routed.trace.lastHopDeviceId != fromDeviceId) return null
        if (!routed.trace.canVisit(topology.localDeviceId)) return null
        if (!deduplicator.accept(routed.envelope.messageId)) return null
        return routed.copy(trace = routed.trace.forwardedBy(topology.localDeviceId))
    }

    fun routeTo(
        targetDeviceId: StudyFlowDeviceId,
        routed: StudyFlowRoutedEnvelope,
    ): StudyFlowPeerRoute? = topology.bestRouteTo(
        targetDeviceId,
        excludedNextHops = routed.trace.visitedDeviceIds.toSet(),
    )

    fun broadcastRoutes(routed: StudyFlowRoutedEnvelope): List<StudyFlowPeerRoute> =
        topology.forwardingRoutes(routed.trace.visitedDeviceIds.toSet())
}
