package com.mikeyphw.studyflow.core.link

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DefaultPresenceStaleAfterMillis = 45_000L
private const val DefaultPresenceExpiryMillis = 180_000L
private const val MaxPresenceBatchRecords = 128
private val presenceTokenPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

@Serializable
@JvmInline
value class StudyFlowPresenceInstanceId(val value: String) {
    init {
        require(presenceTokenPattern.matches(value)) { "Presence instance ID must be a bounded wire token" }
    }
}

@Serializable
@JvmInline
value class StudyFlowPresenceSessionId(val value: String) {
    init {
        require(presenceTokenPattern.matches(value)) { "Presence session ID must be a bounded wire token" }
    }
}

@Serializable
enum class StudyFlowPresenceAvailability {
    @SerialName("online")
    ONLINE,

    @SerialName("away")
    AWAY,

    @SerialName("offline")
    OFFLINE,
}

@Serializable
enum class StudyFlowPresenceStatus {
    @SerialName("online")
    ONLINE,

    @SerialName("away")
    AWAY,

    @SerialName("offline")
    OFFLINE,

    @SerialName("stale")
    STALE,
}

@Serializable
data class StudyFlowPresencePolicy(
    val staleAfterMillis: Long = DefaultPresenceStaleAfterMillis,
    val expiryMillis: Long = DefaultPresenceExpiryMillis,
) {
    init {
        require(staleAfterMillis > 0L) { "Presence stale threshold must be positive" }
        require(expiryMillis > staleAfterMillis) { "Presence expiry must be greater than stale threshold" }
    }
}

@Serializable
data class StudyFlowPresenceActivityHint(
    val activityId: StudyFlowActivityId,
    val kind: StudyFlowActivityKind,
    val entityId: StudyFlowEntityId? = null,
    val positionToken: String,
    val continuityMode: StudyFlowActivityContinuityMode,
    val requiredCapabilities: List<StudyFlowLinkCapability>,
) {
    init {
        require(positionToken.isNotBlank()) { "Presence activity position token must not be blank" }
        require(requiredCapabilities.isNotEmpty()) { "Presence activity hints must declare resume capabilities" }
        require(requiredCapabilities.distinct().size == requiredCapabilities.size) {
            "Presence activity capabilities must be unique"
        }
    }

    fun visibleTo(peer: StudyFlowLinkPeerDescriptor): Boolean {
        val available = peer.capabilities.toSet()
        return requiredCapabilities.all { it in available }
    }
}

@Serializable
data class StudyFlowPresenceRecord(
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDevice: StudyFlowLinkPeerDescriptor,
    val instanceId: StudyFlowPresenceInstanceId,
    val sessionId: StudyFlowPresenceSessionId,
    val sessionStartedAtEpochMillis: Long,
    val sequence: Long,
    val availability: StudyFlowPresenceAvailability,
    val observedAtEpochMillis: Long,
    val activityHint: StudyFlowPresenceActivityHint? = null,
    val visibilityCapabilities: List<StudyFlowLinkCapability> = emptyList(),
    val trace: StudyFlowRouteTrace = StudyFlowRouteTrace.begin(sourceDevice.deviceId),
) {
    init {
        require(sessionStartedAtEpochMillis >= 0L) { "Presence session start must not be negative" }
        require(sequence >= 1L) { "Presence sequence must start at one" }
        require(observedAtEpochMillis >= sessionStartedAtEpochMillis) {
            "Presence observation cannot predate its session"
        }
        require(visibilityCapabilities.distinct().size == visibilityCapabilities.size) {
            "Presence visibility capabilities must be unique"
        }
        require(trace.sourceDeviceId == sourceDevice.deviceId) {
            "Presence route trace must start at the source device"
        }
        require(trace.lastHopDeviceId in trace.visitedDeviceIds) { "Presence trace must expose a valid last hop" }
    }

    fun statusAt(
        nowEpochMillis: Long,
        policy: StudyFlowPresencePolicy = StudyFlowPresencePolicy(),
    ): StudyFlowPresenceStatus {
        require(nowEpochMillis >= 0L) { "Presence evaluation timestamp must not be negative" }
        if (availability == StudyFlowPresenceAvailability.OFFLINE) return StudyFlowPresenceStatus.OFFLINE
        if (nowEpochMillis - observedAtEpochMillis > policy.staleAfterMillis) return StudyFlowPresenceStatus.STALE
        return when (availability) {
            StudyFlowPresenceAvailability.ONLINE -> StudyFlowPresenceStatus.ONLINE
            StudyFlowPresenceAvailability.AWAY -> StudyFlowPresenceStatus.AWAY
            StudyFlowPresenceAvailability.OFFLINE -> StudyFlowPresenceStatus.OFFLINE
        }
    }

    fun isExpired(
        nowEpochMillis: Long,
        policy: StudyFlowPresencePolicy = StudyFlowPresencePolicy(),
    ): Boolean = nowEpochMillis - observedAtEpochMillis > policy.expiryMillis

    fun visibleTo(peer: StudyFlowLinkPeerDescriptor): Boolean {
        val available = peer.capabilities.toSet()
        return visibilityCapabilities.all { it in available }
    }
}

