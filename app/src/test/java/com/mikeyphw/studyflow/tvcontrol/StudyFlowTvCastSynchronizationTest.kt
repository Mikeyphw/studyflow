package com.mikeyphw.studyflow.tvcontrol

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationReceipt
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWireResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvCastSynchronizationTest {
    @Test
    fun receiptMustMatchTheExactSnapshotToAcknowledgeSynchronization() {
        val snapshot = StudyFlowTvPresentationSnapshot.waitingForPlan(
            studyPlanId = "plan-1",
            generatedAtEpochMillis = 1_000L,
        )
        val acknowledged = StudyFlowTvWireResponse(
            requestId = "request-1",
            ok = true,
            applied = true,
            presentationReceipt = StudyFlowTvPresentationReceipt(
                snapshotId = snapshot.snapshotId,
                generatedAtEpochMillis = snapshot.generatedAtEpochMillis,
            ),
            respondedAtEpochMillis = 1_100L,
        )
        val wrongReceipt = acknowledged.copy(
            requestId = "request-2",
            presentationReceipt = StudyFlowTvPresentationReceipt(
                snapshotId = "older-snapshot",
                generatedAtEpochMillis = 900L,
            ),
        )

        assertTrue(StudyFlowTvCastSynchronizationPolicy.acknowledges(snapshot, acknowledged))
        assertFalse(StudyFlowTvCastSynchronizationPolicy.acknowledges(snapshot, wrongReceipt))
    }

    @Test
    fun synchronizationContractRequiresExplicitStopReconnectAndAuthenticatedTransport() {
        assertEquals("TVOV24", StudyFlowTvCastSynchronizationContract.overlay)
        assertTrue(StudyFlowTvCastSynchronizationContract.explicitStartAndStopRequired)
        assertTrue(StudyFlowTvCastSynchronizationContract.canonicalPresentationFeedRequired)
        assertTrue(StudyFlowTvCastSynchronizationContract.authenticatedTvov22TransportRequired)
        assertTrue(StudyFlowTvCastSynchronizationContract.serializedLatestSnapshotDeliveryRequired)
        assertTrue(StudyFlowTvCastSynchronizationContract.receiverReconnectResyncRequired)
        assertTrue(StudyFlowTvCastSynchronizationContract.presentationReceiptAcknowledgementRequired)
        assertTrue(StudyFlowTvCastSynchronizationContract.pendingRemoteStopRetriedAfterReconnect)
    }

    @Test
    fun expiredSessionResponsesAreRecognizedAsCastAuthorizationFailures() {
        val response = StudyFlowTvWireResponse(
            requestId = "request-expired",
            ok = false,
            reason = "session-expired-or-missing",
            respondedAtEpochMillis = 2_000L,
        )

        assertTrue(StudyFlowTvCastSynchronizationPolicy.isSessionFailure(response))
    }
}
