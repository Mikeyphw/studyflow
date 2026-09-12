package com.mikeyphw.studyflow.reminders

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRoute
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowCompactAttentionProjection
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionFormFactor
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionId
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionProfile
import com.mikeyphw.studyflow.core.link.StudyFlowCompactCompanionProjector
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId

/**
 * ATT08 Android bridge for phone-owned wearable/compact companions.
 *
 * The phone remains the ATT07 owner. Watches, lock-screen chips and quick settings tiles receive
 * bounded companion cards only when their parent phone is part of the route. This avoids pretending
 * a companion can wake independently when the phone did not own/mirror the delivery.
 */
object AndroidCompactCompanionAttentionBridge {
    fun defaultPhoneCompanions(
        parentDeviceId: StudyFlowDeviceId = AndroidStudyFlowAttentionRouterBridge.localDeviceId,
        nowEpochMillis: Long,
    ): List<StudyFlowCompactCompanionProfile> = listOf(
        StudyFlowCompactCompanionProfile(
            companionId = StudyFlowCompactCompanionId("android-wearable-primary"),
            displayName = "Wearable glance",
            parentDeviceId = parentDeviceId,
            formFactor = StudyFlowCompactCompanionFormFactor.WEARABLE,
            availability = StudyFlowCompactCompanionAvailability.CONNECTED,
            maxActions = 3,
            canHandoff = true,
            canVibrate = true,
            canShowSensitiveText = false,
            lastSeenAtEpochMillis = nowEpochMillis,
        ),
        StudyFlowCompactCompanionProfile(
            companionId = StudyFlowCompactCompanionId("android-lock-screen-chip"),
            displayName = "Lock-screen chip",
            parentDeviceId = parentDeviceId,
            formFactor = StudyFlowCompactCompanionFormFactor.LOCK_SCREEN,
            availability = StudyFlowCompactCompanionAvailability.CONNECTED,
            maxActions = 2,
            canHandoff = true,
            canVibrate = false,
            canShowSensitiveText = false,
            lastSeenAtEpochMillis = nowEpochMillis,
        ),
        StudyFlowCompactCompanionProfile(
            companionId = StudyFlowCompactCompanionId("android-quick-tile"),
            displayName = "Quick settings tile",
            parentDeviceId = parentDeviceId,
            formFactor = StudyFlowCompactCompanionFormFactor.QUICK_TILE,
            availability = StudyFlowCompactCompanionAvailability.CONNECTED,
            maxActions = 1,
            canHandoff = true,
            canVibrate = false,
            canShowSensitiveText = false,
            lastSeenAtEpochMillis = nowEpochMillis,
        ),
    )

    fun projectPhoneOwnedCompanions(
        occurrence: StudyFlowAttentionOccurrence,
        route: StudyFlowAttentionDeliveryRoute,
        nowEpochMillis: Long,
        companions: List<StudyFlowCompactCompanionProfile> = defaultPhoneCompanions(nowEpochMillis = nowEpochMillis),
    ): StudyFlowCompactAttentionProjection = StudyFlowCompactCompanionProjector().project(
        occurrence = occurrence,
        route = route,
        companions = companions,
        nowEpochMillis = nowEpochMillis,
    )
}
