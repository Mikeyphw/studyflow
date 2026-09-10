package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowLiveSessionContinuityTest {
    private val workspaceId = StudyFlowWorkspaceId("workspace-live")
    private val activityId = StudyFlowActivityId("activity-live")
    private val android = peer("android-live", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
    private val desktop = peer("desktop-live", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)

    @Test
    fun completionIsExactlyOnceAndStaleEpochIsRejected() {
        val reducer = StudyFlowLiveSessionReducer(snapshot())
        val completion = event(
            id = "event-complete",
            source = android.deviceId,
            sequence = 2,
            kind = StudyFlowLiveSessionEventKind.COMPLETED,
            completion = StudyFlowOperationId("op-complete-live"),
        )

        assertTrue(reducer.apply(completion) is StudyFlowLiveSessionApplyResult.Applied)
        assertEquals(StudyFlowLiveSessionState.COMPLETED, reducer.snapshot().state)
        assertEquals(StudyFlowOperationId("op-complete-live"), reducer.snapshot().completionOperationId)
        assertTrue(reducer.apply(completion) is StudyFlowLiveSessionApplyResult.Duplicate)

        val secondCompletion = event(
            id = "event-complete-again",
            source = desktop.deviceId,
            sequence = 3,
            kind = StudyFlowLiveSessionEventKind.COMPLETED,
            completion = StudyFlowOperationId("op-complete-again"),
        )
        val rejected = reducer.apply(secondCompletion) as StudyFlowLiveSessionApplyResult.Rejected
        assertEquals("already_completed", rejected.reason)
    }

    @Test
    fun pauseResumeAndCursorUseMonotonicFencing() {
        val reducer = StudyFlowLiveSessionReducer(snapshot())
        val cursor = StudyFlowLiveSessionCursor(activityId, "question-2", itemIndex = 2, elapsedMillis = 9_000L)
        val cursorResult = reducer.apply(event("event-cursor", android.deviceId, 2, StudyFlowLiveSessionEventKind.CURSOR, cursor))
        assertTrue(cursorResult is StudyFlowLiveSessionApplyResult.Applied)
        assertEquals("question-2", reducer.snapshot().cursor.positionToken)

        assertTrue(reducer.apply(event("event-pause", desktop.deviceId, 3, StudyFlowLiveSessionEventKind.PAUSED)) is StudyFlowLiveSessionApplyResult.Applied)
        assertEquals(StudyFlowLiveSessionState.PAUSED, reducer.snapshot().state)

        val stale = reducer.apply(event("event-old", android.deviceId, 2, StudyFlowLiveSessionEventKind.RESUMED))
        assertEquals("stale_sequence", (stale as StudyFlowLiveSessionApplyResult.Rejected).reason)

        assertTrue(reducer.apply(event("event-resume", desktop.deviceId, 4, StudyFlowLiveSessionEventKind.RESUMED)) is StudyFlowLiveSessionApplyResult.Applied)
        assertEquals(StudyFlowLiveSessionState.RUNNING, reducer.snapshot().state)
    }

    @Test
    fun liveEventsRemainJournalOperationsForOfflineReplay() {
        val completion = event(
            id = "event-journal-complete",
            source = android.deviceId,
            sequence = 2,
            kind = StudyFlowLiveSessionEventKind.COMPLETED,
            completion = StudyFlowOperationId("op-journal-complete"),
        )
        val operation = completion.toDomainOperation(
            entityRevision = StudyFlowEntityRevision(7),
            originSequence = 12,
            logicalTimestamp = 42,
        )
        assertEquals(StudyFlowDomainOperationTypes.SessionCompleted, operation.operationType)
        assertEquals(StudyFlowEntityId("session-live"), operation.entityId)
        assertEquals(StudyFlowOperationId("op-journal-complete"), operation.operationId)
    }

    private fun snapshot(): StudyFlowLiveSessionSnapshot = StudyFlowLiveSessionSnapshot(
        sessionId = StudyFlowLiveSessionId("session-live"),
        workspaceId = workspaceId,
        activityId = activityId,
        state = StudyFlowLiveSessionState.RUNNING,
        activeExecutorDeviceId = android.deviceId,
        fencingToken = "fence-1",
        sessionEpoch = 1,
        lastSequence = 1,
        cursor = StudyFlowLiveSessionCursor(activityId, "question-1", itemIndex = 1, elapsedMillis = 1_000L),
        participants = listOf(
            StudyFlowLiveSessionParticipant(android, StudyFlowLiveSessionRole.EXECUTOR, joinedAtEpochMillis = 1_000L),
            StudyFlowLiveSessionParticipant(desktop, StudyFlowLiveSessionRole.CONTROLLER, joinedAtEpochMillis = 1_200L),
        ),
        updatedAtEpochMillis = 1_500L,
    )

    private fun event(
        id: String,
        source: StudyFlowDeviceId,
        sequence: Long,
        kind: StudyFlowLiveSessionEventKind,
        cursor: StudyFlowLiveSessionCursor = StudyFlowLiveSessionCursor(activityId, "question-1"),
        completion: StudyFlowOperationId? = null,
    ): StudyFlowLiveSessionEvent = StudyFlowLiveSessionEvent(
        eventId = StudyFlowMessageId(id),
        sessionId = StudyFlowLiveSessionId("session-live"),
        workspaceId = workspaceId,
        sourceDeviceId = source,
        sessionEpoch = 1,
        sequence = sequence,
        kind = kind,
        cursor = cursor,
        completionOperationId = completion,
        occurredAtEpochMillis = 2_000L + sequence,
    )

    private fun peer(
        id: String,
        kind: StudyFlowLinkClientKind,
        capabilities: List<StudyFlowLinkCapability>,
    ): StudyFlowLinkPeerDescriptor = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId(id),
        displayName = id,
        clientKind = kind,
        capabilities = capabilities,
    )
}
