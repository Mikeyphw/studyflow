package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowIntegrity
import com.mikeyphw.studyflow.core.domain.StudyCloudSyncRepository
import com.mikeyphw.studyflow.core.domain.StudyManualFolderCheckpointDetails
import com.mikeyphw.studyflow.core.domain.StudyManualFolderScan
import com.mikeyphw.studyflow.core.model.LocalFolderSyncProvider
import com.mikeyphw.studyflow.core.model.LocalOnlyProvider
import com.mikeyphw.studyflow.core.model.NoSyncProvider
import com.mikeyphw.studyflow.core.model.StudyFlowSyncProviderIds
import com.mikeyphw.studyflow.core.model.StudyFlowSyncSettings
import com.mikeyphw.studyflow.core.model.StudySyncBackgroundMode
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncLedgerSnapshot
import com.mikeyphw.studyflow.core.model.StudySyncLedgerFactory
import com.mikeyphw.studyflow.core.model.StudySyncLedgerEventType
import com.mikeyphw.studyflow.core.model.StudySyncLedgerEvent
import com.mikeyphw.studyflow.core.model.StudySyncDeviceIdentity
import com.mikeyphw.studyflow.core.model.StudySyncCheckpointRecord
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncState
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncBundleArchive
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncBundleCodec
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncBundleManifest
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncCheckpoint
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

private val Context.studySyncDataStore by preferencesDataStore(name = "study_sync_foundation")

/**
 * Local-first optional sync state. The durable source of freshness is the Room graph revision, not a
 * synthetic plan count. Every user-data mutation advances that revision through database triggers.
 */
