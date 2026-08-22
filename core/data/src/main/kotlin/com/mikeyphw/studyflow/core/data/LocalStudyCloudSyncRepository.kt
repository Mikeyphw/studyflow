package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowIntegrity
import com.mikeyphw.studyflow.core.domain.StudyCloudSyncRepository
import com.mikeyphw.studyflow.core.model.LocalFolderSyncProvider
import com.mikeyphw.studyflow.core.model.LocalOnlyProvider
import com.mikeyphw.studyflow.core.model.NoSyncProvider
import com.mikeyphw.studyflow.core.model.StudyFlowSyncProviderIds
import com.mikeyphw.studyflow.core.model.StudyFlowSyncSettings
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

private val Context.studySyncDataStore by preferencesDataStore(name = "study_sync_foundation")

/**
 * Local-first optional sync state. The durable source of freshness is the Room graph revision, not a
 * synthetic plan count. Every user-data mutation advances that revision through database triggers.
 */
internal class LocalStudyCloudSyncRepository(
    context: Context,
    private val database: StudyFlowDatabase,
    private val clock: () -> Long,
) : StudyCloudSyncRepository {
    private val dataStore = context.applicationContext.studySyncDataStore
    private val metadataDao = database.databaseMetadataDao()

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
        val now = clock()
        dataStore.edit { preferences ->
            val deviceId = preferences[DeviceIdKey] ?: StudySyncDeviceIdentity.stableDeviceId(deviceName)
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
            recordCheckpointLedger(preferences, checkpointId, deviceId, deviceName, now, revision, token)
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

    override suspend fun configureLocalFolderProvider(folderUri: String): StudySyncState {
        val revision = metadataDao.findValue(StudyFlowIntegrity.GraphRevisionMetadataKey).toRevision()
        val now = clock()
        dataStore.edit { preferences ->
            val deviceName = preferences[DeviceNameKey] ?: "This device"
            val deviceId = preferences[DeviceIdKey] ?: StudySyncDeviceIdentity.stableDeviceId(deviceName)
            preferences[DeviceIdKey] = deviceId
            preferences[DeviceFirstSeenAtKey] = preferences[DeviceFirstSeenAtKey] ?: now
            preferences[DeviceLastSeenAtKey] = now
            preferences[EnabledKey] = true
            preferences[ProviderIdKey] = StudyFlowSyncProviderIds.LOCAL_FOLDER
            preferences[ProviderLocationKey] = folderUri
            preferences[AutomaticSyncEnabledKey] = false
            preferences[EncryptRemoteBackupsKey] = true
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
                message = "Configured optional folder sync provider.",
            )
            preferences.remove(BlockedReasonKey)
        }
        return syncState.first()
    }

    override suspend fun recordManualUploadCheckpoint(): StudySyncState {
        val revision = metadataDao.findValue(StudyFlowIntegrity.GraphRevisionMetadataKey).toRevision()
        val now = clock()
        dataStore.edit { preferences ->
            val remoteLocation = preferences[ProviderLocationKey]
            if (preferences[ProviderIdKey] != StudyFlowSyncProviderIds.LOCAL_FOLDER ||
                remoteLocation.isNullOrBlank()
            ) {
                preferences[StatusKey] = StudySyncConnectionStatus.CHECKPOINT_BLOCKED.name
                preferences[BlockedReasonKey] = "Choose a sync folder before manual upload."
                return@edit
            }
            val deviceName = preferences[DeviceNameKey] ?: "This device"
            val deviceId = preferences[DeviceIdKey] ?: StudySyncDeviceIdentity.stableDeviceId(deviceName)
            val token = revisionToken(revision)
            val checkpointId = StudySyncLedgerFactory.checkpointId(deviceId, revision, now)
            preferences[EnabledKey] = true
            preferences[StatusKey] = StudySyncConnectionStatus.LOCAL_READY.name
            preferences[DeviceIdKey] = deviceId
            preferences[DeviceNameKey] = deviceName
            preferences[DeviceFirstSeenAtKey] = preferences[DeviceFirstSeenAtKey] ?: now
            preferences[DeviceLastSeenAtKey] = now
            preferences[PreparedAtKey] = now
            preferences[ChangeTokenKey] = token
            preferences[PendingLocalChangeKey] = 0L
            preferences[LastManualUploadAtKey] = now
            preferences[RemoteCheckpointCountKey] = maxOf(preferences[RemoteCheckpointCountKey] ?: 0L, 1L)
            recordCheckpointLedger(preferences, checkpointId, deviceId, deviceName, now, revision, token)
            preferences[LastCheckpointUploadedAtKey] = now
            preferences[LastCheckpointRemoteLocationKey] = remoteLocation
            appendLedgerEvent(
                preferences = preferences,
                type = StudySyncLedgerEventType.MANUAL_UPLOAD_RECORDED,
                deviceId = deviceId,
                checkpointId = checkpointId,
                now = now,
                message = "Recorded manual encrypted checkpoint upload.",
            )
            preferences.remove(BlockedReasonKey)
        }
        return syncState.first()
    }

    override suspend fun recordManualRemoteCheck(remoteCheckpointCount: Int): StudySyncState {
        val now = clock()
        dataStore.edit { preferences ->
            if (preferences[ProviderIdKey] != StudyFlowSyncProviderIds.LOCAL_FOLDER ||
                preferences[ProviderLocationKey].isNullOrBlank()
            ) {
                preferences[StatusKey] = StudySyncConnectionStatus.CHECKPOINT_BLOCKED.name
                preferences[BlockedReasonKey] = "Choose a sync folder before checking remote checkpoints."
                return@edit
            }
            val deviceName = preferences[DeviceNameKey] ?: "This device"
            val deviceId = preferences[DeviceIdKey] ?: StudySyncDeviceIdentity.stableDeviceId(deviceName)
            preferences[DeviceIdKey] = deviceId
            preferences[DeviceFirstSeenAtKey] = preferences[DeviceFirstSeenAtKey] ?: now
            preferences[DeviceLastSeenAtKey] = now
            preferences[LastRemoteCheckAtKey] = now
            preferences[RemoteCheckpointCountKey] = remoteCheckpointCount.toLong()
            appendLedgerEvent(
                preferences = preferences,
                type = StudySyncLedgerEventType.REMOTE_CHECK_COMPLETED,
                deviceId = deviceId,
                checkpointId = preferences[LastCheckpointIdKey],
                now = now,
                message = "Checked for remote encrypted checkpoints.",
            )
            preferences.remove(BlockedReasonKey)
        }
        return syncState.first()
    }

    override suspend fun recordRestorePreview(checkpointId: String): StudySyncState {
        val now = clock()
        dataStore.edit { preferences ->
            val deviceName = preferences[DeviceNameKey] ?: "This device"
            val deviceId = preferences[DeviceIdKey] ?: StudySyncDeviceIdentity.stableDeviceId(deviceName)
            preferences[DeviceIdKey] = deviceId
            preferences[DeviceFirstSeenAtKey] = preferences[DeviceFirstSeenAtKey] ?: now
            preferences[DeviceLastSeenAtKey] = now
            if (preferences[LastCheckpointIdKey] == checkpointId) {
                preferences[LastRestorePreviewedAtKey] = now
            }
            appendLedgerEvent(
                preferences = preferences,
                type = StudySyncLedgerEventType.RESTORE_PREVIEWED,
                deviceId = deviceId,
                checkpointId = checkpointId,
                now = now,
                message = "Previewed checkpoint restore before applying data.",
            )
            preferences.remove(BlockedReasonKey)
        }
        return syncState.first()
    }

    override suspend fun clearLocalFoundation(): StudySyncState {
        dataStore.edit { preferences -> preferences.clear() }
        return StudySyncState.disabled()
    }

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
        wifiOnly = preferences[WifiOnlyKey] ?: true,
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
    ) = when (settings.providerId) {
        null -> if (enabled) LocalOnlyProvider else NoSyncProvider
        StudyFlowSyncProviderIds.LOCAL_FOLDER -> LocalFolderSyncProvider
        else -> com.mikeyphw.studyflow.core.model.StudySyncProviderDescriptor(
            kind = com.mikeyphw.studyflow.core.model.StudySyncProviderKind.REMOTE_PROVIDER,
            displayName = settings.providerId,
            configured = true,
            id = settings.providerId,
            requiresAccount = true,
        )
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
        val checkpoint = preferences[LastCheckpointIdKey]?.let { checkpointId ->
            StudySyncCheckpointRecord(
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
        val eventCount = preferences[SyncLedgerEventCountKey] ?: 0L
        val event = if (eventCount > 0L) {
            StudySyncLedgerEvent(
                sequence = eventCount,
                type = decodeLedgerEventType(preferences[LastLedgerEventTypeKey]),
                deviceId = deviceId,
                checkpointId = preferences[LastLedgerEventCheckpointIdKey]?.takeIf { it.isNotBlank() },
                createdAtEpochMillis = preferences[LastLedgerEventAtKey] ?: lastSeen,
                message = preferences[LastLedgerEventMessageKey] ?: "Sync ledger updated.",
            )
        } else {
            null
        }
        return StudySyncLedgerSnapshot(
            devices = listOf(device),
            checkpoints = listOfNotNull(checkpoint),
            events = listOfNotNull(event),
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
    ) {
        preferences[LastCheckpointIdKey] = checkpointId
        preferences[LastCheckpointDeviceIdKey] = deviceId
        preferences[LastCheckpointDeviceNameKey] = deviceName
        preferences[LastCheckpointCreatedAtKey] = now
        preferences[LastCheckpointRevisionKey] = revision
        preferences[LastCheckpointChangeTokenKey] = changeToken
        preferences[LastCheckpointChangeCountKey] = preferences[PendingLocalChangeKey] ?: 0L
    }

    private fun appendLedgerEvent(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        type: StudySyncLedgerEventType,
        deviceId: String,
        checkpointId: String?,
        now: Long,
        message: String,
    ) {
        preferences[SyncLedgerEventCountKey] = (preferences[SyncLedgerEventCountKey] ?: 0L) + 1L
        preferences[LastLedgerEventTypeKey] = type.name
        preferences[LastLedgerEventDeviceIdKey] = deviceId
        preferences[LastLedgerEventCheckpointIdKey] = checkpointId ?: ""
        preferences[LastLedgerEventAtKey] = now
        preferences[LastLedgerEventMessageKey] = message
    }

    private fun decodeLedgerEventType(value: String?): StudySyncLedgerEventType = runCatching {
        value?.let { enumValueOf<StudySyncLedgerEventType>(it) }
    }.getOrNull() ?: StudySyncLedgerEventType.DEVICE_REGISTERED

    private companion object {
        const val RevisionTokenPrefix = "revision="
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
        val WifiOnlyKey = booleanPreferencesKey("wifi_only")
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
        val SyncLedgerEventCountKey = longPreferencesKey("sync_ledger_event_count")
        val LastLedgerEventTypeKey = stringPreferencesKey("last_ledger_event_type")
        val LastLedgerEventDeviceIdKey = stringPreferencesKey("last_ledger_event_device_id")
        val LastLedgerEventCheckpointIdKey = stringPreferencesKey("last_ledger_event_checkpoint_id")
        val LastLedgerEventAtKey = longPreferencesKey("last_ledger_event_at")
        val LastLedgerEventMessageKey = stringPreferencesKey("last_ledger_event_message")
        val BlockedReasonKey = stringPreferencesKey("blocked_reason")
    }
}
