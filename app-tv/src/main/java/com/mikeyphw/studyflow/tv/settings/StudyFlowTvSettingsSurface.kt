package com.mikeyphw.studyflow.tv.settings

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlProtocol
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPairingState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole

enum class StudyFlowTvSettingsCardId {
    PAIRING,
    FOCUS_VISIBILITY,
    ATTENTION,
    PRIVACY,
    ABOUT,
}

enum class StudyFlowTvSettingsAction {
    MANAGE_PAIRING,
    REVOKE_PAIRING,
    TOGGLE_HIGH_VISIBILITY_FOCUS,
    OPEN_ATTENTION_STATUS,
    OPEN_ABOUT,
}

data class StudyFlowTvSettingsCard(
    val id: StudyFlowTvSettingsCardId,
    val title: String,
    val body: String,
    val row: Int,
    val column: Int,
) {
    init {
        require(title.isNotBlank()) { "TV settings card title must not be blank." }
        require(body.isNotBlank()) { "TV settings card body must not be blank." }
        require(row >= 0) { "TV settings card row must be non-negative." }
        require(column >= 0) { "TV settings card column must be non-negative." }
    }
}

data class StudyFlowTvSettingsState(
    val pairingState: StudyFlowTvPairingState = StudyFlowTvPairingState.UNPAIRED,
    val pairedDeviceName: String? = null,
    val highVisibilityFocus: Boolean = false,
    val aboutVisible: Boolean = false,
    val attentionStatusVisible: Boolean = false,
) {
    init {
        require(pairingState != StudyFlowTvPairingState.PAIRED || !pairedDeviceName.isNullOrBlank()) {
            "Paired TV settings state must identify the paired phone."
        }
    }
}

object StudyFlowTvSettingsSurface {
    const val overlay = "TVOV15"
    const val ownsMinimalTvSettings = true
    const val settingsArePresentationOnly = true
    const val accountManagementDisabledOnTv = true
    const val importExportDisabledOnTv = true
    const val databaseAndRepositoryAccessDisabledOnTv = true
    const val pairingStatusAndRevocationVisible = true
    const val focusAccessibilityPreferenceRepresented = true
    const val localFirstPrivacyReminderVisible = true
    const val tvSafeAboutVisible = true
    const val foregroundAttentionBannersVisible = true
    const val backgroundDeliveryIsNotClaimedOnTv = true
    const val designatedNotifierFallbackVisible = true
    const val att11ReliabilityAuditReadOnly = true
    const val att11ExportDelegatesToCompanion = true
    const val usesSharedControlProtocol = true
    const val cardCount = 5
    const val actionCount = 5
    const val cardColumnStart = 1
    const val actionRow = 3

    fun defaultState(): StudyFlowTvSettingsState = StudyFlowTvSettingsState()

    fun cards(state: StudyFlowTvSettingsState): List<StudyFlowTvSettingsCard> = listOf(
        StudyFlowTvSettingsCard(
            id = StudyFlowTvSettingsCardId.PAIRING,
            title = "Phone pairing",
            body = pairingSummary(state),
            row = 0,
            column = cardColumnStart,
        ),
        StudyFlowTvSettingsCard(
            id = StudyFlowTvSettingsCardId.FOCUS_VISIBILITY,
            title = "Focus visibility",
            body = if (state.highVisibilityFocus) {
                "High-visibility focus preference is on."
            } else {
                "Standard focus preference is on."
            },
            row = 0,
            column = cardColumnStart + 1,
        ),
        StudyFlowTvSettingsCard(
            id = StudyFlowTvSettingsCardId.ATTENTION,
            title = "TV attention",
            body = "Foreground banners are available while this TV app is open. ATT11 delivery audit is read-only here; closed-TV reminders and JSON/CSV export stay with a capable phone, desktop, or Web notifier.",
            row = 1,
            column = cardColumnStart,
        ),
        StudyFlowTvSettingsCard(
            id = StudyFlowTvSettingsCardId.PRIVACY,
            title = "Local-first privacy",
            body = "TV control receives StudyFlow intents and presentation state, never database rows or arbitrary screen mirroring.",
            row = 1,
            column = cardColumnStart + 1,
        ),
        StudyFlowTvSettingsCard(
            id = StudyFlowTvSettingsCardId.ABOUT,
            title = "About and diagnostics",
            body = "StudyFlow TV · control protocol v${StudyFlowTvControlProtocol.version} · TV-safe diagnostics only.",
            row = 2,
            column = cardColumnStart,
        ),
    )

