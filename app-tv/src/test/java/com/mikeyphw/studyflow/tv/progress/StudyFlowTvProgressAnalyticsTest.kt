package com.mikeyphw.studyflow.tv.progress

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusPolicy
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvProgressAnalyticsTest {
    @Test
    fun progressAnalyticsOwnsRoadmapMetricAndWeakSubjectContract() {
        assertEquals("TVOV12", StudyFlowTvProgressAnalytics.overlay)
        assertTrue(StudyFlowTvProgressAnalytics.ownsProgressAnalyticsSurface)
        assertTrue(StudyFlowTvProgressAnalytics.weeklyProgressAccuracyVolumeReadinessVisible)
        assertTrue(StudyFlowTvProgressAnalytics.weakSubjectSummaryVisible)
        assertTrue(StudyFlowTvProgressAnalytics.remoteCanOpenRecommendedNextActions)
        assertTrue(StudyFlowTvProgressAnalytics.pairedPhoneCanOpenProgressDestinations)
        assertTrue(StudyFlowTvProgressAnalytics.emitsCastStateWithoutMirroring)
        assertTrue(StudyFlowTvProgressAnalytics.usesSharedControlProtocol)
    }

    @Test
    fun defaultProgressSnapshotContainsMetricsSubjectsAndWeakestSubject() {
        val snapshot = StudyFlowTvProgressAnalytics.defaultSnapshot()

        assertEquals(StudyFlowTvProgressAnalytics.metricCount, snapshot.metrics.size)
        assertEquals(StudyFlowTvProgressAnalytics.subjectCount, snapshot.subjects.size)
        assertEquals("Administrative Law", snapshot.weakestSubject.title)
        assertEquals(68, snapshot.averageReadinessPercent)
        assertEquals(72, snapshot.weeklyProgressPercent)
    }

    @Test
    fun progressActionsMapToConcreteTvDestinations() {
        assertEquals(StudyFlowTvDestination.TODAY, StudyFlowTvProgressAnalytics.destinationForAction(StudyFlowTvProgressAction.OPEN_TODAY))
        assertEquals(StudyFlowTvDestination.PRACTICE, StudyFlowTvProgressAnalytics.destinationForAction(StudyFlowTvProgressAction.OPEN_PRACTICE))
        assertEquals(StudyFlowTvDestination.REVIEWS, StudyFlowTvProgressAnalytics.destinationForAction(StudyFlowTvProgressAction.OPEN_REVIEWS))
        assertEquals(StudyFlowTvDestination.MOCK_EXAM, StudyFlowTvProgressAnalytics.destinationForAction(StudyFlowTvProgressAction.OPEN_MOCK_EXAM))
    }

    @Test
    fun progressFocusCommandsAndCastStateUseSharedProtocols() {
        val snapshot = StudyFlowTvProgressAnalytics.defaultSnapshot()
        val focusItems = StudyFlowTvProgressAnalytics.focusItems(snapshot)
        val command = StudyFlowTvProgressAnalytics.commandForAction(
            action = StudyFlowTvProgressAction.OPEN_REVIEWS,
            commandId = "progress-command-1",
            issuedAtEpochMillis = 10_000L,
        )
        val castState = StudyFlowTvProgressAnalytics.castStateFor(
            snapshot = snapshot,
            castId = "progress-cast-1",
            updatedAtEpochMillis = 11_000L,
        )

        assertEquals("progress-metric-weekly_progress", StudyFlowTvFocusPolicy.initialFocusId(focusItems))
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.CONTENT_CARD })
        assertTrue(focusItems.any { it.role == StudyFlowTvFocusRole.PRIMARY_ACTION })
        assertEquals(StudyFlowTvSurface.REVIEW, command.surface)
        assertEquals(StudyFlowTvControlAction.OPEN, command.action)
        assertEquals("reviews", command.targetId)
        assertEquals(StudyFlowTvSurface.PROGRESS, castState.surface)
        assertEquals(snapshot.averageReadinessPercent, castState.progressPercent ?: -1)
    }
}
