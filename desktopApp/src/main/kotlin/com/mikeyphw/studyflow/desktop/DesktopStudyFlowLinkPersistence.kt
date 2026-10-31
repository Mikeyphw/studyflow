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
import com.mikeyphw.studyflow.core.link.StudyFlowPeerRetryState
import com.mikeyphw.studyflow.core.link.StudyFlowPersistenceCorruptionRecord
import com.mikeyphw.studyflow.core.link.StudyFlowPersistedStateKind
import com.mikeyphw.studyflow.core.link.StudyFlowTrustStore
import com.mikeyphw.studyflow.core.link.StudyFlowTrustedPeerRecord
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceMember
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceMembershipStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest

class DesktopStudyFlowLinkPersistence(
    private val root: Path = defaultRoot(),
) {
    init {
        Files.createDirectories(root)
        Files.createDirectories(root.resolve("queue"))
        Files.createDirectories(root.resolve("journal"))
        Files.createDirectories(root.resolve("quarantine"))
        applyOwnerOnlyPermissions(root, directory = true)
        applyOwnerOnlyPermissions(root.resolve("queue"), directory = true)
        applyOwnerOnlyPermissions(root.resolve("journal"), directory = true)
        applyOwnerOnlyPermissions(root.resolve("quarantine"), directory = true)
    }

    val identityStore: StudyFlowDeviceIdentityStore = object : StudyFlowDeviceIdentityStore {
        override fun load(): StudyFlowLocalDeviceIdentity? = synchronized(this@DesktopStudyFlowLinkPersistence) {
            val path = root.resolve("identity.json")
            readText(path)?.let { encoded ->
                decodeOrQuarantine(path, StudyFlowPersistedStateKind.IDENTITY, encoded, fallback = { null }) {
                    StudyFlowPeerPersistenceCodec.decodeIdentity(it)
                }
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
        ) { queueSnapshot(workspaceId).entries }

        override fun save(workspaceId: StudyFlowWorkspaceId, entries: List<StudyFlowOutgoingQueueEntry>) {
            synchronized(this@DesktopStudyFlowLinkPersistence) {
                val current = queueSnapshot(workspaceId)
                writeText(
                    queuePath(workspaceId),
                    StudyFlowPeerPersistenceCodec.encodeOutgoingQueue(
                        workspaceId,
                        entries,
                        current.peerRetryStates,
                    ),
                )
            }
        }

        override fun loadPeerRetryStates(workspaceId: StudyFlowWorkspaceId): List<StudyFlowPeerRetryState> =
            synchronized(this@DesktopStudyFlowLinkPersistence) { queueSnapshot(workspaceId).peerRetryStates }

        override fun savePeerRetryStates(workspaceId: StudyFlowWorkspaceId, states: List<StudyFlowPeerRetryState>) {
            synchronized(this@DesktopStudyFlowLinkPersistence) {
                val current = queueSnapshot(workspaceId)
                writeText(
                    queuePath(workspaceId),
                    StudyFlowPeerPersistenceCodec.encodeOutgoingQueue(workspaceId, current.entries, states),
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
            val path = root.resolve("workspace-membership.json")
            readText(path)?.let { encoded ->
                decodeOrQuarantine(
                    path, StudyFlowPersistedStateKind.WORKSPACE_MEMBERSHIP, encoded,
                    fallback = { emptyList() }
                ) {
                    StudyFlowPeerPersistenceCodec.decodeWorkspaceMembership(it)
                }
            }.orEmpty()
        }
        override fun put(member: StudyFlowWorkspaceMember) { synchronized(this@DesktopStudyFlowLinkPersistence) {
            val next = list().filterNot {
                it.workspaceId == member.workspaceId && it.deviceId == member.deviceId
            } + member
            writeText(
                root.resolve("workspace-membership.json"),
                StudyFlowPeerPersistenceCodec.encodeWorkspaceMembership(next)
            )
        } }
        override fun remove(
            workspaceId: StudyFlowWorkspaceId,
            deviceId: StudyFlowDeviceId
        ): Boolean = synchronized(this@DesktopStudyFlowLinkPersistence) {
            val current = list();
            val next = current.filterNot {
                it.workspaceId == workspaceId && it.deviceId == deviceId
            }
            if (next.size == current.size) false else {
                writeText(
                    root.resolve("workspace-membership.json"),
                    StudyFlowPeerPersistenceCodec.encodeWorkspaceMembership(next)
                ); true
            }
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

    private fun trustRecords(): List<StudyFlowTrustedPeerRecord> {
        val path = root.resolve("trust.json")
        return readText(path)?.let { encoded ->
            decodeOrQuarantine(path, StudyFlowPersistedStateKind.TRUST, encoded, fallback = { emptyList() }) {
                StudyFlowPeerPersistenceCodec.decodeTrustRecords(it)
            }
        }.orEmpty()
    }

    private fun writeTrust(records: List<StudyFlowTrustedPeerRecord>) {
        writeText(root.resolve("trust.json"), StudyFlowPeerPersistenceCodec.encodeTrustRecords(records))
    }

    private fun endpointRecords(): List<StudyFlowPeerEndpointRecord> {
        val path = root.resolve("endpoints.json")
        return readText(path)?.let { encoded ->
            decodeOrQuarantine(path, StudyFlowPersistedStateKind.ENDPOINTS, encoded, fallback = { emptyList() }) {
                StudyFlowPeerPersistenceCodec.decodePeerEndpoints(it)
            }
        }.orEmpty()
    }

    private fun journalSnapshot(workspaceId: StudyFlowWorkspaceId): StudyFlowJournalSnapshot {
        val path = journalPath(workspaceId)
        return readText(path)?.let { encoded ->
            decodeOrQuarantine(
                path, StudyFlowPersistedStateKind.JOURNAL, encoded,
                fallback = { StudyFlowJournalSnapshot(workspaceId) }
            ) {
                StudyFlowPeerPersistenceCodec.decodeJournal(workspaceId, it)
            }
        } ?: StudyFlowJournalSnapshot(workspaceId)
    }

    private fun queueSnapshot(workspaceId: StudyFlowWorkspaceId) =
        queuePath(workspaceId).let { path ->
            readText(path)?.let { encoded ->
                decodeOrQuarantine(path, StudyFlowPersistedStateKind.OUTGOING_QUEUE, encoded, fallback = {
                    com.mikeyphw.studyflow.core.link.StudyFlowOutgoingQueueSnapshot(workspaceId)
                }) {
                    StudyFlowPeerPersistenceCodec.decodeOutgoingQueueSnapshot(workspaceId, it)
                }
            } ?: com.mikeyphw.studyflow.core.link.StudyFlowOutgoingQueueSnapshot(workspaceId)
        }

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

    fun corruptionRecords(): List<StudyFlowPersistenceCorruptionRecord> = synchronized(this) {
        readText(root.resolve("corruption-ledger.json"))?.let { encoded ->
            runCatching { StudyFlowPeerPersistenceCodec.decodeCorruptionRecords(encoded) }.getOrNull()
        }.orEmpty()
    }

    private fun <T> decodeOrQuarantine(
        path: Path,
        kind: StudyFlowPersistedStateKind,
        encoded: String,
        fallback: () -> T,
        decode: (String) -> T,
    ): T = try {
        decode(encoded)
    } catch (error: Exception) {
        quarantine(path, kind, encoded, error)
        fallback()
    }

    private fun quarantine(
        path: Path,
        kind: StudyFlowPersistedStateKind,
        encoded: String,
        error: Exception,
    ) {
        val observedAt = System.currentTimeMillis()
        val digest = sha256Hex(encoded)
        val safeName = path.fileName.toString().replace(Regex("[^A-Za-z0-9._-]"), "_")
        val quarantine = root.resolve("quarantine").resolve("${observedAt}-${digest.take(12)}-$safeName.corrupt")
        Files.createDirectories(quarantine.parent)
        applyOwnerOnlyPermissions(quarantine.parent, directory = true)
        Files.writeString(quarantine, encoded, StandardCharsets.UTF_8)
        applyOwnerOnlyPermissions(quarantine, directory = false)
        Files.deleteIfExists(path)
        val record = StudyFlowPersistenceCorruptionRecord(
            stateKind = kind,
            storageKey = root.relativize(path).toString(),
            observedAtEpochMillis = observedAt,
            quarantineReference = root.relativize(quarantine).toString(),
            encodedSha256 = digest,
            reason = (error.message ?: error.javaClass.simpleName).take(512),
        )
        val records = corruptionRecords().plus(record).takeLast(64)
        writeText(
            root.resolve("corruption-ledger.json"),
            StudyFlowPeerPersistenceCodec.encodeCorruptionRecords(records)
        )
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

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
        applyOwnerOnlyPermissions(path.parent, directory = true)
        val temp = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temp, value, StandardCharsets.UTF_8)
        applyOwnerOnlyPermissions(temp, directory = false)
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
        applyOwnerOnlyPermissions(path, directory = false)
    }

    private fun applyOwnerOnlyPermissions(path: Path, directory: Boolean) {
        val permissions = if (directory) {
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            )
        } else {
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
        }
        runCatching { Files.setPosixFilePermissions(path, permissions) }
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