internal class LocalStudyCloudSyncRepository(
    context: Context,
    private val database: StudyFlowDatabase,
    private val applicationVersion: String,
    private val clock: () -> Long,
) : StudyCloudSyncRepository {
    private val appContext = context.applicationContext
    private val dataStore = appContext.studySyncDataStore
    private val metadataDao = database.databaseMetadataDao()
    private val syncBundleCodec = StudyFlowSyncBundleCodec()
    private val manualFolderStorage = SafManualFolderSyncStorage(appContext)
    private val attachmentCollector = ManualSyncAttachmentCollector(appContext, database)

    override val syncState: Flow<StudySyncState> = combine(
        dataStore.data,
        metadataDao.observeValue(StudyFlowIntegrity.GraphRevisionMetadataKey),
    ) { preferences, revisionValue ->
        decodeState(preferences, revisionValue.toRevision())
    }

    override suspend fun inspectLocalChanges(): StudySyncChangeSummary {
        val plans = database.studyPlanDao().snapshotAll()
        val activeCount = plans.count { entity ->
            enumValueOf<StudyPlanStatus>(entity.status) in setOf(StudyPlanStatus.ACTIVE, StudyPlanStatus.PAUSED)
        }
        val latestUpdated = plans.maxOfOrNull { entity -> entity.updatedAtEpochMillis }
        val revision = metadataDao.findValue(StudyFlowIntegrity.GraphRevisionMetadataKey).toRevision()
        val preferences = dataStore.data.first()
        val preparedRevision = preferences[ChangeTokenKey].revisionFromToken()
        return StudySyncChangeSummary(
            planCount = plans.size,
            activePlanCount = activeCount,
            latestUpdatedAtEpochMillis = latestUpdated,
            localChangeToken = revisionToken(revision),
            databaseRevision = revision,
            changesSinceCheckpoint = revision.deltaSince(preparedRevision),
        )
    }

    override suspend fun inspectSyncLedger(): StudySyncLedgerSnapshot = decodeLedger(dataStore.data.first())

    override suspend fun prepareLocalFoundation(deviceName: String): StudySyncState {
        val revision = metadataDao.findValue(StudyFlowIntegrity.GraphRevisionMetadataKey).toRevision()
        val previousRevision = dataStore.data.first()[ChangeTokenKey].revisionFromToken()
        val changeCount = revision.deltaSince(previousRevision)
        val now = clock()
        dataStore.edit { preferences ->
            val deviceId = StudySyncDeviceIdentity.resolveInstallationDeviceId(preferences[DeviceIdKey])
            val token = revisionToken(revision)
            val checkpointId = StudySyncLedgerFactory.checkpointId(deviceId, revision, now)
            preferences[EnabledKey] = true
            preferences[StatusKey] = StudySyncConnectionStatus.LOCAL_READY.name
            preferences[ConflictPolicyKey] = preferences[ConflictPolicyKey]
                ?: StudySyncConflictPolicy.MANUAL_REVIEW.name
            preferences[DeviceIdKey] = deviceId
            preferences[DeviceNameKey] = deviceName
            preferences[DeviceFirstSeenAtKey] = preferences[DeviceFirstSeenAtKey] ?: now
            preferences[DeviceLastSeenAtKey] = now
            preferences[PreparedAtKey] = now
            preferences[ChangeTokenKey] = token
            preferences[PendingLocalChangeKey] = 0L
            recordCheckpointLedger(
                preferences = preferences,
                checkpointId = checkpointId,
                deviceId = deviceId,
                deviceName = deviceName,
                now = now,
                revision = revision,
                changeToken = token,
                changeCount = changeCount,
            )
            appendLedgerEvent(
                preferences = preferences,
                type = StudySyncLedgerEventType.LOCAL_CHECKPOINT_PREPARED,
                deviceId = deviceId,
                checkpointId = checkpointId,
                now = now,
                message = "Prepared local checkpoint foundation.",
            )
            preferences.remove(BlockedReasonKey)
        }
        return syncState.first()
    }

    override suspend fun setSyncEnabled(enabled: Boolean): StudySyncState {
        val revision = metadataDao.findValue(StudyFlowIntegrity.GraphRevisionMetadataKey).toRevision()
        dataStore.edit { preferences ->
            preferences[EnabledKey] = enabled
            if (!enabled) {
                preferences[AutomaticSyncEnabledKey] = false
                preferences[BackgroundSyncModeKey] = StudySyncBackgroundMode.OFF.name
            }
            preferences[StatusKey] = if (!enabled) {
                StudySyncConnectionStatus.DISABLED.name
            } else if (preferences[ChangeTokenKey].revisionFromToken() == revision) {
                StudySyncConnectionStatus.LOCAL_READY.name
            } else {
                StudySyncConnectionStatus.CHECKPOINT_NEEDED.name
            }
        }
        return syncState.first()
    }

    override suspend fun setConflictPolicy(policy: StudySyncConflictPolicy): StudySyncState {
        dataStore.edit { preferences -> preferences[ConflictPolicyKey] = policy.name }
        return syncState.first()
    }

    override suspend fun setBackgroundSyncMode(mode: StudySyncBackgroundMode): StudySyncState {
        dataStore.edit { preferences ->
            preferences[BackgroundSyncModeKey] = mode.name
            preferences[AutomaticSyncEnabledKey] = mode in setOf(
                StudySyncBackgroundMode.UPLOAD_AFTER_CHANGES,
                StudySyncBackgroundMode.CHECK_DAILY,
                StudySyncBackgroundMode.FULL_AUTOMATIC,
            )
            if (mode != StudySyncBackgroundMode.OFF) {
                preferences[EnabledKey] = true
            }
        }
        return syncState.first()
    }

    override suspend fun configureLocalFolderProvider(folderUri: String): StudySyncState = withContext(Dispatchers.IO) {
        val documents = manualFolderStorage.listCheckpointDocuments(folderUri)
        val revision = metadataDao.findValue(StudyFlowIntegrity.GraphRevisionMetadataKey).toRevision()
        val now = clock()
        val previousLocation = dataStore.data.first()[ProviderLocationKey]
        dataStore.edit { preferences ->
            val deviceId = StudySyncDeviceIdentity.resolveInstallationDeviceId(preferences[DeviceIdKey])
            if (previousLocation != null && previousLocation != folderUri) {
                preferences.remove(LastManualUploadAtKey)
                preferences.remove(LastCheckpointRemoteLocationKey)
            }
            preferences[DeviceIdKey] = deviceId
            preferences[DeviceFirstSeenAtKey] = preferences[DeviceFirstSeenAtKey] ?: now
            preferences[DeviceLastSeenAtKey] = now
            preferences[EnabledKey] = true
            preferences[ProviderIdKey] = StudyFlowSyncProviderIds.LOCAL_FOLDER
            preferences[ProviderLocationKey] = folderUri
            preferences[AutomaticSyncEnabledKey] = false
            preferences[BackgroundSyncModeKey] = StudySyncBackgroundMode.MANUAL_ONLY.name
            preferences[BatteryNotLowKey] = true
            preferences[EncryptRemoteBackupsKey] = true
            preferences[LastRemoteCheckAtKey] = now
            preferences[RemoteCheckpointCountKey] = documents.size.toLong()
            preferences[StatusKey] = if (preferences[ChangeTokenKey].revisionFromToken() == revision) {
                StudySyncConnectionStatus.LOCAL_READY.name
            } else {
                StudySyncConnectionStatus.CHECKPOINT_NEEDED.name
            }
            appendLedgerEvent(
                preferences = preferences,
                type = StudySyncLedgerEventType.DEVICE_REGISTERED,
                deviceId = deviceId,
                checkpointId = null,
                now = now,
                message = "Configured optional folder sync provider and verified SAF access.",
            )
            preferences.remove(BlockedReasonKey)
        }
        if (previousLocation != null && previousLocation != folderUri) {
            manualFolderStorage.releasePersistedPermission(previousLocation)
        }
        syncState.first()
    }

    override suspend fun uploadManualFolderCheckpoint(
        password: CharArray,
    ): StudyManualFolderCheckpointDetails = withContext(Dispatchers.IO) {
        val before = dataStore.data.first()
        val folderUri = requireConfiguredManualFolder(before, "uploading a checkpoint")
        val previousRevision = before[ChangeTokenKey].revisionFromToken()
        val (revision, databaseSnapshot, attachments) = captureStableManualCheckpoint()
        val now = clock()
        val deviceName = before[DeviceNameKey] ?: "This device"
        val deviceId = StudySyncDeviceIdentity.resolveInstallationDeviceId(before[DeviceIdKey])
        val changeCount = revision.deltaSince(previousRevision)
        val checkpointId = StudySyncLedgerFactory.checkpointId(deviceId, revision, now)
        val databaseHash = sha256(databaseSnapshot)
        val checkpoint = StudyFlowSyncCheckpoint(
            id = checkpointId,
            deviceId = deviceId,
            deviceName = deviceName,
            createdAtEpochMillis = now,
            databaseHashSha256 = databaseHash,
            changeCount = changeCount,
            graphRevision = revision,
        )
        val manifest = StudyFlowSyncBundleManifest(
            applicationVersion = applicationVersion,
            checkpoint = checkpoint,
            encryptedForRemote = true,
            databaseByteSize = databaseSnapshot.size.toLong(),
            databaseChecksumSha256 = databaseHash,
            attachments = attachments.map { source -> source.manifest },
        )
        val archive = StudyFlowSyncBundleArchive(
            manifest = manifest,
            databaseSnapshot = databaseSnapshot,
            attachmentStreams = attachments.associate { source ->
                source.manifest.entryName to source.openStream
            },
        )
        val requestedFileName = syncBundleCodec.defaultFileName(now)
        val committed = manualFolderStorage.writeCheckpoint(folderUri, requestedFileName) { output ->
            syncBundleCodec.writeEncryptedArchive(archive, output, password)
        }
        val remoteDocuments = runCatching {
            manualFolderStorage.listCheckpointDocuments(folderUri)
        }.getOrNull()
        val currentRevision = metadataDao.findValue(StudyFlowIntegrity.GraphRevisionMetadataKey).toRevision()
        val token = revisionToken(revision)
        dataStore.edit { preferences ->
            preferences[EnabledKey] = true
            preferences[StatusKey] = if (currentRevision == revision) {
                StudySyncConnectionStatus.LOCAL_READY.name
            } else {
                StudySyncConnectionStatus.CHECKPOINT_NEEDED.name
            }
            preferences[DeviceIdKey] = deviceId
            preferences[DeviceNameKey] = deviceName
            preferences[DeviceFirstSeenAtKey] = preferences[DeviceFirstSeenAtKey] ?: now
            preferences[DeviceLastSeenAtKey] = now
            preferences[PreparedAtKey] = now
            preferences[ChangeTokenKey] = token
            preferences[PendingLocalChangeKey] = 0L
            preferences[LastManualUploadAtKey] = now
            remoteDocuments?.let { documents ->
                preferences[RemoteCheckpointCountKey] = documents.size.toLong()
            }
            recordCheckpointLedger(
                preferences = preferences,
                checkpointId = checkpointId,
                deviceId = deviceId,
                deviceName = deviceName,
                now = now,
                revision = revision,
                changeToken = token,
                changeCount = changeCount,
            )
            preferences[LastCheckpointUploadedAtKey] = now
            preferences[LastCheckpointRemoteLocationKey] = committed.uri
            persistCurrentCheckpointHistory(preferences)
            appendLedgerEvent(
                preferences = preferences,
                type = StudySyncLedgerEventType.MANUAL_UPLOAD_RECORDED,
                deviceId = deviceId,
                checkpointId = checkpointId,
                now = now,
                message = "Uploaded encrypted checkpoint to the selected SAF folder.",
            )
            preferences.remove(BlockedReasonKey)
        }
        manifest.toManualFolderDetails(committed.fileName)
    }

    override suspend fun scanManualFolderCheckpoints(): StudyManualFolderScan = withContext(Dispatchers.IO) {
        val before = dataStore.data.first()
        val folderUri = requireConfiguredManualFolder(before, "checking remote checkpoints")
        val documents = manualFolderStorage.listCheckpointDocuments(folderUri)
        val now = clock()
        val deviceId = StudySyncDeviceIdentity.resolveInstallationDeviceId(before[DeviceIdKey])
        dataStore.edit { preferences ->
            preferences[DeviceIdKey] = deviceId
            preferences[DeviceFirstSeenAtKey] = preferences[DeviceFirstSeenAtKey] ?: now
            preferences[DeviceLastSeenAtKey] = now
            preferences[LastRemoteCheckAtKey] = now
            preferences[RemoteCheckpointCountKey] = documents.size.toLong()
            appendLedgerEvent(
                preferences = preferences,
                type = StudySyncLedgerEventType.REMOTE_CHECK_COMPLETED,
                deviceId = deviceId,
                checkpointId = preferences[LastCheckpointIdKey],
                now = now,
                message = "Enumerated encrypted checkpoint files in the selected SAF folder.",
            )
            preferences.remove(BlockedReasonKey)
        }
        documents.toManualFolderScan()
    }

    override suspend fun previewLatestManualFolderRestore(
        password: CharArray,
    ): StudyManualFolderCheckpointDetails = withContext(Dispatchers.IO) {
        val before = dataStore.data.first()
        val folderUri = requireConfiguredManualFolder(before, "previewing a restore")
        val documents = manualFolderStorage.listCheckpointDocuments(folderUri)
        val latest = documents.firstOrNull()
            ?: throw IllegalStateException("No encrypted checkpoint exists in the selected sync folder.")
        require(latest.byteSize == 0L || latest.byteSize <= MaximumManualEncryptedBundleBytes) {
            "The selected encrypted sync checkpoint exceeds the supported size limit."
        }
        val plaintext = File.createTempFile("studyflow-sync-preview-", ".zip", appContext.cacheDir)
        try {
            manualFolderStorage.openCheckpoint(latest.uri).use { input ->
                plaintext.outputStream().buffered().use { output ->
                    syncBundleCodec.decryptEncryptedArchive(
                        input = input,
                        output = output,
                        password = password,
                        maximumEncryptedBytes = MaximumManualEncryptedBundleBytes,
                    )
                }
            }
            val manifest = plaintext.inputStream().buffered().use(syncBundleCodec::readManifestStreaming)
            require(manifest.encryptedForRemote) {
                "Manual folder restore preview only accepts encrypted remote-ready checkpoints."
            }
            val now = clock()
            val deviceId = StudySyncDeviceIdentity.resolveInstallationDeviceId(before[DeviceIdKey])
            dataStore.edit { preferences ->
                preferences[DeviceIdKey] = deviceId
                preferences[DeviceFirstSeenAtKey] = preferences[DeviceFirstSeenAtKey] ?: now
                preferences[DeviceLastSeenAtKey] = now
                preferences[LastRemoteCheckAtKey] = now
                preferences[RemoteCheckpointCountKey] = documents.size.toLong()
                if (preferences[LastCheckpointIdKey] == manifest.checkpointId) {
                    preferences[LastRestorePreviewedAtKey] = now
                    persistCurrentCheckpointHistory(preferences)
                } else {
                    persistCheckpointHistory(
                        preferences,
                        StudySyncCheckpointRecord(
                            id = manifest.checkpointId,
                            deviceId = manifest.checkpoint.deviceId,
                            deviceName = manifest.checkpoint.deviceName,
                            createdAtEpochMillis = manifest.checkpoint.createdAtEpochMillis,
                            databaseRevision = manifest.checkpoint.graphRevision,
                            localChangeToken = revisionToken(manifest.checkpoint.graphRevision),
                            changeCount = manifest.checkpoint.changeCount,
                            remoteLocation = latest.uri,
                            restorePreviewedAtEpochMillis = now,
                        ),
                    )
                }
                appendLedgerEvent(
                    preferences = preferences,
                    type = StudySyncLedgerEventType.RESTORE_PREVIEWED,
                    deviceId = deviceId,
                    checkpointId = manifest.checkpointId,
                    now = now,
                    message = "Decrypted, authenticated, and streamed the latest SAF checkpoint " +
                        "metadata for restore preview.",
                )
                preferences.remove(BlockedReasonKey)
            }
            manifest.toManualFolderDetails(latest.fileName)
        } finally {
            require(!plaintext.exists() || plaintext.delete()) {
                "Could not delete decrypted manual-sync preview staging bytes."
            }
        }
    }

    override suspend fun applyLatestManualFolderRestore(
        password: CharArray,
    ): StudyManualFolderCheckpointDetails = withContext(Dispatchers.IO) {
        val before = dataStore.data.first()
        val folderUri = requireConfiguredManualFolder(before, "applying a restore")
        val documents = manualFolderStorage.listCheckpointDocuments(folderUri)
        val latest = documents.firstOrNull()
            ?: throw IllegalStateException("No encrypted checkpoint exists in the selected sync folder.")
        require(latest.byteSize == 0L || latest.byteSize <= MaximumManualEncryptedBundleBytes) {
            "The selected encrypted sync checkpoint exceeds the supported size limit."
        }
        val restoreRoot = File(appContext.cacheDir, "manual-sync-restore-${UUID.randomUUID()}")
        require(restoreRoot.mkdirs()) { "Could not create private manual-sync restore staging." }
        val plaintext = File(restoreRoot, "authenticated.zip")
        val archiveStage = File(restoreRoot, "archive")
        val previousSnapshot = File(restoreRoot, "previous-database.snapshot")
        try {
            manualFolderStorage.openCheckpoint(latest.uri).use { input ->
                plaintext.outputStream().buffered().use { output ->
                    syncBundleCodec.decryptEncryptedArchive(
                        input = input,
                        output = output,
                        password = password,
                        maximumEncryptedBytes = MaximumManualEncryptedBundleBytes,
                    )
                }
            }
            val staged = plaintext.inputStream().buffered().use { input ->
                syncBundleCodec.stageArchive(input, archiveStage)
            }
            val manifest = staged.manifest
            require(manifest.encryptedForRemote) {
                "Manual folder restore only accepts encrypted remote-ready checkpoints."
            }
            StudyFlowDatabaseSnapshotRestorer.inspect(staged.databaseSnapshotFile)
            val prepared = ManualSyncRestoreStager(appContext).prepare(staged)
            previousSnapshot.writeBytes(database.withTransaction { StudyFlowDatabaseSnapshotWriter.capture(database) })

            PhysicalStorageMutationCoordinator.mutex.withLock {
                prepared.commitFiles()
                try {
                    StudyFlowDatabaseSnapshotRestorer.restore(
                        database = database,
                        snapshotFile = staged.databaseSnapshotFile,
                        annexBindings = prepared.bindings,
                    )
                    val restoredRevision = metadataDao
                        .findValue(StudyFlowIntegrity.GraphRevisionMetadataKey)
                        .toRevision()
                    require(restoredRevision == manifest.checkpoint.graphRevision) {
                        "Restored database revision does not match the selected sync checkpoint."
                    }
                    prepared.markCommitted()
                } catch (error: Throwable) {
                    val rollbackFailure = runCatching { prepared.rollbackFiles() }.exceptionOrNull()
                    val databaseRollbackFailure = runCatching {
                        StudyFlowDatabaseSnapshotRestorer.restore(database, previousSnapshot, emptyList())
                    }.exceptionOrNull()
                    if (rollbackFailure != null) error.addSuppressed(rollbackFailure)
                    if (databaseRollbackFailure != null) error.addSuppressed(databaseRollbackFailure)
                    throw error
                }
            }

            val restoredRevision = metadataDao.findValue(StudyFlowIntegrity.GraphRevisionMetadataKey).toRevision()
            val now = clock()
            val localDeviceId = StudySyncDeviceIdentity.resolveInstallationDeviceId(before[DeviceIdKey])
            val localDeviceName = before[DeviceNameKey] ?: "This device"
            dataStore.edit { preferences ->
                preferences[EnabledKey] = true
                preferences[StatusKey] = StudySyncConnectionStatus.LOCAL_READY.name
                preferences[DeviceIdKey] = localDeviceId
                preferences[DeviceNameKey] = localDeviceName
                preferences[DeviceFirstSeenAtKey] = preferences[DeviceFirstSeenAtKey] ?: now
                preferences[DeviceLastSeenAtKey] = now
                preferences[PreparedAtKey] = now
                preferences[ChangeTokenKey] = revisionToken(restoredRevision)
                preferences[PendingLocalChangeKey] = 0L
                preferences[LastRemoteCheckAtKey] = now
                preferences[RemoteCheckpointCountKey] = documents.size.toLong()
                persistCheckpointHistory(
                    preferences,
                    StudySyncCheckpointRecord(
                        id = manifest.checkpointId,
                        deviceId = manifest.checkpoint.deviceId,
                        deviceName = manifest.checkpoint.deviceName,
                        createdAtEpochMillis = manifest.checkpoint.createdAtEpochMillis,
                        databaseRevision = manifest.checkpoint.graphRevision,
                        localChangeToken = revisionToken(manifest.checkpoint.graphRevision),
                        changeCount = manifest.checkpoint.changeCount,
                        remoteLocation = latest.uri,
                    ),
                )
                appendLedgerEvent(
                    preferences = preferences,
                    type = StudySyncLedgerEventType.RESTORE_APPLIED,
                    deviceId = localDeviceId,
                    checkpointId = manifest.checkpointId,
                    now = now,
                    message = "Applied the authenticated SAF checkpoint and verified restored database integrity.",
                )
                preferences.remove(BlockedReasonKey)
            }
            manifest.toManualFolderDetails(latest.fileName)
        } finally {
            require(!restoreRoot.exists() || restoreRoot.deleteRecursively()) {
                "Could not delete private manual-sync restore staging bytes."
            }
        }
    }

    override suspend fun clearLocalFoundation(): StudySyncState {
        val before = dataStore.data.first()
        val previousLocation = before[ProviderLocationKey]
        val existingDeviceId = before[DeviceIdKey]
        val existingDeviceFirstSeenAt = before[DeviceFirstSeenAtKey]
        val checkpointHistory = persistedCheckpointHistory(before)
            .takeIf { history -> history.isNotEmpty() }
            ?.let(StudySyncLedgerHistoryCodec::encodeCheckpoints)
        val eventHistory = persistedEventHistory(before)
            .takeIf { history -> history.isNotEmpty() }
            ?.let(StudySyncLedgerHistoryCodec::encodeEvents)
        val eventCount = before[SyncLedgerEventCountKey]
        dataStore.edit { preferences ->
            preferences.clear()
            existingDeviceId?.let { deviceId -> preferences[DeviceIdKey] = deviceId }
            existingDeviceFirstSeenAt?.let { firstSeen -> preferences[DeviceFirstSeenAtKey] = firstSeen }
            checkpointHistory?.let { history -> preferences[SyncLedgerCheckpointHistoryKey] = history }
            eventHistory?.let { history -> preferences[SyncLedgerEventHistoryKey] = history }
            eventCount?.let { count -> preferences[SyncLedgerEventCountKey] = count }
        }
        previousLocation?.let(manualFolderStorage::releasePersistedPermission)
        return syncState.first()
    }


    private suspend fun captureStableManualCheckpoint(): Triple<Long, ByteArray, List<ManualSyncAttachmentSource>> {
        repeat(MaximumStableCaptureAttempts) {
            val (revision, snapshot) = database.withTransaction {
                metadataDao.findValue(StudyFlowIntegrity.GraphRevisionMetadataKey).toRevision() to
                    StudyFlowDatabaseSnapshotWriter.capture(database)
            }
            val attachments = attachmentCollector.collect()
            val afterCollection = metadataDao.findValue(StudyFlowIntegrity.GraphRevisionMetadataKey).toRevision()
            if (afterCollection == revision) return Triple(revision, snapshot, attachments)
        }
        throw IllegalStateException(
            "StudyFlow data kept changing while preparing the manual checkpoint; retry after current edits finish.",
        )
    }

    private fun requireConfiguredManualFolder(
        preferences: androidx.datastore.preferences.core.Preferences,
        operation: String,
    ): String {
        val location = preferences[ProviderLocationKey]
        check(preferences[ProviderIdKey] == StudyFlowSyncProviderIds.LOCAL_FOLDER && !location.isNullOrBlank()) {
            "Choose a sync folder before $operation."
        }
        return location
    }

    private fun List<ManualFolderSyncDocument>.toManualFolderScan(): StudyManualFolderScan {
        val latest = firstOrNull()
        return StudyManualFolderScan(
            checkpointCount = size,
            latestFileName = latest?.fileName,
            latestModifiedAtEpochMillis = latest?.lastModifiedAtEpochMillis,
        )
    }

    private fun StudyFlowSyncBundleManifest.toManualFolderDetails(
        fileName: String,
    ): StudyManualFolderCheckpointDetails = StudyManualFolderCheckpointDetails(
        fileName = fileName,
        checkpointId = checkpoint.id,
        deviceId = checkpoint.deviceId,
        deviceName = checkpoint.deviceName,
        createdAtEpochMillis = checkpoint.createdAtEpochMillis,
        graphRevision = checkpoint.graphRevision,
        changeCount = checkpoint.changeCount,
        databaseByteSize = databaseByteSize,
        attachmentCount = attachments.size,
        applicationVersion = applicationVersion,
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }

    private fun decodeState(
        preferences: androidx.datastore.preferences.core.Preferences,
        currentRevision: Long,
    ): StudySyncState {
        val enabled = preferences[EnabledKey] ?: false
        val settings = decodeSettings(preferences, enabled)
        val preparedRevision = preferences[ChangeTokenKey].revisionFromToken()
        val persistedStatus = decodeStatus(preferences[StatusKey])
        val status = when {
            !enabled -> StudySyncConnectionStatus.DISABLED
            persistedStatus == StudySyncConnectionStatus.CHECKPOINT_BLOCKED -> persistedStatus
            preparedRevision == currentRevision -> StudySyncConnectionStatus.LOCAL_READY
            else -> StudySyncConnectionStatus.CHECKPOINT_NEEDED
        }
        return StudySyncState(
            enabled = enabled,
            provider = providerDescriptor(settings, enabled),
            connectionStatus = status,
            conflictPolicy = decodeConflictPolicy(preferences[ConflictPolicyKey]),
            deviceId = preferences[DeviceIdKey] ?: StudySyncDeviceIdentity.DefaultDeviceId,
            deviceName = preferences[DeviceNameKey] ?: "This device",
            lastPreparedAtEpochMillis = preferences[PreparedAtKey],
            lastLocalChangeToken = preferences[ChangeTokenKey],
            pendingLocalChangeCount = currentRevision.deltaSince(preparedRevision),
            blockedReason = preferences[BlockedReasonKey],
            settings = settings,
        )
    }

    private fun decodeSettings(
        preferences: androidx.datastore.preferences.core.Preferences,
        enabled: Boolean,
    ): StudyFlowSyncSettings = StudyFlowSyncSettings(
        syncEnabled = enabled,
        providerId = preferences[ProviderIdKey]?.takeUnless { it == StudyFlowSyncProviderIds.NONE },
        automaticSyncEnabled = enabled && (preferences[AutomaticSyncEnabledKey] ?: false),
        backgroundSyncMode = decodeBackgroundSyncMode(preferences[BackgroundSyncModeKey]),
        wifiOnly = preferences[WifiOnlyKey] ?: true,
        batteryNotLow = preferences[BatteryNotLowKey] ?: true,
        chargingOnly = preferences[ChargingOnlyKey] ?: false,
        encryptRemoteBackups = preferences[EncryptRemoteBackupsKey] ?: true,
        providerLocation = preferences[ProviderLocationKey],
        lastManualUploadAtEpochMillis = preferences[LastManualUploadAtKey],
        lastRemoteCheckAtEpochMillis = preferences[LastRemoteCheckAtKey],
        remoteCheckpointCount = preferences[RemoteCheckpointCountKey]
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()
            ?: 0,
    )

    private fun providerDescriptor(
        settings: StudyFlowSyncSettings,
        enabled: Boolean,
    ): com.mikeyphw.studyflow.core.model.StudySyncProviderDescriptor {
        val providerId = settings.providerId
        return when (providerId) {
            null -> if (enabled) LocalOnlyProvider else NoSyncProvider
            StudyFlowSyncProviderIds.LOCAL_FOLDER -> LocalFolderSyncProvider
            else -> com.mikeyphw.studyflow.core.model.StudySyncProviderDescriptor(
                kind = com.mikeyphw.studyflow.core.model.StudySyncProviderKind.REMOTE_PROVIDER,
                displayName = providerId,
                configured = true,
                id = providerId,
                requiresAccount = true,
            )
        }
    }

    private fun decodeStatus(value: String?): StudySyncConnectionStatus = when (value) {
        StudySyncConnectionStatus.LOCAL_READY.name -> StudySyncConnectionStatus.LOCAL_READY
        StudySyncConnectionStatus.CHECKPOINT_NEEDED.name, "PROVIDER_REQUIRED" ->
            StudySyncConnectionStatus.CHECKPOINT_NEEDED
        StudySyncConnectionStatus.CHECKPOINT_BLOCKED.name, "BLOCKED" ->
            StudySyncConnectionStatus.CHECKPOINT_BLOCKED
        else -> StudySyncConnectionStatus.DISABLED
    }

    private fun decodeConflictPolicy(value: String?): StudySyncConflictPolicy = runCatching {
        value?.let { enumValueOf<StudySyncConflictPolicy>(it) }
    }.getOrNull() ?: StudySyncConflictPolicy.MANUAL_REVIEW

    private fun decodeBackgroundSyncMode(value: String?): StudySyncBackgroundMode = runCatching {
        value?.let { enumValueOf<StudySyncBackgroundMode>(it) }
    }.getOrNull() ?: StudySyncBackgroundMode.MANUAL_ONLY

    private fun String?.toRevision(): Long = this?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    private fun String?.revisionFromToken(): Long? = this
        ?.removePrefix(RevisionTokenPrefix)
        ?.toLongOrNull()
        ?.takeIf { it >= 0L }

    private fun revisionToken(revision: Long): String = "$RevisionTokenPrefix$revision"

    private fun Long.deltaSince(previous: Long?): Int {
        if (previous == null) return coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return (this - previous).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }


    private fun decodeLedger(
        preferences: androidx.datastore.preferences.core.Preferences,
    ): StudySyncLedgerSnapshot {
        val now = clock()
        val deviceName = preferences[DeviceNameKey] ?: "This device"
        val deviceId = preferences[DeviceIdKey] ?: StudySyncDeviceIdentity.DefaultDeviceId
        val firstSeen = preferences[DeviceFirstSeenAtKey] ?: preferences[PreparedAtKey] ?: now
        val lastSeen = preferences[DeviceLastSeenAtKey] ?: preferences[LastManualUploadAtKey] ?: firstSeen
        val device = StudySyncLedgerFactory.localDevice(deviceId, deviceName, firstSeen, lastSeen)
        val checkpoints = persistedCheckpointHistory(preferences)
        val events = persistedEventHistory(preferences)
        return StudySyncLedgerSnapshot(
            devices = listOf(device),
            checkpoints = checkpoints,
            events = events,
        )
    }

    private fun recordCheckpointLedger(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        checkpointId: String,
        deviceId: String,
        deviceName: String,
        now: Long,
        revision: Long,
        changeToken: String,
        changeCount: Int,
    ) {
        val existingHistory = persistedCheckpointHistory(preferences)
        val checkpoint = StudySyncCheckpointRecord(
            id = checkpointId,
            deviceId = deviceId,
            deviceName = deviceName,
            createdAtEpochMillis = now,
            databaseRevision = revision,
            localChangeToken = changeToken,
            changeCount = changeCount,
        )
        preferences[LastCheckpointIdKey] = checkpointId
        preferences[LastCheckpointDeviceIdKey] = deviceId
        preferences[LastCheckpointDeviceNameKey] = deviceName
        preferences[LastCheckpointCreatedAtKey] = now
        preferences[LastCheckpointRevisionKey] = revision
        preferences[LastCheckpointChangeTokenKey] = changeToken
        preferences[LastCheckpointChangeCountKey] = changeCount.toLong()
        preferences[SyncLedgerCheckpointHistoryKey] = StudySyncLedgerHistoryCodec.encodeCheckpoints(
            StudySyncLedgerHistoryCodec.upsertCheckpoint(existingHistory, checkpoint),
        )
    }

    private fun persistCurrentCheckpointHistory(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
    ) {
        legacyCheckpoint(preferences)?.let { checkpoint ->
            persistCheckpointHistory(preferences, checkpoint)
        }
    }

    private fun persistCheckpointHistory(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        checkpoint: StudySyncCheckpointRecord,
    ) {
        val existing = persistedCheckpointHistory(preferences)
        preferences[SyncLedgerCheckpointHistoryKey] = StudySyncLedgerHistoryCodec.encodeCheckpoints(
            StudySyncLedgerHistoryCodec.upsertCheckpoint(existing, checkpoint),
        )
    }

    private fun persistedCheckpointHistory(
        preferences: androidx.datastore.preferences.core.Preferences,
    ): List<StudySyncCheckpointRecord> {
        val persisted = StudySyncLedgerHistoryCodec.decodeCheckpoints(
            preferences[SyncLedgerCheckpointHistoryKey],
        )
        return if (persisted.isNotEmpty()) persisted else listOfNotNull(legacyCheckpoint(preferences))
    }

    private fun legacyCheckpoint(
        preferences: androidx.datastore.preferences.core.Preferences,
    ): StudySyncCheckpointRecord? {
        val checkpointId = preferences[LastCheckpointIdKey] ?: return null
        val deviceId = preferences[DeviceIdKey] ?: StudySyncDeviceIdentity.DefaultDeviceId
        val deviceName = preferences[DeviceNameKey] ?: "This device"
        return StudySyncCheckpointRecord(
            id = checkpointId,
            deviceId = preferences[LastCheckpointDeviceIdKey] ?: deviceId,
            deviceName = preferences[LastCheckpointDeviceNameKey] ?: deviceName,
            createdAtEpochMillis = preferences[LastCheckpointCreatedAtKey] ?: 0L,
            databaseRevision = preferences[LastCheckpointRevisionKey] ?: 0L,
            localChangeToken = preferences[LastCheckpointChangeTokenKey] ?: revisionToken(0L),
            changeCount = preferences[LastCheckpointChangeCountKey]
                ?.coerceAtMost(Int.MAX_VALUE.toLong())
                ?.toInt()
                ?: 0,
            uploadedAtEpochMillis = preferences[LastCheckpointUploadedAtKey],
            remoteLocation = preferences[LastCheckpointRemoteLocationKey],
            restorePreviewedAtEpochMillis = preferences[LastRestorePreviewedAtKey],
            conflictCount = preferences[LastCheckpointConflictCountKey]
                ?.coerceAtMost(Int.MAX_VALUE.toLong())
                ?.toInt()
                ?: 0,
        )
    }

    private fun appendLedgerEvent(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        type: StudySyncLedgerEventType,
        deviceId: String,
        checkpointId: String?,
        now: Long,
        message: String,
    ) {
        val existingHistory = persistedEventHistory(preferences)
        val sequence = (preferences[SyncLedgerEventCountKey] ?: 0L) + 1L
        preferences[SyncLedgerEventCountKey] = sequence
        preferences[LastLedgerEventTypeKey] = type.name
        preferences[LastLedgerEventDeviceIdKey] = deviceId
        preferences[LastLedgerEventCheckpointIdKey] = checkpointId ?: ""
        preferences[LastLedgerEventAtKey] = now
        preferences[LastLedgerEventMessageKey] = message
        val event = StudySyncLedgerEvent(
            sequence = sequence,
            type = type,
            deviceId = deviceId,
            checkpointId = checkpointId,
            createdAtEpochMillis = now,
            message = message,
        )
        preferences[SyncLedgerEventHistoryKey] = StudySyncLedgerHistoryCodec.encodeEvents(
            StudySyncLedgerHistoryCodec.appendEvent(existingHistory, event),
        )
    }

    private fun persistedEventHistory(
        preferences: androidx.datastore.preferences.core.Preferences,
    ): List<StudySyncLedgerEvent> {
        val persisted = StudySyncLedgerHistoryCodec.decodeEvents(preferences[SyncLedgerEventHistoryKey])
        if (persisted.isNotEmpty()) return persisted
        val eventCount = preferences[SyncLedgerEventCountKey] ?: 0L
        if (eventCount <= 0L) return emptyList()
        val deviceId = preferences[LastLedgerEventDeviceIdKey]
            ?: preferences[DeviceIdKey]
            ?: StudySyncDeviceIdentity.DefaultDeviceId
        return listOf(
            StudySyncLedgerEvent(
                sequence = eventCount,
                type = decodeLedgerEventType(preferences[LastLedgerEventTypeKey]),
                deviceId = deviceId,
                checkpointId = preferences[LastLedgerEventCheckpointIdKey]?.takeIf { it.isNotBlank() },
                createdAtEpochMillis = preferences[LastLedgerEventAtKey] ?: 0L,
                message = preferences[LastLedgerEventMessageKey] ?: "Sync ledger updated.",
            ),
        )
    }

    private fun decodeLedgerEventType(value: String?): StudySyncLedgerEventType = runCatching {
        value?.let { enumValueOf<StudySyncLedgerEventType>(it) }
    }.getOrNull() ?: StudySyncLedgerEventType.DEVICE_REGISTERED

    private companion object {
        const val RevisionTokenPrefix = "revision="
        const val MaximumManualEncryptedBundleBytes = 576L * 1024L * 1024L
        const val MaximumStableCaptureAttempts = 3
        val EnabledKey = booleanPreferencesKey("enabled")
        val StatusKey = stringPreferencesKey("status")
        val ConflictPolicyKey = stringPreferencesKey("conflict_policy")
        val DeviceNameKey = stringPreferencesKey("device_name")
        val PreparedAtKey = longPreferencesKey("prepared_at")
        val ChangeTokenKey = stringPreferencesKey("change_token")
        val PendingLocalChangeKey = longPreferencesKey("pending_local_change_count")
        val ProviderIdKey = stringPreferencesKey("provider_id")
        val ProviderLocationKey = stringPreferencesKey("provider_location")
        val AutomaticSyncEnabledKey = booleanPreferencesKey("automatic_sync_enabled")
        val BackgroundSyncModeKey = stringPreferencesKey("background_sync_mode")
        val WifiOnlyKey = booleanPreferencesKey("wifi_only")
        val BatteryNotLowKey = booleanPreferencesKey("battery_not_low")
        val ChargingOnlyKey = booleanPreferencesKey("charging_only")
        val EncryptRemoteBackupsKey = booleanPreferencesKey("encrypt_remote_backups")
        val LastManualUploadAtKey = longPreferencesKey("last_manual_upload_at")
        val LastRemoteCheckAtKey = longPreferencesKey("last_remote_check_at")
        val RemoteCheckpointCountKey = longPreferencesKey("remote_checkpoint_count")
        val DeviceIdKey = stringPreferencesKey("device_id")
        val DeviceFirstSeenAtKey = longPreferencesKey("device_first_seen_at")
        val DeviceLastSeenAtKey = longPreferencesKey("device_last_seen_at")
        val LastCheckpointIdKey = stringPreferencesKey("last_checkpoint_id")
        val LastCheckpointDeviceIdKey = stringPreferencesKey("last_checkpoint_device_id")
        val LastCheckpointDeviceNameKey = stringPreferencesKey("last_checkpoint_device_name")
        val LastCheckpointCreatedAtKey = longPreferencesKey("last_checkpoint_created_at")
        val LastCheckpointRevisionKey = longPreferencesKey("last_checkpoint_revision")
        val LastCheckpointChangeTokenKey = stringPreferencesKey("last_checkpoint_change_token")
        val LastCheckpointChangeCountKey = longPreferencesKey("last_checkpoint_change_count")
        val LastCheckpointUploadedAtKey = longPreferencesKey("last_checkpoint_uploaded_at")
        val LastCheckpointRemoteLocationKey = stringPreferencesKey("last_checkpoint_remote_location")
        val LastRestorePreviewedAtKey = longPreferencesKey("last_restore_previewed_at")
        val LastCheckpointConflictCountKey = longPreferencesKey("last_checkpoint_conflict_count")
        val SyncLedgerCheckpointHistoryKey = stringPreferencesKey("sync_ledger_checkpoints_v1")
        val SyncLedgerEventHistoryKey = stringPreferencesKey("sync_ledger_events_v1")
        val SyncLedgerEventCountKey = longPreferencesKey("sync_ledger_event_count")
        val LastLedgerEventTypeKey = stringPreferencesKey("last_ledger_event_type")
        val LastLedgerEventDeviceIdKey = stringPreferencesKey("last_ledger_event_device_id")
        val LastLedgerEventCheckpointIdKey = stringPreferencesKey("last_ledger_event_checkpoint_id")
        val LastLedgerEventAtKey = longPreferencesKey("last_ledger_event_at")
        val LastLedgerEventMessageKey = stringPreferencesKey("last_ledger_event_message")
        val BlockedReasonKey = stringPreferencesKey("blocked_reason")
    }
}
