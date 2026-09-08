package com.mikeyphw.studyflow.tv.home

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvNavigationShell

/**
 * Structured Android TV Home surface introduced in TVOV06.
 *
 * The Home surface is the default TV entry point. It presents remote-first study cards,
 * exposes paired-phone control entry points, and keeps navigation/cast intent routing on the
 * same small enum model introduced by the TV shell and core tv-control protocol.
 */
enum class StudyFlowTvHomeCardId {
    TODAY,
    PRACTICE,
    REVIEWS,
    MOCK_EXAM,
}

enum class StudyFlowTvHomeAction {
    CONTINUE_STUDYING,
    PAIR_PHONE,
    START_PRACTICE,
}

data class StudyFlowTvHomeCard(
    val id: StudyFlowTvHomeCardId,
    val destination: StudyFlowTvDestination,
    val row: Int,
    val column: Int,
) {
    init {
        require(row >= 0) { "TV Home card row must be non-negative." }
        require(column >= 0) { "TV Home card column must be non-negative." }
    }
}

object StudyFlowTvHomeSurface {
    const val overlay = "TVOV06"
    const val ownsDefaultTvDestination = true
    const val structuredHomeCards = true
    const val phonePairingEntryVisible = true
    const val usesSharedNavigationDestinations = true
    const val usesSharedControlProtocol = true
    const val cardCount = 4
    const val primaryActionCount = 3
    const val cardColumnStart = 1
    const val primaryActionRow = 4

    val cards: List<StudyFlowTvHomeCard> = listOf(
        StudyFlowTvHomeCard(StudyFlowTvHomeCardId.TODAY, StudyFlowTvDestination.TODAY, row = 0, column = cardColumnStart),
        StudyFlowTvHomeCard(StudyFlowTvHomeCardId.PRACTICE, StudyFlowTvDestination.PRACTICE, row = 0, column = cardColumnStart + 1),
        StudyFlowTvHomeCard(StudyFlowTvHomeCardId.REVIEWS, StudyFlowTvDestination.REVIEWS, row = 1, column = cardColumnStart),
        StudyFlowTvHomeCard(StudyFlowTvHomeCardId.MOCK_EXAM, StudyFlowTvDestination.MOCK_EXAM, row = 1, column = cardColumnStart + 1),
    )

    val primaryActions: List<StudyFlowTvHomeAction> = listOf(
        StudyFlowTvHomeAction.CONTINUE_STUDYING,
        StudyFlowTvHomeAction.PAIR_PHONE,
        StudyFlowTvHomeAction.START_PRACTICE,
    )

    fun defaultDestination(): StudyFlowTvDestination = StudyFlowTvNavigationShell.defaultDestination()

    fun destinationForAction(action: StudyFlowTvHomeAction): StudyFlowTvDestination = when (action) {
        StudyFlowTvHomeAction.CONTINUE_STUDYING -> StudyFlowTvDestination.STUDY_SESSION
        StudyFlowTvHomeAction.PAIR_PHONE -> StudyFlowTvDestination.SETTINGS
        StudyFlowTvHomeAction.START_PRACTICE -> StudyFlowTvDestination.PRACTICE
    }

    fun focusItems(): List<StudyFlowTvFocusItem> =
        cards.map { card ->
            StudyFlowTvFocusItem(
                id = "home-card-${card.id.name.lowercase()}",
                role = StudyFlowTvFocusRole.CONTENT_CARD,
                row = card.row,
                column = card.column,
            )
        } + primaryActions.mapIndexed { index, action ->
            StudyFlowTvFocusItem(
                id = "home-action-${action.name.lowercase()}",
                role = StudyFlowTvFocusRole.PRIMARY_ACTION,
                row = primaryActionRow,
                column = cardColumnStart + index,
            )
        }

    fun commandForAction(
        action: StudyFlowTvHomeAction,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand {
        if (action == StudyFlowTvHomeAction.PAIR_PHONE) {
            return StudyFlowTvControlCommand(
                commandId = commandId,
                surface = StudyFlowTvSurface.HOME,
                action = StudyFlowTvControlAction.OPEN,
                targetId = "pair-phone",
                issuedAtEpochMillis = issuedAtEpochMillis,
            )
        }
        return StudyFlowTvNavigationShell.openCommandFor(
            destination = destinationForAction(action),
            commandId = commandId,
            issuedAtEpochMillis = issuedAtEpochMillis,
        )
    }
}
