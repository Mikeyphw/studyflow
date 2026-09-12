package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyFlowAttentionPolicyRouterTest {
    private val router = StudyFlowAttentionDeliveryRouter()
    private val workspace = StudyFlowWorkspaceId("workspace-att10-router")
    private val phone = peer("phone", StudyFlowLinkClientKind.ANDROID)
    private val desktop = peer("desktop", StudyFlowLinkClientKind.DESKTOP)
    private val tv = peer("tv", StudyFlowLinkClientKind.ANDROID_TV)

    @Test
    fun sleepProfileDefersWithoutLosingCanonicalOccurrence() {
        val occurrence = occurrence("sleep")
        val route = router.routeWithPolicy(
            occurrence,
            listOf(phone.asAttentionDeviceCapability(primaryUserDevice = true)),
            30 * 60_000L,
            StudyFlowAttentionFocusPolicy(
                profile = StudyFlowAttentionFocusProfile.SLEEP_MODE,
                quietWindow = StudyFlowAttentionQuietWindow(true, 0, 120, "UTC"),
                allowHighPriorityQuietBypass = false,
                allowTimeSensitiveQuietBypass = false,
            ),
        )
        assertFalse(route.deliverable)
        assertEquals(StudyFlowAttentionPolicyOutcome.DEFER_UNTIL_QUIET_END, route.policyOutcome)
        assertTrue(route.diagnostics.deferredUntilEpochMillis!! > 30 * 60_000L)
    }

    @Test
    fun minimalTvPolicyRoutesOwnerToPhone() {
        val occurrence = occurrence("phone")
        val route = router.routeWithPolicy(
            occurrence,
            listOf(
                phone.asAttentionDeviceCapability(primaryUserDevice = true),
                desktop.asAttentionDeviceCapability(),
                tv.asAttentionDeviceCapability(availability = StudyFlowAttentionDeviceAvailability.FOREGROUND),
            ),
            10_000L,
            StudyFlowAttentionFocusPolicy(profile = StudyFlowAttentionFocusProfile.MINIMAL_TV, quietWindow = null),
            localClientKind = StudyFlowLinkClientKind.ANDROID_TV,
        )
        assertEquals(phone.deviceId, route.owner?.deviceId)
        assertEquals(StudyFlowAttentionPolicyOutcome.ROUTE_TO_PHONE, route.policyOutcome)
    }

    @Test
    fun overdueReviewEscalationRemainsBounded() {
        val occurrence = occurrence("mirror", scheduledAt = 1_000L)
        val route = router.routeWithPolicy(
            occurrence,
            listOf(
                phone.asAttentionDeviceCapability(primaryUserDevice = true),
                desktop.asAttentionDeviceCapability(),
            ),
            40 * 60_000L,
            StudyFlowAttentionFocusPolicy(quietWindow = null, maxEscalationMirrors = 1),
            StudyFlowAttentionRoutingContext(primaryDeviceId = phone.deviceId, maxMirrors = 3),
        )
        assertEquals(StudyFlowAttentionPolicyOutcome.ESCALATE_MIRROR, route.policyOutcome)
        assertEquals(1, route.mirrors.size)
    }


    @Test
    fun overdueReviewEscalationCanAddMirrorToPrimaryOnlyRoute() {
        val occurrence = occurrence(
            id = "primary-only-mirror",
            scheduledAt = 1_000L,
            deliveryPolicy = StudyFlowAttentionDeliveryPolicy.PRIMARY_DEVICE,
        )
        val route = router.routeWithPolicy(
            occurrence,
            listOf(
                phone.asAttentionDeviceCapability(primaryUserDevice = true),
                desktop.asAttentionDeviceCapability(),
            ),
            40 * 60_000L,
            StudyFlowAttentionFocusPolicy(quietWindow = null, maxEscalationMirrors = 1),
            StudyFlowAttentionRoutingContext(primaryDeviceId = phone.deviceId, maxMirrors = 3),
        )
        assertEquals(phone.deviceId, route.owner?.deviceId)
        assertEquals(listOf(desktop.deviceId), route.mirrors.map { it.deviceId })
        assertEquals(StudyFlowAttentionPolicyOutcome.ESCALATE_MIRROR, route.policyOutcome)
    }

    private fun occurrence(
        id: String,
        scheduledAt: Long = 10_000L,
        deliveryPolicy: StudyFlowAttentionDeliveryPolicy = StudyFlowAttentionDeliveryPolicy.PHONE_AND_DESKTOP,
    ) = StudyFlowAttentionOccurrence(
        occurrenceId = StudyFlowAttentionOccurrenceId.deterministic("att10-router", id),
        workspaceId = workspace,
        category = StudyFlowAttentionCategory.DUE_REVIEW,
        priority = StudyFlowAttentionPriority.NORMAL,
        scheduledAtEpochMillis = scheduledAt,
        originalScheduledAtEpochMillis = scheduledAt,
        expiresAtEpochMillis = 9_999_999L,
        deliveryPolicy = deliveryPolicy,
    )

    private fun peer(id: String, kind: StudyFlowLinkClientKind) = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId(id),
        displayName = id,
        clientKind = kind,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(kind),
    )
}
