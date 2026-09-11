package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowPresenceTest {
    private val workspace = StudyFlowWorkspaceId("workspace-presence")
    private val local = descriptor("device-local", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
    private val phone = descriptor(
        "device-phone",
        StudyFlowLinkClientKind.ANDROID,
        StudyFlowClientProfiles.AndroidPhone,
    )

    @Test
    fun onlineAwayOfflineStaleAndExpiryAreDistinct() {
        val policy = StudyFlowPresencePolicy(staleAfterMillis = 100L, expiryMillis = 300L)
        val registry = StudyFlowPresenceRegistry(local.deviceId, policy)
        val publisher = StudyFlowLocalPresencePublisher(
            localDevice = local,
            registry = registry,
            clock = StudyFlowPeerClock { 1_000L },
            instanceId = StudyFlowPresenceInstanceId("instance-local"),
            sessionId = StudyFlowPresenceSessionId("session-local"),
            sessionStartedAtEpochMillis = 900L,
        )

        publisher.announce(workspace, StudyFlowPresenceAvailability.ONLINE)
        assertEquals(StudyFlowPresenceStatus.ONLINE, registry.viewsFor(workspace, local, 1_050L).single().status)
        assertEquals(StudyFlowPresenceStatus.STALE, registry.viewsFor(workspace, local, 1_101L).single().status)

        publisher.announce(workspace, StudyFlowPresenceAvailability.AWAY)
        assertEquals(StudyFlowPresenceStatus.AWAY, registry.viewsFor(workspace, local, 1_050L).single().status)

        publisher.announce(workspace, StudyFlowPresenceAvailability.OFFLINE)
        assertEquals(StudyFlowPresenceStatus.OFFLINE, registry.viewsFor(workspace, local, 1_250L).single().status)
        assertTrue(registry.viewsFor(workspace, local, 1_301L).isEmpty())
    }

    @Test
    fun multipleInstancesOfOnePeerCoexistAndActivityHintIsCapabilityFiltered() {
        val registry = StudyFlowPresenceRegistry(local.deviceId)
        val readOnlyViewer = descriptor(
            "device-readonly",
            StudyFlowLinkClientKind.WEB_TV,
            listOf(StudyFlowLinkCapabilities.WorkspaceRead),
        )
        val writingViewer = descriptor(
            "device-writing",
            StudyFlowLinkClientKind.DESKTOP,
            listOf(StudyFlowLinkCapabilities.WorkspaceRead, StudyFlowLinkCapabilities.WritingEdit),
        )
        val hint = StudyFlowPresenceActivityHint(
            activityId = StudyFlowActivityId("activity-writing"),
            kind = StudyFlowActivityKind.WRITING_DRAFT,
            entityId = StudyFlowEntityId("draft-1"),
            positionToken = "paragraph-4",
            continuityMode = StudyFlowActivityContinuityMode.SNAPSHOT,
            requiredCapabilities = listOf(StudyFlowLinkCapabilities.WritingEdit),
        )
        listOf("instance-a", "instance-b").forEachIndexed { index, instance ->
            val record = remoteRecord(
                source = phone,
                instance = instance,
                session = "session-$instance",
                sequence = 1L,
                observedAt = 1_000L + index,
                hint = hint,
            )
            assertEquals(
                StudyFlowPresenceIngestResult.ACCEPTED,
                registry.ingest(record, phone.deviceId, setOf(phone.deviceId), 1_010L),
            )
        }

        val readOnly = registry.viewsFor(workspace, readOnlyViewer, 1_020L)
        assertEquals(2, readOnly.size)
        assertTrue(readOnly.all { it.activityHint == null })
        val writing = registry.viewsFor(workspace, writingViewer, 1_020L)
        assertEquals(2, writing.size)
        assertTrue(writing.all { it.activityHint?.activityId == StudyFlowActivityId("activity-writing") })
    }

    @Test
    fun visibilityCapabilitiesCanHideEntirePresenceRecord() {
        val registry = StudyFlowPresenceRegistry(local.deviceId)
        val record = remoteRecord(
            source = phone,
            instance = "instance-private",
            session = "session-private",
            sequence = 1L,
            observedAt = 1_000L,
            visibility = listOf(StudyFlowLinkCapabilities.AnalyticsRead),
        )
        assertEquals(
            StudyFlowPresenceIngestResult.ACCEPTED,
            registry.ingest(record, phone.deviceId, setOf(phone.deviceId), 1_001L),
        )
        val noAnalytics = descriptor(
            "device-no-analytics",
            StudyFlowLinkClientKind.WEB_TV,
            listOf(StudyFlowLinkCapabilities.WorkspaceRead),
        )
        assertTrue(registry.viewsFor(workspace, noAnalytics, 1_010L).isEmpty())
        val analytics = descriptor(
            "device-analytics",
            StudyFlowLinkClientKind.DESKTOP,
            listOf(StudyFlowLinkCapabilities.AnalyticsRead),
        )
        assertEquals(1, registry.viewsFor(workspace, analytics, 1_010L).size)
    }

    @Test
    fun duplicateOldSequenceAndLoopAreRejectedWhileNewReconnectSessionSupersedesOldSession() {
        val registry = StudyFlowPresenceRegistry(local.deviceId)
        val first = remoteRecord(phone, "instance-main", "session-old", 3L, 1_000L)
        assertEquals(
            StudyFlowPresenceIngestResult.ACCEPTED,
            registry.ingest(first, phone.deviceId, setOf(phone.deviceId), 1_001L),
        )
        assertEquals(
            StudyFlowPresenceIngestResult.DUPLICATE,
            registry.ingest(first, phone.deviceId, setOf(phone.deviceId), 1_002L),
        )
        assertEquals(
            StudyFlowPresenceIngestResult.STALE,
            registry.ingest(first.copy(sequence = 2L), phone.deviceId, setOf(phone.deviceId), 1_003L),
        )

        val newerSession = remoteRecord(phone, "instance-main", "session-new", 1L, 2_000L, sessionStarted = 1_900L)
        assertEquals(
            StudyFlowPresenceIngestResult.ACCEPTED,
            registry.ingest(newerSession, phone.deviceId, setOf(phone.deviceId), 2_001L),
        )
        assertEquals(1, registry.viewsFor(workspace, local, 2_001L).size)

        val bounced = newerSession.copy(trace = StudyFlowRouteTrace.begin(phone.deviceId).forwardedBy(local.deviceId))
        assertEquals(
            StudyFlowPresenceIngestResult.LOOP,
            registry.ingest(bounced, local.deviceId, setOf(phone.deviceId), 2_002L),
        )
    }

    @Test
    fun recordsForPeerNeverEchoBackThroughVisitedRoute() {
        val registry = StudyFlowPresenceRegistry(local.deviceId)
        val remote = remoteRecord(phone, "instance-route", "session-route", 1L, 1_000L)
        registry.ingest(remote, phone.deviceId, setOf(phone.deviceId), 1_001L)
        assertTrue(registry.recordsForPeer(workspace, phone, 1_002L).isEmpty())

        val third = descriptor("device-third", StudyFlowLinkClientKind.WEB, StudyFlowClientProfiles.Web)
        val forwarded = registry.recordsForPeer(workspace, third, 1_002L).single()
        assertEquals(listOf(phone.deviceId, local.deviceId), forwarded.trace.visitedDeviceIds)
        assertNull(forwarded.activityHint)
    }

    private fun remoteRecord(
        source: StudyFlowLinkPeerDescriptor,
        instance: String,
        session: String,
        sequence: Long,
        observedAt: Long,
        sessionStarted: Long = observedAt - 10L,
        hint: StudyFlowPresenceActivityHint? = null,
        visibility: List<StudyFlowLinkCapability> = emptyList(),
    ) = StudyFlowPresenceRecord(
        workspaceId = workspace,
        sourceDevice = source,
        instanceId = StudyFlowPresenceInstanceId(instance),
        sessionId = StudyFlowPresenceSessionId(session),
        sessionStartedAtEpochMillis = sessionStarted,
        sequence = sequence,
        availability = StudyFlowPresenceAvailability.ONLINE,
        observedAtEpochMillis = observedAt,
        activityHint = hint,
        visibilityCapabilities = visibility,
        trace = StudyFlowRouteTrace.begin(source.deviceId),
    )

    private fun descriptor(
        id: String,
        kind: StudyFlowLinkClientKind,
        capabilities: List<StudyFlowLinkCapability>,
    ) = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId(id),
        displayName = id,
        clientKind = kind,
        capabilities = capabilities,
    )
}
