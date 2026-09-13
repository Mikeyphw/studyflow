package com.mikeyphw.studyflow

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.mikeyphw.studyflow.core.model.LocalFolderSyncProvider
import com.mikeyphw.studyflow.core.model.StudyFlowSyncProviderIds
import com.mikeyphw.studyflow.core.model.StudyFlowSyncSettings
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncState
import com.mikeyphw.studyflow.feature.settings.R as SettingsR
import com.mikeyphw.studyflow.feature.settings.SyncFoundationScreen
import org.junit.Rule
import org.junit.Test

class OptionalSyncInstrumentationValidationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun optionalSyncScreenKeepsOfflinePathVisibleOnDevice() {
        composeRule.setContent {
            MaterialTheme {
                SyncFoundationScreen(
                    state = StudySyncState.disabled(deviceName = "Instrumented device"),
                    summary = summary(),
                    summaryFailed = false,
                    onRetrySummary = {},
                    onPrepare = {},
                    onDisable = {},
                    onBack = {},
                    onChooseSyncFolder = {},
                    onUploadEncryptedCheckpoint = {},
                    onCheckRemoteCheckpoints = {},
                    onPreviewRemoteRestore = {},
                    onViewSyncHistory = {},
                    onApplyRemoteRestore = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.sync_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.sync_foundation_message))
            .assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.sync_keep_offline))
            .assertExists()
        composeRule.onAllNodesWithText(composeRule.activity.getString(SettingsR.string.sync_choose_folder))
            .assertCountEquals(2)
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.sync_background_title))
            .assertExists()
    }

    @Test
    fun manualFolderSyncActionsStayDiscoverableOnDevice() {
        composeRule.setContent {
            MaterialTheme {
                SyncFoundationScreen(
                    state = localFolderState(),
                    summary = summary(),
                    summaryFailed = false,
                    onRetrySummary = {},
                    onPrepare = {},
                    onDisable = {},
                    onBack = {},
                    onChooseSyncFolder = {},
                    onUploadEncryptedCheckpoint = {},
                    onCheckRemoteCheckpoints = {},
                    onPreviewRemoteRestore = {},
                    onViewSyncHistory = {},
                    onApplyRemoteRestore = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.sync_folder_selected))
            .assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.sync_prepare_upload_checkpoint))
            .assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.sync_check_remote_checkpoint))
            .assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.sync_preview_restore))
            .assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.sync_view_history))
            .assertExists()
    }

    private fun localFolderState() = StudySyncState(
        enabled = true,
        provider = LocalFolderSyncProvider,
        connectionStatus = StudySyncConnectionStatus.LOCAL_READY,
        conflictPolicy = StudySyncConflictPolicy.MANUAL_REVIEW,
        deviceName = "Instrumented device",
        lastPreparedAtEpochMillis = 1_234L,
        lastLocalChangeToken = "checkpoint-token",
        pendingLocalChangeCount = 1,
        blockedReason = null,
        settings = StudyFlowSyncSettings(
            syncEnabled = true,
            providerId = StudyFlowSyncProviderIds.LOCAL_FOLDER,
            providerLocation = "content://com.android.externalstorage.documents/tree/primary%3AStudyFlow",
            lastManualUploadAtEpochMillis = 1_234L,
            lastRemoteCheckAtEpochMillis = 2_345L,
            remoteCheckpointCount = 1,
        ),
    )

    private fun summary() = StudySyncChangeSummary(
        planCount = 1,
        activePlanCount = 1,
        latestUpdatedAtEpochMillis = 1_234L,
        localChangeToken = "instrumented-token",
        databaseRevision = 7L,
        changesSinceCheckpoint = 1,
    )
}
