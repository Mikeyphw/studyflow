package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val AttentionLedgerSchemaVersion = 1
private const val AttentionLedgerDefaultLimit = 512
private val attentionTokenPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

internal val StudyFlowAttentionWireJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Serializable
@JvmInline
value class StudyFlowAttentionOccurrenceId(val value: String) {
    init {
        require(attentionTokenPattern.matches(value)) {
            "Attention occurrence ID must be a bounded wire token"
        }
    }

    companion object {
        fun deterministic(vararg parts: String): StudyFlowAttentionOccurrenceId {
            val raw = parts.joinToString("\u001f")
            val readable = parts.joinToString(":")
            if (readable.length <= 128 && attentionTokenPattern.matches(readable)) {
                return StudyFlowAttentionOccurrenceId(readable)
            }
            var hash = 0x811c9dc5.toInt()
            raw.forEach { character ->
                hash = hash xor character.code
                hash *= 0x01000193
            }
            val hex = Integer.toUnsignedString(hash, 16).padStart(8, '0')
            return StudyFlowAttentionOccurrenceId("attn-$hex")
        }

        fun fromLegacy(value: String): StudyFlowAttentionOccurrenceId =
            if (value.length <= 128 && attentionTokenPattern.matches(value)) {
                StudyFlowAttentionOccurrenceId(value)
            } else {
                deterministic("legacy", value)
            }
    }
}

@Serializable
enum class StudyFlowAttentionCategory {
    @SerialName("planned_study") PLANNED_STUDY,
    @SerialName("due_review") DUE_REVIEW,
    @SerialName("missed_activity") MISSED_ACTIVITY,
    @SerialName("daily_summary") DAILY_SUMMARY,
    @SerialName("session_ending") SESSION_ENDING,
    @SerialName("session_completed") SESSION_COMPLETED,
    @SerialName("exam_countdown") EXAM_COUNTDOWN,
    @SerialName("schedule_change") SCHEDULE_CHANGE,
    @SerialName("plan_warning") PLAN_WARNING,
    @SerialName("sync_attention") SYNC_ATTENTION,
}

@Serializable
enum class StudyFlowAttentionPriority {
    @SerialName("low") LOW,
    @SerialName("normal") NORMAL,
    @SerialName("high") HIGH,
    @SerialName("time_sensitive") TIME_SENSITIVE,
}

@Serializable
enum class StudyFlowAttentionActionKind {
    @SerialName("open") OPEN,
    @SerialName("start_now") START_NOW,
    @SerialName("review_now") REVIEW_NOW,
    @SerialName("snooze") SNOOZE,
    @SerialName("reschedule") RESCHEDULE,
    @SerialName("mark_done") MARK_DONE,
    @SerialName("dismiss") DISMISS,
    @SerialName("continue") CONTINUE,
    @SerialName("move_to_device") MOVE_TO_DEVICE,
}

@Serializable
data class StudyFlowAttentionAction(
    val kind: StudyFlowAttentionActionKind,
    val payload: JsonObject = JsonObject(emptyMap()),
)

@Serializable
enum class StudyFlowAttentionDeliveryPolicy {
    @SerialName("local_only") LOCAL_ONLY,
    @SerialName("primary_device") PRIMARY_DEVICE,
    @SerialName("active_then_primary") ACTIVE_THEN_PRIMARY,
    @SerialName("phone_and_desktop") PHONE_AND_DESKTOP,
    @SerialName("all_capable") ALL_CAPABLE,
}

@Serializable
enum class StudyFlowAttentionDeliveryState {
    @SerialName("planned") PLANNED,
    @SerialName("scheduled") SCHEDULED,
    @SerialName("delivery_pending") DELIVERY_PENDING,
    @SerialName("delivered") DELIVERED,
    @SerialName("acknowledged") ACKNOWLEDGED,
    @SerialName("started") STARTED,
    @SerialName("completed") COMPLETED,
    @SerialName("snoozed") SNOOZED,
    @SerialName("rescheduled") RESCHEDULED,
    @SerialName("dismissed") DISMISSED,
    @SerialName("missed") MISSED,
    @SerialName("expired") EXPIRED,
    @SerialName("cancelled") CANCELLED,
}

