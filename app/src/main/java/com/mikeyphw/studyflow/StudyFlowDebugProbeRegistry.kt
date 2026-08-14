package com.mikeyphw.studyflow

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mikeyphw.studyflow.core.diagnostics.BackupConfigurationSnapshot
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeCatalog
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeDescriptor
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeId
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeMessage
import com.mikeyphw.studyflow.core.diagnostics.DebugProbePlatformPolicy
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeResult
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeStatus
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRedactor
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticSeverity
import com.mikeyphw.studyflow.core.diagnostics.FileProviderHealthSnapshot
import com.mikeyphw.studyflow.core.diagnostics.NotificationHealthSnapshot
import com.mikeyphw.studyflow.core.diagnostics.ProcessExitHealthSnapshot
import com.mikeyphw.studyflow.core.diagnostics.WidgetHealthSnapshot
import com.mikeyphw.studyflow.core.domain.StudyLocalCheckpointReadinessAuditor
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator
import com.mikeyphw.studyflow.reminders.StudyReminderChannelId
import com.mikeyphw.studyflow.widget.StudyFlowTodayWidgetProvider
import java.io.File
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal fun interface StudyFlowDebugProbeOperations {
    suspend fun execute(id: DebugProbeId, planId: StudyPlanId?): DebugProbeResult
}

internal class StudyFlowDebugProbeRegistry(
    private val operations: StudyFlowDebugProbeOperations,
    private val recorder: DiagnosticRecorder,
    private val defaultPlanId: () -> StudyPlanId?,
    private val executionContext: CoroutineContext = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val descriptorProvider: (DebugProbeId) -> DebugProbeDescriptor = DebugProbeCatalog::descriptor,
) {
    constructor(
        context: Context,
        container: AppContainer,
        defaultPlanId: () -> StudyPlanId?,
        executionContext: CoroutineContext = Dispatchers.IO,
        clock: () -> Long = System::currentTimeMillis,
        elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    ) : this(
        operations = AndroidStudyFlowDebugProbeOperations(context.applicationContext, container),
        recorder = container.diagnosticRecorder,
        defaultPlanId = defaultPlanId,
        executionContext = executionContext,
        clock = clock,
        elapsedRealtime = elapsedRealtime,
    )

    suspend fun run(id: DebugProbeId): DebugProbeResult {
        val descriptor = descriptorProvider(id)
        val action = "debug-probe-${id.actionSuffix()}"
        val startedAt = elapsedRealtime()
        recorder.record(
            area = descriptor.area,
            action = action,
            outcome = DiagnosticOutcome.STARTED,
            safeDetails = mapOf("operation" to id.actionSuffix()),
        )

        return try {
            val planId = defaultPlanId()
            if (descriptor.requiresDefaultPlan && planId == null) {
                val blocked = DebugProbeResult(
                    id = id,
                    status = DebugProbeStatus.BLOCKED,
                    message = DebugProbeMessage.MISSING_DEFAULT_PLAN,
                    durationMillis = elapsedRealtime() - startedAt,
                    completedAtEpochMillis = clock(),
                ).sanitized()
                recordTerminal(descriptor, action, blocked)
                blocked
            } else {
                val result = withTimeout(descriptor.timeoutMillis) {
                    withContext(executionContext) {
                        operations.execute(id, planId)
                    }
                }.copy(
                    durationMillis = elapsedRealtime() - startedAt,
                    completedAtEpochMillis = clock(),
                ).sanitized()
                recordTerminal(descriptor, action, result)
                result
            }
        } catch (timeout: TimeoutCancellationException) {
            val result = terminalFailure(
                id = id,
                status = DebugProbeStatus.FAILED,
                startedAt = startedAt,
                failure = timeout,
                message = DebugProbeMessage.PROBE_TIMED_OUT,
            )
            recordTerminal(descriptor, action, result)
            result
        } catch (cancelled: CancellationException) {
            val result = terminalFailure(
                id = id,
                status = DebugProbeStatus.CANCELLED,
                startedAt = startedAt,
                failure = cancelled,
            )
            recordTerminal(descriptor, action, result)
            throw cancelled
        } catch (failure: Exception) {
            val result = terminalFailure(
                id = id,
                status = DebugProbeStatus.FAILED,
                startedAt = startedAt,
                failure = failure,
            )
            recordTerminal(descriptor, action, result)
            result
        }
    }

    private fun recordTerminal(
        descriptor: DebugProbeDescriptor,
        action: String,
        result: DebugProbeResult,
    ) {
        recorder.record(
            area = descriptor.area,
            action = action,
            outcome = result.status.toDiagnosticOutcome(),
            severity = result.status.toDiagnosticSeverity(),
            durationMillis = result.durationMillis,
            safeDetails = result.safeFacts,
            throwable = result.exception?.let(::RecordedProbeException),
        )
    }

    private fun terminalFailure(
        id: DebugProbeId,
        status: DebugProbeStatus,
        startedAt: Long,
        failure: Throwable,
        message: DebugProbeMessage = DebugProbeMessage.NONE,
    ): DebugProbeResult = DebugProbeResult(
        id = id,
        status = status,
        message = message,
        durationMillis = elapsedRealtime() - startedAt,
        completedAtEpochMillis = clock(),
        exception = DiagnosticRedactor.summarizeThrowable(failure),
    ).sanitized()
}

