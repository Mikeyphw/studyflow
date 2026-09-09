package com.mikeyphw.studyflow.link

import android.content.Context
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
import com.mikeyphw.studyflow.core.link.StudyFlowReconnectResult
import com.mikeyphw.studyflow.core.link.StudyFlowTransportEndpoint
import com.mikeyphw.studyflow.core.link.StudyFlowTrustedPeerRecord
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class AndroidStudyFlowLinkPeer(
    context: Context,
    displayName: String = defaultDisplayName(),
) : Closeable {
    private val appContext = context.applicationContext
    private val persistence = AndroidStudyFlowLinkStatePersistence(appContext)
    private val identity = StudyFlowDeviceIdentityManager(persistence.identityStore).getOrCreate(
        displayName = displayName,
        clientKind = StudyFlowLinkClientKind.ANDROID,
        capabilities = AndroidCapabilities,
        nowEpochMillis = System.currentTimeMillis(),
    )
    private val manualDiscovery = StudyFlowManualDiscovery()
    private val nsdDiscovery = AndroidStudyFlowNsdDiscovery(appContext, identity.descriptor)
    private val discoveryRegistry = StudyFlowDiscoveryRegistry(listOf(manualDiscovery, nsdDiscovery))
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

    fun start(): StudyFlowTransportEndpoint {
        serverEndpoint?.let { return it }
        check(started.compareAndSet(false, true)) { "Android SFLP peer is already starting" }
        return try {
            val endpoint = runtime.startServer()
            serverEndpoint = endpoint
            val port = requireNotNull(endpoint.port)
            runCatching { nsdDiscovery.start(port) }
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
            runCatching { com.mikeyphw.studyflow.core.link.StudyFlowManualEndpointParser.parse(hint) }.getOrNull()
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

    fun revoke(peerDeviceId: StudyFlowDeviceId): StudyFlowTrustedPeerRecord? = runtime.revoke(peerDeviceId)

    fun forget(peerDeviceId: StudyFlowDeviceId): Boolean = runtime.forget(peerDeviceId)

    fun localEndpoints(): List<StudyFlowTransportEndpoint> {
        val port = serverEndpoint?.port ?: return emptyList()
        return StudyFlowLanEndpointResolver.endpointsForPort(requireNotNull(port))
    }

    override fun close() {
        discoveryRegistry.remove(nsdDiscovery)
        nsdDiscovery.close()
        lanDiscovery?.let { discoveryRegistry.remove(it) }
        lanDiscovery?.close()
        lanDiscovery = null
        runtime.close()
        serverEndpoint = null
        started.set(false)
    }

    companion object {
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
