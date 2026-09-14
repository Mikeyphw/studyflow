package com.mikeyphw.studyflow.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.mikeyphw.studyflow.core.link.StudyFlowDiscoveredPeer
import com.mikeyphw.studyflow.core.link.StudyFlowDiscovery
import com.mikeyphw.studyflow.core.link.StudyFlowDiscoverySource
import com.mikeyphw.studyflow.core.link.StudyFlowLanEndpointResolver
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMdnsContract
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android NSD adapter for the generic SFLP peer.
 *
 * OV13 makes this source restartable and user/permission-aware: start() records desired state,
 * transient NSD failures schedule a bounded restart, close() is a reversible stop rather than a
 * terminal object disposal, and service resolution is serialized/retried instead of relying on a
 * single one-shot resolve call per onServiceFound callback.
 */
@Suppress("DEPRECATION")
class AndroidStudyFlowNsdDiscovery(
    context: Context,
    private val localPeer: StudyFlowLinkPeerDescriptor,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val localNetworkPermissionGranted: () -> Boolean = { true },
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "studyflow-android-nsd-recovery").apply { isDaemon = true }
    },
) : StudyFlowDiscovery, Closeable {
    private val nsdManager = context.applicationContext.getSystemService(NsdManager::class.java)
    private val records = ConcurrentHashMap<String, StudyFlowDiscoveredPeer>()
    private val pendingResolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolving = AtomicBoolean(false)
    private val lifecycleLock = Any()

    @Volatile
    private var desiredStarted = false
    @Volatile
    private var tcpPort: Int? = null
    @Volatile
    private var discoveryStarted = false
    @Volatile
    private var registrationStarted = false
    @Volatile
    private var lastFailure: String? = null

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            discoveryStarted = true
            lastFailure = null
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (serviceInfo.serviceType != StudyFlowMdnsContract.ServiceType) return
            enqueueResolve(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            records.entries.removeIf { (_, record) ->
                record.discoveryId.contains(":${serviceInfo.serviceName}:")
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            discoveryStarted = false
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            discoveryStarted = false
            lastFailure = "discover:$errorCode"
            scheduleRestartAfterFailure()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            discoveryStarted = false
            lastFailure = "stop:$errorCode"
            scheduleRestartAfterFailure()
        }
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            registrationStarted = true
            lastFailure = null
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            registrationStarted = false
            lastFailure = "register:$errorCode"
            scheduleRestartAfterFailure()
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            registrationStarted = false
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            registrationStarted = false
            lastFailure = "unregister:$errorCode"
        }
    }

    fun start(tcpPort: Int): Boolean = synchronized(lifecycleLock) {
        require(tcpPort in 1..65_535) { "SFLP NSD advertisement requires a real TCP port." }
        this.tcpPort = tcpPort
        desiredStarted = true
        if (!localNetworkPermissionGranted()) {
            lastFailure = "local-network-permission-required"
            return@synchronized false
        }
        val registered = startRegistrationLocked(tcpPort)
        val discovering = startDiscoveryLocked()
        registered || discovering
    }

    val ready: Boolean get() = discoveryStarted || registrationStarted

    fun lastFailureReason(): String? = lastFailure

    override fun snapshot(nowEpochMillis: Long): List<StudyFlowDiscoveredPeer> {
        if (desiredStarted && localNetworkPermissionGranted() && !ready) {
            tcpPort?.let { scheduleRestartAfterFailure() }
        }
        records.entries.removeIf { !it.value.isActive(nowEpochMillis) }
        return records.values.sortedBy { it.deviceId?.value.orEmpty() }
    }

    override fun close() = synchronized(lifecycleLock) {
        desiredStarted = false
        pendingResolveQueue.clear()
        resolving.set(false)
        if (discoveryStarted) runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        if (registrationStarted) runCatching { nsdManager.unregisterService(registrationListener) }
        discoveryStarted = false
        registrationStarted = false
        records.clear()
    }

    private fun startRegistrationLocked(tcpPort: Int): Boolean {
        if (registrationStarted) return true
        val advertisement = StudyFlowMdnsContract.advertise(localPeer, tcpPort)
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = advertisement.serviceName
            serviceType = advertisement.serviceType
            port = advertisement.port
            advertisement.txt.forEach { (key, value) -> setAttribute(key, value) }
        }
        return runCatching {
            registrationStarted = true
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            true
        }.getOrElse { error ->
            registrationStarted = false
            lastFailure = "register:${error.javaClass.simpleName}"
            scheduleRestartAfterFailure()
            false
        }
    }

    private fun startDiscoveryLocked(): Boolean {
        if (discoveryStarted) return true
        return runCatching {
            discoveryStarted = true
            nsdManager.discoverServices(
                StudyFlowMdnsContract.ServiceType,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener,
            )
            true
        }.getOrElse { error ->
            discoveryStarted = false
            lastFailure = "discover:${error.javaClass.simpleName}"
            scheduleRestartAfterFailure()
            false
        }
    }

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        pendingResolveQueue.add(serviceInfo)
        drainResolveQueue()
    }

    private fun retryResolve(serviceInfo: NsdServiceInfo) {
        if (!desiredStarted || !localNetworkPermissionGranted()) return
        scheduler.schedule({ enqueueResolve(serviceInfo) }, ResolveRetryDelayMillis, TimeUnit.MILLISECONDS)
    }

    private fun drainResolveQueue() {
        if (!resolving.compareAndSet(false, true)) return
        val serviceInfo = pendingResolveQueue.poll()
        if (serviceInfo == null) {
            resolving.set(false)
            return
        }
        runCatching {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        resolving.set(false)
                        lastFailure = "resolve:$errorCode"
                        retryResolve(serviceInfo)
                        drainResolveQueue()
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        try {
                            val txt = resolved.attributes.mapValues { (_, value) ->
                                value.toString(Charsets.UTF_8)
                            }
                            val host = resolved.host?.hostAddress ?: return
                            if (!StudyFlowLanEndpointResolver.isExternallyReachableHost(host)) return
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
                            lastFailure = null
                        } finally {
                            resolving.set(false)
                            drainResolveQueue()
                        }
                    }
                },
            )
        }.onFailure { error ->
            resolving.set(false)
            lastFailure = "resolve:${error.javaClass.simpleName}"
            retryResolve(serviceInfo)
            drainResolveQueue()
        }
    }

    private fun scheduleRestartAfterFailure() {
        val port = tcpPort ?: return
        if (!desiredStarted || !localNetworkPermissionGranted()) return
        scheduler.schedule(
            {
                synchronized(lifecycleLock) {
                    if (desiredStarted && localNetworkPermissionGranted() && (!discoveryStarted || !registrationStarted)) {
                        if (!registrationStarted) startRegistrationLocked(port)
                        if (!discoveryStarted) startDiscoveryLocked()
                    }
                }
            },
            DiscoveryRestartDelayMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    private companion object {
        const val DiscoveryRestartDelayMillis = 2_000L
        const val ResolveRetryDelayMillis = 750L
    }
}