    fun availableActions(state: StudyFlowTvSettingsState): List<StudyFlowTvSettingsAction> = buildList {
        add(StudyFlowTvSettingsAction.MANAGE_PAIRING)
        if (state.pairingState == StudyFlowTvPairingState.PAIRED) {
            add(StudyFlowTvSettingsAction.REVOKE_PAIRING)
        }
        add(StudyFlowTvSettingsAction.TOGGLE_HIGH_VISIBILITY_FOCUS)
        add(StudyFlowTvSettingsAction.OPEN_ATTENTION_STATUS)
        add(StudyFlowTvSettingsAction.OPEN_ABOUT)
    }

    fun applyAction(
        state: StudyFlowTvSettingsState,
        action: StudyFlowTvSettingsAction,
    ): StudyFlowTvSettingsState = when (action) {
        StudyFlowTvSettingsAction.MANAGE_PAIRING -> state.copy(
            pairingState = if (state.pairingState == StudyFlowTvPairingState.PAIRED) {
                StudyFlowTvPairingState.PAIRED
            } else {
                StudyFlowTvPairingState.PAIRING_CODE_VISIBLE
            },
            aboutVisible = false,
            attentionStatusVisible = false,
        )
        StudyFlowTvSettingsAction.REVOKE_PAIRING -> state.copy(
            pairingState = StudyFlowTvPairingState.REVOKED,
            pairedDeviceName = null,
            aboutVisible = false,
            attentionStatusVisible = false,
        )
        StudyFlowTvSettingsAction.TOGGLE_HIGH_VISIBILITY_FOCUS -> state.copy(
            highVisibilityFocus = !state.highVisibilityFocus,
            aboutVisible = false,
            attentionStatusVisible = false,
        )
        StudyFlowTvSettingsAction.OPEN_ATTENTION_STATUS -> state.copy(
            attentionStatusVisible = true,
            aboutVisible = false,
        )
        StudyFlowTvSettingsAction.OPEN_ABOUT -> state.copy(
            aboutVisible = true,
            attentionStatusVisible = false,
        )
    }

    fun focusItems(state: StudyFlowTvSettingsState): List<StudyFlowTvFocusItem> =
        cards(state).map { card ->
            StudyFlowTvFocusItem(
                id = "settings-card-${card.id.name.lowercase()}",
                role = StudyFlowTvFocusRole.CONTENT_CARD,
                row = card.row,
                column = card.column,
            )
        } + availableActions(state).mapIndexed { index, action ->
            StudyFlowTvFocusItem(
                id = "settings-action-${action.name.lowercase()}",
                role = StudyFlowTvFocusRole.PRIMARY_ACTION,
                row = actionRow,
                column = cardColumnStart + index,
            )
        }

    fun commandForAction(
        action: StudyFlowTvSettingsAction,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand = StudyFlowTvControlCommand(
        commandId = commandId,
        surface = StudyFlowTvSurface.SETTINGS,
        action = when (action) {
            StudyFlowTvSettingsAction.REVOKE_PAIRING -> StudyFlowTvControlAction.REVOKE_PAIRING
            else -> StudyFlowTvControlAction.OPEN
        },
        targetId = when (action) {
            StudyFlowTvSettingsAction.MANAGE_PAIRING -> "manage-pairing"
            StudyFlowTvSettingsAction.REVOKE_PAIRING -> "revoke-pairing"
            StudyFlowTvSettingsAction.TOGGLE_HIGH_VISIBILITY_FOCUS -> "focus-visibility"
            StudyFlowTvSettingsAction.OPEN_ATTENTION_STATUS -> "attention-status"
            StudyFlowTvSettingsAction.OPEN_ABOUT -> "about"
        },
        issuedAtEpochMillis = issuedAtEpochMillis,
    )

    private fun pairingSummary(state: StudyFlowTvSettingsState): String = when (state.pairingState) {
        StudyFlowTvPairingState.UNPAIRED -> "No phone is paired. Open pairing to approve a local controller."
        StudyFlowTvPairingState.PAIRING_CODE_VISIBLE -> "Pairing is waiting for explicit approval. Codes expire automatically."
        StudyFlowTvPairingState.PAIRED -> "Paired with ${state.pairedDeviceName}. Pairing can be revoked from either device."
        StudyFlowTvPairingState.REVOKED -> "Phone pairing was revoked. Start pairing again to reconnect."
    }
}
