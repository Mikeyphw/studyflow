package com.mikeyphw.studyflow.tvcontrol

import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWireResponse
import kotlinx.coroutines.flow.Flow

/** TVOV24 live presentation/cast synchronization product contract. */
object StudyFlowTvCastSynchronizationContract {
    const val overlay = "TVOV24"
    const val explicitStartAndStopRequired = true
    const val canonicalPresentationFeedRequired = true
    const val authenticatedTvov22TransportRequired = true
    const val serializedLatestSnapshotDeliveryRequired = true
    const val receiverReconnectResyncRequired = true
    const val presentationReceiptAcknowledgementRequired = true
    const val pendingRemoteStopRetriedAfterReconnect = true
    const val rawDatabaseRowsForbidden = true
    const val castSdkForbidden = true
    const val mediaRouterForbidden = true
}

enum class StudyFlowTvCastSyncStatus {
    STOPPED,
    WAITING_FOR_PLAN,
    WAITING_FOR_PAIRING,
    WAITING_FOR_RECEIVER,
    SYNCING,
    LIVE,
    STOPPING,
    ERROR,
}

data class StudyFlowTvCastSyncState(
    val enabled: Boolean = false,
    val status: StudyFlowTvCastSyncStatus = StudyFlowTvCastSyncStatus.STOPPED,
    val planId: String? = null,
    val latestSnapshotId: String? = null,
    val acknowledgedSnapshotId: String? = null,
    val acknowledgedAtEpochMillis: Long? = null,
    val remoteStopPending: Boolean = false,
    val lastError: String? = null,
)

/** Testable source seam around the repository-backed TVOV21 presentation projector. */
interface StudyFlowTvPresentationFeed {
    fun observe(planId: StudyPlanId): Flow<StudyFlowTvPresentationSnapshot>
}

object StudyFlowTvCastSynchronizationPolicy {
    private val staleSessionReasons = setOf(
        "no-active-session",
        "session-expired-or-missing",
        "session-authorization-failed",
    )

    fun isSessionFailure(response: StudyFlowTvWireResponse): Boolean = response.reason in staleSessionReasons

    fun acknowledges(
        snapshot: StudyFlowTvPresentationSnapshot,
        response: StudyFlowTvWireResponse,
    ): Boolean {
        val receipt = response.presentationReceipt ?: return false
        return response.ok &&
            receipt.snapshotId == snapshot.snapshotId &&
            receipt.generatedAtEpochMillis == snapshot.generatedAtEpochMillis
    }
}