private class RecordedProbeException(
    summary: com.mikeyphw.studyflow.core.diagnostics.DiagnosticExceptionSummary,
) : RuntimeException("${summary.type}:${summary.fingerprint}")

private class AndroidStudyFlowDebugProbeOperations(
    context: Context,
    private val container: AppContainer,
) : StudyFlowDebugProbeOperations {
    private val appContext = context.applicationContext

    override suspend fun execute(id: DebugProbeId, planId: StudyPlanId?): DebugProbeResult = when (id) {
        DebugProbeId.RECORDER_HEALTH -> recorderHealth(id)
        DebugProbeId.NOTIFICATION_HEALTH -> notificationHealth(id)
        DebugProbeId.WIDGET_HEALTH -> widgetHealth(id)
        DebugProbeId.PROCESS_EXIT_HISTORY -> processExitHealth(id)
        DebugProbeId.BACKUP_CONFIGURATION -> backupConfigurationHealth(id)
        DebugProbeId.FILE_PROVIDER_CONFIGURATION -> fileProviderHealth(id)
        DebugProbeId.REMINDER_RECONCILIATION -> reminderReconciliation(id, requireNotNull(planId))
        DebugProbeId.WIDGET_REFRESH -> widgetRefresh(id)
        DebugProbeId.BACKUP_DRY_RUN -> backupDryRun(id, requireNotNull(planId))
        DebugProbeId.SEARCH_QUERY -> searchQuery(id)
        DebugProbeId.RECOMMENDATION_QUERY -> recommendationQuery(id, requireNotNull(planId))
        DebugProbeId.SYNC_FOUNDATION -> syncFoundation(id)
    }

    private fun recorderHealth(id: DebugProbeId): DebugProbeResult {
        val recorder = container.diagnosticRecorder
        return DebugProbeResult(
            id = id,
            status = when {
                recorder.writerFailureFingerprint != null -> DebugProbeStatus.FAILED
                recorder.droppedEventCount > 0L -> DebugProbeStatus.WARNING
                recorder.enabled -> DebugProbeStatus.PASSED
                else -> DebugProbeStatus.BLOCKED
            },
            safeFacts = mapOf(
                "enabled" to recorder.enabled.toString(),
                "count" to recorder.droppedEventCount.toString(),
            ),
        )
    }

    private suspend fun notificationHealth(id: DebugProbeId): DebugProbeResult = runInterruptible {
        val managerCompat = NotificationManagerCompat.from(appContext)
        val appEnabled = managerCompat.areNotificationsEnabled()
        val permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(StudyReminderChannelId)
        } else {
            null
        }
        val channelImportance = notificationChannelImportance(channel)
        DebugProbePlatformPolicy.notification(
            id = id,
            snapshot = NotificationHealthSnapshot(
                permissionGranted = permissionGranted,
                appNotificationsEnabled = appEnabled,
                channelRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                channelPresent = channel != null,
                channelBlocked = channelImportance == NotificationImportanceNone,
                channelImportance = channelImportance,
                apiLevel = Build.VERSION.SDK_INT,
            ),
        )
    }

    @SuppressLint("NewApi")
    private fun notificationChannelImportance(notificationChannel: android.app.NotificationChannel?): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationChannel?.importance
        } else {
            null
        }

    private suspend fun widgetHealth(id: DebugProbeId): DebugProbeResult = runInterruptible {
        val manager = AppWidgetManager.getInstance(appContext)
        val packageManager = appContext.packageManager
        val component = ComponentName(appContext, StudyFlowTodayWidgetProvider::class.java)
        val receiver = receiverInfo(packageManager, component)
        val componentState = packageManager.getComponentEnabledSetting(component)
        val componentDisabled = componentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            componentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            componentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        val providerEnabled = receiver?.enabled == true && !componentDisabled
        val metadataPresent = receiver?.metaData?.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER, 0) != 0
        val registered = receiver != null
        val supported = packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)
        val ids = if (supported && registered && metadataPresent) {
            manager.getAppWidgetIds(component)
        } else {
            intArrayOf()
        }
        DebugProbePlatformPolicy.widget(
            id = id,
            snapshot = WidgetHealthSnapshot(
                deviceSupportsWidgets = supported,
                providerRegistered = registered,
                providerEnabled = providerEnabled,
                providerMetadataPresent = metadataPresent,
                boundInstanceCount = ids.size,
            ),
        )
    }

    private suspend fun processExitHealth(id: DebugProbeId): DebugProbeResult = runInterruptible {
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val reports = if (supported) {
            appContext.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(appContext.packageName, 0, 8)
        } else {
            emptyList()
        }
        val crashOrAnrReasons = setOf(
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR,
        )
        val abnormalReasons = crashOrAnrReasons + setOf(
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            ApplicationExitInfo.REASON_MEMORY_LIMITER,
            ApplicationExitInfo.REASON_SIGNALED,
            ApplicationExitInfo.REASON_UNKNOWN,
        )
        DebugProbePlatformPolicy.processExit(
            id = id,
            snapshot = ProcessExitHealthSnapshot(
                apiSupported = supported,
                recentExitCount = reports.size,
                crashOrAnrCount = reports.count { it.reason in crashOrAnrReasons },
                abnormalExitCount = reports.count { it.reason in abnormalReasons },
                lowMemoryCount = reports.count { it.reason == ApplicationExitInfo.REASON_LOW_MEMORY },
                apiLevel = Build.VERSION.SDK_INT,
            ),
        )
    }

    private suspend fun backupConfigurationHealth(id: DebugProbeId): DebugProbeResult = runInterruptible {
        val appInfo = appContext.applicationInfo
        DebugProbePlatformPolicy.backup(
            id = id,
            snapshot = BackupConfigurationSnapshot(
                backupAllowed = appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0,
                expectedBackupAllowed = false,
                legacyRuleLinked = BuildConfig.BACKUP_LEGACY_RULE_LINKED,
                modernRuleLinked = BuildConfig.BACKUP_MODERN_RULE_LINKED,
                legacyRulePresent = BuildConfig.BACKUP_LEGACY_RULE_LINKED,
                modernRulePresent = BuildConfig.BACKUP_MODERN_RULE_LINKED,
            ),
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun fileProviderHealth(id: DebugProbeId): DebugProbeResult = runInterruptible {
        val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
        val provider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.resolveContentProvider(
                authority,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            appContext.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)
        }
        val allowed = File(appContext.cacheDir, "support-bundles/provider-canary.txt").apply {
            parentFile?.mkdirs()
            writeText("provider-canary")
        }
        val privateFile = File(appContext.cacheDir, "provider-private-canary.txt").apply {
            writeText("private-canary")
        }
        val allowedGranted = try {
            FileProvider.getUriForFile(appContext, authority, allowed)
            true
        } catch (_: Exception) {
            false
        }
        val privateRejected = try {
            FileProvider.getUriForFile(appContext, authority, privateFile)
            false
        } catch (_: IllegalArgumentException) {
            true
        } catch (_: Exception) {
            false
        }
        allowed.delete()
        privateFile.delete()
        DebugProbePlatformPolicy.fileProvider(
            id = id,
            snapshot = FileProviderHealthSnapshot(
                providerPresent = provider != null,
                providerExported = provider?.exported == true,
                grantsUriPermissions = provider?.grantUriPermissions == true,
                pathMetadataPresent = provider?.metaData
                    ?.getInt("android.support.FILE_PROVIDER_PATHS", 0) != 0,
                allowedRootGranted = allowedGranted,
                privateRootRejected = privateRejected,
            ),
        )
    }

    private suspend fun reminderReconciliation(id: DebugProbeId, planId: StudyPlanId): DebugProbeResult {
        container.reminderDeliveryCoordinator.reconcilePlan(planId)
        return DebugProbeResult(id = id, status = DebugProbeStatus.PASSED)
    }

    private suspend fun widgetRefresh(id: DebugProbeId): DebugProbeResult {
        val refresh = StudyFlowWidgetUpdateCoordinator.refreshAllAndAwait(appContext)
        val observed = refresh.boundInstanceCount > 0
        return DebugProbeResult(
            id = id,
            status = if (observed) DebugProbeStatus.PASSED else DebugProbeStatus.BLOCKED,
            message = if (observed) DebugProbeMessage.NONE else DebugProbeMessage.NO_WIDGET_INSTANCES,
            safeFacts = mapOf(
                "count" to refresh.boundInstanceCount.toString(),
                "behavior_verified" to observed.toString(),
                "status" to refresh.state,
            ),
        )
    }

    private suspend fun backupDryRun(id: DebugProbeId, planId: StudyPlanId): DebugProbeResult {
        val packageFile = File.createTempFile("debug-backup-canary-", ".studyflow", appContext.cacheDir)
        return try {
            packageFile.outputStream().buffered().use { output ->
                container.manageStudyPlanPortability.exportPlan(
                    planId = planId,
                    options = StudyPlanExportOptions(
                        mode = StudyPlanExportMode.FULL,
                        includeAnnexMetadata = true,
                        includeAnnexPayloads = true,
                        metadataPrivacyMode = true,
                        redactNotes = true,
                        redactHistory = true,
                    ),
                    output = output,
                )
            }
            val integrity = packageFile.inputStream().buffered().use { input ->
                container.manageStudyPlanPortability.inspectBackup(input)
            }
            val dryRun = packageFile.inputStream().buffered().use { input ->
                container.manageStudyPlanPortability.dryRunImport(
                    input = input,
                    strategy = StudyPlanImportStrategy.CREATE_AS_NEW,
                )
            }
            val passed = integrity.canRestore && integrity.checksumVerified && dryRun.databaseRowsChanged == 0
            DebugProbeResult(
                id = id,
                status = if (passed) DebugProbeStatus.PASSED else DebugProbeStatus.FAILED,
                message = if (passed) DebugProbeMessage.NONE else DebugProbeMessage.BACKUP_DRY_RUN_INTEGRITY_FAILED,
                safeFacts = mapOf(
                    "count" to integrity.annexPayloadCount.toString(),
                    "enabled" to integrity.checksumVerified.toString(),
                    "behavior_verified" to passed.toString(),
                ),
            )
        } finally {
            packageFile.delete()
        }
    }

    private suspend fun searchQuery(id: DebugProbeId): DebugProbeResult {
        val plan = container.manageStudyPlan.plans.first().firstOrNull { it.name.trim().length >= 2 }
            ?: return DebugProbeResult(
                id = id,
                status = DebugProbeStatus.BLOCKED,
                message = DebugProbeMessage.NO_SEARCH_EVIDENCE,
            )
        val results = container.manageStudyDiscovery.search(plan.name)
        val verified = results.any { result ->
            result.type == com.mikeyphw.studyflow.core.domain.GlobalSearchContentType.PLAN &&
                result.studyPlanId == plan.id
        }
        return DebugProbeResult(
            id = id,
            status = if (verified) DebugProbeStatus.PASSED else DebugProbeStatus.FAILED,
            message = if (verified) DebugProbeMessage.NONE else DebugProbeMessage.SEARCH_CANARY_FAILED,
            safeFacts = mapOf(
                "count" to results.size.toString(),
                "behavior_verified" to verified.toString(),
            ),
        )
    }

    private suspend fun recommendationQuery(id: DebugProbeId, planId: StudyPlanId): DebugProbeResult {
        val results = container.manageStudyDiscovery.recommendations(planId)
        if (results.isEmpty()) {
            return DebugProbeResult(
                id = id,
                status = DebugProbeStatus.BLOCKED,
                message = DebugProbeMessage.NO_RECOMMENDATION_EVIDENCE,
                safeFacts = mapOf("count" to "0"),
            )
        }
        val verified = results.all { recommendation ->
            recommendation.studyPlanId == planId && recommendation.reasons.isNotEmpty()
        } && results.map { it.id }.distinct().size == results.size
        return DebugProbeResult(
            id = id,
            status = if (verified) DebugProbeStatus.PASSED else DebugProbeStatus.FAILED,
            message = if (verified) DebugProbeMessage.NONE else DebugProbeMessage.RECOMMENDATION_CANARY_FAILED,
            safeFacts = mapOf(
                "count" to results.size.toString(),
                "behavior_verified" to verified.toString(),
            ),
        )
    }

    private suspend fun syncFoundation(id: DebugProbeId): DebugProbeResult {
        val state = container.manageStudyCloudSync.syncState.first()
        val summary = container.manageStudyCloudSync.inspectLocalChanges()
        val canonicalReady = StudyLocalCheckpointReadinessAuditor.localCheckpointReady(state, summary)
        val verified = !state.checkpointReady || canonicalReady
        return DebugProbeResult(
            id = id,
            status = if (verified) DebugProbeStatus.PASSED else DebugProbeStatus.FAILED,
            message = if (verified) DebugProbeMessage.NONE else DebugProbeMessage.SYNC_CANARY_FAILED,
            safeFacts = mapOf(
                "count" to summary.planCount.toString(),
                "behavior_verified" to verified.toString(),
                "enabled" to state.enabled.toString(),
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun receiverInfo(
        packageManager: PackageManager,
        component: ComponentName,
    ) = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getReceiverInfo(
                component,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            packageManager.getReceiverInfo(component, PackageManager.GET_META_DATA)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

private fun DebugProbeId.actionSuffix(): String = name.lowercase().replace('_', '-')

private const val NotificationImportanceNone = 0

private fun DebugProbeStatus.toDiagnosticOutcome(): DiagnosticOutcome = when (this) {
    DebugProbeStatus.PASSED, DebugProbeStatus.WARNING -> DiagnosticOutcome.SUCCEEDED
    DebugProbeStatus.BLOCKED -> DiagnosticOutcome.BLOCKED
    DebugProbeStatus.CANCELLED -> DiagnosticOutcome.CANCELLED
    DebugProbeStatus.FAILED -> DiagnosticOutcome.FAILED
    DebugProbeStatus.NOT_RUN, DebugProbeStatus.RUNNING -> DiagnosticOutcome.STARTED
}

private fun DebugProbeStatus.toDiagnosticSeverity(): DiagnosticSeverity = when (this) {
    DebugProbeStatus.PASSED -> DiagnosticSeverity.INFO
    DebugProbeStatus.WARNING, DebugProbeStatus.BLOCKED, DebugProbeStatus.CANCELLED -> DiagnosticSeverity.WARNING
    DebugProbeStatus.FAILED -> DiagnosticSeverity.ERROR
    DebugProbeStatus.NOT_RUN, DebugProbeStatus.RUNNING -> DiagnosticSeverity.TRACE
}
