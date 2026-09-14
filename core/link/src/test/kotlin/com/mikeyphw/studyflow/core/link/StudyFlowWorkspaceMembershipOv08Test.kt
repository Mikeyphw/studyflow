package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowWorkspaceMembershipOv08Test {
    @Test fun trustIsNotWorkspaceAuthority() {
        val local = StudyFlowDeviceId("device-local")
        val peer = StudyFlowDeviceId("device-peer")
        val a = StudyFlowWorkspaceId("plan-a")
        val b = StudyFlowWorkspaceId("plan-b")
        val manager = StudyFlowWorkspaceMembershipManager(local, InMemoryStudyFlowWorkspaceMembershipStore())
        assertFalse(manager.canRead(a, peer))
        manager.grant(a, peer, StudyFlowWorkspaceAccess.WRITE, 1L)
        assertTrue(manager.canWrite(a, peer))
        assertFalse(manager.canRead(b, peer))
        assertTrue(manager.canWrite(a, local))
    }

    @Test fun revocationIsScopedToOneWorkspace() {
        val local = StudyFlowDeviceId("device-local")
        val peer = StudyFlowDeviceId("device-peer")
        val a = StudyFlowWorkspaceId("plan-a")
        val b = StudyFlowWorkspaceId("plan-b")
        val manager = StudyFlowWorkspaceMembershipManager(local, InMemoryStudyFlowWorkspaceMembershipStore())
        manager.grant(a, peer, StudyFlowWorkspaceAccess.WRITE, 1L)
        manager.grant(b, peer, StudyFlowWorkspaceAccess.READ, 2L)
        manager.revoke(a, peer)
        assertFalse(manager.canRead(a, peer))
        assertTrue(manager.canRead(b, peer))
        assertFalse(manager.canWrite(b, peer))
    }
}
