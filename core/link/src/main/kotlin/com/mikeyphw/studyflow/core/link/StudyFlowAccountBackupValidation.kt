package com.mikeyphw.studyflow.core.link

import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val DefaultAccountHandoffTtlMillis = 600_000L
private const val DefaultBackupBundleTtlMillis = 3_600_000L
private val accountBackupWireTokenPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
private val accountBackupSha256Pattern = Regex("[a-f0-9]{64}")

@Serializable
@JvmInline
value class StudyFlowAccountHandoffId(val value: String) {
    init { require(accountBackupWireTokenPattern.matches(value)) { "Account handoff ID must be a bounded wire token" } }
}

@Serializable
@JvmInline
value class StudyFlowBackupBundleId(val value: String) {
    init { require(accountBackupWireTokenPattern.matches(value)) { "Backup bundle ID must be a bounded wire token" } }
}

@Serializable
@JvmInline
value class StudyFlowBackupRestoreId(val value: String) {
    init { require(accountBackupWireTokenPattern.matches(value)) { "Backup restore ID must be a bounded wire token" } }
}

@Serializable
@JvmInline
value class StudyFlowReleaseValidationRunId(val value: String) {
    init {
        require(accountBackupWireTokenPattern.matches(value)) {
            "Release validation run ID must be a bounded wire token"
        }
    }
}

@Serializable
enum class StudyFlowAccountHandoffPurpose {
    @SerialName("device_replacement") DEVICE_REPLACEMENT,
    @SerialName("desktop_recovery") DESKTOP_RECOVERY,
    @SerialName("backup_restore") BACKUP_RESTORE,
    @SerialName("audit") AUDIT,
}

@Serializable
enum class StudyFlowAccountHandoffStatus {
    @SerialName("accepted") ACCEPTED,
    @SerialName("rejected") REJECTED,
    @SerialName("duplicate") DUPLICATE,
    @SerialName("expired") EXPIRED,
}

@Serializable
enum class StudyFlowBackupBundleKind {
    @SerialName("full_workspace") FULL_WORKSPACE,
    @SerialName("trusted_devices") TRUSTED_DEVICES,
    @SerialName("settings_only") SETTINGS_ONLY,
    @SerialName("journal_only") JOURNAL_ONLY,
}

@Serializable
enum class StudyFlowBackupRestoreMode {
    @SerialName("dry_run") DRY_RUN,
    @SerialName("restore") RESTORE,
    @SerialName("verify_only") VERIFY_ONLY,
}

@Serializable
enum class StudyFlowBackupRestoreStatus {
    @SerialName("accepted") ACCEPTED,
    @SerialName("rejected") REJECTED,
    @SerialName("duplicate") DUPLICATE,
    @SerialName("expired") EXPIRED,
}

@Serializable
enum class StudyFlowReleaseValidationScope {
    @SerialName("local_static") LOCAL_STATIC,
    @SerialName("gradle_tasks") GRADLE_TASKS,
    @SerialName("web_runtime") WEB_RUNTIME,
    @SerialName("android_package") ANDROID_PACKAGE,
    @SerialName("full_release") FULL_RELEASE,
}

@Serializable
enum class StudyFlowReleaseValidationStatus {
    @SerialName("planned") PLANNED,
    @SerialName("passed") PASSED,
    @SerialName("failed") FAILED,
    @SerialName("skipped") SKIPPED,
}

object StudyFlowAccountBackupMessageNames {
    const val AccountHandoffOffer = "account.handoff_offer"
    const val AccountHandoffAccept = "account.handoff_accept"
    const val AccountHandoffReject = "account.handoff_reject"
    const val BackupBundleOffer = "backup.bundle_offer"
    const val BackupRestorePlan = "backup.restore_plan"
    const val BackupRestoreAck = "backup.restore_ack"
    const val ReleaseValidationPlan = "release.validation_plan"
    const val ReleaseValidationResult = "release.validation_result"
}

