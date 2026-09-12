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
import com.mikeyphw.studyflow.core.link.StudyFlowTrustedPeerRecord
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

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
    )
    private val started = AtomicBoolean(false)
    private var lanDiscovery: StudyFlowLanBeaconDiscovery? = null
    private var serverEndpoint: StudyFlowTransportEndpoint? = null

    val localDeviceId: StudyFlowDeviceId get() = identity.descriptor.deviceId
    val localDescriptor get() = identity.descriptor

    fun start(): StudyFlowTransportEndpoint {
        serverEndpoint?.let { return it }
        check(started.compareAndSet(false, true)) { "Android TV SFLP peer is already starting" }
        return try {
            val endpoint = runtime.startServer()
            serverEndpoint = endpoint
            val port = requireNotNull(endpoint.port)
            runCatching {
                StudyFlowLanBeaconDiscovery(identity.descriptor, port).also { discovery ->
                    discovery.start()
                    lanDiscovery = discovery
                    discoveryRegistry.add(discovery)
                }
            }
            runtime.ingestDiscovery()
            endpoint
        } catch (error: Throwable) {
            started.set(false)
            throw error
        }
    }

    fun pairingOffer(): StudyFlowPairingPublicOffer {
        val endpoint = serverEndpoint ?: start()
        val port = requireNotNull(endpoint.port)
        val hints = StudyFlowLanEndpointResolver.endpointsForPort(port)
            .map(StudyFlowTransportEndpoint::canonicalUri)
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
        runtime.close()
        serverEndpoint = null
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
