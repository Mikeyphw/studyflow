package com.mikeyphw.studyflow.tv.library

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusPolicy
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvLibrarySurfaceTest {
    @Test
    fun libraryOwnsRoadmapLimitedReferenceContract() {
        assertEquals("TVOV14", StudyFlowTvLibrarySurface.overlay)
        assertTrue(StudyFlowTvLibrarySurface.ownsLimitedLibraryReferenceSurface)
        assertTrue(StudyFlowTvLibrarySurface.curatedReferenceCardsVisible)
        assertTrue(StudyFlowTvLibrarySurface.shortReadableExcerptsVisible)
        assertTrue(StudyFlowTvLibrarySurface.fileManagementDisabledOnTv)
        assertTrue(StudyFlowTvLibrarySurface.pairedPhoneCanOpenReferencesButNotManageFiles)
        assertTrue(StudyFlowTvLibrarySurface.emitsCastStateWithoutMirroring)
        assertTrue(StudyFlowTvLibrarySurface.usesSharedControlProtocol)
    }

    @Test
    fun defaultLibraryContainsCuratedReferenceMaterialOnly() {
        val snapshot = StudyFlowTvLibrarySurface.defaultSnapshot()

        assertEquals(StudyFlowTvLibrarySurface.referenceCount, snapshot.references.size)
        assertEquals(StudyFlowTvLibraryReferenceKind.LEGISLATION, snapshot.references.first().kind)
        assertTrue(snapshot.references.all { it.excerpt.length >= 24 })
        assertTrue(snapshot.references.any { it.kind == StudyFlowTvLibraryReferenceKind.ERROR_NOTEBOOK_EXCERPT })
        assertTrue(snapshot.collectionLabel.contains("Pinned"))
    }

    @Test
    fun libraryActionsNavigateWithoutManagingFiles() {
        val snapshot = StudyFlowTvLibrarySurface.defaultSnapshot()
        val next = StudyFlowTvLibrarySurface.applyAction(snapshot, StudyFlowTvLibraryAction.NEXT_REFERENCE)
        val previous = StudyFlowTvLibrarySurface.applyAction(next, StudyFlowTvLibraryAction.PREVIOUS_REFERENCE)

        assertEquals(1, next.activeReferenceIndex)
        assertEquals(0, previous.activeReferenceIndex)
        assertEquals(snapshot.references, next.references)
        assertEquals(StudyFlowTvDestination.LIBRARY, StudyFlowTvLibrarySurface.destinationForAction(StudyFlowTvLibraryAction.OPEN_REFERENCE, snapshot))
        assertEquals(StudyFlowTvDestination.PRACTICE, StudyFlowTvLibrarySurface.destinationForAction(StudyFlowTvLibraryAction.START_RELATED_PRACTICE, next))
        assertEquals(StudyFlowTvDestination.HOME, StudyFlowTvLibrarySurface.destinationForAction(StudyFlowTvLibraryAction.RETURN_HOME, next))
    }

    @Test
    fun libraryFocusCommandsAndCastStateUseSharedProtocol() {
        val snapshot = StudyFlowTvLibrarySurface.defaultSnapshot()
        val focusItems = StudyFlowTvLibrarySurface.focusItems(snapshot)
        val command = StudyFlowTvLibrarySurface.commandForAction(
            action = StudyFlowTvLibraryAction.NEXT_REFERENCE,
            snapshot = snapshot,
            commandId = "library-command-1",
            issuedAtEpochMillis = 15_000L,
        )
        val castState = StudyFlowTvLibrarySurface.castStateFor(
            snapshot = snapshot,
            castId = "library-cast-1",
            updatedAtEpochMillis = 16_000L,
        )

        assertEquals("library-reference-constitution_article_five", StudyFlowTvFocusPolicy.initialFocusId(focusItems))
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.CONTENT_CARD })
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.PRIMARY_ACTION })
        assertEquals(StudyFlowTvSurface.LIBRARY, command.surface)
        assertEquals(StudyFlowTvControlAction.NEXT, command.action)
        assertEquals(StudyFlowTvSurface.LIBRARY, castState.surface)
        assertEquals(snapshot.activeReference.id.name.lowercase(), castState.activeItemId)
    }
}