@Serializable
data class StudyFlowAccountHandoffOffer(
    val handoffId: StudyFlowAccountHandoffId,
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDevice: StudyFlowLinkPeerDescriptor,
    val targetDeviceId: StudyFlowDeviceId? = null,
    val purpose: StudyFlowAccountHandoffPurpose,
    val backupBundleId: StudyFlowBackupBundleId? = null,
    val manifestSha256: String? = null,
    val requiredCapabilities: List<StudyFlowLinkCapability> = listOf(
        StudyFlowLinkCapabilities.WorkspaceSync,
        StudyFlowLinkCapabilities.BackupImport,
    ),
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long = createdAtEpochMillis + DefaultAccountHandoffTtlMillis,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(requiredCapabilities.isNotEmpty()) { "Account handoff requires capabilities" }
        require(requiredCapabilities.distinct().size == requiredCapabilities.size) {
            "Account handoff capabilities must be unique"
        }
        require(manifestSha256 == null || accountBackupSha256Pattern.matches(manifestSha256)) {
            "Account handoff manifest hash must be SHA-256 hex"
        }
        require(createdAtEpochMillis >= 0L) { "Account handoff creation time must not be negative" }
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Account handoff must expire after creation" }
    }

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis > expiresAtEpochMillis

    fun accepts(target: StudyFlowLinkPeerDescriptor, nowEpochMillis: Long): Boolean {
        if (isExpired(nowEpochMillis)) return false
        if (targetDeviceId != null && targetDeviceId != target.deviceId) return false
        if (requiredCapabilities.any { it !in target.capabilities }) return false
        val tvClass = target.clientKind == StudyFlowLinkClientKind.ANDROID_TV ||
            target.clientKind == StudyFlowLinkClientKind.WEB_TV
        if (tvClass && purpose != StudyFlowAccountHandoffPurpose.AUDIT) return false
        return true
    }

    fun toDomainOperation(
        entityId: StudyFlowEntityId,
        entityRevision: StudyFlowEntityRevision,
        originSequence: Long,
        logicalTimestamp: Long,
        occurredAtEpochMillis: Long,
    ): StudyFlowDomainOperation = StudyFlowDomainOperation(
        operationId = StudyFlowOperationId("op-${handoffId.value}"),
        workspaceId = workspaceId,
        originDeviceId = sourceDevice.deviceId,
        originSequence = originSequence,
        entityId = entityId,
        entityRevision = entityRevision,
        logicalTimestamp = logicalTimestamp,
        operationType = StudyFlowDomainOperationTypes.AccountHandoffAccepted,
        occurredAtEpochMillis = occurredAtEpochMillis,
        payload = JsonObject(
            mapOf(
                "accountHandoffId" to JsonPrimitive(handoffId.value),
                "purpose" to JsonPrimitive(purpose.name.lowercase()),
                "accountBackupHandoff" to JsonPrimitive("sflp22"),
            ),
        ),
    )
}

@Serializable
data class StudyFlowAccountHandoffDelivery(
    val messageId: StudyFlowMessageId,
    val sourceDeviceId: StudyFlowDeviceId,
    val targetDeviceId: StudyFlowDeviceId,
    val sequence: Long,
    val offer: StudyFlowAccountHandoffOffer,
    val sentAtEpochMillis: Long,
) {
    init {
        require(sequence >= 1L) { "Account handoff sequence must be positive" }
        require(sourceDeviceId != targetDeviceId) { "Account handoff cannot target source" }
        require(sentAtEpochMillis >= 0L) { "Account handoff sent time must not be negative" }
    }
}

@Serializable
data class StudyFlowAccountHandoffAck(
    val messageId: StudyFlowMessageId,
    val handoffId: StudyFlowAccountHandoffId,
    val targetDeviceId: StudyFlowDeviceId,
    val acknowledgedSequence: Long,
    val status: StudyFlowAccountHandoffStatus,
    val reason: String? = null,
    val acknowledgedAtEpochMillis: Long,
) {
    init {
        require(acknowledgedSequence >= 1L) { "Account handoff acknowledgement sequence must be positive" }
        require(acknowledgedAtEpochMillis >= 0L) { "Account handoff acknowledgement time must not be negative" }
        require(status != StudyFlowAccountHandoffStatus.REJECTED || !reason.isNullOrBlank()) {
            "Rejected account handoffs require a reason"
        }
    }
}

