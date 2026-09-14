package com.mikeyphw.studyflow.widget

import com.mikeyphw.studyflow.reminders.WidgetRefreshResult
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyFlowWidgetOv17ResilienceContractTest {
    @Test
    fun widgetRefreshResultTracksIsolatedInstanceFailures() {
        val result = WidgetRefreshResult(
            boundInstanceCount = 3,
            configuredInstanceCount = 3,
            state = "ready+data_unavailable",
            failedInstanceCount = 1,
        )
        assertEquals(1, result.failedInstanceCount)
    }
}
