package com.mikeyphw.studyflow.tv.link

import com.mikeyphw.studyflow.core.link.StudyFlowActivityHandoffRegistry
import com.mikeyphw.studyflow.core.link.StudyFlowHandoffDecision
import com.mikeyphw.studyflow.core.link.StudyFlowHandoffId
import com.mikeyphw.studyflow.core.link.StudyFlowHandoffOffer
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock

class StudyFlowTvSflpHandoffBridge(
    private val tvIdentity: StudyFlowLinkPeerDescriptor,
    clock: StudyFlowPeerClock,
) {
    private val registry = StudyFlowActivityHandoffRegistry(clock)

    fun acceptPresentationHandoff(offer: StudyFlowHandoffOffer): Boolean {
        registry.issue(offer)
        return registry.consume(offer.handoffId, tvIdentity) is StudyFlowHandoffDecision.Accepted
    }

    fun hasConsumed(handoffId: StudyFlowHandoffId): Boolean = registry.consumed(handoffId)
}
