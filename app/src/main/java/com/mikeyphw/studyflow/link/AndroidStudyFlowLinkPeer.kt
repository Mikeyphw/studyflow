package com.mikeyphw.studyflow.link

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceIdentityManager
import com.mikeyphw.studyflow.core.link.StudyFlowDiscoveryRegistry
import com.mikeyphw.studyflow.core.link.StudyFlowDiscoveredPeer
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
import com.mikeyphw.studyflow.core.link.StudyFlowProductStateBridge
import com.mikeyphw.studyflow.core.link.StudyFlowProductStateContract
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceAccess
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

object AndroidStudyFlowLocalNetworkPermission {
    const val Permission: String = "android.permission.NEARBY_WIFI_DEVICES"

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.applicationContext.checkSelfPermission(Permission) == PackageManager.PERMISSION_GRANTED
}

enum class AndroidStudyFlowPeerLifecycleStatus {
    DISABLED,
    STOPPED,
    PERMISSION_REQUIRED,
    STARTING,
    READY,
    ERROR,
}

data class AndroidStudyFlowPeerLifecycleState(
    val userEnabled: Boolean,
    val permissionGranted: Boolean,
    val status: AndroidStudyFlowPeerLifecycleStatus,
    val tcpEndpoint: StudyFlowTransportEndpoint? = null,
    val webSocketEndpoint: StudyFlowTransportEndpoint? = null,
    val lastFailure: String? = null,
) {
    val ready: Boolean get() = status == AndroidStudyFlowPeerLifecycleStatus.READY && tcpEndpoint != null
}

