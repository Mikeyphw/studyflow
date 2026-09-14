package com.mikeyphw.studyflow.core.link

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

private const val WebSocketMagic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
private const val MaxHttpUpgradeBytes = 16 * 1024

/**
 * Minimal RFC 6455 transport used as the browser/native SFLP bridge.
 *
 * SFLP owns message framing at the envelope level, so the WebSocket layer intentionally supports
 * text/binary messages plus close/ping/pong. RFC 6455 continuation frames are reassembled under the
 * same bounded SFLP message limit, and masking direction is enforced for browser/server peers.
 *
 * TLS (`wss`) is deliberately not implemented here. OV09 establishes protocol interoperability on
 * the LAN WebSocket bridge; transport confidentiality and credential hardening remain an OV10
 * security responsibility.
 */
class StudyFlowWebSocketTransport(
    private val expectedPath: String = "/sflp",
    private val secureRandom: SecureRandom = SecureRandom(),
) : StudyFlowListeningTransport {
    init {
        require(expectedPath.startsWith('/')) { "WebSocket path must be absolute" }
    }

    override val schemes: Set<StudyFlowTransportScheme> = setOf(StudyFlowTransportScheme.WEBSOCKET)

    override fun connect(
        endpoint: StudyFlowTransportEndpoint,
        options: StudyFlowTransportOptions,
    ): StudyFlowConnection {
        require(endpoint.scheme == StudyFlowTransportScheme.WEBSOCKET) {
            "WebSocket transport received ${endpoint.scheme.wireValue}"
        }
        val port = endpoint.port ?: 80
        require(port > 0) { "WebSocket connect endpoint requires a non-zero port" }
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.soTimeout = options.readTimeoutMillis
        socket.connect(InetSocketAddress(endpoint.host, port), options.connectTimeoutMillis)
        try {
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            val keyBytes = ByteArray(16).also(secureRandom::nextBytes)
            val key = encodeBase64(keyBytes)
            val path = endpoint.path.ifBlank { expectedPath }
            val hostHeader = if (port == 80) endpoint.host else "${endpoint.host}:$port"
            output.write(
                buildString {
                    append("GET $path HTTP/1.1\r\n")
                    append("Host: $hostHeader\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Version: 13\r\n")
                    append("Sec-WebSocket-Key: $key\r\n")
                    append("Sec-WebSocket-Protocol: studyflow.sflp.v1\r\n")
                    append("\r\n")
                }.toByteArray(Charsets.US_ASCII),
            )
            output.flush()
            val response = readHttpHeaders(input)
            require(response.startLine.startsWith("HTTP/1.1 101") || response.startLine.startsWith("HTTP/1.0 101")) {
                "WebSocket upgrade was rejected: ${response.startLine}"
            }
            require(response.header("upgrade")?.equals("websocket", ignoreCase = true) == true) {
                "WebSocket upgrade response is missing Upgrade: websocket"
            }
            require(response.header("connection")?.split(',')?.any { it.trim().equals("upgrade", true) } == true) {
                "WebSocket upgrade response is missing Connection: Upgrade"
            }
            val expectedAccept = websocketAccept(key)
            require(response.header("sec-websocket-accept") == expectedAccept) {
                "WebSocket upgrade response has an invalid accept key"
            }
            require(response.header("sec-websocket-protocol") == "studyflow.sflp.v1") {
                "WebSocket upgrade response did not select the SFLP subprotocol"
            }
            val remote = endpoint.copy(port = port, path = path)
            return StudyFlowWebSocketConnection(
                socket = socket,
                input = input,
                output = output,
                remoteEndpoint = remote,
                clientSide = true,
                options = options,
                secureRandom = secureRandom,
            )
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw error
        }
    }

    override fun listen(
        endpoint: StudyFlowTransportEndpoint,
        options: StudyFlowTransportOptions,
    ): StudyFlowConnectionListener {
        require(endpoint.scheme == StudyFlowTransportScheme.WEBSOCKET) {
            "WebSocket listener received ${endpoint.scheme.wireValue}"
        }
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(endpoint.host, endpoint.port ?: 0))
        val address = server.inetAddress.hostAddress ?: endpoint.host
        val path = endpoint.path.ifBlank { expectedPath }
        return StudyFlowWebSocketListener(
            server = server,
            boundEndpoint = StudyFlowTransportEndpoint(
                scheme = StudyFlowTransportScheme.WEBSOCKET,
                host = address,
                port = server.localPort,
                path = path,
            ),
            options = options,
            expectedPath = path,
            secureRandom = secureRandom,
        )
    }
}

