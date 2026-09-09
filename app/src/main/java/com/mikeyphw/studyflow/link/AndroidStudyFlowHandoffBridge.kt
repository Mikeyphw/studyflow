package com.mikeyphw.studyflow.link

import com.mikeyphw.studyflow.core.link.StudyFlowActivityHandoffCoordinator
import com.mikeyphw.studyflow.core.link.StudyFlowActivityHandoffRegistry
import com.mikeyphw.studyflow.core.link.StudyFlowActivitySnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowHandoffAccept
import com.mikeyphw.studyflow.core.link.StudyFlowHandoffId
import com.mikeyphw.studyflow.core.link.StudyFlowHandoffOffer
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock

class AndroidStudyFlowHandoffBridge(
    localDevice: StudyFlowLinkPeerDescriptor,
    clock: StudyFlowPeerClock,
) {
    private val registry = StudyFlowActivityHandoffRegistry(clock)
    private val coordinator = StudyFlowActivityHandoffCoordinator(localDevice, registry, clock)

    fun offerActivityToPeer(
        target: StudyFlowLinkPeerDescriptor,
        snapshot: StudyFlowActivitySnapshot,
        handoffId: StudyFlowHandoffId,
        nonceHex: String,
    ): StudyFlowHandoffOffer = coordinator.offerTo(target, snapshot, handoffId, nonceHex)

    fun acceptFromPeer(
        offer: StudyFlowHandoffOffer,
        target: StudyFlowLinkPeerDescriptor,
    ): StudyFlowHandoffAccept = coordinator.accept(offer, target)
}
