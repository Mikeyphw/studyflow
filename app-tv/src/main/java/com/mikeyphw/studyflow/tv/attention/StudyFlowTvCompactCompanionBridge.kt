package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRoute
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowCompactAttentionProjection
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionFormFactor
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionId
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionProfile
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionProjector

object StudyFlowTvCompactCompanionBridge {
    fun remoteOverlayCompanion(nowEpochMillis: Long): StudyFlowCompactCompanionProfile =
        StudyFlowCompactCompanionProfile(
            companionId = StudyFlowCompactCompanionId("tv-remote-overlay"),
            displayName = "TV remote overlay",
            parentDeviceId = StudyFlowTvAttentionDeliveryRouterBridge.localDeviceId,
            formFactor = StudyFlowCompactCompanionFormFactor.WEB_COMPACT,
            availability = StudyFlowCompactCompanionAvailability.CONNECTED,
            maxActions = 3,
            canHandoff = true,
            canVibrate = false,
            canShowSensitiveText = false,
            lastSeenAtEpochMillis = nowEpochMillis,
        )

    fun projectForegroundOverlay(
        occurrence: StudyFlowAttentionOccurrence,
        route: StudyFlowAttentionDeliveryRoute,
        nowEpochMillis: Long,
    ): StudyFlowCompactAttentionProjection = StudyFlowCompactCompanionProjector().project(
        occurrence = occurrence,
        route = route,
        companions = listOf(remoteOverlayCompanion(nowEpochMillis)),
        nowEpochMillis = nowEpochMillis,
    )
}
