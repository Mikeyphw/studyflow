package com.mikeyphw.studyflow.desktop

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
import com.mikeyphw.studyflow.core.link.StudyFlowTrustStore
import com.mikeyphw.studyflow.core.link.StudyFlowTrustedPeerRecord
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceMember
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceMembershipStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class DesktopStudyFlowLinkPersistence(
    private val root: Path = defaultRoot(),
) {
    init {
        Files.createDirectories(root)
        Files.createDirectories(root.resolve("queue"))
        Files.createDirectories(root.resolve("journal"))
    }

    val identityStore: StudyFlowDeviceIdentityStore = object : StudyFlowDeviceIdentityStore {
        override fun load(): StudyFlowLocalDeviceIdentity? = synchronized(this@DesktopStudyFlowLinkPersistence) {
            readText(root.resolve("identity.json"))?.let { encoded ->
                runCatching { StudyFlowPeerPersistenceCodec.decodeIdentity(encoded) }.getOrNull()
            }
        }

        override fun save(identity: StudyFlowLocalDeviceIdentity) {
            synchronized(this@DesktopStudyFlowLinkPersistence) {
                writeText(root.resolve("identity.json"), StudyFlowPeerPersistenceCodec.encodeIdentity(identity))
            }
        }
    }

    val trustStore: StudyFlowTrustStore = object : StudyFlowTrustStore {
        override fun get(deviceId: StudyFlowDeviceId): StudyFlowTrustedPeerRecord? = synchronized(
            this@DesktopStudyFlowLinkPersistence,
        ) {
            trustRecords().firstOrNull { it.peer.deviceId == deviceId }
        }

        override fun list(): List<StudyFlowTrustedPeerRecord> = synchronized(this@DesktopStudyFlowLinkPersistence) {
            trustRecords()
        }

        override fun put(record: StudyFlowTrustedPeerRecord) {
            synchronized(this@DesktopStudyFlowLinkPersistence) {
                writeTrust(
                    trustRecords()
                        .filterNot { it.peer.deviceId == record.peer.deviceId }
                        .plus(record)
                        .sortedBy { it.peer.deviceId.value },
                )
            }
        }

        override fun remove(deviceId: StudyFlowDeviceId): Boolean = synchronized(this@DesktopStudyFlowLinkPersistence) {
            val current = trustRecords()
            val retained = current.filterNot { it.peer.deviceId == deviceId }
            if (retained.size == current.size) return@synchronized false
            writeTrust(retained)
            true
        }
    }

    val queueStore: StudyFlowOutgoingQueueStore = object : StudyFlowOutgoingQueueStore {
        override fun load(workspaceId: StudyFlowWorkspaceId): List<StudyFlowOutgoingQueueEntry> = synchronized(
            this@DesktopStudyFlowLinkPersistence,
        ) {
            readText(queuePath(workspaceId))?.let { encoded ->
                runCatching { StudyFlowPeerPersistenceCodec.decodeOutgoingQueue(workspaceId, encoded) }.getOrNull()
            }.orEmpty()
        }

        override fun save(workspaceId: StudyFlowWorkspaceId, entries: List<StudyFlowOutgoingQueueEntry>) {
            synchronized(this@DesktopStudyFlowLinkPersistence) {
                writeText(
                    queuePath(workspaceId),
                    StudyFlowPeerPersistenceCodec.encodeOutgoingQueue(workspaceId, entries),
                )
            }
        }
    }

    val journalStore: StudyFlowOperationJournalStore = object : StudyFlowOperationJournalStore {
        override fun entries(workspaceId: StudyFlowWorkspaceId): List<StudyFlowJournalEntry> = synchronized(
            this@DesktopStudyFlowLinkPersistence,
        ) {
            journalSnapshot(workspaceId).entries
        }

        override fun append(entry: StudyFlowJournalEntry) {
            synchronized(this@DesktopStudyFlowLinkPersistence) {
                val workspaceId = entry.operation.workspaceId
                val snapshot = journalSnapshot(workspaceId)
                writeJournal(workspaceId, snapshot.entries + entry, snapshot.checkpoints)
            }
        }

        override fun checkpoints(workspaceId: StudyFlowWorkspaceId): List<StudyFlowJournalCheckpoint> = synchronized(
            this@DesktopStudyFlowLinkPersistence,
        ) {
            journalSnapshot(workspaceId).checkpoints
        }

        override fun appendCheckpoint(checkpoint: StudyFlowJournalCheckpoint) {
            synchronized(this@DesktopStudyFlowLinkPersistence) {
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
        override fun list(): List<StudyFlowWorkspaceMember> = synchronized(this@DesktopStudyFlowLinkPersistence) {
            readText(root.resolve("workspace-membership.json"))?.let { encoded ->
                runCatching { StudyFlowPeerPersistenceCodec.decodeWorkspaceMembership(encoded) }.getOrNull()
            }.orEmpty()
        }
        override fun put(member: StudyFlowWorkspaceMember) { synchronized(this@DesktopStudyFlowLinkPersistence) {
            val next = list().filterNot { it.workspaceId == member.workspaceId && it.deviceId == member.deviceId } + member
            writeText(root.resolve("workspace-membership.json"), StudyFlowPeerPersistenceCodec.encodeWorkspaceMembership(next))
        } }
        override fun remove(workspaceId: StudyFlowWorkspaceId, deviceId: StudyFlowDeviceId): Boolean = synchronized(this@DesktopStudyFlowLinkPersistence) {
            val current = list(); val next = current.filterNot { it.workspaceId == workspaceId && it.deviceId == deviceId }
            if (next.size == current.size) false else { writeText(root.resolve("workspace-membership.json"), StudyFlowPeerPersistenceCodec.encodeWorkspaceMembership(next)); true }
        }
    }

    val endpointStore: StudyFlowPeerEndpointStore = object : StudyFlowPeerEndpointStore {
        override fun get(deviceId: StudyFlowDeviceId): StudyFlowPeerEndpointRecord? = synchronized(
            this@DesktopStudyFlowLinkPersistence,
        ) {
            endpointRecords().firstOrNull { it.deviceId == deviceId }
        }

        override fun list(): List<StudyFlowPeerEndpointRecord> = synchronized(this@DesktopStudyFlowLinkPersistence) {
            endpointRecords()
        }

        override fun put(record: StudyFlowPeerEndpointRecord) {
            synchronized(this@DesktopStudyFlowLinkPersistence) {
                writeText(
                    root.resolve("endpoints.json"),
                    StudyFlowPeerPersistenceCodec.encodePeerEndpoints(
                        endpointRecords()
                            .filterNot { it.deviceId == record.deviceId }
                            .plus(record)
                            .sortedBy { it.deviceId.value },
                    ),
                )
            }
        }

        override fun remove(deviceId: StudyFlowDeviceId): Boolean = synchronized(this@DesktopStudyFlowLinkPersistence) {
            val current = endpointRecords()
            val retained = current.filterNot { it.deviceId == deviceId }
            if (retained.size == current.size) return@synchronized false
            writeText(root.resolve("endpoints.json"), StudyFlowPeerPersistenceCodec.encodePeerEndpoints(retained))
            true
        }
    }

    private fun trustRecords(): List<StudyFlowTrustedPeerRecord> =
        readText(root.resolve("trust.json"))?.let { encoded ->
            runCatching { StudyFlowPeerPersistenceCodec.decodeTrustRecords(encoded) }.getOrNull()
        }.orEmpty()

    private fun writeTrust(records: List<StudyFlowTrustedPeerRecord>) {
        writeText(root.resolve("trust.json"), StudyFlowPeerPersistenceCodec.encodeTrustRecords(records))
    }

    private fun endpointRecords(): List<StudyFlowPeerEndpointRecord> =
        readText(root.resolve("endpoints.json"))?.let { encoded ->
            runCatching { StudyFlowPeerPersistenceCodec.decodePeerEndpoints(encoded) }.getOrNull()
        }.orEmpty()

    private fun journalSnapshot(workspaceId: StudyFlowWorkspaceId): StudyFlowJournalSnapshot =
        readText(journalPath(workspaceId))?.let { encoded ->
            runCatching { StudyFlowPeerPersistenceCodec.decodeJournal(workspaceId, encoded) }.getOrNull()
        } ?: StudyFlowJournalSnapshot(workspaceId)

    private fun writeJournal(
        workspaceId: StudyFlowWorkspaceId,
        entries: List<StudyFlowJournalEntry>,
        checkpoints: List<StudyFlowJournalCheckpoint>,
    ) {
        writeText(
            journalPath(workspaceId),
            StudyFlowPeerPersistenceCodec.encodeJournal(workspaceId, entries, checkpoints),
        )
    }

    private fun queuePath(workspaceId: StudyFlowWorkspaceId): Path =
        root.resolve("queue").resolve("${workspaceKey(workspaceId)}.json")

    private fun journalPath(workspaceId: StudyFlowWorkspaceId): Path =
        root.resolve("journal").resolve("${workspaceKey(workspaceId)}.json")

    private fun readText(path: Path): String? = if (Files.isRegularFile(path)) {
        Files.readString(path, StandardCharsets.UTF_8)
    } else {
        null
    }

    private fun writeText(path: Path, value: String) {
        Files.createDirectories(path.parent)
        val temp = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temp, value, StandardCharsets.UTF_8)
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun workspaceKey(workspaceId: StudyFlowWorkspaceId): String =
        MessageDigest.getInstance("SHA-256")
            .digest(workspaceId.value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        fun defaultRoot(): Path = Path.of(
            System.getProperty("user.home"),
            ".studyflow",
            "link",
            "v1",
        )
    }
}
