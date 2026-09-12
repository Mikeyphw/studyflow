package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRoute
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerEntry
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowCompactAttentionProjection
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionFormFactor
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionId
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionProfile
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionProjector
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId

object StudyFlowTvCompactCompanionBridge {
    fun remoteOverlayCompanion(
        parentDeviceId: StudyFlowDeviceId,
        nowEpochMillis: Long,
    ): StudyFlowCompactCompanionProfile =
        StudyFlowCompactCompanionProfile(
            companionId = StudyFlowCompactCompanionId("tv-remote-overlay"),
            displayName = "TV remote overlay",
            parentDeviceId = parentDeviceId,
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
        parentDeviceId: StudyFlowDeviceId,
        nowEpochMillis: Long,
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
    ): StudyFlowCompactAttentionProjection = StudyFlowCompactCompanionProjector().project(
        occurrence = occurrence,
        route = route,
        companions = listOf(remoteOverlayCompanion(parentDeviceId, nowEpochMillis)),
        nowEpochMillis = nowEpochMillis,
        ledgerEntry = ledgerEntry,
    )
}
