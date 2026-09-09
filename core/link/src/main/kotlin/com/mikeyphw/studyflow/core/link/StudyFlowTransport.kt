package com.mikeyphw.studyflow.core.link

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DefaultConnectTimeoutMillis = 10_000
private const val DefaultReadTimeoutMillis = 30_000
private const val DefaultMaxFrameBytes = 1_048_576

@Serializable
enum class StudyFlowTransportScheme(val wireValue: String) {
    @SerialName("sflp+tcp")
    TCP("sflp+tcp"),

    @SerialName("ws")
    WEBSOCKET("ws"),

    @SerialName("wss")
    WEBSOCKET_SECURE("wss"),

    @SerialName("sflp+relay")
    RELAY("sflp+relay"),

    @SerialName("loopback")
    LOOPBACK("loopback");

    companion object {
        fun fromWireValue(value: String): StudyFlowTransportScheme? = entries.firstOrNull { it.wireValue == value }
    }
}

@Serializable
data class StudyFlowTransportEndpoint(
    val scheme: StudyFlowTransportScheme,
    val host: String,
    val port: Int? = null,
    val path: String = "",
) {
    init {
        require(host.isNotBlank()) { "Transport endpoint host must not be blank" }
        require(port == null || port in 0..65_535) { "Transport endpoint port is out of range" }
        require(path.isEmpty() || path.startsWith('/')) { "Transport endpoint path must be empty or absolute" }
        when (scheme) {
            StudyFlowTransportScheme.TCP -> requireNotNull(port) { "TCP endpoint requires a port" }
            StudyFlowTransportScheme.WEBSOCKET,
            StudyFlowTransportScheme.WEBSOCKET_SECURE,
            StudyFlowTransportScheme.RELAY,
            -> Unit
            StudyFlowTransportScheme.LOOPBACK -> require(port == null) { "Loopback endpoint must not use a port" }
        }
    }

    fun canonicalUri(): String {
        val encodedHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
        val authority = buildString {
            append(encodedHost)
            if (port != null) append(":$port")
        }
        return "${scheme.wireValue}://$authority$path"
    }
}

object StudyFlowManualEndpointParser {
    fun parse(value: String, defaultTcpPort: Int? = null): StudyFlowTransportEndpoint {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Manual endpoint must not be blank" }
        if (!trimmed.contains("://")) {
            return parseBareHost(trimmed, defaultTcpPort)
        }
        val uri = URI(trimmed)
        val scheme = StudyFlowTransportScheme.fromWireValue(uri.scheme.lowercase())
            ?: error("Unsupported StudyFlow transport scheme ${uri.scheme}")
        val host = uri.host ?: error("Transport URI requires a host")
        val port = uri.port.takeIf { it >= 0 }
        return StudyFlowTransportEndpoint(
            scheme = scheme,
            host = host,
            port = port,
            path = uri.rawPath.orEmpty(),
        )
    }

    private fun parseBareHost(value: String, defaultTcpPort: Int?): StudyFlowTransportEndpoint {
        val bracketed = Regex("^\\[([^]]+)](?::(\\d+))?$").matchEntire(value)
        if (bracketed != null) {
            val host = bracketed.groupValues[1]
            val port = bracketed.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: defaultTcpPort
            return StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, host, port)
        }
        val separator = value.lastIndexOf(':')
        val hasSingleColon = separator > 0 && value.indexOf(':') == separator
        val host = if (hasSingleColon) value.substring(0, separator) else value
        val explicitPort = if (hasSingleColon) value.substring(separator + 1).toIntOrNull() else null
        val port = explicitPort ?: defaultTcpPort
        return StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, host, port)
    }
}

data class StudyFlowTransportOptions(
    val connectTimeoutMillis: Int = DefaultConnectTimeoutMillis,
    val readTimeoutMillis: Int = DefaultReadTimeoutMillis,
    val maxFrameBytes: Int = DefaultMaxFrameBytes,
) {
    init {
        require(connectTimeoutMillis > 0) { "Connect timeout must be positive" }
        require(readTimeoutMillis > 0) { "Read timeout must be positive" }
        require(maxFrameBytes in 1..DefaultMaxFrameBytes) { "Frame limit must be within the SFLP v1 maximum" }
    }
}

