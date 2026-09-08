package com.mikeyphw.studyflow.tv.library

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvCastState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvNavigationShell

/**
 * Structured Android TV limited library/reference surface introduced in TVOV14.
 *
 * The TV library exposes a curated, read-only set of reference cards and short excerpts. It never
 * imports files, exports packages, opens SAF pickers, edits notes, or reads repositories directly.
 * The phone/tablet app remains responsible for managing the library; TV can open an approved
 * reference, step between references, cast presentation state, and jump into related practice.
 */
enum class StudyFlowTvLibraryReferenceId {
    CONSTITUTION_ARTICLE_FIVE,
    ADMINISTRATIVE_ACTS,
    PORTUGUESE_AGREEMENT,
    INFORMATICS_SECURITY,
}

enum class StudyFlowTvLibraryReferenceKind {
    LEGISLATION,
    SUMMARY,
    ERROR_NOTEBOOK_EXCERPT,
    CHECKLIST,
}

enum class StudyFlowTvLibraryAction {
    OPEN_REFERENCE,
    PREVIOUS_REFERENCE,
    NEXT_REFERENCE,
    START_RELATED_PRACTICE,
    RETURN_HOME,
}

data class StudyFlowTvLibraryReference(
    val id: StudyFlowTvLibraryReferenceId,
    val sourceId: String = id.name.lowercase(),
    val title: String,
    val subject: String,
    val sourceLabel: String,
    val excerpt: String,
    val kind: StudyFlowTvLibraryReferenceKind,
    val relatedDestination: StudyFlowTvDestination,
    val row: Int,
    val column: Int,
) {
    init {
        require(sourceId.isNotBlank()) { "TV library source id must not be blank." }
        require(title.isNotBlank()) { "TV library reference title must not be blank." }
        require(subject.isNotBlank()) { "TV library reference subject must not be blank." }
        require(sourceLabel.isNotBlank()) { "TV library reference source must not be blank." }
        require(excerpt.isNotBlank()) { "TV library reference excerpt must not be blank." }
        require(row >= 0) { "TV library reference row must be non-negative." }
        require(column >= 0) { "TV library reference column must be non-negative." }
        require(relatedDestination != StudyFlowTvDestination.SETTINGS) { "TV library references must route to learning surfaces." }
    }
}

data class StudyFlowTvLibrarySnapshot(
    val libraryId: String,
    val title: String,
    val collectionLabel: String,
    val references: List<StudyFlowTvLibraryReference>,
    val activeReferenceIndex: Int = 0,
) {
    init {
        require(libraryId.isNotBlank()) { "TV library id must not be blank." }
        require(title.isNotBlank()) { "TV library title must not be blank." }
        require(collectionLabel.isNotBlank()) { "TV library collection label must not be blank." }
        require(references.isNotEmpty()) { "TV library must expose at least one reference." }
        require(activeReferenceIndex in references.indices) { "TV library active index is out of bounds." }
        require(references.map { it.id }.toSet().size == references.size) { "TV library references must be unique." }
    }

    val activeReference: StudyFlowTvLibraryReference
        get() = references[activeReferenceIndex]
}

object StudyFlowTvLibrarySurface {
    const val overlay = "TVOV14"
    const val ownsLimitedLibraryReferenceSurface = true
    const val curatedReferenceCardsVisible = true
    const val shortReadableExcerptsVisible = true
    const val fileManagementDisabledOnTv = true
    const val pairedPhoneCanOpenReferencesButNotManageFiles = true
    const val emitsCastStateWithoutMirroring = true
    const val usesSharedControlProtocol = true
    const val referenceCount = 4
    const val actionCount = 5
    const val referenceColumnStart = 1
    const val actionRow = 3

    fun defaultSnapshot(): StudyFlowTvLibrarySnapshot = StudyFlowTvLibrarySnapshot(
        libraryId = "tv-library-core-references",
        title = "Reference library",
        collectionLabel = "Pinned review material",
        references = listOf(
            StudyFlowTvLibraryReference(
                id = StudyFlowTvLibraryReferenceId.CONSTITUTION_ARTICLE_FIVE,
                title = "Constitution article 5",
                subject = "Constitutional Law",
                sourceLabel = "Pinned legislation excerpt",
                excerpt = "Rights and guarantees review summary for TV reading.",
                kind = StudyFlowTvLibraryReferenceKind.LEGISLATION,
                relatedDestination = StudyFlowTvDestination.REVIEWS,
                row = 0,
                column = referenceColumnStart,
            ),
            StudyFlowTvLibraryReference(
                id = StudyFlowTvLibraryReferenceId.ADMINISTRATIVE_ACTS,
                title = "Administrative acts",
                subject = "Administrative Law",
                sourceLabel = "High-frequency summary",
                excerpt = "Competence, purpose, form, motive, and object checklist.",
                kind = StudyFlowTvLibraryReferenceKind.SUMMARY,
                relatedDestination = StudyFlowTvDestination.PRACTICE,
                row = 0,
                column = referenceColumnStart + 1,
            ),
            StudyFlowTvLibraryReference(
                id = StudyFlowTvLibraryReferenceId.PORTUGUESE_AGREEMENT,
                title = "Agreement traps",
                subject = "Portuguese",
                sourceLabel = "Error notebook excerpt",
                excerpt = "Common agreement mistakes collected from recent reviews.",
                kind = StudyFlowTvLibraryReferenceKind.ERROR_NOTEBOOK_EXCERPT,
                relatedDestination = StudyFlowTvDestination.REVIEWS,
                row = 1,
                column = referenceColumnStart,
            ),
            StudyFlowTvLibraryReference(
                id = StudyFlowTvLibraryReferenceId.INFORMATICS_SECURITY,
                title = "Security basics",
                subject = "Informatics",
                sourceLabel = "Checklist",
                excerpt = "Authentication, backup, phishing, and safe browsing reminders.",
                kind = StudyFlowTvLibraryReferenceKind.CHECKLIST,
                relatedDestination = StudyFlowTvDestination.PRACTICE,
                row = 1,
                column = referenceColumnStart + 1,
            ),
        ),
    )

