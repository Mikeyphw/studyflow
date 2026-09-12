package com.mikeyphw.studyflow.tv.settings

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPairingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvSettingsSurfaceTest {
    @Test
    fun minimalSettingsStayTvSafeAndPresentationOnly() {
        val state = StudyFlowTvSettingsSurface.defaultState()
        val cards = StudyFlowTvSettingsSurface.cards(state)
        val text = cards.joinToString(" ") { "${it.title} ${it.body}" }

        assertEquals(StudyFlowTvSettingsSurface.cardCount, cards.size)
        assertTrue(StudyFlowTvSettingsSurface.settingsArePresentationOnly)
        assertTrue(StudyFlowTvSettingsSurface.accountManagementDisabledOnTv)
        assertTrue(StudyFlowTvSettingsSurface.importExportDisabledOnTv)
        assertTrue(StudyFlowTvSettingsSurface.databaseAndRepositoryAccessDisabledOnTv)
        assertTrue(text.contains("Local-first privacy"))
        assertTrue(text.contains("control protocol v1"))
        assertFalse(text.contains("file://"))
        assertFalse(text.contains("RoomDatabase"))
        assertFalse(text.contains("SAF"))
    }

    @Test
    fun pairedSettingsExposeRevocationAndFocusPreference() {
        var state = StudyFlowTvSettingsState(
            pairingState = StudyFlowTvPairingState.PAIRED,
            pairedDeviceName = "Study phone",
        )
        assertTrue(StudyFlowTvSettingsAction.REVOKE_PAIRING in StudyFlowTvSettingsSurface.availableActions(state))

        state = StudyFlowTvSettingsSurface.applyAction(state, StudyFlowTvSettingsAction.TOGGLE_HIGH_VISIBILITY_FOCUS)
        assertTrue(state.highVisibilityFocus)
        state = StudyFlowTvSettingsSurface.applyAction(state, StudyFlowTvSettingsAction.OPEN_ABOUT)
        assertTrue(state.aboutVisible)
    }

    @Test
    fun revokePairingMapsToSharedIntentAndFocusItemsStayDeterministic() {
        val state = StudyFlowTvSettingsState(
            pairingState = StudyFlowTvPairingState.PAIRED,
            pairedDeviceName = "Study phone",
        )
        val command = StudyFlowTvSettingsSurface.commandForAction(
            action = StudyFlowTvSettingsAction.REVOKE_PAIRING,
            commandId = "settings-revoke",
            issuedAtEpochMillis = 1_000L,
        )

        assertEquals(StudyFlowTvControlAction.REVOKE_PAIRING, command.action)
        assertEquals("revoke-pairing", command.targetId)
        assertEquals(
            StudyFlowTvSettingsSurface.cards(state).size + StudyFlowTvSettingsSurface.availableActions(state).size,
            StudyFlowTvSettingsSurface.focusItems(state).size,
        )
    }
}