class StudyFlowAccountHandoffReducer(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val acceptedHandoffs = mutableSetOf<StudyFlowAccountHandoffId>()
    private val sourceFrontier = mutableMapOf<StudyFlowDeviceId, Long>()

    fun apply(delivery: StudyFlowAccountHandoffDelivery): StudyFlowAccountHandoffAck {
        if (delivery.targetDeviceId != localDevice.deviceId) {
            return delivery.ack(StudyFlowAccountHandoffStatus.REJECTED, "wrong_target")
        }
        if (delivery.offer.isExpired(clock.nowEpochMillis())) {
            return delivery.ack(StudyFlowAccountHandoffStatus.EXPIRED, "expired")
        }
        if (delivery.offer.sourceDevice.deviceId != delivery.sourceDeviceId) {
            return delivery.ack(StudyFlowAccountHandoffStatus.REJECTED, "wrong_source")
        }
        if (delivery.offer.targetDeviceId != null && delivery.offer.targetDeviceId != delivery.targetDeviceId) {
            return delivery.ack(StudyFlowAccountHandoffStatus.REJECTED, "inner_target_mismatch")
        }
        if (!delivery.offer.accepts(localDevice, clock.nowEpochMillis())) {
            return delivery.ack(StudyFlowAccountHandoffStatus.REJECTED, "missing_capability")
        }
        if (delivery.offer.handoffId in acceptedHandoffs) {
            return delivery.ack(StudyFlowAccountHandoffStatus.DUPLICATE, "duplicate")
        }
        val last = sourceFrontier[delivery.sourceDeviceId] ?: 0L
        if (delivery.sequence <= last) return delivery.ack(StudyFlowAccountHandoffStatus.REJECTED, "stale_sequence")
        sourceFrontier[delivery.sourceDeviceId] = delivery.sequence
        acceptedHandoffs += delivery.offer.handoffId
        return delivery.ack(StudyFlowAccountHandoffStatus.ACCEPTED)
    }

    private fun StudyFlowAccountHandoffDelivery.ack(
        status: StudyFlowAccountHandoffStatus,
        reason: String? = null,
    ) = StudyFlowAccountHandoffAck(
        messageId = messageId,
        handoffId = offer.handoffId,
        targetDeviceId = localDevice.deviceId,
        acknowledgedSequence = sequence,
        status = status,
        reason = reason,
        acknowledgedAtEpochMillis = clock.nowEpochMillis(),
    )
}