@Serializable
data class StudyFlowAttentionQuietHours(
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        require(startMinute in 0..1439) { "Quiet-hour start minute out of range" }
        require(endMinute in 0..1439) { "Quiet-hour end minute out of range" }
    }
}

@Serializable
data class StudyFlowAttentionOccurrence(
    val occurrenceId: StudyFlowAttentionOccurrenceId,
    val workspaceId: StudyFlowWorkspaceId,
    val scheduleEntityId: StudyFlowEntityId? = null,
    val category: StudyFlowAttentionCategory,
    val priority: StudyFlowAttentionPriority = StudyFlowAttentionPriority.NORMAL,
    val scheduledAtEpochMillis: Long,
    val originalScheduledAtEpochMillis: Long = scheduledAtEpochMillis,
    val expiresAtEpochMillis: Long? = null,
    val timezoneId: String? = null,
    val quietHours: StudyFlowAttentionQuietHours? = null,
    val deliveryPolicy: StudyFlowAttentionDeliveryPolicy = StudyFlowAttentionDeliveryPolicy.ACTIVE_THEN_PRIMARY,
    val actions: List<StudyFlowAttentionAction> = emptyList(),
    val sourceDeviceId: StudyFlowDeviceId? = null,
    val targetDeviceIds: List<StudyFlowDeviceId> = emptyList(),
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(scheduledAtEpochMillis >= 0L) { "Attention scheduled time must not be negative" }
        require(originalScheduledAtEpochMillis >= 0L) { "Attention original scheduled time must not be negative" }
        require(expiresAtEpochMillis == null || expiresAtEpochMillis >= scheduledAtEpochMillis) {
            "Attention expiry must not precede the scheduled time"
        }
        require(timezoneId == null || timezoneId.isNotBlank()) { "Attention timezone must not be blank" }
        require(actions.distinctBy(StudyFlowAttentionAction::kind).size == actions.size) {
            "Attention actions must be unique by kind"
        }
        require(targetDeviceIds.distinct().size == targetDeviceIds.size) {
            "Attention target devices must be unique"
        }
    }

    fun isExpired(nowEpochMillis: Long): Boolean =
        expiresAtEpochMillis?.let { nowEpochMillis > it } ?: false
}

@Serializable
data class StudyFlowAttentionLedgerEntry(
    val occurrence: StudyFlowAttentionOccurrence,
    val state: StudyFlowAttentionDeliveryState,
    val updatedAtEpochMillis: Long,
    val deliveredAtEpochMillis: Long? = null,
    val acknowledgedAtEpochMillis: Long? = null,
    val sourceDeviceId: StudyFlowDeviceId? = occurrence.sourceDeviceId,
    val targetDeviceId: StudyFlowDeviceId? = null,
    val sequence: Long? = null,
    val deliveryAttempts: Int = 0,
) {
    init {
        require(updatedAtEpochMillis >= 0L) { "Attention ledger timestamp must not be negative" }
        require(deliveredAtEpochMillis == null || deliveredAtEpochMillis >= 0L) {
            "Attention delivery timestamp must not be negative"
        }
        require(acknowledgedAtEpochMillis == null || acknowledgedAtEpochMillis >= 0L) {
            "Attention acknowledgement timestamp must not be negative"
        }
        require(sequence == null || sequence >= 1L) { "Attention delivery sequence must be positive" }
        require(deliveryAttempts >= 0) { "Attention delivery attempts must not be negative" }
    }
}

@Serializable
data class StudyFlowAttentionLedgerSnapshot(
    val schemaVersion: Int = AttentionLedgerSchemaVersion,
    val entries: List<StudyFlowAttentionLedgerEntry> = emptyList(),
    val lastSequenceBySource: Map<String, Long> = emptyMap(),
    val legacyDeliveredOccurrenceIds: List<String> = emptyList(),
) {
    init {
        require(schemaVersion >= 1) { "Attention ledger schema version must be positive" }
        require(entries.distinctBy { it.occurrence.occurrenceId }.size == entries.size) {
            "Attention ledger occurrence IDs must be unique"
        }
        require(lastSequenceBySource.values.all { it >= 1L }) {
            "Attention ledger source sequences must be positive"
        }
        require(legacyDeliveredOccurrenceIds.distinct().size == legacyDeliveredOccurrenceIds.size) {
            "Attention legacy delivered occurrence IDs must be unique"
        }
    }
}

