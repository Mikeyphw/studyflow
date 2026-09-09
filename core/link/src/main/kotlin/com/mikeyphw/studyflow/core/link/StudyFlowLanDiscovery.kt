package com.mikeyphw.studyflow.core.link

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class StudyFlowLanBeacon(
    val peer: StudyFlowLinkPeerDescriptor,
    val tcpPort: Int,
    val emittedAtEpochMillis: Long,
    val ttlMillis: Long = 8_000L,
) {
    init {
        require(tcpPort in 1..65_535) { "LAN beacon TCP port is invalid" }
        require(emittedAtEpochMillis >= 0L) { "LAN beacon timestamp must not be negative" }
        require(ttlMillis in 1_000L..60_000L) { "LAN beacon TTL is out of range" }
    }
}

object StudyFlowLanBeaconCodec {
    const val Magic = "STUDYFLOW-SFLP-LAN-V1\n"

    fun encode(beacon: StudyFlowLanBeacon): ByteArray =
        (Magic + StudyFlowLinkCodec.json.encodeToString(beacon)).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray, length: Int = bytes.size): StudyFlowLanBeacon {
        val encoded = bytes.copyOfRange(0, length).toString(Charsets.UTF_8)
        require(encoded.startsWith(Magic)) { "Not a StudyFlow LAN beacon" }
        return StudyFlowLinkCodec.json.decodeFromString(encoded.removePrefix(Magic))
    }
}

object StudyFlowLanEndpointResolver {
    fun endpointsForPort(port: Int): List<StudyFlowTransportEndpoint> {
        require(port in 1..65_535) { "LAN endpoint port is invalid" }
        return networkInterfaces()
            .flatMap { network -> network.inetAddresses.toList() }
            .filter { address -> !address.isLoopbackAddress && !address.isAnyLocalAddress }
            .mapNotNull { address -> address.hostAddress?.substringBefore('%') }
            .distinct()
            .sorted()
            .map { host -> StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, host, port) }
    }

    fun ipv4BroadcastAddresses(): List<InetAddress> = networkInterfaces()
        .flatMap { it.interfaceAddresses }
        .mapNotNull { it.broadcast }
        .filterIsInstance<Inet4Address>()
        .distinctBy(InetAddress::getHostAddress)
        .ifEmpty { listOf(InetAddress.getByName("255.255.255.255")) }

    private fun networkInterfaces(): List<NetworkInterface> = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { network -> runCatching { network.isUp }.getOrDefault(false) }
    } catch (_: SocketException) {
        emptyList()
    }
}

class StudyFlowLanBeaconDiscovery(
    private val localPeer: StudyFlowLinkPeerDescriptor,
    private val tcpPort: Int,
    private val clock: StudyFlowPeerClock = StudyFlowPeerClock(System::currentTimeMillis),
    private val beaconPort: Int = DefaultBeaconPort,
    private val broadcastIntervalMillis: Long = 2_000L,
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2) { runnable ->
        Thread(runnable, "studyflow-sflp-lan-discovery").apply { isDaemon = true }
    },
) : StudyFlowDiscovery, Closeable {
    private val started = AtomicBoolean(false)
    private val records = ConcurrentHashMap<StudyFlowDeviceId, StudyFlowDiscoveredPeer>()
    private var receiverSocket: DatagramSocket? = null

    init {
        require(tcpPort in 1..65_535) { "LAN discovery TCP port is invalid" }
        require(beaconPort in 1..65_535) { "LAN discovery beacon port is invalid" }
        require(broadcastIntervalMillis in 500L..30_000L) { "LAN discovery interval is out of range" }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        val receiver = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(beaconPort))
            soTimeout = 1_000
        }
        receiverSocket = receiver
        scheduler.execute { receiveLoop(receiver) }
        scheduler.scheduleWithFixedDelay(
            { runCatching { broadcast() } },
            0L,
            broadcastIntervalMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    override fun snapshot(nowEpochMillis: Long): List<StudyFlowDiscoveredPeer> {
        records.entries.removeIf { !it.value.isActive(nowEpochMillis) }
        return records.values
            .filter { it.deviceId != localPeer.deviceId && it.isActive(nowEpochMillis) }
            .sortedBy { it.deviceId?.value.orEmpty() }
    }

    private fun broadcast() {
        if (!started.get()) return
        val beacon = StudyFlowLanBeacon(
            peer = localPeer,
            tcpPort = tcpPort,
            emittedAtEpochMillis = clock.nowEpochMillis(),
        )
        val bytes = StudyFlowLanBeaconCodec.encode(beacon)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            StudyFlowLanEndpointResolver.ipv4BroadcastAddresses().forEach { address ->
                socket.send(DatagramPacket(bytes, bytes.size, address, beaconPort))
            }
        }
    }

    private fun receiveLoop(socket: DatagramSocket) {
        val buffer = ByteArray(64 * 1024)
        while (started.get() && !socket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            val received = runCatching {
                socket.receive(packet)
                packet
            }.getOrNull() ?: continue
            val beacon = runCatching {
                StudyFlowLanBeaconCodec.decode(received.data, received.length)
            }.getOrNull() ?: continue
            if (beacon.peer.deviceId == localPeer.deviceId) continue
            val host = received.address?.hostAddress?.substringBefore('%') ?: continue
            records[beacon.peer.deviceId] = StudyFlowDiscoveredPeer(
                discoveryId = "lan:${beacon.peer.deviceId.value}",
                deviceId = beacon.peer.deviceId,
                displayName = beacon.peer.displayName,
                clientKind = beacon.peer.clientKind,
                protocolSupport = beacon.peer.protocolSupport,
                endpoints = listOf(
                    StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, host, beacon.tcpPort),
                ),
                source = StudyFlowDiscoverySource.LAN_BEACON,
                discoveredAtEpochMillis = clock.nowEpochMillis(),
                expiresAtEpochMillis = clock.nowEpochMillis() + beacon.ttlMillis,
            )
        }
    }

    override fun close() {
        started.set(false)
        receiverSocket?.close()
        receiverSocket = null
        scheduler.shutdownNow()
    }

    companion object {
        const val DefaultBeaconPort: Int = 45_457
    }
}
