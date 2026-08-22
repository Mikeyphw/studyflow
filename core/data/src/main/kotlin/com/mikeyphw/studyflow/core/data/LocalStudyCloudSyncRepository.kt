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

    override suspend fun prepareLocalFoundation(deviceName: String): StudySyncState {
        val revision = metadataDao.findValue(StudyFlowIntegrity.GraphRevisionMetadataKey).toRevision()
        dataStore.edit { preferences ->
            preferences[EnabledKey] = true
            preferences[StatusKey] = StudySyncConnectionStatus.LOCAL_READY.name
            preferences[ConflictPolicyKey] = preferences[ConflictPolicyKey]
                ?: StudySyncConflictPolicy.MANUAL_REVIEW.name
            preferences[DeviceNameKey] = deviceName
            preferences[PreparedAtKey] = clock()
            preferences[ChangeTokenKey] = revisionToken(revision)
            preferences[PendingLocalChangeKey] = 0L
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
        val AutomaticSyncEnabledKey = booleanPreferencesKey("automatic_sync_enabled")
        val WifiOnlyKey = booleanPreferencesKey("wifi_only")
        val ChargingOnlyKey = booleanPreferencesKey("charging_only")
        val EncryptRemoteBackupsKey = booleanPreferencesKey("encrypt_remote_backups")
        val BlockedReasonKey = stringPreferencesKey("blocked_reason")
    }
}
