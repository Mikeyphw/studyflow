package com.mikeyphw.studyflow.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugProbePlatformPolicyTest {
    @Test
    fun `notification policy distinguishes permission channel and healthy states`() {
        val denied = DebugProbePlatformPolicy.notification(
            DebugProbeId.NOTIFICATION_HEALTH,
            NotificationHealthSnapshot(
                permissionGranted = false,
                appNotificationsEnabled = true,
                channelRequired = true,
                channelPresent = true,
                channelBlocked = false,
                channelImportance = 3,
                apiLevel = 33,
            ),
        )
        assertEquals(DebugProbeMessage.NOTIFICATION_PERMISSION_DENIED, denied.message)
        assertEquals(DebugProbeStatus.WARNING, denied.status)

        val missing = DebugProbePlatformPolicy.notification(
            DebugProbeId.NOTIFICATION_HEALTH,
            NotificationHealthSnapshot(true, true, true, false, false, null, 36),
        )
        assertEquals(DebugProbeMessage.CHANNEL_MISSING, missing.message)

        val healthy = DebugProbePlatformPolicy.notification(
            DebugProbeId.NOTIFICATION_HEALTH,
            NotificationHealthSnapshot(true, true, true, true, false, 3, 36),
        )
        assertEquals(DebugProbeStatus.PASSED, healthy.status)
    }

    @Test
    fun `widget policy verifies device provider metadata enabled state and instances`() {
        val unsupported = DebugProbePlatformPolicy.widget(
            DebugProbeId.WIDGET_HEALTH,
            WidgetHealthSnapshot(false, true, true, true, 1),
        )
        assertEquals(DebugProbeMessage.WIDGETS_UNSUPPORTED, unsupported.message)
        assertEquals(DebugProbeStatus.BLOCKED, unsupported.status)

        val missing = DebugProbePlatformPolicy.widget(
            DebugProbeId.WIDGET_HEALTH,
            WidgetHealthSnapshot(true, false, false, false, 0),
        )
        assertEquals(DebugProbeMessage.WIDGET_PROVIDER_MISSING, missing.message)
        assertEquals(DebugProbeStatus.FAILED, missing.status)

        val disabled = DebugProbePlatformPolicy.widget(
            DebugProbeId.WIDGET_HEALTH,
            WidgetHealthSnapshot(true, true, false, true, 0),
        )
        assertEquals(DebugProbeMessage.WIDGET_PROVIDER_DISABLED, disabled.message)
        assertEquals(DebugProbeStatus.WARNING, disabled.status)

        val unbound = DebugProbePlatformPolicy.widget(
            DebugProbeId.WIDGET_HEALTH,
            WidgetHealthSnapshot(true, true, true, true, 0),
        )
        assertEquals(DebugProbeMessage.NO_WIDGET_INSTANCES, unbound.message)
        assertEquals(DebugProbeStatus.BLOCKED, unbound.status)

        val healthy = DebugProbePlatformPolicy.widget(
            DebugProbeId.WIDGET_HEALTH,
            WidgetHealthSnapshot(true, true, true, true, 2),
        )
        assertEquals(DebugProbeStatus.PASSED, healthy.status)
    }

    @Test
    fun `backup policy fails when either manifest link or resource is missing`() {
        val missingLink = DebugProbePlatformPolicy.backup(
            DebugProbeId.BACKUP_CONFIGURATION,
            BackupConfigurationSnapshot(
                backupAllowed = true,
                legacyRuleLinked = false,
                modernRuleLinked = true,
                legacyRulePresent = true,
                modernRulePresent = true,
            ),
        )
        assertEquals(DebugProbeStatus.FAILED, missingLink.status)
        assertEquals(DebugProbeMessage.BACKUP_RULES_INVALID, missingLink.message)

        val missingResource = DebugProbePlatformPolicy.backup(
            DebugProbeId.BACKUP_CONFIGURATION,
            BackupConfigurationSnapshot(true, true, true, true, false),
        )
        assertEquals(DebugProbeStatus.FAILED, missingResource.status)

        val healthy = DebugProbePlatformPolicy.backup(
            DebugProbeId.BACKUP_CONFIGURATION,
            BackupConfigurationSnapshot(true, true, true, true, true),
        )
        assertEquals(DebugProbeStatus.PASSED, healthy.status)
    }

    @Test
    fun `file provider policy requires narrow provider metadata and grants`() {
        val invalid = DebugProbePlatformPolicy.fileProvider(
            DebugProbeId.FILE_PROVIDER_CONFIGURATION,
            FileProviderHealthSnapshot(true, false, true, false),
        )
        assertEquals(DebugProbeStatus.FAILED, invalid.status)

        val healthy = DebugProbePlatformPolicy.fileProvider(
            DebugProbeId.FILE_PROVIDER_CONFIGURATION,
            FileProviderHealthSnapshot(true, false, true, true),
        )
        assertEquals(DebugProbeStatus.PASSED, healthy.status)
    }

    @Test
    fun `process history policy treats unsupported and empty history as blocked`() {
        val unsupported = DebugProbePlatformPolicy.processExit(
            DebugProbeId.PROCESS_EXIT_HISTORY,
            ProcessExitHealthSnapshot(false, 0, 29),
        )
        assertEquals(DebugProbeMessage.PROCESS_HISTORY_UNAVAILABLE, unsupported.message)

        val empty = DebugProbePlatformPolicy.processExit(
            DebugProbeId.PROCESS_EXIT_HISTORY,
            ProcessExitHealthSnapshot(true, 0, 36),
        )
        assertEquals(DebugProbeMessage.NO_PROCESS_EXIT_HISTORY, empty.message)

        val present = DebugProbePlatformPolicy.processExit(
            DebugProbeId.PROCESS_EXIT_HISTORY,
            ProcessExitHealthSnapshot(true, 2, 36),
        )
        assertEquals(DebugProbeStatus.PASSED, present.status)
    }
}