@Serializable
data class StudyFlowBackupBundleManifest(
    val bundleId: StudyFlowBackupBundleId,
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDevice: StudyFlowLinkPeerDescriptor,
    val targetDeviceId: StudyFlowDeviceId? = null,
    val kind: StudyFlowBackupBundleKind,
    val totalBytes: Long,
    val itemCount: Int,
    val contentSha256: String,
    val encryptionMode: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long? = createdAtEpochMillis + DefaultBackupBundleTtlMillis,
    val requiredRestoreCapabilities: List<StudyFlowLinkCapability> = listOf(StudyFlowLinkCapabilities.BackupImport),
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(totalBytes >= 0L) { "Backup bundle size must not be negative" }
        require(itemCount >= 0) { "Backup bundle item count must not be negative" }
        require(accountBackupSha256Pattern.matches(contentSha256)) { "Backup bundle hash must be SHA-256 hex" }
        require(encryptionMode.isNotBlank()) { "Backup bundle encryption mode must be explicit" }
        require(createdAtEpochMillis >= 0L) { "Backup bundle creation time must not be negative" }
        require(expiresAtEpochMillis == null || expiresAtEpochMillis > createdAtEpochMillis) {
            "Backup bundle expiry must be after creation"
        }
        require(requiredRestoreCapabilities.distinct().size == requiredRestoreCapabilities.size) {
            "Backup restore capabilities must be unique"
        }
    }

    fun isExpired(nowEpochMillis: Long): Boolean = expiresAtEpochMillis != null && nowEpochMillis > expiresAtEpochMillis

    fun canBeRestoredBy(
        target: StudyFlowLinkPeerDescriptor,
        mode: StudyFlowBackupRestoreMode,
        nowEpochMillis: Long,
    ): Boolean {
        if (isExpired(nowEpochMillis)) return false
        if (targetDeviceId != null && targetDeviceId != target.deviceId) return false
        val tvClass = target.clientKind == StudyFlowLinkClientKind.ANDROID_TV ||
            target.clientKind == StudyFlowLinkClientKind.WEB_TV
        if (tvClass && mode != StudyFlowBackupRestoreMode.VERIFY_ONLY) return false
        return requiredRestoreCapabilities.all { it in target.capabilities }
    }

    fun toBundleOperation(entityRevision: StudyFlowEntityRevision, logicalTimestamp: Long): StudyFlowDomainOperation =
        StudyFlowDomainOperation(
            operationId = StudyFlowOperationId("op-${bundleId.value}"),
            workspaceId = workspaceId,
            originDeviceId = sourceDevice.deviceId,
            originSequence = 1L,
            entityId = StudyFlowEntityId("backup-${bundleId.value}"),
            entityRevision = entityRevision,
            logicalTimestamp = logicalTimestamp,
            operationType = StudyFlowDomainOperationTypes.BackupBundleOffered,
            occurredAtEpochMillis = createdAtEpochMillis,
            payload = JsonObject(
                mapOf(
                    "backupBundleId" to JsonPrimitive(bundleId.value),
                    "bundleKind" to JsonPrimitive(kind.name.lowercase()),
                    "accountBackupHandoff" to JsonPrimitive("sflp22"),
                ),
            ),
        )
}

@Serializable
data class StudyFlowBackupRestorePlan(
    val restoreId: StudyFlowBackupRestoreId,
    val bundle: StudyFlowBackupBundleManifest,
    val targetDeviceId: StudyFlowDeviceId,
    val mode: StudyFlowBackupRestoreMode,
    val sequence: Long,
    val createdAtEpochMillis: Long,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(sequence >= 1L) { "Backup restore sequence must be positive" }
        require(createdAtEpochMillis >= 0L) { "Backup restore plan time must not be negative" }
    }

    fun accepts(target: StudyFlowLinkPeerDescriptor, nowEpochMillis: Long): Boolean {
        if (target.deviceId != targetDeviceId) return false
        return bundle.canBeRestoredBy(target, mode, nowEpochMillis)
    }

    fun toDomainOperation(entityRevision: StudyFlowEntityRevision, logicalTimestamp: Long): StudyFlowDomainOperation =
        StudyFlowDomainOperation(
            operationId = StudyFlowOperationId("op-${restoreId.value}"),
            workspaceId = bundle.workspaceId,
            originDeviceId = targetDeviceId,
            originSequence = sequence,
            entityId = StudyFlowEntityId("backup-${bundle.bundleId.value}"),
            entityRevision = entityRevision,
            logicalTimestamp = logicalTimestamp,
            operationType = StudyFlowDomainOperationTypes.BackupRestorePlanned,
            occurredAtEpochMillis = createdAtEpochMillis,
            payload = JsonObject(
                mapOf(
                    "backupRestoreId" to JsonPrimitive(restoreId.value),
                    "mode" to JsonPrimitive(mode.name.lowercase()),
                    "accountBackupHandoff" to JsonPrimitive("sflp22"),
                ),
            ),
        )
}

@Serializable
data class StudyFlowBackupRestoreAck(
    val restoreId: StudyFlowBackupRestoreId,
    val bundleId: StudyFlowBackupBundleId,
    val targetDeviceId: StudyFlowDeviceId,
    val acknowledgedSequence: Long,
    val status: StudyFlowBackupRestoreStatus,
    val reason: String? = null,
    val acknowledgedAtEpochMillis: Long,
) {
    init {
        require(acknowledgedSequence >= 1L) { "Backup restore acknowledgement sequence must be positive" }
        require(acknowledgedAtEpochMillis >= 0L) { "Backup restore acknowledgement time must not be negative" }
        require(status != StudyFlowBackupRestoreStatus.REJECTED || !reason.isNullOrBlank()) {
            "Rejected backup restores require a reason"
        }
    }
}

