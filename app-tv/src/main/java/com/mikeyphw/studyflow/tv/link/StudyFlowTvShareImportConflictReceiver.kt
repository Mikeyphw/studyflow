package com.mikeyphw.studyflow.tv.link

import com.mikeyphw.studyflow.core.link.StudyFlowConflictReview
import com.mikeyphw.studyflow.core.link.StudyFlowConflictReviewReducer
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportIntent
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportReducer
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportStatus

class StudyFlowTvShareImportConflictReceiver(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val imports = StudyFlowShareImportReducer(localDevice, clock)
    private val conflicts = StudyFlowConflictReviewReducer(localDevice, clock)

    fun acceptsPreview(delivery: StudyFlowShareImportDelivery): Boolean =
        delivery.offer.intent == StudyFlowShareImportIntent.CLIPBOARD_PREVIEW ||
            delivery.offer.intent == StudyFlowShareImportIntent.SHARE_PREVIEW

    fun receiveImport(delivery: StudyFlowShareImportDelivery): StudyFlowShareImportStatus {
        if (!acceptsPreview(delivery)) return StudyFlowShareImportStatus.REJECTED
        return imports.apply(delivery).status
    }

    fun openConflictReview(review: StudyFlowConflictReview): Boolean = conflicts.open(review)
}
