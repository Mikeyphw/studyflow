package com.mikeyphw.studyflow.reminders

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

/** ATT08 Android production bridge: phone-owned Wear bridge, lock screen and Quick Settings tile. */
object AndroidCompactCompanionAttentionBridge {
    fun defaultPhoneCompanions(
        parentDeviceId: StudyFlowDeviceId,
        nowEpochMillis: Long,
    ): List<StudyFlowCompactCompanionProfile> = listOf(
        StudyFlowCompactCompanionProfile(
            StudyFlowCompactCompanionId("android-wearable-primary"), "Wearable glance", parentDeviceId,
            StudyFlowCompactCompanionFormFactor.WEARABLE, StudyFlowCompactCompanionAvailability.CONNECTED,
            3, true, true, false, nowEpochMillis,
        ),
        StudyFlowCompactCompanionProfile(
            StudyFlowCompactCompanionId("android-lock-screen-chip"), "Lock-screen chip", parentDeviceId,
            StudyFlowCompactCompanionFormFactor.LOCK_SCREEN, StudyFlowCompactCompanionAvailability.CONNECTED,
            2, true, false, false, nowEpochMillis,
        ),
        StudyFlowCompactCompanionProfile(
            StudyFlowCompactCompanionId("android-quick-tile"), "Quick settings tile", parentDeviceId,
            StudyFlowCompactCompanionFormFactor.QUICK_TILE, StudyFlowCompactCompanionAvailability.CONNECTED,
            1, true, false, false, nowEpochMillis,
        ),
    )

    fun projectPhoneOwnedCompanions(
        occurrence: StudyFlowAttentionOccurrence,
        route: StudyFlowAttentionDeliveryRoute,
        parentDeviceId: StudyFlowDeviceId,
        nowEpochMillis: Long,
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
    ): StudyFlowCompactAttentionProjection = StudyFlowCompactCompanionProjector().project(
        occurrence = occurrence,
        route = route,
        companions = defaultPhoneCompanions(parentDeviceId, nowEpochMillis),
        nowEpochMillis = nowEpochMillis,
        ledgerEntry = ledgerEntry,
    )
}
