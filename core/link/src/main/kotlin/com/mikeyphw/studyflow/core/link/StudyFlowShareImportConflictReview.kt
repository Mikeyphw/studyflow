package com.mikeyphw.studyflow.core.link

import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val DefaultShareImportTtlMillis = 300_000L
private const val DefaultConflictReviewTtlMillis = 86_400_000L
private val shareImportWireTokenPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
private val shareImportSha256Pattern = Regex("[a-f0-9]{64}")

@Serializable
@JvmInline
value class StudyFlowShareImportId(val value: String) {
    init { require(shareImportWireTokenPattern.matches(value)) { "Share/import ID must be a bounded wire token" } }
}

@Serializable
@JvmInline
value class StudyFlowConflictReviewId(val value: String) {
    init { require(shareImportWireTokenPattern.matches(value)) { "Conflict review ID must be a bounded wire token" } }
}

@Serializable
@JvmInline
value class StudyFlowConflictResolutionId(val value: String) {
    init {
        require(shareImportWireTokenPattern.matches(value)) {
            "Conflict resolution ID must be a bounded wire token"
        }
    }
}

@Serializable
enum class StudyFlowShareImportIntent {
    @SerialName("clipboard_preview") CLIPBOARD_PREVIEW,
    @SerialName("clipboard_apply") CLIPBOARD_APPLY,
    @SerialName("share_preview") SHARE_PREVIEW,
    @SerialName("share_import") SHARE_IMPORT,
}

@Serializable
enum class StudyFlowShareImportPayloadKind {
    @SerialName("plain_text") PLAIN_TEXT,
    @SerialName("markdown") MARKDOWN,
    @SerialName("url") URL,
    @SerialName("html") HTML,
    @SerialName("json") JSON,
    @SerialName("csv") CSV,
    @SerialName("pdf") PDF,
    @SerialName("image") IMAGE,
    @SerialName("opaque") OPAQUE,
}

@Serializable
enum class StudyFlowShareImportStatus {
    @SerialName("accepted") ACCEPTED,
    @SerialName("rejected") REJECTED,
    @SerialName("duplicate") DUPLICATE,
    @SerialName("expired") EXPIRED,
}

@Serializable
enum class StudyFlowConflictFieldKind {
    @SerialName("content") CONTENT,
    @SerialName("schedule") SCHEDULE,
    @SerialName("tags") TAGS,
    @SerialName("answer") ANSWER,
    @SerialName("note") NOTE,
    @SerialName("metadata") METADATA,
}

@Serializable
enum class StudyFlowConflictResolutionKind {
    @SerialName("keep_local") KEEP_LOCAL,
    @SerialName("take_remote") TAKE_REMOTE,
    @SerialName("merge") MERGE,
    @SerialName("skip") SKIP,
}

@Serializable
enum class StudyFlowConflictReviewStatus {
    @SerialName("open") OPEN,
    @SerialName("resolved") RESOLVED,
    @SerialName("duplicate") DUPLICATE,
    @SerialName("rejected") REJECTED,
}