interface StudyFlowConnection : Closeable {
    val localEndpoint: StudyFlowTransportEndpoint?
    val remoteEndpoint: StudyFlowTransportEndpoint
    val isOpen: Boolean
    fun sendFrame(frame: ByteArray)
    fun receiveFrame(): ByteArray?
}

interface StudyFlowConnectionListener : Closeable {
    val boundEndpoint: StudyFlowTransportEndpoint
    val isOpen: Boolean
    fun accept(): StudyFlowConnection?
}

interface StudyFlowTransport {
    val schemes: Set<StudyFlowTransportScheme>
    fun connect(
        endpoint: StudyFlowTransportEndpoint,
        options: StudyFlowTransportOptions = StudyFlowTransportOptions(),
    ): StudyFlowConnection
}

interface StudyFlowListeningTransport : StudyFlowTransport {
    fun listen(
        endpoint: StudyFlowTransportEndpoint,
        options: StudyFlowTransportOptions = StudyFlowTransportOptions(),
    ): StudyFlowConnectionListener
}

class StudyFlowTransportRegistry(
    transports: Iterable<StudyFlowTransport> = emptyList(),
) {
    private val byScheme = linkedMapOf<StudyFlowTransportScheme, StudyFlowTransport>()

    init {
        transports.forEach(::register)
    }

    fun register(transport: StudyFlowTransport) {
        require(transport.schemes.isNotEmpty()) { "Transport must support at least one scheme" }
        synchronized(byScheme) {
            transport.schemes.forEach { scheme ->
                require(scheme !in byScheme) { "Transport already registered for ${scheme.wireValue}" }
                byScheme[scheme] = transport
            }
        }
    }

    fun resolve(endpoint: StudyFlowTransportEndpoint): StudyFlowTransport =
        synchronized(byScheme) { byScheme[endpoint.scheme] }
            ?: error("No transport registered for ${endpoint.scheme.wireValue}")

    fun connect(
        endpoint: StudyFlowTransportEndpoint,
        options: StudyFlowTransportOptions = StudyFlowTransportOptions(),
    ): StudyFlowConnection = resolve(endpoint).connect(endpoint, options)
}

class StudyFlowLinkConnection(
    private val connection: StudyFlowConnection,
    private val maxFrameBytes: Int = StudyFlowTransportContract.MaxFrameBytes,
) : Closeable {
    init {
        require(maxFrameBytes in 1..StudyFlowTransportContract.MaxFrameBytes) { "Invalid SFLP frame limit" }
    }
    val remoteEndpoint: StudyFlowTransportEndpoint get() = connection.remoteEndpoint
    val isOpen: Boolean get() = connection.isOpen

    fun send(envelope: StudyFlowLinkEnvelope) {
        val frame = StudyFlowLinkCodec.encode(envelope).toByteArray(Charsets.UTF_8)
        require(frame.size in 1..maxFrameBytes) { "SFLP envelope exceeds configured frame limit" }
        connection.sendFrame(frame)
    }

    fun receive(): StudyFlowLinkEnvelope? {
        val frame = connection.receiveFrame() ?: return null
        require(frame.size in 1..maxFrameBytes) { "SFLP envelope exceeds configured frame limit" }
        return StudyFlowLinkCodec.decode(frame.toString(Charsets.UTF_8))
    }

    override fun close() = connection.close()
}

object StudyFlowLengthPrefixedFrameCodec {
    fun write(
        output: DataOutputStream,
        frame: ByteArray,
        maxFrameBytes: Int = DefaultMaxFrameBytes,
    ) {
        require(frame.size in 1..maxFrameBytes) { "SFLP frame exceeds configured bounds" }
        output.writeInt(frame.size)
        output.write(frame)
        output.flush()
    }

    fun read(
        input: DataInputStream,
        maxFrameBytes: Int = DefaultMaxFrameBytes,
    ): ByteArray? {
        val length = try {
            input.readInt()
        } catch (_: EOFException) {
            return null
        }
        require(length in 1..maxFrameBytes) { "SFLP frame length is invalid" }
        return ByteArray(length).also(input::readFully)
    }
}

class StudyFlowTcpTransport : StudyFlowListeningTransport {
    override val schemes: Set<StudyFlowTransportScheme> = setOf(StudyFlowTransportScheme.TCP)

