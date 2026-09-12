package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRoute
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowCompactAttentionProjection
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionFormFactor
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionId
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionProfile
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionProjector

object DesktopCompactCompanionAttentionBridge {
    fun localMiniSurface(nowEpochMillis: Long): StudyFlowCompactCompanionProfile =
        StudyFlowCompactCompanionProfile(
            companionId = StudyFlowCompactCompanionId("desktop-mini-dashboard"),
            displayName = "Desktop mini dashboard",
            parentDeviceId = DesktopAttentionDeliveryRouterBridge.localDeviceId,
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
        nowEpochMillis: Long,
    ): StudyFlowCompactAttentionProjection = StudyFlowCompactCompanionProjector().project(
        occurrence = occurrence,
        route = route,
        companions = listOf(localMiniSurface(nowEpochMillis)),
        nowEpochMillis = nowEpochMillis,
    )
}
