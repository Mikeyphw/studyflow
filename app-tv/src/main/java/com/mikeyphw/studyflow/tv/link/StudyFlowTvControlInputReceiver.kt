package com.mikeyphw.studyflow.tv.link

import com.mikeyphw.studyflow.core.link.StudyFlowControlApplyResult
import com.mikeyphw.studyflow.core.link.StudyFlowControlCommand
import com.mikeyphw.studyflow.core.link.StudyFlowControlCommandReducer
import com.mikeyphw.studyflow.core.link.StudyFlowControlLease
import com.mikeyphw.studyflow.core.link.StudyFlowInputDelegationOffer
import com.mikeyphw.studyflow.core.link.StudyFlowInputDelegationReducer
import com.mikeyphw.studyflow.core.link.StudyFlowInputDelegationResult
import com.mikeyphw.studyflow.core.link.StudyFlowInputEvent
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock

class StudyFlowTvControlInputReceiver(
    tvIdentity: StudyFlowLinkPeerDescriptor,
    clock: StudyFlowPeerClock,
) {
    private val control = StudyFlowControlCommandReducer(tvIdentity, clock)
    private val input = StudyFlowInputDelegationReducer(tvIdentity, clock)

    init {
        require(StudyFlowLinkCapabilities.WorkspaceWrite !in tvIdentity.capabilities) {
            "TV control receiver must not advertise workspace.write"
        }
    }

    fun rememberLease(lease: StudyFlowControlLease) = control.rememberLease(lease)
    fun applyControl(command: StudyFlowControlCommand): StudyFlowControlApplyResult = control.apply(command)
    fun acceptInput(offer: StudyFlowInputDelegationOffer): StudyFlowInputDelegationResult = input.accept(offer)
    fun applyInput(event: StudyFlowInputEvent): StudyFlowInputDelegationResult = input.apply(event)
}
