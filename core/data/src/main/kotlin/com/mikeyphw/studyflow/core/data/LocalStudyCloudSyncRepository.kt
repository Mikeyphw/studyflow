package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.domain.StudyCloudSyncRepository
import com.mikeyphw.studyflow.core.model.LocalOnlyProvider
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.studySyncDataStore by preferencesDataStore(name = "study_sync_foundation")

internal class LocalStudyCloudSyncRepository(
    context: Context,
    private val database: StudyFlowDatabase,
    private val clock: () -> Long,
) : StudyCloudSyncRepository {
    private val dataStore = context.applicationContext.studySyncDataStore

    override val syncState: Flow<StudySyncState> = dataStore.data.map(::decodeState)

    override suspend fun inspectLocalChanges(): StudySyncChangeSummary {
        val plans = database.studyPlanDao().snapshotAll()
        val activeCount = plans.count { entity ->
            enumValueOf<StudyPlanStatus>(entity.status) in setOf(StudyPlanStatus.ACTIVE, StudyPlanStatus.PAUSED)
        }
        val latestUpdated = plans.maxOfOrNull { entity -> entity.updatedAtEpochMillis }
        val token = buildString {
            append("plans=").append(plans.size)
            append(";active=").append(activeCount)
            append(";updated=").append(latestUpdated ?: 0L)
        }
        return StudySyncChangeSummary(
            planCount = plans.size,
            activePlanCount = activeCount,
            latestUpdatedAtEpochMillis = latestUpdated,
            localChangeToken = token,
        )
    }

    override suspend fun prepareLocalFoundation(deviceName: String): StudySyncState {
        val summary = inspectLocalChanges()
        dataStore.edit { preferences ->
            preferences[EnabledKey] = true
            preferences[StatusKey] = StudySyncConnectionStatus.LOCAL_READY.name
            preferences[ConflictPolicyKey] = StudySyncConflictPolicy.MANUAL_REVIEW.name
            preferences[DeviceNameKey] = deviceName
            preferences[PreparedAtKey] = clock()
            preferences[ChangeTokenKey] = summary.localChangeToken
            preferences[PendingUploadKey] = summary.planCount.toLong()
            preferences.remove(BlockedReasonKey)
        }
        return syncState.first()
    }

    override suspend fun setSyncEnabled(enabled: Boolean): StudySyncState {
        val summary = inspectLocalChanges()
        dataStore.edit { preferences ->
            preferences[EnabledKey] = enabled
            preferences[StatusKey] = if (enabled) {
                StudySyncConnectionStatus.LOCAL_READY.name
            } else {
                StudySyncConnectionStatus.DISABLED.name
            }
            if (enabled && preferences[ChangeTokenKey] == null) {
                preferences[ChangeTokenKey] = summary.localChangeToken
                preferences[PreparedAtKey] = clock()
                preferences[PendingUploadKey] = summary.planCount.toLong()
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

    private fun decodeState(preferences: androidx.datastore.preferences.core.Preferences): StudySyncState =
        StudySyncState(
            enabled = preferences[EnabledKey] ?: false,
            provider = LocalOnlyProvider,
            connectionStatus = decodeStatus(preferences[StatusKey]),
            conflictPolicy = decodeConflictPolicy(preferences[ConflictPolicyKey]),
            deviceName = preferences[DeviceNameKey] ?: "This device",
            lastPreparedAtEpochMillis = preferences[PreparedAtKey],
            lastLocalChangeToken = preferences[ChangeTokenKey],
            pendingUploadCount = preferences[PendingUploadKey]?.toInt() ?: 0,
            pendingDownloadCount = 0,
            blockedReason = preferences[BlockedReasonKey],
        )

    private fun decodeStatus(value: String?): StudySyncConnectionStatus = runCatching {
        value?.let { enumValueOf<StudySyncConnectionStatus>(it) }
    }.getOrNull() ?: StudySyncConnectionStatus.DISABLED

    private fun decodeConflictPolicy(value: String?): StudySyncConflictPolicy = runCatching {
        value?.let { enumValueOf<StudySyncConflictPolicy>(it) }
    }.getOrNull() ?: StudySyncConflictPolicy.MANUAL_REVIEW

    private companion object {
        val EnabledKey = booleanPreferencesKey("enabled")
        val StatusKey = stringPreferencesKey("status")
        val ConflictPolicyKey = stringPreferencesKey("conflict_policy")
        val DeviceNameKey = stringPreferencesKey("device_name")
        val PreparedAtKey = longPreferencesKey("prepared_at")
        val ChangeTokenKey = stringPreferencesKey("change_token")
        val PendingUploadKey = longPreferencesKey("pending_upload")
        val BlockedReasonKey = stringPreferencesKey("blocked_reason")
    }
}