class StudyFlowBackupRestoreReducer(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val restored = mutableSetOf<StudyFlowBackupRestoreId>()
    private var lastSequence = 0L

    fun apply(plan: StudyFlowBackupRestorePlan): StudyFlowBackupRestoreAck {
        if (plan.bundle.targetDeviceId != null && plan.bundle.targetDeviceId != plan.targetDeviceId) {
            return plan.ack(StudyFlowBackupRestoreStatus.REJECTED, "inner_target_mismatch")
        }
        if (!plan.accepts(localDevice, clock.nowEpochMillis())) {
            return plan.ack(StudyFlowBackupRestoreStatus.REJECTED, "not_accepted")
        }
        if (plan.bundle.isExpired(clock.nowEpochMillis())) {
            return plan.ack(StudyFlowBackupRestoreStatus.EXPIRED, "expired")
        }
        if (plan.restoreId in restored) return plan.ack(StudyFlowBackupRestoreStatus.DUPLICATE, "duplicate")
        if (plan.sequence <= lastSequence) return plan.ack(StudyFlowBackupRestoreStatus.REJECTED, "stale_sequence")
        lastSequence = plan.sequence
        restored += plan.restoreId
        return plan.ack(StudyFlowBackupRestoreStatus.ACCEPTED)
    }

    private fun StudyFlowBackupRestorePlan.ack(
        status: StudyFlowBackupRestoreStatus,
        reason: String? = null,
    ) = StudyFlowBackupRestoreAck(
        restoreId = restoreId,
        bundleId = bundle.bundleId,
        targetDeviceId = localDevice.deviceId,
        acknowledgedSequence = sequence,
        status = status,
        reason = reason,
        acknowledgedAtEpochMillis = clock.nowEpochMillis(),
    )
}

@Serializable
data class StudyFlowReleaseValidationTask(
    val name: String,
    val scope: StudyFlowReleaseValidationScope,
    val command: String,
    val required: Boolean = true,
    val sourcePaths: List<String> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "Validation task name must not be blank" }
        require(command.isNotBlank()) { "Validation task command must not be blank" }
        require(sourcePaths.distinct().size == sourcePaths.size) { "Validation task source paths must be unique" }
    }
}

@Serializable
data class StudyFlowReleaseValidationPlan(
    val runId: StudyFlowReleaseValidationRunId,
    val workspaceId: StudyFlowWorkspaceId,
    val tasks: List<StudyFlowReleaseValidationTask>,
    val requiredArtifacts: List<String>,
    val createdAtEpochMillis: Long,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(tasks.isNotEmpty()) { "Release validation plan must contain tasks" }
        require(tasks.map { it.name }.distinct().size == tasks.size) { "Release validation tasks must be unique" }
        require(requiredArtifacts.distinct().size == requiredArtifacts.size) { "Release artifacts must be unique" }
        require(createdAtEpochMillis >= 0L) { "Release validation plan time must not be negative" }
    }

    fun requiredTaskNames(): List<String> = tasks.filter { it.required }.map { it.name }

    fun containsCompleteSflpChain(): Boolean {
        val names = requiredTaskNames().toSet()
        return StudyFlowSflp23ReleaseGate.RequiredSflpTasks.all { it in names }
    }
}