@Serializable
data class StudyFlowShareImportOffer(
    val importId: StudyFlowShareImportId,
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDevice: StudyFlowLinkPeerDescriptor,
    val targetDeviceId: StudyFlowDeviceId? = null,
    val intent: StudyFlowShareImportIntent,
    val payloadKind: StudyFlowShareImportPayloadKind,
    val title: String,
    val mimeType: String? = null,
    val previewText: String? = null,
    val contentSha256: String,
    val contentBytesBase64: String? = null,
    val requiredCapabilities: List<StudyFlowLinkCapability>,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long = createdAtEpochMillis + DefaultShareImportTtlMillis,
    val metadata: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(title.isNotBlank()) { "Share/import title must not be blank" }
        require(mimeType == null || mimeType.contains('/')) { "Share/import MIME type must be explicit" }
        require(previewText == null || previewText.length <= 16_384) { "Share/import preview must be bounded" }
        require(shareImportSha256Pattern.matches(contentSha256)) { "Share/import content hash must be SHA-256 hex" }
        require(contentBytesBase64 == null || contentBytesBase64.isNotBlank()) {
            "Share/import content bytes must not be blank when supplied"
        }
        require(requiredCapabilities.isNotEmpty()) { "Share/import route must require at least one capability" }
        require(requiredCapabilities.distinct().size == requiredCapabilities.size) {
            "Share/import capabilities must be unique"
        }
        require(createdAtEpochMillis >= 0L) { "Share/import creation time must not be negative" }
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Share/import offer must expire after creation" }
    }

    val isWriteIntent: Boolean
        get() = intent == StudyFlowShareImportIntent.CLIPBOARD_APPLY ||
            intent == StudyFlowShareImportIntent.SHARE_IMPORT

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis > expiresAtEpochMillis

    fun accepts(target: StudyFlowLinkPeerDescriptor, nowEpochMillis: Long): Boolean {
        if (isExpired(nowEpochMillis)) return false
        if (targetDeviceId != null && targetDeviceId != target.deviceId) return false
        val tvClass = target.clientKind == StudyFlowLinkClientKind.ANDROID_TV ||
            target.clientKind == StudyFlowLinkClientKind.WEB_TV
        if (tvClass && isWriteIntent) return false
        if (
            tvClass && requiredCapabilities.any {
                it == StudyFlowLinkCapabilities.LibraryImport || it.value.endsWith(".write")
            }
        ) {
            return false
        }
        return requiredCapabilities.all { it in target.capabilities }
    }

    fun verifiedContentBytes(): ByteArray? {
        val encoded = contentBytesBase64 ?: return null
        val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        return if (digest == contentSha256) bytes else null
    }
}

object StudyFlowShareImportPayloadPolicy {
    fun validateCompleteContent(offer: StudyFlowShareImportOffer): String? {
        if (!offer.isWriteIntent && offer.contentBytesBase64 == null) return null
        if (offer.verifiedContentBytes() == null) {
            return if (offer.contentBytesBase64 == null) "missing_content_bytes" else "content_hash_mismatch"
        }
        return null
    }
}

@Serializable
data class StudyFlowShareImportDelivery(
    val messageId: StudyFlowMessageId,
    val sourceDeviceId: StudyFlowDeviceId,
    val targetDeviceId: StudyFlowDeviceId,
    val sequence: Long,
    val offer: StudyFlowShareImportOffer,
    val sentAtEpochMillis: Long,
) {
    init {
        require(sequence >= 1L) { "Share/import delivery sequence must be positive" }
        require(sourceDeviceId != targetDeviceId) { "Share/import delivery cannot target source" }
        require(sentAtEpochMillis >= 0L) { "Share/import delivery time must not be negative" }
    }
}

@Serializable
data class StudyFlowShareImportAck(
    val messageId: StudyFlowMessageId,
    val importId: StudyFlowShareImportId,
    val targetDeviceId: StudyFlowDeviceId,
    val acknowledgedSequence: Long,
    val status: StudyFlowShareImportStatus,
    val reason: String? = null,
    val acknowledgedAtEpochMillis: Long,
) {
    init {
        require(acknowledgedSequence >= 1L) { "Share/import acknowledgement sequence must be positive" }
        require(acknowledgedAtEpochMillis >= 0L) { "Share/import acknowledgement time must not be negative" }
        require(status != StudyFlowShareImportStatus.REJECTED || !reason.isNullOrBlank()) {
            "Rejected share/import acknowledgements require a reason"
        }
    }
}

