package com.mikeyphw.studyflow.desktop

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

object DesktopCompactCompanionAttentionBridge {
    fun localMiniSurface(parentDeviceId: StudyFlowDeviceId, nowEpochMillis: Long): StudyFlowCompactCompanionProfile =
        StudyFlowCompactCompanionProfile(
            companionId = StudyFlowCompactCompanionId("desktop-mini-dashboard"),
            displayName = "Desktop mini dashboard",
            parentDeviceId = parentDeviceId,
            formFactor = StudyFlowCompactCompanionFormFactor.DESKTOP_MINI,
            availability = StudyFlowCompactCompanionAvailability.CONNECTED,
            maxActions = 3,
            canHandoff = true,
            canVibrate = false,
            canShowSensitiveText = true,
            lastSeenAtEpochMillis = nowEpochMillis,
        )

    fun projectMiniDashboard(
        occurrence: StudyFlowAttentionOccurrence,
        route: StudyFlowAttentionDeliveryRoute,
        parentDeviceId: StudyFlowDeviceId,
        nowEpochMillis: Long,
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
    ): StudyFlowCompactAttentionProjection = StudyFlowCompactCompanionProjector().project(
        occurrence = occurrence,
        route = route,
        companions = listOf(localMiniSurface(parentDeviceId, nowEpochMillis)),
        nowEpochMillis = nowEpochMillis,
        ledgerEntry = ledgerEntry,
    )
}