interface StudyFlowAttentionLedgerStorage {
    fun readEncodedSnapshot(): String?
    fun writeEncodedSnapshot(encoded: String)
}

class StudyFlowInMemoryAttentionLedgerStorage(
    initialEncodedSnapshot: String? = null,
) : StudyFlowAttentionLedgerStorage {
    private var encodedSnapshot = initialEncodedSnapshot

    override fun readEncodedSnapshot(): String? = synchronized(this) { encodedSnapshot }

    override fun writeEncodedSnapshot(encoded: String) = synchronized(this) {
        encodedSnapshot = encoded
    }
}

enum class StudyFlowAttentionLedgerAdmission {
    ACCEPTED,
    DUPLICATE,
    STALE_SEQUENCE,
}

data class StudyFlowAttentionDiagnosticsSnapshot(
    val schemaVersion: Int,
    val entryCount: Int,
    val activeCount: Int,
    val terminalCount: Int,
    val deliveredCount: Int,
    val sourceSequenceCount: Int,
    val legacyDeliveredAliasCount: Int,
    val lastUpdatedAtEpochMillis: Long?,
    val loadRecoveredFromInvalidState: Boolean,
)

class StudyFlowAttentionLedger(
    private val storage: StudyFlowAttentionLedgerStorage = StudyFlowInMemoryAttentionLedgerStorage(),
    private val maxEntries: Int = AttentionLedgerDefaultLimit,
) {
    init {
        require(maxEntries >= 32) { "Attention ledger must retain at least 32 entries" }
    }

    private var recoveredFromInvalidState = false
    private var snapshot = loadSnapshot()

    @Synchronized
    fun snapshot(): StudyFlowAttentionLedgerSnapshot = snapshot

    @Synchronized
    fun entry(occurrenceId: StudyFlowAttentionOccurrenceId): StudyFlowAttentionLedgerEntry? =
        snapshot.entries.firstOrNull { it.occurrence.occurrenceId == occurrenceId }

    @Synchronized
    fun lastSequenceFor(sourceDeviceId: StudyFlowDeviceId): Long =
        snapshot.lastSequenceBySource[sourceDeviceId.value] ?: 0L

    @Synchronized
    fun upsertScheduled(
        occurrence: StudyFlowAttentionOccurrence,
        atEpochMillis: Long,
    ): StudyFlowAttentionLedgerEntry {
        val current = entry(occurrence.occurrenceId)
        val targetState = when (current?.state) {
            null, StudyFlowAttentionDeliveryState.PLANNED, StudyFlowAttentionDeliveryState.RESCHEDULED,
            StudyFlowAttentionDeliveryState.SNOOZED -> StudyFlowAttentionDeliveryState.SCHEDULED
            else -> current.state
        }
        val updated = StudyFlowAttentionLedgerEntry(
            occurrence = occurrence,
            state = targetState,
            updatedAtEpochMillis = atEpochMillis,
            deliveredAtEpochMillis = current?.deliveredAtEpochMillis,
            acknowledgedAtEpochMillis = current?.acknowledgedAtEpochMillis,
            sourceDeviceId = current?.sourceDeviceId ?: occurrence.sourceDeviceId,
            targetDeviceId = current?.targetDeviceId,
            sequence = current?.sequence,
            deliveryAttempts = current?.deliveryAttempts ?: 0,
        )
        replaceEntry(updated)
        return updated
    }

    @Synchronized
    fun admitInboundDelivery(
        occurrence: StudyFlowAttentionOccurrence,
        sourceDeviceId: StudyFlowDeviceId,
        targetDeviceId: StudyFlowDeviceId,
        sequence: Long,
        deliveredAtEpochMillis: Long,
    ): StudyFlowAttentionLedgerAdmission {
        require(sequence >= 1L) { "Attention delivery sequence must be positive" }
        val current = entry(occurrence.occurrenceId)
        if (
            current != null && current.state.deliveredOrHandled() ||
            occurrence.occurrenceId.value in snapshot.legacyDeliveredOccurrenceIds
        ) {
            return StudyFlowAttentionLedgerAdmission.DUPLICATE
        }
        if (sequence <= lastSequenceFor(sourceDeviceId)) {
            return StudyFlowAttentionLedgerAdmission.STALE_SEQUENCE
        }
        val nextSequences = snapshot.lastSequenceBySource + (sourceDeviceId.value to sequence)
        val delivered = StudyFlowAttentionLedgerEntry(
            occurrence = occurrence,
            state = StudyFlowAttentionDeliveryState.DELIVERED,
            updatedAtEpochMillis = deliveredAtEpochMillis,
            deliveredAtEpochMillis = deliveredAtEpochMillis,
            sourceDeviceId = sourceDeviceId,
            targetDeviceId = targetDeviceId,
            sequence = sequence,
            deliveryAttempts = (current?.deliveryAttempts ?: 0) + 1,
        )
        snapshot = compact(
            snapshot.copy(
                entries = snapshot.entries.filterNot {
                    it.occurrence.occurrenceId == occurrence.occurrenceId
                } + delivered,
                lastSequenceBySource = nextSequences,
            ),
        )
        persist()
        return StudyFlowAttentionLedgerAdmission.ACCEPTED
    }

    @Synchronized
    fun transition(
        occurrenceId: StudyFlowAttentionOccurrenceId,
        state: StudyFlowAttentionDeliveryState,
        atEpochMillis: Long,
    ): StudyFlowAttentionLedgerEntry {
        val current = requireNotNull(entry(occurrenceId)) {
            "Unknown attention occurrence: ${occurrenceId.value}"
        }
        require(current.state.canTransitionTo(state)) {
            "Invalid attention transition ${current.state} -> $state"
        }
        val updated = current.copy(
            state = state,
            updatedAtEpochMillis = atEpochMillis,
            acknowledgedAtEpochMillis = if (
                state == StudyFlowAttentionDeliveryState.ACKNOWLEDGED
            ) {
                atEpochMillis
            } else {
                current.acknowledgedAtEpochMillis
            },
        )
        replaceEntry(updated)
        return updated
    }

    @Synchronized
    fun migrateLegacyDeliveredAliases(occurrenceIds: Collection<String>): Int {
        val current = LinkedHashSet(snapshot.legacyDeliveredOccurrenceIds)
        val before = current.size
        occurrenceIds.filter(String::isNotBlank).forEach { current += it }
        val retained = current.toList().takeLast(maxEntries)
        if (retained != snapshot.legacyDeliveredOccurrenceIds) {
            snapshot = snapshot.copy(legacyDeliveredOccurrenceIds = retained)
            persist()
        }
        return (current.size - before).coerceAtLeast(0)
    }

    @Synchronized
    fun migrateLegacyDeliveredOccurrenceIds(
        occurrenceIds: Collection<String>,
        workspaceId: StudyFlowWorkspaceId,
        sourceDeviceId: StudyFlowDeviceId?,
        migratedAtEpochMillis: Long,
    ): Int {
        var migrated = 0
        occurrenceIds.distinct().forEach { legacyId ->
            val occurrenceId = StudyFlowAttentionOccurrenceId.fromLegacy(legacyId)
            if (entry(occurrenceId) != null) return@forEach
            val occurrence = StudyFlowAttentionOccurrence(
                occurrenceId = occurrenceId,
                workspaceId = workspaceId,
                category = StudyFlowAttentionCategory.SYNC_ATTENTION,
                scheduledAtEpochMillis = migratedAtEpochMillis,
                originalScheduledAtEpochMillis = migratedAtEpochMillis,
                sourceDeviceId = sourceDeviceId,
                payload = JsonObject(mapOf("legacyOccurrenceId" to JsonPrimitive(legacyId))),
            )
            replaceEntry(
                StudyFlowAttentionLedgerEntry(
                    occurrence = occurrence,
                    state = StudyFlowAttentionDeliveryState.DELIVERED,
                    updatedAtEpochMillis = migratedAtEpochMillis,
                    deliveredAtEpochMillis = migratedAtEpochMillis,
                    sourceDeviceId = sourceDeviceId,
                ),
            )
            migrated += 1
        }
        return migrated
    }

    @Synchronized
    fun diagnostics(): StudyFlowAttentionDiagnosticsSnapshot {
        val terminal = snapshot.entries.count { it.state.isTerminal() }
        return StudyFlowAttentionDiagnosticsSnapshot(
            schemaVersion = snapshot.schemaVersion,
            entryCount = snapshot.entries.size,
            activeCount = snapshot.entries.size - terminal,
            terminalCount = terminal,
            deliveredCount = snapshot.entries.count { it.state.deliveredOrHandled() },
            sourceSequenceCount = snapshot.lastSequenceBySource.size,
            legacyDeliveredAliasCount = snapshot.legacyDeliveredOccurrenceIds.size,
            lastUpdatedAtEpochMillis = snapshot.entries.maxOfOrNull(StudyFlowAttentionLedgerEntry::updatedAtEpochMillis),
            loadRecoveredFromInvalidState = recoveredFromInvalidState,
        )
    }

    private fun loadSnapshot(): StudyFlowAttentionLedgerSnapshot {
        val encoded = storage.readEncodedSnapshot() ?: return StudyFlowAttentionLedgerSnapshot()
        return try {
            val decoded = StudyFlowAttentionWireJson.decodeFromString(
                StudyFlowAttentionLedgerSnapshot.serializer(),
                encoded,
            )
            if (decoded.schemaVersion > AttentionLedgerSchemaVersion) {
                recoveredFromInvalidState = true
                StudyFlowAttentionLedgerSnapshot()
            } else {
                compact(decoded.copy(schemaVersion = AttentionLedgerSchemaVersion))
            }
        } catch (_: Exception) {
            recoveredFromInvalidState = true
            StudyFlowAttentionLedgerSnapshot()
        }
    }

    private fun replaceEntry(entry: StudyFlowAttentionLedgerEntry) {
        snapshot = compact(
            snapshot.copy(
                entries = snapshot.entries.filterNot {
                    it.occurrence.occurrenceId == entry.occurrence.occurrenceId
                } + entry,
            ),
        )
        persist()
    }

    private fun compact(value: StudyFlowAttentionLedgerSnapshot): StudyFlowAttentionLedgerSnapshot {
        if (value.entries.size <= maxEntries) return value
        val newest = value.entries.sortedByDescending(StudyFlowAttentionLedgerEntry::updatedAtEpochMillis)
        val active = newest.filterNot { it.state.isTerminal() }
        val terminal = newest.filter { it.state.isTerminal() }
        return value.copy(entries = (active + terminal).take(maxEntries).sortedBy(StudyFlowAttentionLedgerEntry::updatedAtEpochMillis))
    }

    private fun persist() {
        storage.writeEncodedSnapshot(
            StudyFlowAttentionWireJson.encodeToString(StudyFlowAttentionLedgerSnapshot.serializer(), snapshot),
        )
    }
}