    val actions: List<StudyFlowTvLibraryAction> = listOf(
        StudyFlowTvLibraryAction.OPEN_REFERENCE,
        StudyFlowTvLibraryAction.PREVIOUS_REFERENCE,
        StudyFlowTvLibraryAction.NEXT_REFERENCE,
        StudyFlowTvLibraryAction.START_RELATED_PRACTICE,
        StudyFlowTvLibraryAction.RETURN_HOME,
    )

    fun applyAction(
        snapshot: StudyFlowTvLibrarySnapshot,
        action: StudyFlowTvLibraryAction,
    ): StudyFlowTvLibrarySnapshot = when (action) {
        StudyFlowTvLibraryAction.PREVIOUS_REFERENCE -> snapshot.copy(
            activeReferenceIndex = (snapshot.activeReferenceIndex - 1).coerceAtLeast(0),
        )
        StudyFlowTvLibraryAction.NEXT_REFERENCE -> snapshot.copy(
            activeReferenceIndex = (snapshot.activeReferenceIndex + 1).coerceAtMost(snapshot.references.lastIndex),
        )
        StudyFlowTvLibraryAction.OPEN_REFERENCE,
        StudyFlowTvLibraryAction.START_RELATED_PRACTICE,
        StudyFlowTvLibraryAction.RETURN_HOME,
        -> snapshot
    }

    fun destinationForAction(
        action: StudyFlowTvLibraryAction,
        snapshot: StudyFlowTvLibrarySnapshot,
    ): StudyFlowTvDestination = when (action) {
        StudyFlowTvLibraryAction.OPEN_REFERENCE,
        StudyFlowTvLibraryAction.PREVIOUS_REFERENCE,
        StudyFlowTvLibraryAction.NEXT_REFERENCE,
        -> StudyFlowTvDestination.LIBRARY
        StudyFlowTvLibraryAction.START_RELATED_PRACTICE -> snapshot.activeReference.relatedDestination
        StudyFlowTvLibraryAction.RETURN_HOME -> StudyFlowTvDestination.HOME
    }

    fun focusItems(snapshot: StudyFlowTvLibrarySnapshot): List<StudyFlowTvFocusItem> {
        val references = snapshot.references.map { reference ->
            StudyFlowTvFocusItem(
                id = "library-reference-${reference.id.name.lowercase()}",
                role = StudyFlowTvFocusRole.CONTENT_CARD,
                row = reference.row,
                column = reference.column,
            )
        }
        val actionItems = actions.mapIndexed { index, action ->
            StudyFlowTvFocusItem(
                id = "library-action-${action.name.lowercase()}",
                role = StudyFlowTvFocusRole.PRIMARY_ACTION,
                row = actionRow,
                column = referenceColumnStart + index,
            )
        }
        return references + actionItems
    }

    fun commandForAction(
        action: StudyFlowTvLibraryAction,
        snapshot: StudyFlowTvLibrarySnapshot,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand = when (action) {
        StudyFlowTvLibraryAction.PREVIOUS_REFERENCE -> libraryCommand(action, snapshot, commandId, issuedAtEpochMillis, StudyFlowTvControlAction.PREVIOUS)
        StudyFlowTvLibraryAction.NEXT_REFERENCE -> libraryCommand(action, snapshot, commandId, issuedAtEpochMillis, StudyFlowTvControlAction.NEXT)
        else -> StudyFlowTvNavigationShell.openCommandFor(
            destination = destinationForAction(action, snapshot),
            commandId = commandId,
            issuedAtEpochMillis = issuedAtEpochMillis,
        )
    }

    fun castStateFor(
        snapshot: StudyFlowTvLibrarySnapshot,
        castId: String,
        updatedAtEpochMillis: Long,
    ): StudyFlowTvCastState = StudyFlowTvCastState(
        castId = castId,
        surface = StudyFlowTvSurface.LIBRARY,
        title = snapshot.activeReference.title,
        subtitle = "${snapshot.activeReference.subject} · ${snapshot.activeReference.sourceLabel}",
        progressPercent = ((snapshot.activeReferenceIndex + 1) * 100 / snapshot.references.size).coerceIn(0, 100),
        activeItemId = snapshot.activeReference.sourceId,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun libraryCommand(
        action: StudyFlowTvLibraryAction,
        snapshot: StudyFlowTvLibrarySnapshot,
        commandId: String,
        issuedAtEpochMillis: Long,
        controlAction: StudyFlowTvControlAction,
    ): StudyFlowTvControlCommand = StudyFlowTvControlCommand(
        commandId = commandId,
        surface = StudyFlowTvSurface.LIBRARY,
        action = controlAction,
        targetId = snapshot.activeReference.sourceId,
        optionId = action.name.lowercase(),
        issuedAtEpochMillis = issuedAtEpochMillis,
    )
}
