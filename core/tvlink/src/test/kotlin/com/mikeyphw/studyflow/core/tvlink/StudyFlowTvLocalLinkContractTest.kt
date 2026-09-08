package com.mikeyphw.studyflow.core.tvlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvLocalLinkContractTest {
    @Test
    fun transportIsLocalExplicitAndDependencyLight() {
        assertEquals("TVOV22", StudyFlowTvLocalLinkContract.overlay)
        assertTrue(StudyFlowTvLocalLinkContract.androidNsdDiscovery)
        assertTrue(StudyFlowTvLocalLinkContract.localTcpTransport)
        assertTrue(StudyFlowTvLocalLinkContract.explicitPairingCodeRequired)
        assertTrue(StudyFlowTvLocalLinkContract.pairingNonceRequired)
        assertTrue(StudyFlowTvLocalLinkContract.sessionSecretRequired)
        assertTrue(StudyFlowTvLocalLinkContract.sessionsPersistAcrossProcessRestart)
        assertTrue(StudyFlowTvLocalLinkContract.phoneAndTvShareOneTransportModule)
        assertTrue(StudyFlowTvLocalLinkContract.transportUsesNoCastSdk)
        assertTrue(StudyFlowTvLocalLinkContract.transportUsesNoMediaRouter)
    }
}
