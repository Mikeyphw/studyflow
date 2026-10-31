package com.mikeyphw.studyflow.link

import android.content.Context
import android.content.SharedPreferences
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceIdentityStore
import com.mikeyphw.studyflow.core.link.StudyFlowJournalCheckpoint
import com.mikeyphw.studyflow.core.link.StudyFlowJournalEntry
import com.mikeyphw.studyflow.core.link.StudyFlowJournalSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowLocalDeviceIdentity
import com.mikeyphw.studyflow.core.link.StudyFlowOperationJournalStore
import com.mikeyphw.studyflow.core.link.StudyFlowOutgoingQueueEntry
import com.mikeyphw.studyflow.core.link.StudyFlowOutgoingQueueStore
import com.mikeyphw.studyflow.core.link.StudyFlowPeerEndpointRecord
import com.mikeyphw.studyflow.core.link.StudyFlowPeerEndpointStore
import com.mikeyphw.studyflow.core.link.StudyFlowPeerPersistenceCodec
import com.mikeyphw.studyflow.core.link.StudyFlowPeerRetryState
import com.mikeyphw.studyflow.core.link.StudyFlowPersistenceCorruptionRecord
import com.mikeyphw.studyflow.core.link.StudyFlowPersistedStateKind
import com.mikeyphw.studyflow.core.link.StudyFlowTrustStore
import com.mikeyphw.studyflow.core.link.StudyFlowTrustedPeerRecord
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceMember
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceMembershipStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class AndroidStudyFlowLinkStatePersistence(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    val identityStore: StudyFlowDeviceIdentityStore = object : StudyFlowDeviceIdentityStore {
        override fun load(): StudyFlowLocalDeviceIdentity? = synchronized(preferences) {
            preferences.getString(IdentityKey, null)?.let { encoded ->
                decodeOrQuarantine(IdentityKey, StudyFlowPersistedStateKind.IDENTITY, encoded, fallback = { null }) {
                    StudyFlowPeerPersistenceCodec.decodeIdentity(it)
                }
            }
        }

        override fun save(identity: StudyFlowLocalDeviceIdentity) {
            synchronized(preferences) {
                preferences.edit()
                    .putString(IdentityKey, StudyFlowPeerPersistenceCodec.encodeIdentity(identity))
                    .apply()
            }
        }
    }

    val trustStore: StudyFlowTrustStore = object : StudyFlowTrustStore {
        override fun get(deviceId: StudyFlowDeviceId): StudyFlowTrustedPeerRecord? = synchronized(preferences) {
            trustRecords().firstOrNull { it.peer.deviceId == deviceId }
        }

        override fun list(): List<StudyFlowTrustedPeerRecord> = synchronized(preferences) { trustRecords() }

        override fun put(record: StudyFlowTrustedPeerRecord) {
            synchronized(preferences) {
                val records = trustRecords()
                    .filterNot { it.peer.deviceId == record.peer.deviceId }
                    .plus(record)
                    .sortedBy { it.peer.deviceId.value }
                writeTrust(records)
            }
        }

        override fun remove(deviceId: StudyFlowDeviceId): Boolean = synchronized(preferences) {
            val current = trustRecords()
            val retained = current.filterNot { it.peer.deviceId == deviceId }
            if (retained.size == current.size) return@synchronized false
            writeTrust(retained)
            true
        }
    }

    val queueStore: StudyFlowOutgoingQueueStore = object : StudyFlowOutgoingQueueStore {
        override fun load(workspaceId: StudyFlowWorkspaceId): List<StudyFlowOutgoingQueueEntry> =
            synchronized(preferences) { queueSnapshot(workspaceId).entries }

        override fun save(workspaceId: StudyFlowWorkspaceId, entries: List<StudyFlowOutgoingQueueEntry>) {
            synchronized(preferences) {
                val current = queueSnapshot(workspaceId)
                preferences.edit()
                    .putString(
                        queueKey(workspaceId),
                        StudyFlowPeerPersistenceCodec.encodeOutgoingQueue(
                            workspaceId,
                            entries,
                            current.peerRetryStates,
                        ),
                    )
                    .apply()
            }
        }

        override fun loadPeerRetryStates(workspaceId: StudyFlowWorkspaceId): List<StudyFlowPeerRetryState> =
            synchronized(preferences) { queueSnapshot(workspaceId).peerRetryStates }

        override fun savePeerRetryStates(workspaceId: StudyFlowWorkspaceId, states: List<StudyFlowPeerRetryState>) {
            synchronized(preferences) {
                val current = queueSnapshot(workspaceId)
                preferences.edit()
                    .putString(
                        queueKey(workspaceId),
                        StudyFlowPeerPersistenceCodec.encodeOutgoingQueue(workspaceId, current.entries, states),
                    )
                    .apply()
            }
        }
    }

    val journalStore: StudyFlowOperationJournalStore = object : StudyFlowOperationJournalStore {
        override fun entries(workspaceId: StudyFlowWorkspaceId): List<StudyFlowJournalEntry> =
            synchronized(preferences) { journalSnapshot(workspaceId).entries }

        override fun append(entry: StudyFlowJournalEntry) {
            synchronized(preferences) {
                val workspaceId = entry.operation.workspaceId
                val snapshot = journalSnapshot(workspaceId)
                writeJournal(workspaceId, snapshot.entries + entry, snapshot.checkpoints)
            }
        }

        override fun checkpoints(workspaceId: StudyFlowWorkspaceId): List<StudyFlowJournalCheckpoint> =
            synchronized(preferences) { journalSnapshot(workspaceId).checkpoints }

        override fun appendCheckpoint(checkpoint: StudyFlowJournalCheckpoint) {
            synchronized(preferences) {
                val snapshot = journalSnapshot(checkpoint.workspaceId)
                writeJournal(
                    checkpoint.workspaceId,
                    snapshot.entries,
                    snapshot.checkpoints + checkpoint,
                )
            }
        }
    }

    val membershipStore: StudyFlowWorkspaceMembershipStore = object : StudyFlowWorkspaceMembershipStore {
        override fun list(): List<StudyFlowWorkspaceMember> = synchronized(preferences) {
            preferences.getString(MembershipKey, null)?.let { encoded ->
                decodeOrQuarantine(
                    MembershipKey,
                    StudyFlowPersistedStateKind.WORKSPACE_MEMBERSHIP,
                    encoded,
                    fallback = { emptyList() },
                ) { StudyFlowPeerPersistenceCodec.decodeWorkspaceMembership(it) }
            }.orEmpty()
        }

        override fun put(member: StudyFlowWorkspaceMember) { synchronized(preferences) {
            val next = list().filterNot {
                it.workspaceId == member.workspaceId && it.deviceId == member.deviceId
            } + member
            preferences.edit()
                .putString(MembershipKey, StudyFlowPeerPersistenceCodec.encodeWorkspaceMembership(next))
                .apply()
        } }

        override fun remove(
            workspaceId: StudyFlowWorkspaceId,
            deviceId: StudyFlowDeviceId
        ): Boolean = synchronized(preferences) {
            val current = list();
            val next = current.filterNot {
                it.workspaceId == workspaceId && it.deviceId == deviceId
            }
            if (next.size == current.size) false else {
                preferences.edit()
                    .putString(MembershipKey, StudyFlowPeerPersistenceCodec.encodeWorkspaceMembership(next))
                    .apply(); true
            }
        }
    }

    val endpointStore: StudyFlowPeerEndpointStore = object : StudyFlowPeerEndpointStore {
        override fun get(deviceId: StudyFlowDeviceId): StudyFlowPeerEndpointRecord? = synchronized(preferences) {
            endpointRecords().firstOrNull { it.deviceId == deviceId }
        }

        override fun list(): List<StudyFlowPeerEndpointRecord> = synchronized(preferences) { endpointRecords() }

        override fun put(record: StudyFlowPeerEndpointRecord) {
            synchronized(preferences) {
                val records = endpointRecords()
                    .filterNot { it.deviceId == record.deviceId }
                    .plus(record)
                    .sortedBy { it.deviceId.value }
                preferences.edit()
                    .putString(EndpointsKey, StudyFlowPeerPersistenceCodec.encodePeerEndpoints(records))
                    .apply()
            }
        }

        override fun remove(deviceId: StudyFlowDeviceId): Boolean = synchronized(preferences) {
            val current = endpointRecords()
            val retained = current.filterNot { it.deviceId == deviceId }
            if (retained.size == current.size) return@synchronized false
            preferences.edit()
                .putString(EndpointsKey, StudyFlowPeerPersistenceCodec.encodePeerEndpoints(retained))
                .apply()
            true
        }
    }

    private fun trustRecords(): List<StudyFlowTrustedPeerRecord> =
        preferences.getString(TrustKey, null)?.let { encoded ->
            decodeOrQuarantine(TrustKey, StudyFlowPersistedStateKind.TRUST, encoded, fallback = { emptyList() }) {
                StudyFlowPeerPersistenceCodec.decodeTrustRecords(it)
            }
        }.orEmpty()

    private fun writeTrust(records: List<StudyFlowTrustedPeerRecord>) {
        preferences.edit()
            .putString(TrustKey, StudyFlowPeerPersistenceCodec.encodeTrustRecords(records))
            .apply()
    }

    private fun endpointRecords(): List<StudyFlowPeerEndpointRecord> =
        preferences.getString(EndpointsKey, null)?.let { encoded ->
            decodeOrQuarantine(
                EndpointsKey, StudyFlowPersistedStateKind.ENDPOINTS, encoded,
                fallback = { emptyList() }
            ) {
                StudyFlowPeerPersistenceCodec.decodePeerEndpoints(it)
            }
        }.orEmpty()

    private fun journalSnapshot(workspaceId: StudyFlowWorkspaceId): StudyFlowJournalSnapshot {
        val key = journalKey(workspaceId)
        return preferences.getString(key, null)?.let { encoded ->
            decodeOrQuarantine(
                key, StudyFlowPersistedStateKind.JOURNAL, encoded,
                fallback = { StudyFlowJournalSnapshot(workspaceId) }
            ) {
                StudyFlowPeerPersistenceCodec.decodeJournal(workspaceId, it)
            }
        } ?: StudyFlowJournalSnapshot(workspaceId)
    }

    private fun queueSnapshot(
        workspaceId: StudyFlowWorkspaceId
    ): com.mikeyphw.studyflow.core.link.StudyFlowOutgoingQueueSnapshot {
        val key = queueKey(workspaceId)
        return preferences.getString(key, null)?.let { encoded ->
            decodeOrQuarantine(
                key,
                StudyFlowPersistedStateKind.OUTGOING_QUEUE,
                encoded,
                fallback = { com.mikeyphw.studyflow.core.link.StudyFlowOutgoingQueueSnapshot(workspaceId) },
            ) { StudyFlowPeerPersistenceCodec.decodeOutgoingQueueSnapshot(workspaceId, it) }
        } ?: com.mikeyphw.studyflow.core.link.StudyFlowOutgoingQueueSnapshot(workspaceId)
    }

    private fun writeJournal(
        workspaceId: StudyFlowWorkspaceId,
        entries: List<StudyFlowJournalEntry>,
        checkpoints: List<StudyFlowJournalCheckpoint>,
    ) {
        preferences.edit()
            .putString(
                journalKey(workspaceId),
                StudyFlowPeerPersistenceCodec.encodeJournal(workspaceId, entries, checkpoints),
            )
            .apply()
    }

    fun corruptionRecords(): List<StudyFlowPersistenceCorruptionRecord> = synchronized(preferences) {
        preferences.getString(CorruptionKey, null)?.let { encoded ->
            runCatching { StudyFlowPeerPersistenceCodec.decodeCorruptionRecords(encoded) }.getOrNull()
        }.orEmpty()
    }

    private fun <T> decodeOrQuarantine(
        storageKey: String,
        kind: StudyFlowPersistedStateKind,
        encoded: String,
        fallback: () -> T,
        decode: (String) -> T,
    ): T = try {
        decode(encoded)
    } catch (error: Exception) {
        val observedAt = System.currentTimeMillis()
        val digest = sha256Hex(encoded)
        val quarantineKey = "quarantine:$observedAt:${digest.take(12)}:$storageKey"
        val record = StudyFlowPersistenceCorruptionRecord(
            stateKind = kind,
            storageKey = storageKey,
            observedAtEpochMillis = observedAt,
            quarantineReference = quarantineKey,
            encodedSha256 = digest,
            reason = (error.message ?: error.javaClass.simpleName).take(512),
        )
        val records = corruptionRecords().plus(record).takeLast(64)
        preferences.edit()
            .putString(quarantineKey, encoded)
            .putString(CorruptionKey, StudyFlowPeerPersistenceCodec.encodeCorruptionRecords(records))
            .remove(storageKey)
            .commit()
        fallback()
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun queueKey(workspaceId: StudyFlowWorkspaceId): String = "queue:${workspaceId.value}"

    private fun journalKey(workspaceId: StudyFlowWorkspaceId): String = "journal:${workspaceId.value}"

    private companion object {
        const val PreferencesName = "studyflow-sflp-peer-v1"
        const val IdentityKey = "identity"
        const val TrustKey = "trust"
        const val EndpointsKey = "endpoints"
        const val MembershipKey = "workspace-membership"
        const val CorruptionKey = "corruption-ledger"
    }
}