private class StudyFlowWebSocketListener(
    private val server: ServerSocket,
    override val boundEndpoint: StudyFlowTransportEndpoint,
    private val options: StudyFlowTransportOptions,
    private val expectedPath: String,
    private val secureRandom: SecureRandom,
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
        return try {
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            val request = readHttpHeaders(input)
            val requestParts = request.startLine.split(' ')
            require(requestParts.size >= 3 && requestParts[0] == "GET") { "WebSocket upgrade requires GET" }
            val requestedPath = requestParts[1].substringBefore('?')
            require(requestedPath == expectedPath) { "WebSocket upgrade requested an unexpected path" }
            require(request.header("upgrade")?.equals("websocket", ignoreCase = true) == true) {
                "WebSocket upgrade request is missing Upgrade: websocket"
            }
            require(request.header("connection")?.split(',')?.any { it.trim().equals("upgrade", true) } == true) {
                "WebSocket upgrade request is missing Connection: Upgrade"
            }
            require(request.header("sec-websocket-version") == "13") { "Only RFC 6455 version 13 is supported" }
            val key = requireNotNull(request.header("sec-websocket-key")) { "WebSocket upgrade key is missing" }
            val protocolHeader = request.header("sec-websocket-protocol")
            val selectedProtocol = protocolHeader
                ?.split(',')
                ?.map { it.trim() }
                ?.firstOrNull { it == "studyflow.sflp.v1" }
            require(selectedProtocol != null) { "WebSocket client must offer the studyflow.sflp.v1 subprotocol" }
            output.write(
                buildString {
                    append("HTTP/1.1 101 Switching Protocols\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Accept: ${websocketAccept(key)}\r\n")
                    append("Sec-WebSocket-Protocol: $selectedProtocol\r\n")
                    append("\r\n")
                }.toByteArray(Charsets.US_ASCII),
            )
            output.flush()
            val remoteHost = socket.inetAddress.hostAddress ?: socket.inetAddress.hostName
            StudyFlowWebSocketConnection(
                socket = socket,
                input = input,
                output = output,
                remoteEndpoint = StudyFlowTransportEndpoint(
                    StudyFlowTransportScheme.WEBSOCKET,
                    remoteHost,
                    socket.port,
                    expectedPath,
                ),
                clientSide = false,
                options = options,
                secureRandom = secureRandom,
            )
        } catch (_: Throwable) {
            runCatching { socket.close() }
            null
        }
    }

    override fun close() = server.close()
}

