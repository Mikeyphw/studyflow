package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrenceId
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionSource
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionTrigger
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationKind
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyFlowTvAttentionSurfaceTest {
    @Test
    fun foregroundOnlyCapabilityDoesNotClaimTvBackgroundDelivery() {
        val capability = StudyFlowTvAttentionCapabilityState()

        assertTrue(StudyFlowTvAttentionSurface.foregroundOnly)
        assertTrue(capability.foregroundBanners)
        assertFalse(capability.systemNotifications)
        assertFalse(capability.backgroundAlarms)
        assertFalse(capability.closedAppDelivery)
        assertTrue(capability.requiresDesignatedNotifierFallback)
        assertTrue(StudyFlowTvAttentionSurface.backgroundDeliveryRequiresDesignatedNotifier)
        assertTrue(StudyFlowTvAttentionSurface.doesNotClaimSystemNotifications)
        assertTrue(StudyFlowTvAttentionSurface.doesNotClaimBackgroundAlarms)
    }

    @Test
    fun dueReviewBecomesDpadSafeReviewBanner() {
        val banner = StudyFlowTvAttentionSurface.bannerFor(
            occurrence = occurrence(StudyFlowAttentionCategory.DUE_REVIEW),
            title = "Reviews ready",
            body = "Use the TV review surface.",
            deliveredAtEpochMillis = 2_000L,
        )

        assertEquals("attention-due_review", banner.occurrenceId)
        assertEquals(StudyFlowTvDestination.REVIEWS, banner.route)
        assertEquals(StudyFlowTvAttentionAction.REVIEW_NOW, banner.actions.first())
        assertTrue(StudyFlowTvAttentionAction.MOVE_TO_PHONE in banner.actions)
        assertTrue(banner.actions.size <= StudyFlowTvAttentionSurface.maximumActions)
        assertEquals(
            banner.actions.size,
            StudyFlowTvAttentionSurface.focusItems(StudyFlowTvAttentionState(activeBanner = banner)).size,
        )
    }

    @Test
    fun examCountdownRoutesToProgressWithoutClaimingBackgroundAlarm() {
        val banner = StudyFlowTvAttentionSurface.bannerFor(occurrence(StudyFlowAttentionCategory.EXAM_COUNTDOWN))

        assertEquals(StudyFlowTvDestination.PROGRESS, banner.route)
        assertTrue(StudyFlowTvAttentionAction.OPEN in banner.actions)
        assertTrue(StudyFlowTvAttentionAction.MOVE_TO_PHONE in banner.actions)
        assertTrue(StudyFlowTvAttentionSurface.capabilitySummary().contains("background-alarms=false"))
    }

    private fun occurrence(category: StudyFlowAttentionCategory) = StudyFlowAttentionOccurrence(
        occurrenceId = StudyFlowAttentionOccurrenceId("attention-${category.name.lowercase()}"),
        category = category,
        source = StudyFlowAttentionSource.REMOTE_DELIVERY,
        trigger = StudyFlowAttentionTrigger.NOTIFICATION,
        notificationKind = when (category) {
            StudyFlowAttentionCategory.DUE_REVIEW -> StudyFlowNotificationKind.REVIEW_DUE
            StudyFlowAttentionCategory.EXAM_COUNTDOWN -> StudyFlowNotificationKind.PLAN_WARNING
            else -> StudyFlowNotificationKind.PLAN_WARNING
        },
        scheduledAtEpochMillis = 1_000L,
        expiresAtEpochMillis = 10_000L,
    )
}
