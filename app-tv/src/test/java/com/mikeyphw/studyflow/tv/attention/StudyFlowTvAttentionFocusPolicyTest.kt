package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusProfile
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrenceId
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionPriority
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyFlowTvAttentionFocusPolicyTest {
    @Test
    fun minimalTvPolicyIsReadOnlyAndRoutesAwayFromTv() {
        val occurrence = StudyFlowAttentionOccurrence(
            occurrenceId = StudyFlowAttentionOccurrenceId("att10-tv"),
            workspaceId = StudyFlowWorkspaceId("workspace"),
            category = StudyFlowAttentionCategory.PLANNED_STUDY,
            priority = StudyFlowAttentionPriority.NORMAL,
            scheduledAtEpochMillis = 1_000L,
            originalScheduledAtEpochMillis = 1_000L,
        )
        val projection = StudyFlowTvAttentionDeliveryRouterBridge.focusProjection(
            occurrence = occurrence,
            policy = StudyFlowAttentionFocusPolicy(profile = StudyFlowAttentionFocusProfile.MINIMAL_TV, quietWindow = null),
            nowEpochMillis = 2_000L,
        )
        assertEquals("ROUTE_TO_PHONE", projection.outcome)
        assertTrue(projection.controlsReadOnly)
        assertTrue(projection.remoteControlRequired)
        val tv = StudyFlowLinkPeerDescriptor(
            StudyFlowDeviceId("tv-test"),
            "TV",
            StudyFlowLinkClientKind.ANDROID_TV,
            capabilities = emptyList(),
        )
        assertTrue(
            StudyFlowTvAttentionDeliveryRouterBridge
                .localForegroundCapability(tv, 2_000L)
                .notificationPermissionGranted
                .not(),
        )
    }
}