    override fun connect(
        endpoint: StudyFlowTransportEndpoint,
        options: StudyFlowTransportOptions,
    ): StudyFlowConnection {
        require(endpoint.scheme == StudyFlowTransportScheme.TCP) { "TCP transport received a non-TCP endpoint" }
        require(requireNotNull(endpoint.port) > 0) { "TCP connect endpoint requires a non-zero port" }
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.soTimeout = options.readTimeoutMillis
        socket.connect(InetSocketAddress(endpoint.host, requireNotNull(endpoint.port)), options.connectTimeoutMillis)
        return StudyFlowTcpConnection(socket, endpoint, options)
    }

    override fun listen(
        endpoint: StudyFlowTransportEndpoint,
        options: StudyFlowTransportOptions,
    ): StudyFlowConnectionListener {
        require(endpoint.scheme == StudyFlowTransportScheme.TCP) { "TCP listener received a non-TCP endpoint" }
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(endpoint.host, requireNotNull(endpoint.port)))
        val address = server.inetAddress.hostAddress ?: endpoint.host
        val bound = StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, address, server.localPort)
        return StudyFlowTcpListener(server, bound, options)
    }
}

private class StudyFlowTcpConnection(
    private val socket: Socket,
    override val remoteEndpoint: StudyFlowTransportEndpoint,
    private val options: StudyFlowTransportOptions,
) : StudyFlowConnection {
    private val input by lazy { DataInputStream(socket.getInputStream().buffered()) }
    private val output by lazy { DataOutputStream(socket.getOutputStream().buffered()) }

    override val localEndpoint: StudyFlowTransportEndpoint?
        get() = socket.localAddress?.hostAddress?.let { host ->
            StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, host, socket.localPort)
        }

    override val isOpen: Boolean get() = socket.isConnected && !socket.isClosed

    override fun sendFrame(frame: ByteArray) {
        check(isOpen) { "Connection is closed" }
        synchronized(output) { StudyFlowLengthPrefixedFrameCodec.write(output, frame, options.maxFrameBytes) }
    }

    override fun receiveFrame(): ByteArray? {
        check(isOpen) { "Connection is closed" }
        return synchronized(input) { StudyFlowLengthPrefixedFrameCodec.read(input, options.maxFrameBytes) }
    }

    override fun close() = socket.close()
}

private class StudyFlowTcpListener(
    private val server: ServerSocket,
    override val boundEndpoint: StudyFlowTransportEndpoint,
    private val options: StudyFlowTransportOptions,
) : StudyFlowConnectionListener {
    override val isOpen: Boolean get() = !server.isClosed

    override fun accept(): StudyFlowConnection? {
        if (server.isClosed) return null
        val socket = try {
            server.accept()
        } catch (_: Exception) {
            return null
        }
        socket.tcpNoDelay = true
        socket.soTimeout = options.readTimeoutMillis
        val remoteHost = socket.inetAddress.hostAddress ?: socket.inetAddress.hostName
        val remote = StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, remoteHost, socket.port)
        return StudyFlowTcpConnection(socket, remote, options)
    }

    override fun close() = server.close()
}

fun interface StudyFlowTransportDialer {
    fun connect(endpoint: StudyFlowTransportEndpoint, options: StudyFlowTransportOptions): StudyFlowConnection
}

class StudyFlowAdapterTransport(
    override val schemes: Set<StudyFlowTransportScheme>,
    private val dialer: StudyFlowTransportDialer,
) : StudyFlowTransport {
    init {
        require(schemes.isNotEmpty()) { "Adapter transport must declare supported schemes" }
        require(StudyFlowTransportScheme.TCP !in schemes) { "Use StudyFlowTcpTransport for native TCP" }
    }

    override fun connect(
        endpoint: StudyFlowTransportEndpoint,
        options: StudyFlowTransportOptions,
    ): StudyFlowConnection {
        require(endpoint.scheme in schemes) { "Adapter transport does not support ${endpoint.scheme.wireValue}" }
        return dialer.connect(endpoint, options)
    }
}

@Serializable
enum class StudyFlowDiscoverySource {
    @SerialName("mdns")
    MDNS,

    @SerialName("android_nsd")
    ANDROID_NSD,

    @SerialName("manual")
    MANUAL,

    @SerialName("relay")
    RELAY,
}

