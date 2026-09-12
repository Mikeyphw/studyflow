package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyFlowCompactCompanionAttentionTest {
    private val workspace = StudyFlowWorkspaceId("workspace-main")
    private val router = StudyFlowAttentionDeliveryRouter()
    private val projector = StudyFlowCompactCompanionProjector()
    private val phone = peer("phone", StudyFlowLinkClientKind.ANDROID)
    private val desktop = peer("desktop", StudyFlowLinkClientKind.DESKTOP)

    @Test
    fun compactCompanionsMirrorParentRouteButNeverBecomeOwners() {
        val occurrence = occurrence("review")
        val route = router.route(
            occurrence = occurrence,
            devices = listOf(
                phone.asAttentionDeviceCapability(primaryUserDevice = true),
                desktop.asAttentionDeviceCapability(),
            ),
            nowEpochMillis = 10_000L,
            context = StudyFlowAttentionRoutingContext(primaryDeviceId = phone.deviceId),
        )
        val projection = projector.project(
            occurrence = occurrence,
            route = route,
            companions = listOf(wearable("watch", phone.deviceId)),
            nowEpochMillis = 10_000L,
        )

        assertTrue(projection.deliverable)
        assertEquals(phone.deviceId, projection.ownerDeviceId)
        assertEquals(1, projection.cards.size)
        assertEquals(StudyFlowAttentionDeliveryRole.OWNER, projection.cards.single().parentRouteRole)
        assertEquals(StudyFlowCompactAttentionRenderMode.GLANCE, projection.cards.single().renderMode)
        assertEquals(
            listOf(StudyFlowAttentionActionKind.REVIEW_NOW),
            projection.cards.single().actions.take(1).map { it.kind },
        )
    }

    @Test
    fun disconnectedMutedAndStaleCompanionsAreSuppressed() {
        val occurrence = occurrence("suppressed")
        val route = router.route(
            occurrence = occurrence,
            devices = listOf(phone.asAttentionDeviceCapability(primaryUserDevice = true)),
            nowEpochMillis = 10_000L,
            context = StudyFlowAttentionRoutingContext(primaryDeviceId = phone.deviceId),
        )
        val projection = projector.project(
            occurrence = occurrence,
            route = route,
            companions = listOf(
                wearable("muted", phone.deviceId, mutedUntilEpochMillis = 20_000L),
                wearable("stale", phone.deviceId, lastSeenAtEpochMillis = 1_000L),
                wearable("offline", phone.deviceId, StudyFlowCompactCompanionAvailability.DISCONNECTED),
            ),
            nowEpochMillis = 10_000L,
        )

        assertFalse(projection.deliverable)
        assertEquals(3, projection.diagnostics.suppressedCompanionIds.size)
        assertEquals("Compact companions mirror ATT07 parent ownership; they never become owners.",
            projection.diagnostics.explanation)
    }

    @Test
    fun handledLedgerEntrySuppressesCompactReplay() {
        val occurrence = occurrence("handled")
        val route = router.route(
            occurrence = occurrence,
            devices = listOf(phone.asAttentionDeviceCapability(primaryUserDevice = true)),
            nowEpochMillis = 10_000L,
            context = StudyFlowAttentionRoutingContext(primaryDeviceId = phone.deviceId),
        )
        val projection = projector.project(
            occurrence = occurrence,
            route = route,
            companions = listOf(wearable("watch", phone.deviceId)),
            nowEpochMillis = 10_000L,
            ledgerEntry = StudyFlowAttentionLedgerEntry(
                occurrence = occurrence,
                state = StudyFlowAttentionDeliveryState.ACKNOWLEDGED,
                updatedAtEpochMillis = 10_000L,
                targetDeviceId = phone.deviceId,
            ),
        )

        assertFalse(projection.deliverable)
        assertTrue(projection.diagnostics.explanation.contains("suppress replay"))
    }

    @Test
    fun compactProjectionHidesSensitiveTextUnlessAllowed() {
        val occurrence = occurrence("private")
        val route = router.route(
            occurrence = occurrence,
            devices = listOf(phone.asAttentionDeviceCapability(primaryUserDevice = true)),
            nowEpochMillis = 10_000L,
            context = StudyFlowAttentionRoutingContext(primaryDeviceId = phone.deviceId),
        )
        val hidden = projector.project(
            occurrence = occurrence,
            route = route,
            companions = listOf(wearable("private-watch", phone.deviceId, canShowSensitiveText = false)),
            nowEpochMillis = 10_000L,
        ).cards.single()
        val visible = projector.project(
            occurrence = occurrence,
            route = route,
            companions = listOf(wearable("visible-watch", phone.deviceId, canShowSensitiveText = true)),
            nowEpochMillis = 10_000L,
        ).cards.single()

        assertEquals("Review due", hidden.title)
        assertEquals("Mock exam law review", visible.title)
    }

    private fun peer(id: String, kind: StudyFlowLinkClientKind): StudyFlowLinkPeerDescriptor =
        StudyFlowLinkPeerDescriptor(
            deviceId = StudyFlowDeviceId(id),
            displayName = id.replaceFirstChar(Char::uppercaseChar),
            clientKind = kind,
            capabilities = StudyFlowClientProfiles.capabilitiesFor(kind),
        )

    private fun wearable(
        id: String,
        parentDeviceId: StudyFlowDeviceId,
        availability: StudyFlowCompactCompanionAvailability = StudyFlowCompactCompanionAvailability.CONNECTED,
        lastSeenAtEpochMillis: Long? = 9_950L,
        mutedUntilEpochMillis: Long? = null,
        canShowSensitiveText: Boolean = false,
    ): StudyFlowCompactCompanionProfile = StudyFlowCompactCompanionProfile(
        companionId = StudyFlowCompactCompanionId(id),
        displayName = id,
        parentDeviceId = parentDeviceId,
        formFactor = StudyFlowCompactCompanionFormFactor.WEARABLE,
        availability = availability,
        lastSeenAtEpochMillis = lastSeenAtEpochMillis,
        mutedUntilEpochMillis = mutedUntilEpochMillis,
        canShowSensitiveText = canShowSensitiveText,
    )

    private fun occurrence(id: String): StudyFlowAttentionOccurrence = StudyFlowAttentionOccurrence(
        occurrenceId = StudyFlowAttentionOccurrenceId.deterministic("att08", id),
        workspaceId = workspace,
        category = StudyFlowAttentionCategory.DUE_REVIEW,
        priority = StudyFlowAttentionPriority.HIGH,
        scheduledAtEpochMillis = 9_000L,
        expiresAtEpochMillis = 20_000L,
        deliveryPolicy = StudyFlowAttentionDeliveryPolicy.PRIMARY_DEVICE,
        sourceDeviceId = StudyFlowDeviceId("source"),
        actions = listOf(
            StudyFlowAttentionAction(StudyFlowAttentionActionKind.REVIEW_NOW),
            StudyFlowAttentionAction(StudyFlowAttentionActionKind.SNOOZE),
            StudyFlowAttentionAction(StudyFlowAttentionActionKind.MOVE_TO_DEVICE),
        ),
        payload = kotlinx.serialization.json.JsonObject(
            mapOf("title" to kotlinx.serialization.json.JsonPrimitive("Mock exam law review")),
        ),
    )
}
