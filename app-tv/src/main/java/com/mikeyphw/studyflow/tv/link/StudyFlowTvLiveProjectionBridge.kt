package com.mikeyphw.studyflow.tv.link

import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionState
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationApplyResult
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationFrame
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationFrameAck
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationProjectionOffer
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationProjectionReceiver
import com.mikeyphw.studyflow.core.link.StudyFlowProjectionId

class StudyFlowTvLiveProjectionBridge(
    private val tvIdentity: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val receiver = StudyFlowPresentationProjectionReceiver(tvIdentity, clock)

    fun canObserveLiveSession(snapshot: StudyFlowLiveSessionSnapshot): Boolean =
        snapshot.state != StudyFlowLiveSessionState.COMPLETED &&
            StudyFlowLinkCapabilities.SessionPresent in tvIdentity.capabilities

    fun acceptPresentationProjection(offer: StudyFlowPresentationProjectionOffer): Boolean =
        receiver.accept(offer) is StudyFlowPresentationApplyResult.Accepted

    fun applyProjectionFrame(frame: StudyFlowPresentationFrame): Boolean =
        receiver.applyFrame(frame) is StudyFlowPresentationApplyResult.Accepted

    fun acknowledge(projectionId: StudyFlowProjectionId): StudyFlowPresentationFrameAck? = receiver.acknowledge(projectionId)
}