@Serializable
data class StudyFlowDiscoveredPeer(
    val discoveryId: String,
    val deviceId: StudyFlowDeviceId? = null,
    val displayName: String? = null,
    val clientKind: StudyFlowLinkClientKind? = null,
    val protocolSupport: StudyFlowLinkProtocolSupport? = null,
    val endpoints: List<StudyFlowTransportEndpoint>,
    val source: StudyFlowDiscoverySource,
    val discoveredAtEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(discoveryId.isNotBlank()) { "Discovery id must not be blank" }
        require(endpoints.isNotEmpty()) { "Discovered peer must expose at least one endpoint" }
        require(endpoints.distinct().size == endpoints.size) { "Discovered endpoints must be unique" }
        require(expiresAtEpochMillis == null || expiresAtEpochMillis > discoveredAtEpochMillis) {
            "Discovery expiry must be after discovery time"
        }
    }

    fun isActive(nowEpochMillis: Long): Boolean = expiresAtEpochMillis == null || nowEpochMillis < expiresAtEpochMillis
}

interface StudyFlowDiscovery {
    fun snapshot(nowEpochMillis: Long): List<StudyFlowDiscoveredPeer>
}

class StudyFlowManualDiscovery : StudyFlowDiscovery {
    private val records = linkedMapOf<String, StudyFlowDiscoveredPeer>()

    fun remember(
        value: String,
        nowEpochMillis: Long,
        defaultTcpPort: Int? = null,
    ): StudyFlowDiscoveredPeer {
        val endpoint = StudyFlowManualEndpointParser.parse(value, defaultTcpPort)
        val canonical = endpoint.canonicalUri()
        val record = StudyFlowDiscoveredPeer(
            discoveryId = "manual:$canonical",
            endpoints = listOf(endpoint),
            source = StudyFlowDiscoverySource.MANUAL,
            discoveredAtEpochMillis = nowEpochMillis,
        )
        synchronized(records) { records[record.discoveryId] = record }
        return record
    }

    fun forget(discoveryId: String): Boolean = synchronized(records) { records.remove(discoveryId) != null }

    override fun snapshot(nowEpochMillis: Long): List<StudyFlowDiscoveredPeer> =
        synchronized(records) { records.values.filter { it.isActive(nowEpochMillis) }.sortedBy { it.discoveryId } }
}

class StudyFlowCompositeDiscovery(
    private val sources: List<StudyFlowDiscovery>,
) : StudyFlowDiscovery {
    override fun snapshot(nowEpochMillis: Long): List<StudyFlowDiscoveredPeer> {
        val merged = linkedMapOf<String, StudyFlowDiscoveredPeer>()
        sources.flatMap { it.snapshot(nowEpochMillis) }
            .filter { it.isActive(nowEpochMillis) }
            .sortedWith(compareBy({ it.deviceId?.value.orEmpty() }, { it.discoveryId }))
            .forEach { record ->
                val key = record.deviceId?.value ?: record.endpoints.joinToString { it.canonicalUri() }
                val existing = merged[key]
                merged[key] = if (existing == null) record else mergeDiscovery(existing, record)
            }
        return merged.values.toList()
    }

    private fun mergeDiscovery(
        left: StudyFlowDiscoveredPeer,
        right: StudyFlowDiscoveredPeer,
    ): StudyFlowDiscoveredPeer = left.copy(
        displayName = left.displayName ?: right.displayName,
        clientKind = left.clientKind ?: right.clientKind,
        protocolSupport = left.protocolSupport ?: right.protocolSupport,
        endpoints = (left.endpoints + right.endpoints).distinct().sortedBy(StudyFlowTransportEndpoint::canonicalUri),
        discoveredAtEpochMillis = minOf(left.discoveredAtEpochMillis, right.discoveredAtEpochMillis),
        expiresAtEpochMillis = listOfNotNull(left.expiresAtEpochMillis, right.expiresAtEpochMillis).maxOrNull(),
        metadata = left.metadata + right.metadata,
    )
}

@Serializable
data class StudyFlowMdnsAdvertisement(
    val serviceName: String,
    val serviceType: String,
    val port: Int,
    val txt: Map<String, String>,
)

object StudyFlowMdnsContract {
    const val ServiceType = "_studyflow._tcp."
    private const val DeviceKey = "device"
    private const val NameKey = "name"
    private const val KindKey = "kind"
    private const val MajorKey = "major"
    private const val MinMinorKey = "min_minor"
    private const val MaxMinorKey = "max_minor"
    private const val CapabilitiesKey = "caps_sha256"

