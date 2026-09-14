package com.mikeyphw.studyflow.feature.workspace

import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov15WorkspaceHonestyContractsTest {
    @Test
    fun optionalStreamFailuresDoNotBlockWholeWorkspace() {
        assertFalse(
            shouldBlockWorkspaceForOptionalStreams(
                listOf(StudyFlowLoadState.Failed("library"), StudyFlowLoadState.Loading),
            ),
        )
    }

    @Test
    fun focusBackIsNotTrappedWhileLoading() {
        assertFalse(shouldInterceptFocusBackWhileLoading(sessionsLoading = true, StudySessionStatus.RUNNING))
        assertTrue(shouldInterceptFocusBackWhileLoading(sessionsLoading = false, StudySessionStatus.RUNNING))
    }

    @Test
    fun failedOptionalStateFallsBackToSafeEmptyValue() {
        val failed: StudyFlowLoadState<List<String>> = StudyFlowLoadState.Failed("questions")
        assertEquals(emptyList<String>(), failed.readyOrFallback(emptyList()))
    }
}
