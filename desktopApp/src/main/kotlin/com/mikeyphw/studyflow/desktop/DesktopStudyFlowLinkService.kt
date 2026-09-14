package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceIdentityManager
import com.mikeyphw.studyflow.core.link.StudyFlowDiscoveryRegistry
import com.mikeyphw.studyflow.core.link.StudyFlowLanBeaconDiscovery
import com.mikeyphw.studyflow.core.link.StudyFlowLanEndpointResolver
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowManualDiscovery
import com.mikeyphw.studyflow.core.link.StudyFlowPairingPublicOffer
import com.mikeyphw.studyflow.core.link.StudyFlowPairingQrCodec
import com.mikeyphw.studyflow.core.link.StudyFlowPeerRuntime
import com.mikeyphw.studyflow.core.link.StudyFlowPresenceActivityHint
import com.mikeyphw.studyflow.core.link.StudyFlowPresenceAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowPresenceRecord
import com.mikeyphw.studyflow.core.link.StudyFlowPresenceView
import com.mikeyphw.studyflow.core.link.StudyFlowReconnectResult
import com.mikeyphw.studyflow.core.link.StudyFlowTransportEndpoint
import com.mikeyphw.studyflow.core.link.StudyFlowTransportScheme
import com.mikeyphw.studyflow.core.link.StudyFlowTrustedPeerRecord
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceAccess
import java.io.Closeable
import java.net.InetAddress
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DesktopStudyFlowLinkService(
    stateRoot: Path = DesktopStudyFlowLinkPersistence.defaultRoot(),
    displayName: String = defaultDisplayName(),
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "studyflow-desktop-sflp-reconnect").apply { isDaemon = true }
    },
) : Closeable {
    private val persistence = DesktopStudyFlowLinkPersistence(stateRoot)
    private val identity = StudyFlowDeviceIdentityManager(persistence.identityStore).getOrCreate(
        displayName = displayName,
        clientKind = StudyFlowLinkClientKind.DESKTOP,
        capabilities = DesktopCapabilities,
        nowEpochMillis = System.currentTimeMillis(),
    )
    private val manualDiscovery = StudyFlowManualDiscovery()
    private val discoveryRegistry = StudyFlowDiscoveryRegistry(listOf(manualDiscovery))
    val runtime = StudyFlowPeerRuntime(
        localIdentity = identity,
        trustStore = persistence.trustStore,
        journalStore = persistence.journalStore,
        queueStore = persistence.queueStore,
        endpointStore = persistence.endpointStore,
        discovery = discoveryRegistry,
        membershipStore = persistence.membershipStore,
    )
    private val started = AtomicBoolean(false)
    private val activeWorkspaces = linkedSetOf<StudyFlowWorkspaceId>()
    private var lanDiscovery: StudyFlowLanBeaconDiscovery? = null
    private var serverEndpoint: StudyFlowTransportEndpoint? = null
    private var browserBridgeEndpoint: StudyFlowTransportEndpoint? = null
    private var currentOffer: StudyFlowPairingPublicOffer? = null
    @Volatile
    private var lastStatus: String = "SFLP not started"

    val localDeviceId: StudyFlowDeviceId get() = identity.descriptor.deviceId

    fun start(): StudyFlowTransportEndpoint {
        serverEndpoint?.let { return it }
        check(started.compareAndSet(false, true)) { "Desktop SFLP peer is already starting" }
        return try {
            val endpoint = runtime.startServer()
            serverEndpoint = endpoint
            browserBridgeEndpoint = runtime.startWebSocketServer()
            val port = requireNotNull(endpoint.port)
            runCatching {
                StudyFlowLanBeaconDiscovery(identity.descriptor, port).also { discovery ->
                    discovery.start()
                    lanDiscovery = discovery
                    discoveryRegistry.add(discovery)
                }
            }
            scheduler.scheduleWithFixedDelay(
                ::backgroundReconnect,
                5L,
                15L,
                TimeUnit.SECONDS,
            )
            lastStatus = "Listening for StudyFlow peers"
            endpoint
        } catch (error: Throwable) {
            started.set(false)
            lastStatus = "SFLP startup failed: ${error.javaClass.simpleName}"
            throw error
        }
    }

    fun registerWorkspace(workspaceId: StudyFlowWorkspaceId) {
        synchronized(activeWorkspaces) { activeWorkspaces += workspaceId }
        runtime.workspace(workspaceId)
        runtime.trustManager.trustedPeers().forEach { peer ->
            runtime.grantWorkspaceAccess(workspaceId, peer.peer.deviceId, StudyFlowWorkspaceAccess.WRITE)
        }
        runtime.updatePresence(workspaceId, StudyFlowPresenceAvailability.ONLINE)
    }

    fun createPairingOffer(): StudyFlowPairingPublicOffer {
        val endpoint = serverEndpoint ?: start()
        val port = requireNotNull(endpoint.port)
        val tcpEndpoints = StudyFlowLanEndpointResolver.endpointsForPort(port)
        val webSocketPort = browserBridgeEndpoint?.port
        val webSocketEndpoints = if (webSocketPort == null) emptyList() else tcpEndpoints.map { endpoint ->
            endpoint.copy(
                scheme = StudyFlowTransportScheme.WEBSOCKET,
                port = webSocketPort,
                path = "/sflp",
            )
        }
        val hints = (webSocketEndpoints + tcpEndpoints)
            .map(StudyFlowTransportEndpoint::canonicalUri)
            .distinct()
            .ifEmpty { listOf("sflp+tcp://127.0.0.1:$port") }
        return runtime.createPairingOffer(hints).also {
            currentOffer = it
            lastStatus = "Pairing offer ready"
        }
    }

    fun pairingCode(): String? = currentOffer?.let { offer ->
        runtime.pairingAuthority.pairingCode(offer.pairingId, System.currentTimeMillis())
    }

    fun pairFromQr(qrPayload: String): StudyFlowTrustedPeerRecord {
        val payload = StudyFlowPairingQrCodec.decode(qrPayload)
        val record = runtime.pairingClient.pairFromQr(qrPayload)
        val endpoints = payload.endpointHints.mapNotNull { hint ->
            runCatching { com.mikeyphw.studyflow.core.link.StudyFlowManualEndpointParser.parse(hint) }.getOrNull()
        }
        if (endpoints.isNotEmpty()) runtime.rememberEndpoints(record.peer.deviceId, endpoints)
        synchronized(activeWorkspaces) { activeWorkspaces.forEach { runtime.grantWorkspaceAccess(it, record.peer.deviceId, StudyFlowWorkspaceAccess.WRITE) } }
        lastStatus = "Paired with ${record.peer.displayName}"
        return record
    }

    fun rememberManualEndpoint(value: String) {
        manualDiscovery.remember(value, System.currentTimeMillis())
        runtime.ingestDiscovery()
        lastStatus = "Manual peer endpoint remembered"
    }

    fun syncPeer(
        workspaceId: StudyFlowWorkspaceId,
        peerDeviceId: StudyFlowDeviceId,
    ): StudyFlowReconnectResult {
        registerWorkspace(workspaceId)
        runtime.ingestDiscovery()
        return runtime.reconcileWith(workspaceId, peerDeviceId).also { result ->
            lastStatus = if (result.failure == null) {
                "Synchronized with ${peerDeviceId.value}"
            } else {
                "Sync interrupted: ${result.failure}"
            }
        }
    }

    fun syncAll(workspaceId: StudyFlowWorkspaceId): List<StudyFlowReconnectResult> {
        registerWorkspace(workspaceId)
        runtime.ingestDiscovery()
        return runtime.workspacePeers(workspaceId).mapNotNull { record ->
            runCatching { runtime.reconcileWith(workspaceId, record.peer.deviceId) }
                .onFailure { lastStatus = "Peer offline: ${record.peer.displayName}" }
                .getOrNull()
        }
    }

    fun updatePresence(
        workspaceId: StudyFlowWorkspaceId,
        availability: StudyFlowPresenceAvailability,
        activityHint: StudyFlowPresenceActivityHint? = null,
        visibilityCapabilities: List<StudyFlowLinkCapability> = emptyList(),
    ): StudyFlowPresenceRecord = runtime.updatePresence(
        workspaceId = workspaceId,
        availability = availability,
        activityHint = activityHint,
        visibilityCapabilities = visibilityCapabilities,
    )

    fun presenceViews(workspaceId: StudyFlowWorkspaceId): List<StudyFlowPresenceView> =
        runtime.presenceViews(workspaceId)

    fun trustedPeers(): List<StudyFlowTrustedPeerRecord> = runtime.trustManager.trustedPeers()

    fun revoke(peerDeviceId: StudyFlowDeviceId) {
        runtime.revoke(peerDeviceId)
        lastStatus = "Peer revoked"
    }

    fun forget(peerDeviceId: StudyFlowDeviceId) {
        runtime.forget(peerDeviceId)
        lastStatus = "Peer forgotten"
    }

    fun snapshot(workspaceId: StudyFlowWorkspaceId?): DesktopStudyFlowLinkState {
        runtime.ingestDiscovery()
        val port = serverEndpoint?.port
        val tcpEndpoints = if (port == null) emptyList() else StudyFlowLanEndpointResolver.endpointsForPort(port)
        val wsPort = browserBridgeEndpoint?.port
        val endpoints = if (wsPort == null) tcpEndpoints else (
            tcpEndpoints.map { it.copy(scheme = StudyFlowTransportScheme.WEBSOCKET, port = wsPort, path = "/sflp") } + tcpEndpoints
        ).distinct()
        val pending = workspaceId?.let { runtime.workspace(it).outgoingQueue.entries().size } ?: 0
        return DesktopStudyFlowLinkState(
            localDeviceId = localDeviceId.value,
            localEndpoints = endpoints.map(StudyFlowTransportEndpoint::canonicalUri),
            discoveredPeers = runtime.discovery.snapshot(System.currentTimeMillis()).map { peer ->
                DesktopStudyFlowDiscoveredPeer(
                    deviceId = peer.deviceId?.value,
                    displayName = peer.displayName ?: peer.discoveryId,
                    clientKind = peer.clientKind?.name ?: "UNKNOWN",
                    endpoints = peer.endpoints.map(StudyFlowTransportEndpoint::canonicalUri),
                    source = peer.source.name,
                )
            },
            trustedPeers = runtime.trustManager.rememberedPeers().map { record ->
                DesktopStudyFlowTrustedPeer(
                    deviceId = record.peer.deviceId.value,
                    displayName = record.peer.displayName,
                    clientKind = record.peer.clientKind.name,
                    trusted = record.state.name == "TRUSTED",
                    endpointCount = runtime.endpointStore.get(record.peer.deviceId)?.endpoints?.size ?: 0,
                )
            },
            pairingCode = pairingCode(),
            pairingQrPayload = currentOffer?.qrPayload,
            pendingOutgoingOperations = pending,
            presence = workspaceId?.let(::presenceViews).orEmpty().map { view ->
                DesktopStudyFlowPresence(
                    deviceId = view.sourceDevice.deviceId.value,
                    instanceId = view.instanceId.value,
                    displayName = view.sourceDevice.displayName,
                    clientKind = view.sourceDevice.clientKind.name,
                    status = view.status.name,
                    activityKind = view.activityHint?.kind?.name,
                    activityId = view.activityHint?.activityId?.value,
                )
            },
            status = lastStatus,
        )
    }

    private fun backgroundReconnect() {
        if (!started.get()) return
        runCatching { runtime.ingestDiscovery() }
        val workspaces = synchronized(activeWorkspaces) { activeWorkspaces.toList() }
        workspaces.forEach { workspaceId ->
            runtime.workspacePeers(workspaceId).forEach { peer ->
                runCatching { runtime.reconcileWith(workspaceId, peer.peer.deviceId) }
            }
        }
    }

    override fun close() {
        lanDiscovery?.let { discoveryRegistry.remove(it) }
        lanDiscovery?.close()
        lanDiscovery = null
        runtime.close()
        scheduler.shutdownNow()
        serverEndpoint = null
        browserBridgeEndpoint = null
        started.set(false)
        lastStatus = "SFLP stopped"
    }

    companion object {
        val DesktopCapabilities: List<StudyFlowLinkCapability> = listOf(
            StudyFlowLinkCapabilities.WorkspaceRead,
            StudyFlowLinkCapabilities.WorkspaceWrite,
            StudyFlowLinkCapabilities.WorkspaceSync,
            StudyFlowLinkCapabilities.PlanRead,
            StudyFlowLinkCapabilities.PlanWrite,
            StudyFlowLinkCapabilities.ScheduleRead,
            StudyFlowLinkCapabilities.ScheduleWrite,
            StudyFlowLinkCapabilities.SessionExecute,
            StudyFlowLinkCapabilities.SessionControl,
            StudyFlowLinkCapabilities.SessionPresent,
            StudyFlowLinkCapabilities.QuestionAnswer,
            StudyFlowLinkCapabilities.QuestionAuthor,
            StudyFlowLinkCapabilities.ReviewExecute,
            StudyFlowLinkCapabilities.ReviewEdit,
            StudyFlowLinkCapabilities.GoalRead,
            StudyFlowLinkCapabilities.GoalComplete,
            StudyFlowLinkCapabilities.GoalEdit,
            StudyFlowLinkCapabilities.LibraryRead,
            StudyFlowLinkCapabilities.LibraryImport,
            StudyFlowLinkCapabilities.LibraryPresent,
            StudyFlowLinkCapabilities.DocumentRead,
            StudyFlowLinkCapabilities.DocumentPresent,
            StudyFlowLinkCapabilities.WritingRead,
            StudyFlowLinkCapabilities.WritingEdit,
            StudyFlowLinkCapabilities.AnalyticsRead,
            StudyFlowLinkCapabilities.BackupExport,
            StudyFlowLinkCapabilities.BackupImport,
            StudyFlowLinkCapabilities.DeviceControl,
            StudyFlowLinkCapabilities.DevicePresentationSource,
            StudyFlowLinkCapabilities.TextInputProvide,
            StudyFlowLinkCapabilities.AudioOutput,
            StudyFlowLinkCapabilities.VideoOutput,
            StudyFlowLinkCapabilities.KeyboardInput,
        )

        private fun defaultDisplayName(): String = runCatching {
            "StudyFlow Desktop (${InetAddress.getLocalHost().hostName})"
        }.getOrDefault("StudyFlow Desktop")
    }
}

data class DesktopStudyFlowLinkState(
    val localDeviceId: String,
    val localEndpoints: List<String>,
    val discoveredPeers: List<DesktopStudyFlowDiscoveredPeer>,
    val trustedPeers: List<DesktopStudyFlowTrustedPeer>,
    val pairingCode: String?,
    val pairingQrPayload: String?,
    val pendingOutgoingOperations: Int,
    val presence: List<DesktopStudyFlowPresence>,
    val status: String,
)

data class DesktopStudyFlowPresence(
    val deviceId: String,
    val instanceId: String,
    val displayName: String,
    val clientKind: String,
    val status: String,
    val activityKind: String?,
    val activityId: String?,
)

data class DesktopStudyFlowDiscoveredPeer(
    val deviceId: String?,
    val displayName: String,
    val clientKind: String,
    val endpoints: List<String>,
    val source: String,
)

data class DesktopStudyFlowTrustedPeer(
    val deviceId: String,
    val displayName: String,
    val clientKind: String,
    val trusted: Boolean,
    val endpointCount: Int,
)