    fun advertise(
        peer: StudyFlowLinkPeerDescriptor,
        port: Int,
        serviceName: String = peer.displayName,
    ): StudyFlowMdnsAdvertisement {
        require(port in 1..65_535) { "mDNS port is out of range" }
        return StudyFlowMdnsAdvertisement(
            serviceName = serviceName,
            serviceType = ServiceType,
            port = port,
            txt = linkedMapOf(
                DeviceKey to peer.deviceId.value,
                NameKey to peer.displayName,
                KindKey to clientKindWireValue(peer.clientKind),
                MajorKey to peer.protocolSupport.major.toString(),
                MinMinorKey to peer.protocolSupport.minMinor.toString(),
                MaxMinorKey to peer.protocolSupport.maxMinor.toString(),
                CapabilitiesKey to capabilitiesDigest(peer.capabilities),
            ),
        )
    }

    fun resolve(
        serviceName: String,
        host: String,
        port: Int,
        txt: Map<String, String>,
        source: StudyFlowDiscoverySource,
        nowEpochMillis: Long,
        ttlMillis: Long = 120_000L,
    ): StudyFlowDiscoveredPeer? {
        if (port !in 1..65_535 || ttlMillis <= 0L) return null
        val deviceId = txt[DeviceKey]
            ?.let { value -> runCatching { StudyFlowDeviceId(value) }.getOrNull() }
            ?: return null
        val displayName = txt[NameKey]?.takeIf(String::isNotBlank) ?: serviceName
        val kind = txt[KindKey]?.let(::parseClientKind) ?: return null
        val major = txt[MajorKey]?.toIntOrNull() ?: return null
        val minMinor = txt[MinMinorKey]?.toIntOrNull() ?: return null
        val maxMinor = txt[MaxMinorKey]?.toIntOrNull() ?: return null
        val support = runCatching { StudyFlowLinkProtocolSupport(major, minMinor, maxMinor) }.getOrNull() ?: return null
        return StudyFlowDiscoveredPeer(
            discoveryId = "$source:$serviceName:${deviceId.value}",
            deviceId = deviceId,
            displayName = displayName,
            clientKind = kind,
            protocolSupport = support,
            endpoints = listOf(StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, host, port)),
            source = source,
            discoveredAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = nowEpochMillis + ttlMillis,
            metadata = txt,
        )
    }

    private fun capabilitiesDigest(capabilities: List<StudyFlowLinkCapability>): String {
        val bytes = capabilities.map { it.value }.sorted().joinToString("\n").toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun clientKindWireValue(kind: StudyFlowLinkClientKind): String = when (kind) {
        StudyFlowLinkClientKind.ANDROID -> "android"
        StudyFlowLinkClientKind.DESKTOP -> "desktop"
        StudyFlowLinkClientKind.WEB -> "web"
        StudyFlowLinkClientKind.ANDROID_TV -> "android_tv"
        StudyFlowLinkClientKind.WEB_TV -> "web_tv"
    }

    private fun parseClientKind(value: String): StudyFlowLinkClientKind? = when (value) {
        "android" -> StudyFlowLinkClientKind.ANDROID
        "desktop" -> StudyFlowLinkClientKind.DESKTOP
        "web" -> StudyFlowLinkClientKind.WEB
        "android_tv" -> StudyFlowLinkClientKind.ANDROID_TV
        "web_tv" -> StudyFlowLinkClientKind.WEB_TV
        else -> null
    }
}

object StudyFlowTransportContract {
    const val MaxFrameBytes: Int = DefaultMaxFrameBytes
    const val MdnsServiceType: String = StudyFlowMdnsContract.ServiceType
    val NativeLanSchemes: Set<StudyFlowTransportScheme> = setOf(StudyFlowTransportScheme.TCP)
    val BrowserSchemes: Set<StudyFlowTransportScheme> = setOf(
        StudyFlowTransportScheme.WEBSOCKET,
        StudyFlowTransportScheme.WEBSOCKET_SECURE,
    )
    val FutureRelaySchemes: Set<StudyFlowTransportScheme> = setOf(StudyFlowTransportScheme.RELAY)
}
