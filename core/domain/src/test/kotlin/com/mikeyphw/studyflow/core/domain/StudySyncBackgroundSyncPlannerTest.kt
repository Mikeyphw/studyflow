package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyFlowSyncProviderIds
import com.mikeyphw.studyflow.core.model.StudyFlowSyncSettings
import com.mikeyphw.studyflow.core.model.StudySyncBackgroundMode
import com.mikeyphw.studyflow.core.model.StudySyncBackgroundSyncPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySyncBackgroundSyncPlannerTest {
    @Test
    fun `fresh install has effective background sync off while preserving manual default`() {
        val settings = StudyFlowSyncSettings.LocalFirstDefault
        val plan = StudySyncBackgroundSyncPlanner.plan(settings)

        assertEquals(StudySyncBackgroundMode.MANUAL_ONLY, settings.backgroundSyncMode)
        assertEquals(StudySyncBackgroundMode.OFF, plan.mode)
        assertFalse(plan.canSchedule)
    }

    @Test
    fun `manual only is the default configured mode and schedules nothing`() {
        val settings = StudyFlowSyncSettings(
            syncEnabled = true,
            providerId = StudyFlowSyncProviderIds.LOCAL_FOLDER,
        )
        val plan = StudySyncBackgroundSyncPlanner.plan(settings)

        assertEquals(StudySyncBackgroundMode.MANUAL_ONLY, plan.mode)
        assertFalse(plan.enabled)
        assertTrue(plan.canSchedule.not())
    }

    @Test
    fun `automatic modes require provider and encrypted remote backups`() {
        val blocked = StudyFlowSyncSettings(
            syncEnabled = true,
            backgroundSyncMode = StudySyncBackgroundMode.CHECK_DAILY,
        )
        val ready = blocked.copy(
            providerId = StudyFlowSyncProviderIds.LOCAL_FOLDER,
            providerLocation = "content://sync",
        )

        assertEquals(
            "Choose a sync provider before enabling background sync.",
            StudySyncBackgroundSyncPlanner.plan(blocked).blockedReason,
        )
        assertTrue(StudySyncBackgroundSyncPlanner.plan(ready).canSchedule)
    }
}