@Serializable
data class StudyFlowPresenceView(
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDevice: StudyFlowLinkPeerDescriptor,
    val instanceId: StudyFlowPresenceInstanceId,
    val status: StudyFlowPresenceStatus,
    val observedAtEpochMillis: Long,
    val activityHint: StudyFlowPresenceActivityHint? = null,
)

@Serializable
data class StudyFlowPresenceExchangeRequest(
    val records: List<StudyFlowPresenceRecord>,
) {
    init {
        require(records.size <= MaxPresenceBatchRecords) { "Presence exchange request is too large" }
    }
}

@Serializable
data class StudyFlowPresenceExchangeResponse(
    val records: List<StudyFlowPresenceRecord>,
) {
    init {
        require(records.size <= MaxPresenceBatchRecords) { "Presence exchange response is too large" }
    }
}

object StudyFlowPresenceMessageNames {
    const val Exchange = "presence.exchange"
}

enum class StudyFlowPresenceIngestResult {
    ACCEPTED,
    DUPLICATE,
    STALE,
    LOOP,
    EXPIRED,
    UNTRUSTED,
}

private data class StudyFlowPresenceKey(
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDeviceId: StudyFlowDeviceId,
    val instanceId: StudyFlowPresenceInstanceId,
)

/**
 * Ephemeral Presence registry. Nothing in this type is durable or required for journal reconciliation.
 * Records are bounded by expiry and are safe to gossip because every accepted relay appends the local
 * device to a route trace and rejects any record that has already visited that device.
 */
class StudyFlowPresenceRegistry(
    private val localDeviceId: StudyFlowDeviceId,
    private val policy: StudyFlowPresencePolicy = StudyFlowPresencePolicy(),
) {
    private val records = linkedMapOf<StudyFlowPresenceKey, StudyFlowPresenceRecord>()

    fun publishLocal(record: StudyFlowPresenceRecord): StudyFlowPresenceRecord = synchronized(this) {
        require(record.sourceDevice.deviceId == localDeviceId) { "Local presence must originate from the local device" }
        require(record.trace == StudyFlowRouteTrace.begin(localDeviceId)) {
            "Local presence must start with a fresh route trace"
        }
        expireLocked(record.observedAtEpochMillis)
        records[key(record)] = record
        record
    }

    fun ingest(
        record: StudyFlowPresenceRecord,
        authenticatedPeerDeviceId: StudyFlowDeviceId,
        trustedSourceDeviceIds: Set<StudyFlowDeviceId>,
        nowEpochMillis: Long,
    ): StudyFlowPresenceIngestResult = synchronized(this) {
        expireLocked(nowEpochMillis)
        if (record.isExpired(nowEpochMillis, policy)) return StudyFlowPresenceIngestResult.EXPIRED
        if (record.sourceDevice.deviceId == localDeviceId || localDeviceId in record.trace.visitedDeviceIds) {
            return StudyFlowPresenceIngestResult.LOOP
        }
        if (record.trace.lastHopDeviceId != authenticatedPeerDeviceId) {
            return StudyFlowPresenceIngestResult.UNTRUSTED
        }
        if (record.sourceDevice.deviceId !in trustedSourceDeviceIds) {
            return StudyFlowPresenceIngestResult.UNTRUSTED
        }
        val existing = records[key(record)]
        if (existing != null) {
            if (existing.sessionId == record.sessionId) {
                if (record.sequence == existing.sequence) return StudyFlowPresenceIngestResult.DUPLICATE
                if (record.sequence < existing.sequence) return StudyFlowPresenceIngestResult.STALE
            } else {
                val sessionOrder = compareValuesBy(
                    record,
                    existing,
                    StudyFlowPresenceRecord::sessionStartedAtEpochMillis,
                    { it.sessionId.value },
                )
                if (sessionOrder <= 0) return StudyFlowPresenceIngestResult.STALE
            }
        }
        if (!record.trace.canVisit(localDeviceId)) return StudyFlowPresenceIngestResult.STALE
        val relayed = record.copy(trace = record.trace.forwardedBy(localDeviceId))
        records[key(record)] = relayed
        StudyFlowPresenceIngestResult.ACCEPTED
    }

    fun recordsForPeer(
        workspaceId: StudyFlowWorkspaceId,
        peer: StudyFlowLinkPeerDescriptor,
        nowEpochMillis: Long,
    ): List<StudyFlowPresenceRecord> = synchronized(this) {
        expireLocked(nowEpochMillis)
        records.values
            .asSequence()
            .filter { it.workspaceId == workspaceId }
            .filter { it.sourceDevice.deviceId != peer.deviceId }
            .filter { peer.deviceId !in it.trace.visitedDeviceIds }
            .filter { it.visibleTo(peer) }
            .sortedWith(compareBy({ it.sourceDevice.deviceId.value }, { it.instanceId.value }))
            .take(MaxPresenceBatchRecords)
            .toList()
    }

    fun viewsFor(
        workspaceId: StudyFlowWorkspaceId,
        viewer: StudyFlowLinkPeerDescriptor,
        nowEpochMillis: Long,
    ): List<StudyFlowPresenceView> = synchronized(this) {
        expireLocked(nowEpochMillis)
        records.values
            .asSequence()
            .filter { it.workspaceId == workspaceId }
            .filter { it.visibleTo(viewer) }
            .map { record ->
                StudyFlowPresenceView(
                    workspaceId = record.workspaceId,
                    sourceDevice = record.sourceDevice,
                    instanceId = record.instanceId,
                    status = record.statusAt(nowEpochMillis, policy),
                    observedAtEpochMillis = record.observedAtEpochMillis,
                    activityHint = record.activityHint?.takeIf { it.visibleTo(viewer) },
                )
            }
            .sortedWith(compareBy({ it.sourceDevice.deviceId.value }, { it.instanceId.value }))
            .toList()
    }

    fun removePeer(deviceId: StudyFlowDeviceId) = synchronized(this) {
        records.entries.removeIf { (_, record) -> record.sourceDevice.deviceId == deviceId }
    }

    fun size(nowEpochMillis: Long): Int = synchronized(this) {
        expireLocked(nowEpochMillis)
        records.size
    }

    private fun expireLocked(nowEpochMillis: Long) {
        records.entries.removeIf { (_, record) -> record.isExpired(nowEpochMillis, policy) }
    }

    private fun key(record: StudyFlowPresenceRecord) = StudyFlowPresenceKey(
        workspaceId = record.workspaceId,
        sourceDeviceId = record.sourceDevice.deviceId,
        instanceId = record.instanceId,
    )
}

