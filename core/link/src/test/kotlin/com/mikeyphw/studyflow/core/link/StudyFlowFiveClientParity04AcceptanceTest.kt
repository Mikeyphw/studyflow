package com.mikeyphw.studyflow.core.link

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowFiveClientParity04AcceptanceTest {
    @Test
    fun fiveClientsPairDirectlyAndConvergeOverRealTcpWithoutAndroidHub() {
        val fixture = fixture().jsonObject
        assertEquals("PARITY04", fixture.getValue("milestone").jsonPrimitive.content)
        assertEquals(5, fixture.getValue("clients").jsonArray.size)
        assertEquals(10, fixture.getValue("directPairs").jsonArray.size)

        var now = 1_000_000L
        val clock = StudyFlowPeerClock { now++ }
        val nodes = nodes(clock)
        val endpoints = nodes.mapValues { (_, runtime) -> runtime.startServer("127.0.0.1", 0) }
        val workspace = StudyFlowWorkspaceId("workspace-parity04-full-mesh")
        try {
            pairEveryClientDirectly(nodes, endpoints)
            assertFullMeshTrust(nodes)

            listOf("android", "desktop", "web").forEachIndexed { index, key ->
                nodes.getValue(key).appendLocalMutation(
                    workspaceId = workspace,
                    operationId = StudyFlowOperationId("parity-edit-${index + 1}"),
                    entityId = StudyFlowEntityId("entity-${index + 1}"),
                    operationType = "study.updated",
                )
            }
            reconcileEveryDirection(nodes, endpoints, workspace)

            nodes.forEach { (_, runtime) ->
                assertEquals(3, runtime.workspace(workspace).journal.operations().size)
                assertEquals(4, runtime.trustManager.trustedPeers().size)
                assertEquals(4, runtime.topology(workspace).peers().size)
                nodes.values.filter { it !== runtime }.forEach { other ->
                    val routes = runtime.topology(workspace).routesTo(other.localIdentity.descriptor.deviceId)
                    assertTrue(routes.any { it.kind == StudyFlowRouteKind.DIRECT && it.hopCount == 1 })
                }
            }

            // These direct edges are the explicit no-Android-intermediary acceptance cases.
            assertNotNull(nodes.getValue("desktop").bestEndpoint(nodes.getValue("android_tv").localIdentity.descriptor.deviceId))
            assertNotNull(nodes.getValue("web").bestEndpoint(nodes.getValue("web_tv").localIdentity.descriptor.deviceId))
        } finally {
            nodes.values.forEach(StudyFlowPeerRuntime::close)
        }
    }

    @Test
    fun offlineConcurrentWritersResolveDeterministicallyOnEveryClient() {
        var now = 2_000_000L
        val clock = StudyFlowPeerClock { now++ }
        val nodes = nodes(clock)
        val endpoints = nodes.mapValues { (_, runtime) -> runtime.startServer("127.0.0.1", 0) }
        val workspace = StudyFlowWorkspaceId("workspace-parity04-conflict")
        try {
            pairEveryClientDirectly(nodes, endpoints)
            listOf("android", "desktop", "web").forEach { key ->
                nodes.getValue(key).appendLocalMutation(
                    workspaceId = workspace,
                    operationId = StudyFlowOperationId("conflict-$key"),
                    entityId = StudyFlowEntityId("shared-goal"),
                    operationType = "goal.updated",
                )
            }
            reconcileEveryDirection(nodes, endpoints, workspace)
            nodes.forEach { (_, runtime) ->
                val replay = runtime.workspace(workspace).reconciliation.resolvedReplay()
                assertEquals(1, replay.conflicts.size)
                val winner = replay.operations.single { it.entityId == StudyFlowEntityId("shared-goal") }
                assertEquals(StudyFlowDeviceId("parity-web"), winner.originDeviceId)
                assertEquals(3, replay.conflicts.single().operations.size)
            }
        } finally {
            nodes.values.forEach(StudyFlowPeerRuntime::close)
        }
    }

    @Test
    fun continuationPresentationCapabilityRejectionAndExactlyOnceCompletionHoldAcrossProfiles() {
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP)
        val web = descriptor("web", StudyFlowLinkClientKind.WEB)
        val androidTv = descriptor("android-tv", StudyFlowLinkClientKind.ANDROID_TV)
        val webTv = descriptor("web-tv", StudyFlowLinkClientKind.WEB_TV)
        val workspace = StudyFlowWorkspaceId("workspace-parity04-continuity")
        var now = 3_000_000L
        val clock = StudyFlowPeerClock { now++ }
        val registry = StudyFlowActivityHandoffRegistry(clock)
        val coordinator = StudyFlowActivityHandoffCoordinator(desktop, registry, clock)
        val snapshot = StudyFlowActivitySnapshot(
            activityId = StudyFlowActivityId("activity-parity04"),
            workspaceId = workspace,
            kind = StudyFlowActivityKind.STUDY_SESSION,
            entityId = StudyFlowEntityId("session-parity04"),
            entityRevision = StudyFlowEntityRevision(1),
            cursor = StudyFlowActivityCursor(positionToken = "question-4", questionIndex = 4, elapsedMillis = 90_000),
            continuityMode = StudyFlowActivityContinuityMode.LIVE_CONTROL,
            requiredCapabilities = listOf(StudyFlowLinkCapabilities.SessionExecute),
            payloadHash = "0".repeat(64),
        )
        val offer = coordinator.offerTo(
            target = web,
            snapshot = snapshot,
            handoffId = StudyFlowHandoffId("handoff-parity04"),
            nonceHex = "00112233445566778899aabbccddeeff",
        )
        assertTrue(coordinator.accept(offer, web).handoffId == offer.handoffId)
        assertTrue(registry.consume(offer.handoffId, web) is StudyFlowHandoffDecision.Rejected)

        val writeSnapshot = snapshot.copy(
            activityId = StudyFlowActivityId("activity-write-parity04"),
            requiredCapabilities = listOf(StudyFlowLinkCapabilities.WritingEdit),
        )
        val writeOffer = StudyFlowHandoffOffer(
            handoffId = StudyFlowHandoffId("handoff-write-tv"),
            sourceDevice = desktop,
            targetDeviceId = androidTv.deviceId,
            snapshot = writeSnapshot,
            createdAtEpochMillis = now,
            expiresAtEpochMillis = now + 90_000,
            oneTimeNonceHex = "00112233445566778899aabbccddeeff",
        )
        assertTrue(StudyFlowHandoffPolicy.evaluate(writeOffer, androidTv, now) is StudyFlowHandoffDecision.Rejected)
        assertTrue(StudyFlowHandoffPolicy.evaluate(writeOffer.copy(targetDeviceId = webTv.deviceId), webTv, now) is StudyFlowHandoffDecision.Rejected)

        listOf(androidTv, webTv).forEach { sink ->
            val projection = StudyFlowPresentationProjectionOffer(
                projectionId = StudyFlowProjectionId("projection-${sink.deviceId.value}"),
                workspaceId = workspace,
                sourceDevice = web,
                targetDeviceId = sink.deviceId,
                contentKind = StudyFlowPresentationContentKind.DOCUMENT,
                entityId = StudyFlowEntityId("document-parity04"),
                title = "PARITY04 document",
                contentHash = "1".repeat(64),
                requiredCapabilities = StudyFlowPresentationProjectionPolicy.defaultSinkCapabilities(
                    StudyFlowPresentationContentKind.DOCUMENT,
                ),
                createdAtEpochMillis = now,
            )
            val receiver = StudyFlowPresentationProjectionReceiver(sink, clock)
            assertTrue(receiver.accept(projection) is StudyFlowPresentationApplyResult.Accepted)
        }

        var meshNow = 4_000_000L
        val meshClock = StudyFlowPeerClock { meshNow++ }
        val nodes = nodes(meshClock)
        val endpoints = nodes.mapValues { (_, runtime) -> runtime.startServer("127.0.0.1", 0) }
        val completionWorkspace = StudyFlowWorkspaceId("workspace-parity04-completion")
        try {
            pairEveryClientDirectly(nodes, endpoints)
            nodes.getValue("web").appendLocalMutation(
                workspaceId = completionWorkspace,
                operationId = StudyFlowOperationId("parity-session-complete"),
                entityId = StudyFlowEntityId("session-complete"),
                operationType = StudyFlowDomainOperationTypes.SessionCompleted,
            )
            repeat(2) { reconcileEveryDirection(nodes, endpoints, completionWorkspace) }
            nodes.forEach { (_, runtime) ->
                assertEquals(
                    1,
                    runtime.workspace(completionWorkspace).journal.operations()
                        .count { it.operationId == StudyFlowOperationId("parity-session-complete") },
                )
            }
        } finally {
            nodes.values.forEach(StudyFlowPeerRuntime::close)
        }
    }

    @Test
    fun routeTracePresenceAndClientCapabilitiesStayLoopFreeAndAuthoritySafe() {
        val workspace = StudyFlowWorkspaceId("workspace-parity04-route")
        val source = StudyFlowDeviceId("parity-android")
        val trace = StudyFlowRouteTrace.begin(source, hopLimit = 8)
            .forwardedBy(StudyFlowDeviceId("parity-desktop"))
            .forwardedBy(StudyFlowDeviceId("parity-web"))
            .forwardedBy(StudyFlowDeviceId("parity-android-tv"))
            .forwardedBy(StudyFlowDeviceId("parity-web-tv"))
        assertFalse(trace.canVisit(source))
        assertFalse(trace.canVisit(StudyFlowDeviceId("parity-desktop")))
        assertEquals(5, trace.visitedDeviceIds.distinct().size)

        listOf(StudyFlowLinkClientKind.ANDROID_TV, StudyFlowLinkClientKind.WEB_TV).forEach { kind ->
            val capabilities = StudyFlowClientProfiles.capabilitiesFor(kind).toSet()
            assertTrue(StudyFlowLinkCapabilities.WorkspaceSync in capabilities)
            assertTrue(StudyFlowLinkCapabilities.DevicePresentationSink in capabilities)
            assertFalse(StudyFlowLinkCapabilities.WorkspaceWrite in capabilities)
            assertFalse(StudyFlowLinkCapabilities.WritingEdit in capabilities)
            assertFalse(StudyFlowLinkCapabilities.BackupImport in capabilities)
        }
        listOf(StudyFlowLinkClientKind.ANDROID, StudyFlowLinkClientKind.DESKTOP, StudyFlowLinkClientKind.WEB).forEach { kind ->
            val capabilities = StudyFlowClientProfiles.capabilitiesFor(kind).toSet()
            assertTrue(StudyFlowLinkCapabilities.WorkspaceWrite in capabilities)
            assertTrue(StudyFlowLinkCapabilities.DevicePresentationSource in capabilities)
            assertTrue(StudyFlowLinkCapabilities.BackupImport in capabilities)
        }

        var now = 5_000_000L
        val clock = StudyFlowPeerClock { now++ }
        val viewer = descriptor("desktop-viewer", StudyFlowLinkClientKind.DESKTOP)
        val observer = StudyFlowPresenceRegistry(viewer.deviceId)
        val publishers = listOf(
            descriptor("android", StudyFlowLinkClientKind.ANDROID),
            descriptor("desktop", StudyFlowLinkClientKind.DESKTOP),
            descriptor("web", StudyFlowLinkClientKind.WEB),
            descriptor("android-tv", StudyFlowLinkClientKind.ANDROID_TV),
            descriptor("web-tv", StudyFlowLinkClientKind.WEB_TV),
        )
        val trusted = publishers.mapTo(linkedSetOf()) { it.deviceId }
        publishers.forEach { peer ->
            val sourceRegistry = StudyFlowPresenceRegistry(peer.deviceId)
            val record = StudyFlowLocalPresencePublisher(peer, sourceRegistry, clock)
                .announce(workspace, StudyFlowPresenceAvailability.ONLINE)
            assertEquals(
                StudyFlowPresenceIngestResult.ACCEPTED,
                observer.ingest(record, peer.deviceId, trusted, now),
            )
        }
        assertEquals(5, observer.viewsFor(workspace, viewer, now).size)
    }

    private fun nodes(clock: StudyFlowPeerClock): LinkedHashMap<String, StudyFlowPeerRuntime> = linkedMapOf(
        "android" to runtime("parity-android", StudyFlowLinkClientKind.ANDROID, clock),
        "desktop" to runtime("parity-desktop", StudyFlowLinkClientKind.DESKTOP, clock),
        "web" to runtime("parity-web", StudyFlowLinkClientKind.WEB, clock),
        "android_tv" to runtime("parity-android-tv", StudyFlowLinkClientKind.ANDROID_TV, clock),
        "web_tv" to runtime("parity-web-tv", StudyFlowLinkClientKind.WEB_TV, clock),
    )

    private fun runtime(id: String, kind: StudyFlowLinkClientKind, clock: StudyFlowPeerClock): StudyFlowPeerRuntime {
        val identity = StudyFlowLocalDeviceIdentity(
            descriptor = StudyFlowLinkPeerDescriptor(
                deviceId = StudyFlowDeviceId(id),
                displayName = id,
                clientKind = kind,
                capabilities = StudyFlowClientProfiles.capabilitiesFor(kind),
            ),
            createdAtEpochMillis = clock.nowEpochMillis(),
        )
        return StudyFlowPeerRuntime(
            localIdentity = identity,
            trustStore = InMemoryStudyFlowTrustStore(),
            journalStore = InMemoryStudyFlowOperationJournalStore(),
            queueStore = InMemoryStudyFlowOutgoingQueueStore(),
            endpointStore = InMemoryStudyFlowPeerEndpointStore(),
            discovery = StudyFlowManualDiscovery(),
            clock = clock,
        )
    }

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind) = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId("parity-$id"),
        displayName = "PARITY04 $id",
        clientKind = kind,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(kind),
    )

    private fun pairEveryClientDirectly(
        nodes: LinkedHashMap<String, StudyFlowPeerRuntime>,
        endpoints: Map<String, StudyFlowTransportEndpoint>,
    ) {
        val keys = nodes.keys.toList()
        for (i in keys.indices) for (j in i + 1 until keys.size) {
            val left = nodes.getValue(keys[i])
            val right = nodes.getValue(keys[j])
            val rightEndpoint = endpoints.getValue(keys[j])
            val offer = right.createPairingOffer(listOf(rightEndpoint.canonicalUri()))
            left.pairingClient.pairFromQr(offer.qrPayload)
            left.rememberEndpoints(right.localIdentity.descriptor.deviceId, listOf(rightEndpoint))
            right.rememberEndpoints(left.localIdentity.descriptor.deviceId, listOf(endpoints.getValue(keys[i])))
        }
    }

    private fun assertFullMeshTrust(nodes: LinkedHashMap<String, StudyFlowPeerRuntime>) {
        nodes.forEach { (_, runtime) ->
            assertEquals(4, runtime.trustManager.trustedPeers().size)
            assertEquals(4, runtime.trustManager.trustedPeers().map { it.peer.deviceId }.distinct().size)
        }
    }

    private fun reconcileEveryDirection(
        nodes: LinkedHashMap<String, StudyFlowPeerRuntime>,
        endpoints: Map<String, StudyFlowTransportEndpoint>,
        workspace: StudyFlowWorkspaceId,
    ) {
        val keys = nodes.keys.toList()
        for (source in keys) for (target in keys) if (source != target) {
            val result = nodes.getValue(source).reconcileWith(
                workspaceId = workspace,
                peerDeviceId = nodes.getValue(target).localIdentity.descriptor.deviceId,
                endpoint = endpoints.getValue(target),
            )
            assertEquals(StudyFlowReconnectStatus.COMPLETE, result.status)
        }
    }

    private fun fixture() = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val file = File(dir, "protocol/parity/v1/five-client-scenarios.json")
            if (file.isFile) return@run Json.parseToJsonElement(file.readText())
            dir = dir.parentFile ?: dir
        }
        error("PARITY04 fixture not found")
    }
}
