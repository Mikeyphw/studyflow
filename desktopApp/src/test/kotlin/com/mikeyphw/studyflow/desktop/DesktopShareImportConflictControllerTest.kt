package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowConflictCandidate
import com.mikeyphw.studyflow.core.link.StudyFlowConflictFieldKind
import com.mikeyphw.studyflow.core.link.StudyFlowConflictResolutionId
import com.mikeyphw.studyflow.core.link.StudyFlowConflictReviewId
import com.mikeyphw.studyflow.core.link.StudyFlowConflictReviewStatus
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityRevision
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopShareImportConflictControllerTest {
    @Test
    fun desktopCreatesImportOfferAndResolvesConflictReview() {
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val web = descriptor("web", StudyFlowLinkClientKind.WEB, StudyFlowClientProfiles.Web)
        val controller = DesktopShareImportConflictController(desktop, StudyFlowPeerClock { 5_000L })
        val offer = controller.sharedMarkdownImport(
            importId = StudyFlowShareImportId("desktop-share-1"),
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            target = web,
            title = "Shared notes",
            markdown = "# Shared",
        )
        assertEquals(web.deviceId, offer.targetDeviceId)
        val reviewId = StudyFlowConflictReviewId("desktop-conflict-1")
        assertTrue(controller.queueConflictReview(reviewId, StudyFlowWorkspaceId("workspace-main"), desktop, candidate()))
        assertEquals(
            StudyFlowConflictReviewStatus.RESOLVED,
            controller.resolveMerge(
                resolutionId = StudyFlowConflictResolutionId("desktop-resolution-1"),
                reviewId = reviewId,
                workspaceId = StudyFlowWorkspaceId("workspace-main"),
                sequence = 1L,
                mergedSummary = "Merged answer",
            ).status,
        )
    }

    private fun candidate() = StudyFlowConflictCandidate(
        entityId = StudyFlowEntityId("entity-1"),
        localRevision = StudyFlowEntityRevision(1),
        remoteRevision = StudyFlowEntityRevision(2),
        fieldKinds = listOf(StudyFlowConflictFieldKind.CONTENT),
        localSummary = "Local content",
        remoteSummary = "Remote content",
    )

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
