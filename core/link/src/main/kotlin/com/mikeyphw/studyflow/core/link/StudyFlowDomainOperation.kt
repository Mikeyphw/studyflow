package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

private const val MaxOperationTypeLength = 128
private val operationTypePattern = Regex("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+")

@Serializable
enum class StudyFlowOperationKind {
    @SerialName("mutation")
    MUTATION,

    @SerialName("tombstone")
    TOMBSTONE,
}


object StudyFlowDomainOperationTypes {
    const val GoalCreated = "goal.created"
    const val GoalCompleted = "goal.completed"
    const val GoalPaused = "goal.paused"
    const val QuestionAttemptRecorded = "question.attempt_recorded"
    const val ReviewGraded = "review.graded"
    const val SessionStarted = "session.started"
    const val SessionPaused = "session.paused"
    const val SessionCompleted = "session.completed"
    const val MaterialAdded = "material.added"
    const val TopicPriorityChanged = "topic.priority_changed"
    const val ScheduleActivityMoved = "schedule.activity_moved"
    const val ScheduleReminderUpserted = "schedule.reminder_upserted"
    const val ScheduleReminderCancelled = "schedule.reminder_cancelled"
    const val NotificationDelivered = "notification.delivered"
    const val NotificationPolicyChanged = "notification.policy_changed"
    const val NotificationQuietHoursChanged = "notification.quiet_hours_changed"
    const val MaterialStreamPrepared = "material.stream_prepared"
    const val MaterialChunkReceived = "material.chunk_received"
    const val ClipboardPreviewed = "clipboard.previewed"
    const val ClipboardApplied = "clipboard.applied"
    const val SharePreviewed = "share.previewed"
    const val ShareImported = "share.imported"
    const val ConflictKeepLocal = "conflict.keep_local"
    const val ConflictTakeRemote = "conflict.take_remote"
    const val ConflictMerged = "conflict.merged"
    const val ConflictSkipped = "conflict.skipped"
    const val AccountHandoffAccepted = "account.handoff_accepted"
    const val BackupBundleOffered = "backup.bundle_offered"
    const val BackupRestorePlanned = "backup.restore_planned"
    const val ReleaseValidationRecorded = "release.validation_recorded"
}

@Serializable
data class StudyFlowDomainOperation(
    val operationId: StudyFlowOperationId,
    val workspaceId: StudyFlowWorkspaceId,
    val originDeviceId: StudyFlowDeviceId,
    val originSequence: Long,
    val entityId: StudyFlowEntityId,
    val entityRevision: StudyFlowEntityRevision,
    val logicalTimestamp: Long,
    val operationType: String,
    val kind: StudyFlowOperationKind = StudyFlowOperationKind.MUTATION,
    val occurredAtEpochMillis: Long,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(originSequence >= 1L) { "Origin sequence must be at least 1" }
        require(entityRevision.value >= 1L) { "Operation entity revision must be at least 1" }
        require(logicalTimestamp >= 1L) { "Logical timestamp must be at least 1" }
        require(operationType.length <= MaxOperationTypeLength) { "Operation type is too long" }
        require(operationTypePattern.matches(operationType)) {
            "Operation type must use dotted lower-case tokens"
        }
        require(occurredAtEpochMillis >= 0L) { "Operation timestamp must not be negative" }
    }
}

internal val StudyFlowDomainOperationDeterministicComparator =
    compareBy<StudyFlowDomainOperation>(
        StudyFlowDomainOperation::logicalTimestamp,
        { it.originDeviceId.value },
        StudyFlowDomainOperation::originSequence,
        { it.operationId.value },
    )