class StudyFlowShareImportReducer(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val acceptedImports = mutableSetOf<StudyFlowShareImportId>()
    private val sourceFrontier = mutableMapOf<StudyFlowDeviceId, Long>()

    fun apply(delivery: StudyFlowShareImportDelivery): StudyFlowShareImportAck {
        if (delivery.targetDeviceId != localDevice.deviceId) {
            return delivery.ack(StudyFlowShareImportStatus.REJECTED, "wrong_target")
        }
        if (delivery.offer.isExpired(clock.nowEpochMillis())) {
            return delivery.ack(StudyFlowShareImportStatus.EXPIRED, "expired")
        }
        if (delivery.offer.sourceDevice.deviceId != delivery.sourceDeviceId) {
            return delivery.ack(StudyFlowShareImportStatus.REJECTED, "wrong_source")
        }
        if (delivery.offer.targetDeviceId != null && delivery.offer.targetDeviceId != delivery.targetDeviceId) {
            return delivery.ack(StudyFlowShareImportStatus.REJECTED, "inner_target_mismatch")
        }
        if (!delivery.offer.accepts(localDevice, clock.nowEpochMillis())) {
            return delivery.ack(StudyFlowShareImportStatus.REJECTED, "missing_capability")
        }
        StudyFlowShareImportPayloadPolicy.validateCompleteContent(delivery.offer)?.let { reason ->
            return delivery.ack(StudyFlowShareImportStatus.REJECTED, reason)
        }
        if (delivery.offer.importId in acceptedImports) {
            return delivery.ack(StudyFlowShareImportStatus.DUPLICATE, "duplicate")
        }
        val last = sourceFrontier[delivery.sourceDeviceId] ?: 0L
        if (delivery.sequence <= last) return delivery.ack(StudyFlowShareImportStatus.REJECTED, "stale_sequence")
        sourceFrontier[delivery.sourceDeviceId] = delivery.sequence
        acceptedImports += delivery.offer.importId
        return delivery.ack(StudyFlowShareImportStatus.ACCEPTED)
    }

    private fun StudyFlowShareImportDelivery.ack(status: StudyFlowShareImportStatus, reason: String? = null) =
        StudyFlowShareImportAck(
            messageId = messageId,
            importId = offer.importId,
            targetDeviceId = targetDeviceId,
            acknowledgedSequence = sequence,
            status = status,
            reason = reason,
            acknowledgedAtEpochMillis = clock.nowEpochMillis(),
        )
}

fun StudyFlowShareImportOffer.toDomainOperation(
    entityId: StudyFlowEntityId,
    entityRevision: StudyFlowEntityRevision,
    logicalTimestamp: Long,
    originSequence: Long,
    occurredAtEpochMillis: Long,
): StudyFlowDomainOperation = StudyFlowDomainOperation(
    operationId = StudyFlowOperationId("op-${importId.value}"),
    workspaceId = workspaceId,
    originDeviceId = sourceDevice.deviceId,
    originSequence = originSequence,
    entityId = entityId,
    entityRevision = entityRevision,
    logicalTimestamp = logicalTimestamp,
    operationType = when (intent) {
        StudyFlowShareImportIntent.CLIPBOARD_PREVIEW -> StudyFlowDomainOperationTypes.ClipboardPreviewed
        StudyFlowShareImportIntent.CLIPBOARD_APPLY -> StudyFlowDomainOperationTypes.ClipboardApplied
        StudyFlowShareImportIntent.SHARE_PREVIEW -> StudyFlowDomainOperationTypes.SharePreviewed
        StudyFlowShareImportIntent.SHARE_IMPORT -> StudyFlowDomainOperationTypes.ShareImported
    },
    occurredAtEpochMillis = occurredAtEpochMillis,
    payload = JsonObject(
        mapOf(
            "shareImportId" to JsonPrimitive(importId.value),
            "intent" to JsonPrimitive(intent.name.lowercase()),
            "payloadKind" to JsonPrimitive(payloadKind.name.lowercase()),
            "contentSha256" to JsonPrimitive(contentSha256),
            "sflp" to JsonPrimitive("sflp20"),
        ),
    ),
)

@Serializable
data class StudyFlowConflictCandidate(
    val entityId: StudyFlowEntityId,
    val localRevision: StudyFlowEntityRevision,
    val remoteRevision: StudyFlowEntityRevision,
    val fieldKinds: List<StudyFlowConflictFieldKind>,
    val localSummary: String,
    val remoteSummary: String,
) {
    init {
        require(remoteRevision.value >= localRevision.value) { "Remote revision must not go backwards" }
        require(fieldKinds.isNotEmpty()) { "Conflict candidate must name affected fields" }
        require(fieldKinds.distinct().size == fieldKinds.size) { "Conflict fields must be unique" }
        require(localSummary.isNotBlank()) { "Local conflict summary must not be blank" }
        require(remoteSummary.isNotBlank()) { "Remote conflict summary must not be blank" }
    }
}