private class StudyFlowWebSocketConnection(
    private val socket: Socket,
    private val input: InputStream,
    private val output: OutputStream,
    override val remoteEndpoint: StudyFlowTransportEndpoint,
    private val clientSide: Boolean,
    private val options: StudyFlowTransportOptions,
    private val secureRandom: SecureRandom,
) : StudyFlowConnection {
    private val open = AtomicBoolean(true)

    override val localEndpoint: StudyFlowTransportEndpoint?
        get() = socket.localAddress?.hostAddress?.let { host ->
            StudyFlowTransportEndpoint(
                StudyFlowTransportScheme.WEBSOCKET,
                host,
                socket.localPort,
                remoteEndpoint.path,
            )
        }

    override val isOpen: Boolean get() = open.get() && socket.isConnected && !socket.isClosed

    override fun sendFrame(frame: ByteArray) {
        require(frame.size in 1..options.maxFrameBytes) { "SFLP WebSocket frame exceeds configured bounds" }
        check(isOpen) { "Connection is closed" }
        synchronized(output) {
            writeWebSocketFrame(output, opcode = 0x1, payload = frame, masked = clientSide, secureRandom = secureRandom)
            output.flush()
        }
    }

    override fun receiveFrame(): ByteArray? {
        check(isOpen) { "Connection is closed" }
        synchronized(input) {
            var messageOpcode: Int? = null
            var aggregate: ByteArrayOutputStream? = null
            while (true) {
                val frame = readWebSocketFrame(
                    input = input,
                    maxFrameBytes = options.maxFrameBytes,
                    expectedMasked = !clientSide,
                ) ?: return null
                when (frame.opcode) {
                    0x0 -> {
                        require(messageOpcode != null && aggregate != null) { "Unexpected WebSocket continuation frame" }
                        require(aggregate.size() + frame.payload.size <= options.maxFrameBytes) {
                            "WebSocket message exceeds configured bounds"
                        }
                        aggregate.write(frame.payload)
                        if (frame.fin) return aggregate.toByteArray()
                    }
                    0x1, 0x2 -> {
                        require(messageOpcode == null) { "WebSocket data frame arrived before fragmented message completed" }
                        if (frame.fin) return frame.payload
                        messageOpcode = frame.opcode
                        aggregate = ByteArrayOutputStream(frame.payload.size.coerceAtLeast(32)).also { it.write(frame.payload) }
                    }
                    0x8 -> {
                        runCatching {
                            synchronized(output) {
                                writeWebSocketFrame(output, 0x8, frame.payload, clientSide, secureRandom)
                                output.flush()
                            }
                        }
                        close()
                        return null
                    }
                    0x9 -> synchronized(output) {
                        writeWebSocketFrame(output, 0xA, frame.payload, clientSide, secureRandom)
                        output.flush()
                    }
                    0xA -> Unit
                    else -> error("Unsupported WebSocket opcode ${frame.opcode}")
                }
            }
        }
    }

    override fun close() {
        if (!open.compareAndSet(true, false)) return
        runCatching { socket.close() }
    }
}

private data class WebSocketFrame(val fin: Boolean, val opcode: Int, val payload: ByteArray)

private fun writeWebSocketFrame(
    output: OutputStream,
    opcode: Int,
    payload: ByteArray,
    masked: Boolean,
    secureRandom: SecureRandom,
) {
    output.write(0x80 or (opcode and 0x0f))
    val maskBit = if (masked) 0x80 else 0
    when {
        payload.size <= 125 -> output.write(maskBit or payload.size)
        payload.size <= 0xffff -> {
            output.write(maskBit or 126)
            output.write((payload.size ushr 8) and 0xff)
            output.write(payload.size and 0xff)
        }
        else -> {
            output.write(maskBit or 127)
            val length = payload.size.toLong()
            for (shift in 56 downTo 0 step 8) output.write(((length ushr shift) and 0xff).toInt())
        }
    }
    if (!masked) {
        output.write(payload)
        return
    }
    val mask = ByteArray(4).also(secureRandom::nextBytes)
    output.write(mask)
    payload.forEachIndexed { index, byte -> output.write((byte.toInt() xor mask[index % 4].toInt()) and 0xff) }
}

