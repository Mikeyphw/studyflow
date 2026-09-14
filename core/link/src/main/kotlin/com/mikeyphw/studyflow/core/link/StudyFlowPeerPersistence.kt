package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class StudyFlowTrustSnapshot(
    val records: List<StudyFlowTrustedPeerRecord> = emptyList(),
)

@Serializable
data class StudyFlowWorkspaceMembershipSnapshot(val members: List<StudyFlowWorkspaceMember> = emptyList())

@Serializable
data class StudyFlowOutgoingQueueSnapshot(
    val workspaceId: StudyFlowWorkspaceId,
    val entries: List<StudyFlowOutgoingQueueEntry> = emptyList(),
)

@Serializable
data class StudyFlowJournalSnapshot(
    val workspaceId: StudyFlowWorkspaceId,
    val entries: List<StudyFlowJournalEntry> = emptyList(),
    val checkpoints: List<StudyFlowJournalCheckpoint> = emptyList(),
)

@Serializable
data class StudyFlowPeerEndpointRecord(
    val deviceId: StudyFlowDeviceId,
    val endpoints: List<StudyFlowTransportEndpoint>,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(endpoints.isNotEmpty()) { "Peer endpoint record must contain at least one endpoint" }
        require(endpoints.distinct().size == endpoints.size) { "Peer endpoint record must not contain duplicates" }
        require(updatedAtEpochMillis >= 0L) { "Endpoint record timestamp must not be negative" }
    }
}

@Serializable
data class StudyFlowPeerEndpointSnapshot(
    val records: List<StudyFlowPeerEndpointRecord> = emptyList(),
)

interface StudyFlowPeerEndpointStore {
    fun get(deviceId: StudyFlowDeviceId): StudyFlowPeerEndpointRecord?
    fun list(): List<StudyFlowPeerEndpointRecord>
    fun put(record: StudyFlowPeerEndpointRecord)
    fun remove(deviceId: StudyFlowDeviceId): Boolean
}

class InMemoryStudyFlowPeerEndpointStore(
    records: Iterable<StudyFlowPeerEndpointRecord> = emptyList(),
) : StudyFlowPeerEndpointStore {
    private val recordsByDeviceId = linkedMapOf<StudyFlowDeviceId, StudyFlowPeerEndpointRecord>()

    init {
        records.forEach { recordsByDeviceId[it.deviceId] = it }
    }

    override fun get(deviceId: StudyFlowDeviceId): StudyFlowPeerEndpointRecord? = synchronized(this) {
        recordsByDeviceId[deviceId]
    }

    override fun list(): List<StudyFlowPeerEndpointRecord> = synchronized(this) {
        recordsByDeviceId.values.sortedBy { it.deviceId.value }
    }

    override fun put(record: StudyFlowPeerEndpointRecord) {
        synchronized(this) { recordsByDeviceId[record.deviceId] = record }
    }

    override fun remove(deviceId: StudyFlowDeviceId): Boolean = synchronized(this) {
        recordsByDeviceId.remove(deviceId) != null
    }
}

object StudyFlowPeerPersistenceCodec {
    fun encodeIdentity(identity: StudyFlowLocalDeviceIdentity): String =
        StudyFlowLinkCodec.json.encodeToString(identity)

    fun decodeIdentity(encoded: String): StudyFlowLocalDeviceIdentity =
        StudyFlowLinkCodec.json.decodeFromString(encoded)

    fun encodeTrustRecords(records: List<StudyFlowTrustedPeerRecord>): String =
        StudyFlowLinkCodec.json.encodeToString(StudyFlowTrustSnapshot(records))

    fun decodeTrustRecords(encoded: String): List<StudyFlowTrustedPeerRecord> =
        StudyFlowLinkCodec.json.decodeFromString<StudyFlowTrustSnapshot>(encoded).records

    fun encodeWorkspaceMembership(members: List<StudyFlowWorkspaceMember>): String =
        StudyFlowLinkCodec.json.encodeToString(StudyFlowWorkspaceMembershipSnapshot(members))

    fun decodeWorkspaceMembership(encoded: String): List<StudyFlowWorkspaceMember> =
        StudyFlowLinkCodec.json.decodeFromString<StudyFlowWorkspaceMembershipSnapshot>(encoded).members

    fun encodeOutgoingQueue(
        workspaceId: StudyFlowWorkspaceId,
        entries: List<StudyFlowOutgoingQueueEntry>,
    ): String = StudyFlowLinkCodec.json.encodeToString(
        StudyFlowOutgoingQueueSnapshot(workspaceId = workspaceId, entries = entries),
    )

    fun decodeOutgoingQueue(
        workspaceId: StudyFlowWorkspaceId,
        encoded: String,
    ): List<StudyFlowOutgoingQueueEntry> =
        StudyFlowLinkCodec.json.decodeFromString<StudyFlowOutgoingQueueSnapshot>(encoded).also {
            require(it.workspaceId == workspaceId) { "Outgoing queue snapshot belongs to another workspace" }
        }.entries

    fun encodeJournal(
        workspaceId: StudyFlowWorkspaceId,
        entries: List<StudyFlowJournalEntry>,
        checkpoints: List<StudyFlowJournalCheckpoint>,
    ): String = StudyFlowLinkCodec.json.encodeToString(
        StudyFlowJournalSnapshot(workspaceId = workspaceId, entries = entries, checkpoints = checkpoints),
    )

    fun decodeJournal(
        workspaceId: StudyFlowWorkspaceId,
        encoded: String,
    ): StudyFlowJournalSnapshot = StudyFlowLinkCodec.json.decodeFromString<StudyFlowJournalSnapshot>(encoded).also {
        require(it.workspaceId == workspaceId) { "Journal snapshot belongs to another workspace" }
    }

    fun encodePeerEndpoints(records: List<StudyFlowPeerEndpointRecord>): String =
        StudyFlowLinkCodec.json.encodeToString(StudyFlowPeerEndpointSnapshot(records))

    fun decodePeerEndpoints(encoded: String): List<StudyFlowPeerEndpointRecord> =
        StudyFlowLinkCodec.json.decodeFromString<StudyFlowPeerEndpointSnapshot>(encoded).records
}