private fun StudyFlowAttentionDeliveryState.deliveredOrHandled(): Boolean = when (this) {
    StudyFlowAttentionDeliveryState.DELIVERED,
    StudyFlowAttentionDeliveryState.ACKNOWLEDGED,
    StudyFlowAttentionDeliveryState.STARTED,
    StudyFlowAttentionDeliveryState.COMPLETED,
    StudyFlowAttentionDeliveryState.SNOOZED,
    StudyFlowAttentionDeliveryState.RESCHEDULED,
    StudyFlowAttentionDeliveryState.DISMISSED,
    StudyFlowAttentionDeliveryState.MISSED,
    StudyFlowAttentionDeliveryState.EXPIRED,
    StudyFlowAttentionDeliveryState.CANCELLED -> true
    StudyFlowAttentionDeliveryState.PLANNED,
    StudyFlowAttentionDeliveryState.SCHEDULED,
    StudyFlowAttentionDeliveryState.DELIVERY_PENDING -> false
}

private fun StudyFlowAttentionDeliveryState.isTerminal(): Boolean = when (this) {
    StudyFlowAttentionDeliveryState.COMPLETED,
    StudyFlowAttentionDeliveryState.DISMISSED,
    StudyFlowAttentionDeliveryState.EXPIRED,
    StudyFlowAttentionDeliveryState.CANCELLED -> true
    else -> false
}

