package com.mikeyphw.studyflow.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.mikeyphw.studyflow.core.link.StudyFlowDiscoveredPeer
import com.mikeyphw.studyflow.core.link.StudyFlowDiscovery
import com.mikeyphw.studyflow.core.link.StudyFlowDiscoverySource
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMdnsContract
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

@Suppress("DEPRECATION")
class AndroidStudyFlowNsdDiscovery(
    context: Context,
    private val localPeer: StudyFlowLinkPeerDescriptor,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : StudyFlowDiscovery, Closeable {
    private val nsdManager = context.applicationContext.getSystemService(NsdManager::class.java)
    private val records = ConcurrentHashMap<String, StudyFlowDiscoveredPeer>()
    private var discoveryStarted = false
    private var registrationStarted = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (serviceInfo.serviceType != StudyFlowMdnsContract.ServiceType) return
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val txt = resolved.attributes.mapValues { (_, value) ->
                            value.toString(Charsets.UTF_8)
                        }
                        val host = resolved.host?.hostAddress?.substringBefore('%') ?: return
                        val peer = StudyFlowMdnsContract.resolve(
                            serviceName = resolved.serviceName,
                            host = host,
                            port = resolved.port,
                            txt = txt,
                            source = StudyFlowDiscoverySource.ANDROID_NSD,
                            nowEpochMillis = nowEpochMillis(),
                        ) ?: return
                        if (peer.deviceId == localPeer.deviceId) return
                        records[peer.discoveryId] = peer
                    }
                },
            )
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            records.entries.removeIf { (_, record) ->
                record.discoveryId.contains(":${serviceInfo.serviceName}:")
            }
        }

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            discoveryStarted = false
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            discoveryStarted = false
        }
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            registrationStarted = false
        }
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
    }

    fun start(tcpPort: Int) {
        if (!registrationStarted) {
            val advertisement = StudyFlowMdnsContract.advertise(localPeer, tcpPort)
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = advertisement.serviceName
                serviceType = advertisement.serviceType
                port = advertisement.port
                advertisement.txt.forEach { (key, value) -> setAttribute(key, value) }
            }
            registrationStarted = true
            runCatching {
                nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            }.onFailure { registrationStarted = false }
        }
        if (!discoveryStarted) {
            discoveryStarted = true
            runCatching {
                nsdManager.discoverServices(
                    StudyFlowMdnsContract.ServiceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener,
                )
            }.onFailure { discoveryStarted = false }
        }
    }

    override fun snapshot(nowEpochMillis: Long): List<StudyFlowDiscoveredPeer> {
        records.entries.removeIf { !it.value.isActive(nowEpochMillis) }
        return records.values.sortedBy { it.deviceId?.value.orEmpty() }
    }

    override fun close() {
        if (discoveryStarted) runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        if (registrationStarted) runCatching { nsdManager.unregisterService(registrationListener) }
        discoveryStarted = false
        registrationStarted = false
        records.clear()
    }
}
