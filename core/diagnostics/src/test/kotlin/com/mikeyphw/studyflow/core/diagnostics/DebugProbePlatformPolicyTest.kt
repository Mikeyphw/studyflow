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
    fun `backup policy fails when actual backup differs from fail closed intent`() {
        val unsafe = DebugProbePlatformPolicy.backup(
            DebugProbeId.BACKUP_CONFIGURATION,
            BackupConfigurationSnapshot(
                backupAllowed = true,
                expectedBackupAllowed = false,
                legacyRuleLinked = true,
                modernRuleLinked = true,
                legacyRulePresent = true,
                modernRulePresent = true,
            ),
        )
        assertEquals(DebugProbeStatus.FAILED, unsafe.status)
        assertEquals(DebugProbeMessage.BACKUP_POLICY_INVALID, unsafe.message)

        val missingRule = DebugProbePlatformPolicy.backup(
            DebugProbeId.BACKUP_CONFIGURATION,
            BackupConfigurationSnapshot(
                backupAllowed = false,
                expectedBackupAllowed = false,
                legacyRuleLinked = true,
                modernRuleLinked = true,
                legacyRulePresent = true,
                modernRulePresent = false,
            ),
        )
        assertEquals(DebugProbeStatus.FAILED, missingRule.status)
        assertEquals(DebugProbeMessage.BACKUP_RULES_INVALID, missingRule.message)

        val healthy = DebugProbePlatformPolicy.backup(
            DebugProbeId.BACKUP_CONFIGURATION,
            BackupConfigurationSnapshot(
                backupAllowed = false,
                expectedBackupAllowed = false,
                legacyRuleLinked = true,
                modernRuleLinked = true,
                legacyRulePresent = true,
                modernRulePresent = true,
            ),
        )
        assertEquals(DebugProbeStatus.PASSED, healthy.status)
    }

    @Test
    fun `file provider policy fails when private path is grantable`() {
        val exposedPrivatePath = DebugProbePlatformPolicy.fileProvider(
            DebugProbeId.FILE_PROVIDER_CONFIGURATION,
            FileProviderHealthSnapshot(
                providerPresent = true,
                providerExported = false,
                grantsUriPermissions = true,
                pathMetadataPresent = true,
                allowedRootGranted = true,
                privateRootRejected = false,
            ),
        )
        assertEquals(DebugProbeStatus.FAILED, exposedPrivatePath.status)

        val healthy = DebugProbePlatformPolicy.fileProvider(
            DebugProbeId.FILE_PROVIDER_CONFIGURATION,
            FileProviderHealthSnapshot(
                providerPresent = true,
                providerExported = false,
                grantsUriPermissions = true,
                pathMetadataPresent = true,
                allowedRootGranted = true,
                privateRootRejected = true,
            ),
        )
        assertEquals(DebugProbeStatus.PASSED, healthy.status)
    }

    @Test
    fun `process history policy classifies crash and abnormal exits rather than passing on presence`() {
        val unsupported = DebugProbePlatformPolicy.processExit(
            DebugProbeId.PROCESS_EXIT_HISTORY,
            ProcessExitHealthSnapshot(
                apiSupported = false,
                recentExitCount = 0,
                crashOrAnrCount = 0,
                abnormalExitCount = 0,
                lowMemoryCount = 0,
                apiLevel = 29,
            ),
        )
        assertEquals(DebugProbeMessage.PROCESS_HISTORY_UNAVAILABLE, unsupported.message)

        val empty = DebugProbePlatformPolicy.processExit(
            DebugProbeId.PROCESS_EXIT_HISTORY,
            ProcessExitHealthSnapshot(true, 0, 0, 0, 0, 36),
        )
        assertEquals(DebugProbeMessage.NO_PROCESS_EXIT_HISTORY, empty.message)

        val crash = DebugProbePlatformPolicy.processExit(
            DebugProbeId.PROCESS_EXIT_HISTORY,
            ProcessExitHealthSnapshot(true, 2, 1, 1, 0, 36),
        )
        assertEquals(DebugProbeStatus.WARNING, crash.status)
        assertEquals(DebugProbeMessage.RECENT_CRASH_OR_ANR, crash.message)

        val lowMemory = DebugProbePlatformPolicy.processExit(
            DebugProbeId.PROCESS_EXIT_HISTORY,
            ProcessExitHealthSnapshot(true, 2, 0, 0, 1, 36),
        )
        assertEquals(DebugProbeStatus.WARNING, lowMemory.status)
        assertEquals(DebugProbeMessage.RECENT_LOW_MEMORY_EXIT, lowMemory.message)

        val healthyHistory = DebugProbePlatformPolicy.processExit(
            DebugProbeId.PROCESS_EXIT_HISTORY,
            ProcessExitHealthSnapshot(true, 2, 0, 0, 0, 36),
        )
        assertEquals(DebugProbeStatus.PASSED, healthyHistory.status)
    }
}