@Serializable
data class StudyFlowConflictReview(
    val reviewId: StudyFlowConflictReviewId,
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDevice: StudyFlowLinkPeerDescriptor,
    val targetDeviceId: StudyFlowDeviceId? = null,
    val candidate: StudyFlowConflictCandidate,
    val requiredCapabilities: List<StudyFlowLinkCapability> = listOf(StudyFlowLinkCapabilities.ConflictReview),
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long = createdAtEpochMillis + DefaultConflictReviewTtlMillis,
    val metadata: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(requiredCapabilities.isNotEmpty()) { "Conflict review must require a capability" }
        require(requiredCapabilities.distinct().size == requiredCapabilities.size) {
            "Conflict capabilities must be unique"
        }
        require(createdAtEpochMillis >= 0L) { "Conflict review creation time must not be negative" }
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Conflict review must expire after creation" }
    }

    fun accepts(target: StudyFlowLinkPeerDescriptor, nowEpochMillis: Long): Boolean {
        if (nowEpochMillis > expiresAtEpochMillis) return false
        if (targetDeviceId != null && targetDeviceId != target.deviceId) return false
        return requiredCapabilities.all { it in target.capabilities }
    }
}

@Serializable
data class StudyFlowConflictResolution(
    val resolutionId: StudyFlowConflictResolutionId,
    val reviewId: StudyFlowConflictReviewId,
    val workspaceId: StudyFlowWorkspaceId,
    val resolverDeviceId: StudyFlowDeviceId,
    val targetDeviceId: StudyFlowDeviceId? = null,
    val sequence: Long,
    val decision: StudyFlowConflictResolutionKind,
    val resolvedAtEpochMillis: Long,
    val mergedSummary: String? = null,
    val metadata: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(sequence >= 1L) { "Conflict resolution sequence must be positive" }
        require(resolvedAtEpochMillis >= 0L) { "Conflict resolution time must not be negative" }
        require(decision != StudyFlowConflictResolutionKind.MERGE || !mergedSummary.isNullOrBlank()) {
            "Merged conflict resolutions require a merged summary"
        }
    }

    fun toDomainOperation(
        candidate: StudyFlowConflictCandidate,
        entityRevision: StudyFlowEntityRevision,
        logicalTimestamp: Long,
    ): StudyFlowDomainOperation = StudyFlowDomainOperation(
        operationId = StudyFlowOperationId("op-${resolutionId.value}"),
        workspaceId = workspaceId,
        originDeviceId = resolverDeviceId,
        originSequence = sequence,
        entityId = candidate.entityId,
        entityRevision = entityRevision,
        logicalTimestamp = logicalTimestamp,
        operationType = when (decision) {
            StudyFlowConflictResolutionKind.KEEP_LOCAL -> StudyFlowDomainOperationTypes.ConflictKeepLocal
            StudyFlowConflictResolutionKind.TAKE_REMOTE -> StudyFlowDomainOperationTypes.ConflictTakeRemote
            StudyFlowConflictResolutionKind.MERGE -> StudyFlowDomainOperationTypes.ConflictMerged
            StudyFlowConflictResolutionKind.SKIP -> StudyFlowDomainOperationTypes.ConflictSkipped
        },
        occurredAtEpochMillis = resolvedAtEpochMillis,
        payload = JsonObject(
            mapOf(
                "conflictReviewId" to JsonPrimitive(reviewId.value),
                "resolutionId" to JsonPrimitive(resolutionId.value),
                "decision" to JsonPrimitive(decision.name.lowercase()),
                "sflp" to JsonPrimitive("sflp21"),
            ),
        ),
    )
}