private fun StudyFlowAttentionDeliveryState.canTransitionTo(next: StudyFlowAttentionDeliveryState): Boolean {
    if (this == next) return true
    return when (this) {
        StudyFlowAttentionDeliveryState.PLANNED -> next in setOf(
            StudyFlowAttentionDeliveryState.SCHEDULED,
            StudyFlowAttentionDeliveryState.CANCELLED,
        )
        StudyFlowAttentionDeliveryState.SCHEDULED -> next in setOf(
            StudyFlowAttentionDeliveryState.DELIVERY_PENDING,
            StudyFlowAttentionDeliveryState.MISSED,
            StudyFlowAttentionDeliveryState.RESCHEDULED,
            StudyFlowAttentionDeliveryState.EXPIRED,
            StudyFlowAttentionDeliveryState.CANCELLED,
        )
        StudyFlowAttentionDeliveryState.DELIVERY_PENDING -> next in setOf(
            StudyFlowAttentionDeliveryState.DELIVERED,
            StudyFlowAttentionDeliveryState.SNOOZED,
            StudyFlowAttentionDeliveryState.EXPIRED,
            StudyFlowAttentionDeliveryState.CANCELLED,
        )
        StudyFlowAttentionDeliveryState.DELIVERED -> next in setOf(
            StudyFlowAttentionDeliveryState.ACKNOWLEDGED,
            StudyFlowAttentionDeliveryState.STARTED,
            StudyFlowAttentionDeliveryState.COMPLETED,
            StudyFlowAttentionDeliveryState.SNOOZED,
            StudyFlowAttentionDeliveryState.RESCHEDULED,
            StudyFlowAttentionDeliveryState.DISMISSED,
            StudyFlowAttentionDeliveryState.EXPIRED,
        )
        StudyFlowAttentionDeliveryState.ACKNOWLEDGED -> next in setOf(
            StudyFlowAttentionDeliveryState.STARTED,
            StudyFlowAttentionDeliveryState.COMPLETED,
            StudyFlowAttentionDeliveryState.SNOOZED,
            StudyFlowAttentionDeliveryState.RESCHEDULED,
            StudyFlowAttentionDeliveryState.DISMISSED,
        )
        StudyFlowAttentionDeliveryState.STARTED -> next in setOf(
            StudyFlowAttentionDeliveryState.COMPLETED,
            StudyFlowAttentionDeliveryState.DISMISSED,
        )
        StudyFlowAttentionDeliveryState.SNOOZED -> next in setOf(
            StudyFlowAttentionDeliveryState.SCHEDULED,
            StudyFlowAttentionDeliveryState.RESCHEDULED,
            StudyFlowAttentionDeliveryState.CANCELLED,
        )
        StudyFlowAttentionDeliveryState.RESCHEDULED -> next in setOf(
            StudyFlowAttentionDeliveryState.SCHEDULED,
            StudyFlowAttentionDeliveryState.CANCELLED,
        )
        StudyFlowAttentionDeliveryState.MISSED -> next in setOf(
            StudyFlowAttentionDeliveryState.RESCHEDULED,
            StudyFlowAttentionDeliveryState.COMPLETED,
            StudyFlowAttentionDeliveryState.DISMISSED,
        )
        StudyFlowAttentionDeliveryState.COMPLETED,
        StudyFlowAttentionDeliveryState.DISMISSED,
        StudyFlowAttentionDeliveryState.EXPIRED,
        StudyFlowAttentionDeliveryState.CANCELLED -> false
    }
}
