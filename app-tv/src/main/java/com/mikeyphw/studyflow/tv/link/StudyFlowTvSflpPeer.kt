package com.mikeyphw.studyflow.tv.link

import android.content.Context
import android.os.Build
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceIdentityManager
import com.mikeyphw.studyflow.core.link.StudyFlowDiscoveryRegistry
import com.mikeyphw.studyflow.core.link.StudyFlowDiscoveredPeer
import com.mikeyphw.studyflow.core.link.StudyFlowLanBeaconDiscovery
import com.mikeyphw.studyflow.core.link.StudyFlowLanEndpointResolver
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability
import com.mikeyphw.studyflow.core.link.StudyFlowPresenceActivityHint
import com.mikeyphw.studyflow.core.link.StudyFlowPresenceAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowPresenceRecord
import com.mikeyphw.studyflow.core.link.StudyFlowPresenceView
import com.mikeyphw.studyflow.core.link.StudyFlowManualDiscovery
import com.mikeyphw.studyflow.core.link.StudyFlowManualEndpointParser
import com.mikeyphw.studyflow.core.link.StudyFlowPairingPublicOffer
import com.mikeyphw.studyflow.core.link.StudyFlowPairingQrCodec
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryAck
import com.mikeyphw.studyflow.core.link.StudyFlowPeerRuntime
import com.mikeyphw.studyflow.core.link.StudyFlowReconnectResult
import com.mikeyphw.studyflow.core.link.StudyFlowTransportEndpoint
import com.mikeyphw.studyflow.core.link.StudyFlowTransportScheme
import com.mikeyphw.studyflow.core.link.StudyFlowTrustedPeerRecord
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceAccess
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowProductStateContract
import com.mikeyphw.studyflow.core.link.StudyFlowOperationId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCodec
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionJournalKeys
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * SFLP11 generic Android TV peer. The historical phone/TV local-control host stays available as
 * a compatibility adapter, but this class lets Android TV pair and synchronize with Android,
 * Desktop, Web, and Web TV through the same SFLP identity/trust/topology stack.
 */