class StudyFlowLocalPresencePublisher(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val registry: StudyFlowPresenceRegistry,
    private val clock: StudyFlowPeerClock,
    private val instanceId: StudyFlowPresenceInstanceId = StudyFlowPresenceInstanceId("instance-${UUID.randomUUID()}"),
    private val sessionId: StudyFlowPresenceSessionId = StudyFlowPresenceSessionId("session-${UUID.randomUUID()}"),
    private val sessionStartedAtEpochMillis: Long = clock.nowEpochMillis(),
) {
    private data class LocalState(
        val sequence: Long,
        val availability: StudyFlowPresenceAvailability,
        val activityHint: StudyFlowPresenceActivityHint?,
        val visibilityCapabilities: List<StudyFlowLinkCapability>,
    )

    private val byWorkspace = linkedMapOf<StudyFlowWorkspaceId, LocalState>()

    fun announce(
        workspaceId: StudyFlowWorkspaceId,
        availability: StudyFlowPresenceAvailability,
        activityHint: StudyFlowPresenceActivityHint? = null,
        visibilityCapabilities: List<StudyFlowLinkCapability> = emptyList(),
    ): StudyFlowPresenceRecord = synchronized(this) {
        val previous = byWorkspace[workspaceId]
        val state = LocalState(
            sequence = (previous?.sequence ?: 0L) + 1L,
            availability = availability,
            activityHint = activityHint,
            visibilityCapabilities = visibilityCapabilities.distinct(),
        )
        byWorkspace[workspaceId] = state
        publish(workspaceId, state)
    }

    fun ensureOnline(workspaceId: StudyFlowWorkspaceId): StudyFlowPresenceRecord = synchronized(this) {
        val current = byWorkspace[workspaceId]
        if (current == null) {
            announce(workspaceId, StudyFlowPresenceAvailability.ONLINE)
        } else {
            val refreshed = current.copy(sequence = current.sequence + 1L)
            byWorkspace[workspaceId] = refreshed
            publish(workspaceId, refreshed)
        }
    }

    private fun publish(
        workspaceId: StudyFlowWorkspaceId,
        state: LocalState,
    ): StudyFlowPresenceRecord = registry.publishLocal(
        StudyFlowPresenceRecord(
            workspaceId = workspaceId,
            sourceDevice = localDevice,
            instanceId = instanceId,
            sessionId = sessionId,
            sessionStartedAtEpochMillis = sessionStartedAtEpochMillis,
            sequence = state.sequence,
            availability = state.availability,
            observedAtEpochMillis = clock.nowEpochMillis(),
            activityHint = state.activityHint,
            visibilityCapabilities = state.visibilityCapabilities,
            trace = StudyFlowRouteTrace.begin(localDevice.deviceId),
        ),
    )
}
