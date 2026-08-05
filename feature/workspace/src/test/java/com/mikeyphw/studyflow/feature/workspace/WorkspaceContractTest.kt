package com.mikeyphw.studyflow.feature.workspace

import com.mikeyphw.studyflow.core.designsystem.shouldStackWorkspacePair
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.navigation.PlanWorkspaceTab
import com.mikeyphw.studyflow.core.navigation.PracticeWorkspaceTab
import com.mikeyphw.studyflow.core.navigation.ProgressWorkspacePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceContractTest {
    @Test
    fun fourWorkspaceControlsExposeCompleteTabSets() {
        assertEquals(4, PlanWorkspaceTab.entries.size)
        assertEquals(3, PracticeWorkspaceTab.entries.size)
        assertEquals(listOf(7, 30, 90), ProgressWorkspacePeriod.entries.map { it.days })
    }

    @Test
    fun largeFontsAndCompactWidthsStackPairedControls() {
        assertTrue(shouldStackWorkspacePair(maxWidthDp = 390f, fontScale = 2f))
        assertTrue(shouldStackWorkspacePair(maxWidthDp = 320f, fontScale = 1f))
        assertFalse(shouldStackWorkspacePair(maxWidthDp = 600f, fontScale = 1f))
    }

    @Test
    fun timerAccessibilityAnnouncementsChangeByMinuteExceptFinalSeconds() {
        assertEquals(3_660L, accessibilityTimerBucket(3_719L))
        assertEquals(60L, accessibilityTimerBucket(119L))
        assertEquals(10L, accessibilityTimerBucket(10L))
        assertEquals(3L, accessibilityTimerBucket(3L))
    }

    @Test
    fun predictiveBackIsInterceptedOnlyForMeaningfulActiveSessionState() {
        assertTrue(shouldInterceptFocusBack(StudySessionStatus.RUNNING))
        assertTrue(shouldInterceptFocusBack(StudySessionStatus.PAUSED))
        assertFalse(shouldInterceptFocusBack(StudySessionStatus.COMPLETED))
        assertFalse(shouldInterceptFocusBack(StudySessionStatus.CANCELLED))
        assertFalse(shouldInterceptFocusBack(null))
    }
}