@Serializable
data class StudyFlowConflictResolutionAck(
    val resolutionId: StudyFlowConflictResolutionId,
    val reviewId: StudyFlowConflictReviewId,
    val targetDeviceId: StudyFlowDeviceId,
    val acknowledgedSequence: Long,
    val status: StudyFlowConflictReviewStatus,
    val reason: String? = null,
    val acknowledgedAtEpochMillis: Long,
) {
    init {
        require(acknowledgedSequence >= 1L) { "Conflict acknowledgement sequence must be positive" }
        require(acknowledgedAtEpochMillis >= 0L) { "Conflict acknowledgement time must not be negative" }
        require(status != StudyFlowConflictReviewStatus.REJECTED || !reason.isNullOrBlank()) {
            "Rejected conflict acknowledgements require a reason"
        }
    }
}

class StudyFlowConflictReviewReducer(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val openReviews = mutableMapOf<StudyFlowConflictReviewId, StudyFlowConflictReview>()
    private val resolved = mutableSetOf<StudyFlowConflictResolutionId>()
    private val reviewFrontier = mutableMapOf<StudyFlowConflictReviewId, Long>()

    fun open(review: StudyFlowConflictReview): Boolean {
        if (!review.accepts(localDevice, clock.nowEpochMillis())) return false
        openReviews[review.reviewId] = review
        return true
    }

    fun resolve(resolution: StudyFlowConflictResolution): StudyFlowConflictResolutionAck {
        val review = openReviews[resolution.reviewId]
            ?: return resolution.ack(StudyFlowConflictReviewStatus.REJECTED, "unknown_review")
        if (resolution.targetDeviceId != null && resolution.targetDeviceId != localDevice.deviceId) {
            return resolution.ack(StudyFlowConflictReviewStatus.REJECTED, "wrong_target")
        }
        if (resolution.resolutionId in resolved) {
            return resolution.ack(StudyFlowConflictReviewStatus.DUPLICATE, "duplicate")
        }
        val last = reviewFrontier[resolution.reviewId] ?: 0L
        if (resolution.sequence <= last) return resolution.ack(StudyFlowConflictReviewStatus.REJECTED, "stale_sequence")
        if (!review.accepts(localDevice, clock.nowEpochMillis())) {
            return resolution.ack(StudyFlowConflictReviewStatus.REJECTED, "review_expired_or_forbidden")
        }
        reviewFrontier[resolution.reviewId] = resolution.sequence
        resolved += resolution.resolutionId
        openReviews.remove(resolution.reviewId)
        return resolution.ack(StudyFlowConflictReviewStatus.RESOLVED)
    }

    fun pending(): List<StudyFlowConflictReview> = openReviews.values.sortedBy { it.reviewId.value }

    private fun StudyFlowConflictResolution.ack(status: StudyFlowConflictReviewStatus, reason: String? = null) =
        StudyFlowConflictResolutionAck(
            resolutionId = resolutionId,
            reviewId = reviewId,
            targetDeviceId = localDevice.deviceId,
            acknowledgedSequence = sequence,
            status = status,
            reason = reason,
            acknowledgedAtEpochMillis = clock.nowEpochMillis(),
        )
}

object StudyFlowShareImportDefaults {
    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun base64Utf8(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    fun textCapabilitiesFor(intent: StudyFlowShareImportIntent): List<StudyFlowLinkCapability> = when (intent) {
        StudyFlowShareImportIntent.CLIPBOARD_PREVIEW -> listOf(StudyFlowLinkCapabilities.ClipboardRead)
        StudyFlowShareImportIntent.CLIPBOARD_APPLY -> listOf(
            StudyFlowLinkCapabilities.ClipboardRead,
            StudyFlowLinkCapabilities.ClipboardWrite,
        )
        StudyFlowShareImportIntent.SHARE_PREVIEW -> listOf(
            StudyFlowLinkCapabilities.ShareReceive,
            StudyFlowLinkCapabilities.ImportPreview,
        )
        StudyFlowShareImportIntent.SHARE_IMPORT -> listOf(
            StudyFlowLinkCapabilities.ShareReceive,
            StudyFlowLinkCapabilities.LibraryImport,
        )
    }
}

object StudyFlowShareImportWireNames {
    const val ShareImportOffer = "share.import_offer"
    const val ShareImportAck = "share.import_ack"
    const val ConflictReviewOpen = "conflict.review_open"
    const val ConflictResolve = "conflict.resolve"
    const val ConflictResolutionAck = "conflict.resolution_ack"
}