@Serializable
data class StudyFlowReleaseValidationResult(
    val runId: StudyFlowReleaseValidationRunId,
    val taskName: String,
    val status: StudyFlowReleaseValidationStatus,
    val passedCount: Int = 0,
    val failedCount: Int = 0,
    val warningCount: Int = 0,
    val completedAtEpochMillis: Long,
) {
    init {
        require(taskName.isNotBlank()) { "Release validation task name must not be blank" }
        require(passedCount >= 0) { "Passed count must not be negative" }
        require(failedCount >= 0) { "Failed count must not be negative" }
        require(warningCount >= 0) { "Warning count must not be negative" }
        require(completedAtEpochMillis >= 0L) { "Release validation completion time must not be negative" }
    }

    fun toDomainOperation(
        workspaceId: StudyFlowWorkspaceId,
        originDeviceId: StudyFlowDeviceId,
        entityRevision: StudyFlowEntityRevision,
        logicalTimestamp: Long,
    ): StudyFlowDomainOperation = StudyFlowDomainOperation(
        operationId = StudyFlowOperationId("op-${runId.value}-${taskName.replace(':', '-')}"),
        workspaceId = workspaceId,
        originDeviceId = originDeviceId,
        originSequence = 1L,
        entityId = StudyFlowEntityId("release-${runId.value}"),
        entityRevision = entityRevision,
        logicalTimestamp = logicalTimestamp,
        operationType = StudyFlowDomainOperationTypes.ReleaseValidationRecorded,
        occurredAtEpochMillis = completedAtEpochMillis,
        payload = JsonObject(
            mapOf(
                "taskName" to JsonPrimitive(taskName),
                "status" to JsonPrimitive(status.name.lowercase()),
                "releaseValidationConsolidation" to JsonPrimitive("sflp23"),
            ),
        ),
    )
}

object StudyFlowSflp23ReleaseGate {
    val RequiredSflpTasks: List<String> = listOf(
        "verifySflp22Sflp23AccountBackupValidation",
        "verifySflp20Sflp21ShareImportConflict",
        "verifySflp18Sflp19NotificationMediaSync",
        "verifySflp16Sflp17ControlInputDelegation",
        "verifySflp14Sflp15LiveSessionProjection",
        "verifySflp12Sflp13WebTvHandoff",
        "verifySflp10Sflp11WebTvGeneralization",
        "verifySflp08Sflp09AndroidDesktopIntegration",
        "verifySflp06Sflp07TopologyOfflineQueue",
        "verifySflp04Sflp05IdentityTransport",
        "verifySflp02Sflp03JournalReconciliation",
        "verifySflp01ProtocolBaseline",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )

    fun defaultFinalPlan(
        workspaceId: StudyFlowWorkspaceId = StudyFlowWorkspaceId("release-sflp-v1"),
        createdAtEpochMillis: Long,
    ): StudyFlowReleaseValidationPlan = StudyFlowReleaseValidationPlan(
        runId = StudyFlowReleaseValidationRunId("sflp23-final-release"),
        workspaceId = workspaceId,
        tasks = RequiredSflpTasks.map { taskName ->
            StudyFlowReleaseValidationTask(
                name = taskName,
                scope = when {
                    taskName.startsWith(":app") -> StudyFlowReleaseValidationScope.ANDROID_PACKAGE
                    taskName.startsWith(":desktop") -> StudyFlowReleaseValidationScope.GRADLE_TASKS
                    taskName.contains("Web") -> StudyFlowReleaseValidationScope.WEB_RUNTIME
                    else -> StudyFlowReleaseValidationScope.FULL_RELEASE
                },
                command = taskName,
                required = true,
            )
        },
        requiredArtifacts = listOf(
            ".devtool-artifact.json",
            "protocol/sflp/v1/sflp-account-backup-release.schema.json",
            "protocol/sflp/v1/golden/account-backup-release-plan.json",
        ),
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

object StudyFlowAccountBackupDefaults {
    fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    fun handoffCapabilitiesFor(
        purpose: StudyFlowAccountHandoffPurpose,
    ): List<StudyFlowLinkCapability> = when (purpose) {
        StudyFlowAccountHandoffPurpose.AUDIT -> listOf(StudyFlowLinkCapabilities.WorkspaceSync)
        StudyFlowAccountHandoffPurpose.DEVICE_REPLACEMENT,
        StudyFlowAccountHandoffPurpose.DESKTOP_RECOVERY,
        StudyFlowAccountHandoffPurpose.BACKUP_RESTORE,
        -> listOf(StudyFlowLinkCapabilities.WorkspaceSync, StudyFlowLinkCapabilities.BackupImport)
    }
}