private fun readWebSocketFrame(
    input: InputStream,
    maxFrameBytes: Int,
    expectedMasked: Boolean,
): WebSocketFrame? {
    val first = input.read()
    if (first < 0) return null
    val second = input.read()
    if (second < 0) throw EOFException("Incomplete WebSocket frame")
    require((first and 0x70) == 0) { "WebSocket extensions/RSV bits are not negotiated" }
    val fin = (first and 0x80) != 0
    val opcode = first and 0x0f
    val control = opcode >= 0x8
    if (control) require(fin) { "WebSocket control frames must not be fragmented" }
    val masked = (second and 0x80) != 0
    require(masked == expectedMasked) {
        if (expectedMasked) "Client WebSocket frames must be masked" else "Server WebSocket frames must not be masked"
    }
    var length = (second and 0x7f).toLong()
    if (length == 126L) {
        length = ((readRequiredByte(input) shl 8) or readRequiredByte(input)).toLong()
    } else if (length == 127L) {
        length = 0L
        repeat(8) { length = (length shl 8) or readRequiredByte(input).toLong() }
    }
    if (control) require(length <= 125L) { "WebSocket control frame exceeds RFC 6455 bounds" }
    require(length in 0..maxFrameBytes.toLong()) { "WebSocket frame exceeds configured bounds" }
    val mask = if (masked) ByteArray(4).also { readFully(input, it) } else null
    val payload = ByteArray(length.toInt()).also { readFully(input, it) }
    if (mask != null) {
        payload.indices.forEach { index -> payload[index] = (payload[index].toInt() xor mask[index % 4].toInt()).toByte() }
    }
    return WebSocketFrame(fin, opcode, payload)
}

private fun readRequiredByte(input: InputStream): Int = input.read().also {
    if (it < 0) throw EOFException("Incomplete WebSocket frame")
}

private fun readFully(input: InputStream, buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val read = input.read(buffer, offset, buffer.size - offset)
        if (read < 0) throw EOFException("Incomplete WebSocket frame")
        offset += read
    }
}

private data class HttpHeaders(val startLine: String, val values: Map<String, String>) {
    fun header(name: String): String? = values[name.lowercase()]
}

private fun readHttpHeaders(input: InputStream): HttpHeaders {
    val bytes = ArrayList<Byte>()
    var state = 0
    while (bytes.size < MaxHttpUpgradeBytes) {
        val value = input.read()
        if (value < 0) throw EOFException("Connection ended during WebSocket upgrade")
        bytes += value.toByte()
        state = when {
            state == 0 && value == '\r'.code -> 1
            state == 1 && value == '\n'.code -> 2
            state == 2 && value == '\r'.code -> 3
            state == 3 && value == '\n'.code -> 4
            value == '\r'.code -> 1
            else -> 0
        }
        if (state == 4) break
    }
    require(state == 4) { "WebSocket HTTP upgrade headers exceed the limit" }
    val text = bytes.toByteArray().toString(Charsets.US_ASCII)
    val lines = text.split("\r\n")
    require(lines.firstOrNull()?.isNotBlank() == true) { "WebSocket HTTP upgrade start line is missing" }
    val headers = linkedMapOf<String, String>()
    lines.drop(1).filter { it.isNotEmpty() }.forEach { line ->
        val separator = line.indexOf(':')
        require(separator > 0) { "Malformed WebSocket HTTP header" }
        headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
    }
    return HttpHeaders(lines.first(), headers)
}

private fun websocketAccept(key: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest((key.trim() + WebSocketMagic).toByteArray(Charsets.US_ASCII))
    return encodeBase64(digest)
}


private const val Base64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/** Pure Kotlin RFC 4648 encoder so the shared core remains safe on Android API 23. */
private fun encodeBase64(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val output = StringBuilder(((bytes.size + 2) / 3) * 4)
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index++].toInt() and 0xff
        val secondPresent = index < bytes.size
        val second = if (secondPresent) bytes[index++].toInt() and 0xff else 0
        val thirdPresent = index < bytes.size
        val third = if (thirdPresent) bytes[index++].toInt() and 0xff else 0
        output.append(Base64Alphabet[first ushr 2])
        output.append(Base64Alphabet[((first and 0x03) shl 4) or (second ushr 4)])
        output.append(if (secondPresent) Base64Alphabet[((second and 0x0f) shl 2) or (third ushr 6)] else '=')
        output.append(if (thirdPresent) Base64Alphabet[third and 0x3f] else '=')
    }
    return output.toString()
}
