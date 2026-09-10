package com.mikeyphw.studyflow.tv.link

import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialChunk
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialChunkAck
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialStreamOffer
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialStreamReducer
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryAck
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationScheduleReducer
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock

class StudyFlowTvNotificationMediaReceiver(
    tvIdentity: StudyFlowLinkPeerDescriptor,
    clock: StudyFlowPeerClock,
) {
    private val notifications = StudyFlowNotificationScheduleReducer(tvIdentity, clock)
    private val media = StudyFlowMaterialStreamReducer(tvIdentity, clock)

    init {
        require(StudyFlowLinkCapabilities.WorkspaceWrite !in tvIdentity.capabilities) {
            "TV notification/media receiver must remain read-only"
        }
        require(StudyFlowLinkCapabilities.LibraryImport !in tvIdentity.capabilities) {
            "TV notification/media receiver must not import media into the library"
        }
    }

    fun applyNotification(delivery: StudyFlowNotificationDelivery): StudyFlowNotificationDeliveryAck =
        notifications.apply(delivery)

    fun acceptMaterial(offer: StudyFlowMaterialStreamOffer): Boolean = media.accept(offer)

    fun applyChunk(chunk: StudyFlowMaterialChunk): StudyFlowMaterialChunkAck = media.apply(chunk)
}