class AndroidStudyFlowLinkPeer(
    context: Context,
    displayName: String = defaultDisplayName(),
) : Closeable {
    private val appContext = context.applicationContext
    private val lifecyclePreferences: SharedPreferences =
        appContext.getSharedPreferences("studyflow_sflp_peer_lifecycle", Context.MODE_PRIVATE)
    private val persistence = AndroidStudyFlowLinkStatePersistence(appContext)
    private val identity = StudyFlowDeviceIdentityManager(persistence.identityStore).getOrCreate(
        displayName = displayName,
        clientKind = StudyFlowLinkClientKind.ANDROID,
        capabilities = AndroidCapabilities,
        nowEpochMillis = System.currentTimeMillis(),
    )
    private val manualDiscovery = StudyFlowManualDiscovery()
    private val nsdDiscovery = AndroidStudyFlowNsdDiscovery(
        context = appContext,
        localPeer = identity.descriptor,
        localNetworkPermissionGranted = { AndroidStudyFlowLocalNetworkPermission.isGranted(appContext) },
    )
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
    @Volatile
    private var lifecycleStatus: AndroidStudyFlowPeerLifecycleStatus =
        if (isLocalPeerEnabled()) AndroidStudyFlowPeerLifecycleStatus.STOPPED else AndroidStudyFlowPeerLifecycleStatus.DISABLED
    @Volatile
    private var lastStartupFailure: String? = null

    val localDeviceId: StudyFlowDeviceId get() = identity.descriptor.deviceId

    fun isLocalPeerEnabled(): Boolean = lifecyclePreferences.getBoolean(KEY_USER_ENABLED, false)

    fun lifecycleState(): AndroidStudyFlowPeerLifecycleState = AndroidStudyFlowPeerLifecycleState(
        userEnabled = isLocalPeerEnabled(),
        permissionGranted = AndroidStudyFlowLocalNetworkPermission.isGranted(appContext),
        status = lifecycleStatus,
        tcpEndpoint = serverEndpoint,
        webSocketEndpoint = browserBridgeEndpoint,
        lastFailure = lastStartupFailure,
    )

    fun enableLocalPeer(): StudyFlowTransportEndpoint {
        lifecyclePreferences.edit().putBoolean(KEY_USER_ENABLED, true).apply()
        return start()
    }

    fun disableLocalPeer() {
        lifecyclePreferences.edit().putBoolean(KEY_USER_ENABLED, false).apply()
        stopLocalTransports(markDisabled = true)
    }

    fun startIfUserEnabled(): StudyFlowTransportEndpoint? {
        if (!isLocalPeerEnabled()) {
            lifecycleStatus = AndroidStudyFlowPeerLifecycleStatus.DISABLED
            return null
        }
        return runCatching { start() }
            .onFailure { lastStartupFailure = it.javaClass.simpleName }
            .getOrNull()
    }

    fun start(): StudyFlowTransportEndpoint {
        require(isLocalPeerEnabled()) { "Local SFLP peer must be enabled by the user before sockets start." }
        require(AndroidStudyFlowLocalNetworkPermission.isGranted(appContext)) {
            lifecycleStatus = AndroidStudyFlowPeerLifecycleStatus.PERMISSION_REQUIRED
            "Local network permission is required before SFLP sockets start."
        }
        serverEndpoint?.let { return it }
        check(started.compareAndSet(false, true)) { "Android SFLP peer is already starting" }
        lifecycleStatus = AndroidStudyFlowPeerLifecycleStatus.STARTING
        lastStartupFailure = null
        return try {
            discoveryRegistry.add(nsdDiscovery)
            val endpoint = runtime.startServer()
            serverEndpoint = endpoint
            browserBridgeEndpoint = runtime.startWebSocketServer()
            val port = requireNotNull(endpoint.port)
            nsdDiscovery.start(port)
            StudyFlowLanBeaconDiscovery(identity.descriptor, port).also { discovery ->
                discovery.start()
                lanDiscovery = discovery
                discoveryRegistry.add(discovery)
            }
            runtime.ingestDiscovery()
            lifecycleStatus = AndroidStudyFlowPeerLifecycleStatus.READY
            endpoint
        } catch (error: Throwable) {
            lastStartupFailure = error.javaClass.simpleName
            stopLocalTransports(markDisabled = false)
            lifecycleStatus = AndroidStudyFlowPeerLifecycleStatus.ERROR
            throw error
        }
    }

    fun pairingOffer(): StudyFlowPairingPublicOffer {
        if (!isLocalPeerEnabled()) lifecyclePreferences.edit().putBoolean(KEY_USER_ENABLED, true).apply()
        val endpoint = serverEndpoint ?: start()
        val port = requireNotNull(endpoint.port)
        val tcpEndpoints = StudyFlowLanEndpointResolver.endpointsForPort(port)
            .filter(StudyFlowLanEndpointResolver::isExternallyReachableEndpoint)
        val webSocketPort = browserBridgeEndpoint?.port
        val webSocketEndpoints = if (webSocketPort == null) emptyList() else tcpEndpoints.map { reachable ->
            reachable.copy(
                scheme = StudyFlowTransportScheme.WEBSOCKET,
                port = webSocketPort,
                path = "/sflp",
            )
        }
        val hints = (webSocketEndpoints + tcpEndpoints)
            .filter(StudyFlowLanEndpointResolver::isExternallyReachableEndpoint)
            .map(StudyFlowTransportEndpoint::canonicalUri)
            .distinct()
        require(hints.isNotEmpty()) { "No externally reachable LAN endpoint is available for the pairing QR." }
        return runtime.createPairingOffer(hints)
    }

    fun pairFromQr(qrPayload: String): StudyFlowTrustedPeerRecord {
        val payload = StudyFlowPairingQrCodec.decode(qrPayload)
        val record = runtime.pairingClient.pairFromQr(qrPayload)
        val endpoints = payload.endpointHints.mapNotNull { hint ->
            runCatching { com.mikeyphw.studyflow.core.link.StudyFlowManualEndpointParser.parse(hint) }.getOrNull()
        }.filter(StudyFlowLanEndpointResolver::isExternallyReachableEndpoint)
        if (endpoints.isNotEmpty()) runtime.rememberEndpoints(record.peer.deviceId, endpoints)
        runtime.grantWorkspaceAccess(StudyFlowProductStateContract.WorkspaceId, record.peer.deviceId, StudyFlowWorkspaceAccess.WRITE)
        return record
    }

    fun rememberManualEndpoint(value: String): StudyFlowDiscoveredPeer {
        val discovered = manualDiscovery.remember(value, System.currentTimeMillis())
        runtime.ingestDiscovery()
        return discovered
    }

    fun discoveredPeers(): List<StudyFlowDiscoveredPeer> {
        startIfUserEnabled()
        return runtime.ingestDiscovery()
    }

    fun trustedPeers(): List<StudyFlowTrustedPeerRecord> = runtime.trustManager.trustedPeers()

    fun reconcile(
        workspaceId: StudyFlowWorkspaceId,
        peerDeviceId: StudyFlowDeviceId,
        endpoint: StudyFlowTransportEndpoint? = null,
    ): StudyFlowReconnectResult {
        startIfUserEnabled()
        runtime.ingestDiscovery()
        return if (endpoint == null) {
            runtime.reconcileWith(workspaceId, peerDeviceId)
        } else {
            require(StudyFlowLanEndpointResolver.isExternallyReachableEndpoint(endpoint)) {
                "Manual reconciliation endpoint must be externally reachable."
            }
            runtime.rememberEndpoints(peerDeviceId, listOf(endpoint))
            runtime.reconcileWith(workspaceId, peerDeviceId, endpoint)
        }
    }

    fun registerWritableWorkspace(workspaceId: StudyFlowWorkspaceId) {
        runtime.workspace(workspaceId)
        trustedPeers().filter { it.peer.capabilities.contains(StudyFlowLinkCapabilities.WorkspaceWrite) }.forEach { peer ->
            runtime.grantWorkspaceAccess(workspaceId, peer.peer.deviceId, StudyFlowWorkspaceAccess.WRITE)
        }
    }

    fun reconcileProductState(bridge: StudyFlowProductStateBridge): Int {
        val workspaceId = StudyFlowProductStateContract.WorkspaceId
        registerWritableWorkspace(workspaceId)
        var changes = runtime.flushProductState(workspaceId, bridge)
        startIfUserEnabled()
        runtime.ingestDiscovery()
        runtime.workspacePeers(workspaceId).forEach { peer ->
            runCatching { runtime.reconcileWith(workspaceId, peer.peer.deviceId) }
        }
        changes += runtime.projectProductState(workspaceId, bridge)
        return changes
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

    fun revoke(peerDeviceId: StudyFlowDeviceId): StudyFlowTrustedPeerRecord? = runtime.revoke(peerDeviceId)

    fun forget(peerDeviceId: StudyFlowDeviceId): Boolean = runtime.forget(peerDeviceId)

    fun localEndpoints(): List<StudyFlowTransportEndpoint> {
        val port = serverEndpoint?.port ?: return emptyList()
        val tcp = StudyFlowLanEndpointResolver.endpointsForPort(requireNotNull(port))
            .filter(StudyFlowLanEndpointResolver::isExternallyReachableEndpoint)
        val wsPort = browserBridgeEndpoint?.port ?: return tcp
        return (tcp.map { it.copy(scheme = StudyFlowTransportScheme.WEBSOCKET, port = wsPort, path = "/sflp") } + tcp)
            .filter(StudyFlowLanEndpointResolver::isExternallyReachableEndpoint)
            .distinct()
    }

    private fun stopLocalTransports(markDisabled: Boolean) {
        discoveryRegistry.remove(nsdDiscovery)
        nsdDiscovery.close()
        lanDiscovery?.let { discoveryRegistry.remove(it) }
        lanDiscovery?.close()
        lanDiscovery = null
        runtime.close()
        serverEndpoint = null
        browserBridgeEndpoint = null
        started.set(false)
        lifecycleStatus = if (markDisabled) AndroidStudyFlowPeerLifecycleStatus.DISABLED else AndroidStudyFlowPeerLifecycleStatus.STOPPED
    }

    override fun close() {
        stopLocalTransports(markDisabled = !isLocalPeerEnabled())
        if (isLocalPeerEnabled()) lifecycleStatus = AndroidStudyFlowPeerLifecycleStatus.STOPPED
    }

    companion object {
        private const val KEY_USER_ENABLED = "sflp_user_enabled"
        val AndroidCapabilities: List<StudyFlowLinkCapability> = listOf(
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
            StudyFlowLinkCapabilities.TouchInput,
        )

        private fun defaultDisplayName(): String = buildString {
            append(Build.MANUFACTURER.ifBlank { "Android" }.replaceFirstChar(Char::uppercase))
            append(' ')
            append(Build.MODEL.ifBlank { "device" })
        }
    }
}
