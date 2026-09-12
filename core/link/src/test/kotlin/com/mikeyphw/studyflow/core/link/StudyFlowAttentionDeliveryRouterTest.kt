package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudyFlowAttentionDeliveryRouterTest {
    private val router = StudyFlowAttentionDeliveryRouter()
    private val workspace = StudyFlowWorkspaceId("workspace-main")
    private val phone = peer("phone", StudyFlowLinkClientKind.ANDROID)
    private val desktop = peer("desktop", StudyFlowLinkClientKind.DESKTOP)
    private val web = peer("web", StudyFlowLinkClientKind.WEB)
    private val tv = peer("tv", StudyFlowLinkClientKind.ANDROID_TV)

    @Test
    fun activeForegroundDeviceOwnsBeforePrimaryWhenPolicyAllowsIt() {
        val route = router.route(
            occurrence = occurrence("active-first", StudyFlowAttentionDeliveryPolicy.ACTIVE_THEN_PRIMARY),
            devices = listOf(
                phone.asAttentionDeviceCapability(primaryUserDevice = true, designatedNotifier = true),
                desktop.asAttentionDeviceCapability(
                    availability = StudyFlowAttentionDeviceAvailability.FOREGROUND,
                    foregroundActive = true,
                    lastSeenAtEpochMillis = 9_900L,
                ),
            ),
            nowEpochMillis = 10_000L,
            context = StudyFlowAttentionRoutingContext(
                primaryDeviceId = phone.deviceId,
                foregroundDeviceId = desktop.deviceId,
            ),
        )

        assertEquals(desktop.deviceId, route.owner?.deviceId)
        assertEquals(StudyFlowAttentionRouteReason.ACTIVE_FOREGROUND, route.reason)
        assertTrue(route.deliverable)
        assertTrue(route.mirrors.any { it.deviceId == phone.deviceId })
    }

    @Test
    fun primaryPolicyFallsBackToDesignatedNotifierOnlyWhenPrimaryCannotDeliver() {
        val route = router.route(
            occurrence = occurrence("primary-fallback", StudyFlowAttentionDeliveryPolicy.PRIMARY_DEVICE),
            devices = listOf(
                phone.asAttentionDeviceCapability(
                    notificationPermissionGranted = false,
                    mutedUntilEpochMillis = 20_000L,
                ),
                web.asAttentionDeviceCapability(designatedNotifier = true),
            ),
            nowEpochMillis = 10_000L,
            context = StudyFlowAttentionRoutingContext(
                primaryDeviceId = phone.deviceId,
                designatedNotifierDeviceIds = listOf(web.deviceId),
            ),
        )

        assertEquals(web.deviceId, route.owner?.deviceId)
        assertEquals(StudyFlowAttentionRouteReason.FALLBACK_BEST_CAPABLE, route.reason)
        assertTrue(phone.deviceId in route.diagnostics.suppressedDeviceIds)
    }

    @Test
    fun phoneAndDesktopPolicyDedupesOwnerAndMirror() {
        val route = router.route(
            occurrence = occurrence("pair", StudyFlowAttentionDeliveryPolicy.PHONE_AND_DESKTOP),
            devices = listOf(
                phone.asAttentionDeviceCapability(primaryUserDevice = true),
                desktop.asAttentionDeviceCapability(
                    availability = StudyFlowAttentionDeviceAvailability.FOREGROUND,
                    foregroundActive = true,
                ),
                tv.asAttentionDeviceCapability(
                    availability = StudyFlowAttentionDeviceAvailability.FOREGROUND,
                    foregroundActive = true,
                ),
            ),
            nowEpochMillis = 10_000L,
            context = StudyFlowAttentionRoutingContext(primaryDeviceId = phone.deviceId, maxMirrors = 3),
        )

        assertEquals(phone.deviceId, route.owner?.deviceId)
        assertEquals(listOf(desktop.deviceId), route.mirrors.map { it.deviceId })
        assertFalse(route.targetDeviceIds.groupingBy { it }.eachCount().any { it.value > 1 })
        assertEquals(StudyFlowAttentionRouteReason.PHONE_DESKTOP_PAIR, route.reason)
    }

    @Test
    fun explicitTargetListPreventsAccidentalBroadcast() {
        val route = router.route(
            occurrence = occurrence(
                id = "explicit",
                policy = StudyFlowAttentionDeliveryPolicy.ALL_CAPABLE,
                targets = listOf(tv.deviceId),
            ),
            devices = listOf(
                phone.asAttentionDeviceCapability(primaryUserDevice = true),
                desktop.asAttentionDeviceCapability(),
                tv.asAttentionDeviceCapability(
                    availability = StudyFlowAttentionDeviceAvailability.FOREGROUND,
                    foregroundActive = true,
                ),
            ),
            nowEpochMillis = 10_000L,
        )

        assertEquals(tv.deviceId, route.owner?.deviceId)
        assertTrue(route.mirrors.isEmpty())
        assertEquals(StudyFlowAttentionDeliverySurface.FOREGROUND_BANNER, route.owner?.surface)
        assertEquals(StudyFlowAttentionRouteReason.EXPLICIT_TARGET, route.reason)
    }

    @Test
    fun handledLedgerStateSuppressesRedeliveryAcrossDevices() {
        val handled = StudyFlowAttentionLedgerEntry(
            occurrence = occurrence("handled", StudyFlowAttentionDeliveryPolicy.ACTIVE_THEN_PRIMARY),
            state = StudyFlowAttentionDeliveryState.ACKNOWLEDGED,
            updatedAtEpochMillis = 9_000L,
            targetDeviceId = phone.deviceId,
        )
        val route = router.route(
            occurrence = handled.occurrence,
            devices = listOf(phone.asAttentionDeviceCapability(), desktop.asAttentionDeviceCapability()),
            nowEpochMillis = 10_000L,
            ledgerEntry = handled,
        )

        assertFalse(route.deliverable)
        assertEquals(StudyFlowAttentionRouteReason.LEDGER_ALREADY_HANDLED, route.reason)
        assertEquals(StudyFlowAttentionDeliveryState.ACKNOWLEDGED, route.diagnostics.ledgerState)
    }

    @Test
    fun staleMutedAndLedgerOnlyDevicesAreNotOwners() {
        val route = router.route(
            occurrence = occurrence("no-owner", StudyFlowAttentionDeliveryPolicy.ALL_CAPABLE),
            devices = listOf(
                web.asAttentionDeviceCapability(lastSeenAtEpochMillis = 1_000L),
                phone.asAttentionDeviceCapability(mutedUntilEpochMillis = 20_000L),
                tv.asAttentionDeviceCapability(foregroundActive = false),
            ),
            nowEpochMillis = 10_000L,
            context = StudyFlowAttentionRoutingContext(staleAfterMillis = 1_000L),
        )

        assertFalse(route.deliverable)
        assertEquals(StudyFlowAttentionRouteReason.NO_CAPABLE_DEVICE, route.reason)
        assertEquals(3, route.diagnostics.candidateCount)
    }

    @Test
    fun handoffRequestUsesCanonicalOccurrenceAndDistinctDevices() {
        val occurrence = occurrence("handoff", StudyFlowAttentionDeliveryPolicy.ACTIVE_THEN_PRIMARY)
        val handoff = router.createHandoffRequest(
            occurrence = occurrence,
            fromDeviceId = tv.deviceId,
            toDeviceId = phone.deviceId,
            requestedAtEpochMillis = 10_000L,
        )

        assertEquals(occurrence.occurrenceId, handoff.occurrenceId)
        assertEquals(tv.deviceId, handoff.fromDeviceId)
        assertEquals(phone.deviceId, handoff.toDeviceId)
        assertEquals(StudyFlowAttentionActionKind.MOVE_TO_DEVICE, handoff.action)
        assertTrue(handoff.handoffId.startsWith("handoff:"))
    }

    private fun peer(id: String, kind: StudyFlowLinkClientKind): StudyFlowLinkPeerDescriptor =
        StudyFlowLinkPeerDescriptor(
            deviceId = StudyFlowDeviceId(id),
            displayName = id.replaceFirstChar(Char::uppercaseChar),
            clientKind = kind,
            capabilities = StudyFlowClientProfiles.capabilitiesFor(kind),
        )

    private fun occurrence(
        id: String,
        policy: StudyFlowAttentionDeliveryPolicy,
        targets: List<StudyFlowDeviceId> = emptyList(),
    ): StudyFlowAttentionOccurrence = StudyFlowAttentionOccurrence(
        occurrenceId = StudyFlowAttentionOccurrenceId.deterministic("att07", id),
        workspaceId = workspace,
        category = StudyFlowAttentionCategory.DUE_REVIEW,
        priority = StudyFlowAttentionPriority.HIGH,
        scheduledAtEpochMillis = 9_000L,
        expiresAtEpochMillis = 20_000L,
        deliveryPolicy = policy,
        sourceDeviceId = StudyFlowDeviceId("source"),
        targetDeviceIds = targets,
        actions = listOf(
            StudyFlowAttentionAction(StudyFlowAttentionActionKind.OPEN),
            StudyFlowAttentionAction(StudyFlowAttentionActionKind.SNOOZE),
            StudyFlowAttentionAction(StudyFlowAttentionActionKind.MOVE_TO_DEVICE),
        ),
    )
}