class StudyFlowTvSflpPeer(
    context: Context,
    displayName: String = defaultDisplayName(),
) : Closeable {
    private val persistence = StudyFlowTvSflpStatePersistence(context.applicationContext)
    private val identity = StudyFlowDeviceIdentityManager(persistence.identityStore).getOrCreate(
        displayName = displayName,
        clientKind = StudyFlowLinkClientKind.ANDROID_TV,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID_TV),
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
    private var lanDiscovery: StudyFlowLanBeaconDiscovery? = null
    private var serverEndpoint: StudyFlowTransportEndpoint? = null
    private var browserBridgeEndpoint: StudyFlowTransportEndpoint? = null
    private val activeWorkspaces = linkedSetOf<StudyFlowWorkspaceId>()
    private var reconciliationScheduler: ScheduledExecutorService? = null

    val localDeviceId: StudyFlowDeviceId get() = identity.descriptor.deviceId
    val localDescriptor get() = identity.descriptor

    fun start(): StudyFlowTransportEndpoint {
        serverEndpoint?.let { return it }
        check(started.compareAndSet(false, true)) { "Android TV SFLP peer is already starting" }
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
            runtime.ingestDiscovery()
            reconciliationScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "studyflow-tv-sflp-reconcile").apply { isDaemon = true }
            }.also { scheduler -> scheduler.scheduleWithFixedDelay(::reconcileActiveWorkspaces, 5L, 15L, TimeUnit.SECONDS) }
            endpoint
        } catch (error: Throwable) {
            started.set(false)
            throw error
        }
    }

    fun pairingOffer(): StudyFlowPairingPublicOffer {
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
        return runtime.createPairingOffer(hints)
    }

    fun pairFromQr(qrPayload: String): StudyFlowTrustedPeerRecord {
        val payload = StudyFlowPairingQrCodec.decode(qrPayload)
        val record = runtime.pairingClient.pairFromQr(qrPayload)
        val endpoints = payload.endpointHints.mapNotNull { hint ->
            runCatching { StudyFlowManualEndpointParser.parse(hint) }.getOrNull()
        }
        if (endpoints.isNotEmpty()) runtime.rememberEndpoints(record.peer.deviceId, endpoints)
        synchronized(activeWorkspaces) { activeWorkspaces.forEach { runtime.grantWorkspaceAccess(it, record.peer.deviceId, StudyFlowWorkspaceAccess.WRITE) } }
        return record
    }

    fun rememberManualEndpoint(value: String): StudyFlowDiscoveredPeer {
        val discovered = manualDiscovery.remember(value, System.currentTimeMillis())
        runtime.ingestDiscovery()
        return discovered
    }

    fun discoveredPeers(): List<StudyFlowDiscoveredPeer> = runtime.ingestDiscovery()

    fun trustedPeers(): List<StudyFlowTrustedPeerRecord> = runtime.trustManager.trustedPeers()

    fun reconcile(
        workspaceId: StudyFlowWorkspaceId,
        peerDeviceId: StudyFlowDeviceId,
        endpoint: StudyFlowTransportEndpoint? = null,
    ): StudyFlowReconnectResult {
        runtime.ingestDiscovery()
        return if (endpoint == null) {
            runtime.reconcileWith(workspaceId, peerDeviceId)
        } else {
            runtime.rememberEndpoints(peerDeviceId, listOf(endpoint))
            runtime.reconcileWith(workspaceId, peerDeviceId, endpoint)
        }
    }

    fun registerWritableWorkspace(workspaceId: StudyFlowWorkspaceId) {
        synchronized(activeWorkspaces) { activeWorkspaces += workspaceId }
        runtime.workspace(workspaceId)
        trustedPeers().filter { it.peer.capabilities.contains(StudyFlowLinkCapabilities.WorkspaceWrite) }.forEach { peer ->
            runtime.grantWorkspaceAccess(workspaceId, peer.peer.deviceId, StudyFlowWorkspaceAccess.WRITE)
        }
    }

    fun publishDomainAction(action: StudyFlowTvDomainAction) {
        val workspaceId = StudyFlowWorkspaceId("plan-${action.studyPlanId}")
        registerWritableWorkspace(workspaceId)
        runtime.appendLocalMutation(
            workspaceId = workspaceId,
            operationId = StudyFlowOperationId(StudyFlowTvDomainActionJournalKeys.sflpOperationId(action.actionId)),
            entityId = StudyFlowEntityId(StudyFlowTvDomainActionJournalKeys.sflpEntityId(action.actionId)),
            operationType = StudyFlowProductStateContract.TvDomainAction,
            payload = StudyFlowLinkCodec.encodePayload(StudyFlowTvDomainAction.serializer(), action),
        )
    }

    private fun reconcileActiveWorkspaces() {
        if (!started.get()) return
        runCatching { runtime.ingestDiscovery() }
        val workspaces = synchronized(activeWorkspaces) { activeWorkspaces.toList() }
        workspaces.forEach { workspaceId ->
            runtime.workspacePeers(workspaceId).forEach { peer ->
                runCatching { runtime.reconcileWith(workspaceId, peer.peer.deviceId) }
            }
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

    fun setNotificationDeliveryHandler(
        handler: ((StudyFlowNotificationDelivery) -> StudyFlowNotificationDeliveryAck)?,
    ) = runtime.setNotificationDeliveryHandler(handler)

    fun revoke(peerDeviceId: StudyFlowDeviceId): StudyFlowTrustedPeerRecord? = runtime.revoke(peerDeviceId)

    fun forget(peerDeviceId: StudyFlowDeviceId): Boolean = runtime.forget(peerDeviceId)

    fun localEndpoints(): List<StudyFlowTransportEndpoint> {
        val port = serverEndpoint?.port ?: return emptyList()
        return StudyFlowLanEndpointResolver.endpointsForPort(requireNotNull(port))
    }

    override fun close() {
        lanDiscovery?.let { discoveryRegistry.remove(it) }
        lanDiscovery?.close()
        lanDiscovery = null
        reconciliationScheduler?.shutdownNow()
        reconciliationScheduler = null
        runtime.close()
        serverEndpoint = null
        browserBridgeEndpoint = null
        started.set(false)
    }

    companion object {
        const val GenericSflpTvPeerEnabled = true
        const val LegacyPhoneTvTransportRemainsCompatibilityAdapter = true
        const val AndroidTvIsNotPhoneHub = true
        const val PairingWorksWithAndroidDesktopWebAndWebTv = true

        private fun defaultDisplayName(): String = buildString {
            append("StudyFlow Android TV")
            append(" · ")
            append(Build.MODEL.ifBlank { "display" })
        }
    }
}
