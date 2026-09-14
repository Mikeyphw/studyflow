package com.mikeyphw.studyflow.core.link

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowWebSocketBrowserInteropTest {
    @Test
    fun browserHandshakeAndFragmentedMaskedMessageAreAccepted() {
        val transport = StudyFlowWebSocketTransport("/sflp")
        val listener = transport.listen(
            StudyFlowTransportEndpoint(StudyFlowTransportScheme.WEBSOCKET, "127.0.0.1", 0, "/sflp"),
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val accepted = executor.submit<StudyFlowConnection?> { listener.accept() }
            Socket("127.0.0.1", requireNotNull(listener.boundEndpoint.port)).use { browser ->
                browser.soTimeout = 5_000
                browser.getOutputStream().apply {
                    write(browserUpgradeRequest())
                    flush()
                }
                val headers = readHeaders(browser.getInputStream())
                assertTrue(headers.startsWith("HTTP/1.1 101 Switching Protocols"))
                assertTrue(headers.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="))
                assertTrue(headers.contains("Sec-WebSocket-Protocol: studyflow.sflp.v1"))

                val connection = requireNotNull(accepted.get(5, TimeUnit.SECONDS))
                connection.use {
                    val output = browser.getOutputStream()
                    writeMaskedFrame(output, fin = false, opcode = 0x1, payload = "hello ".toByteArray())
                    writeMaskedFrame(output, fin = true, opcode = 0x9, payload = "p".toByteArray())
                    writeMaskedFrame(output, fin = true, opcode = 0x0, payload = "world".toByteArray())
                    output.flush()
                    assertEquals("hello world", connection.receiveFrame()?.toString(Charsets.UTF_8))
                }
            }
        } finally {
            listener.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun browserFramesMustBeMasked() {
        val transport = StudyFlowWebSocketTransport("/sflp")
        val listener = transport.listen(
            StudyFlowTransportEndpoint(StudyFlowTransportScheme.WEBSOCKET, "127.0.0.1", 0, "/sflp"),
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val accepted = executor.submit<StudyFlowConnection?> { listener.accept() }
            Socket("127.0.0.1", requireNotNull(listener.boundEndpoint.port)).use { browser ->
                browser.soTimeout = 5_000
                browser.getOutputStream().apply {
                    write(browserUpgradeRequest())
                    flush()
                }
                readHeaders(browser.getInputStream())
                val connection = requireNotNull(accepted.get(5, TimeUnit.SECONDS))
                connection.use {
                    val payload = "unmasked".toByteArray()
                    browser.getOutputStream().apply {
                        write(0x81)
                        write(payload.size)
                        write(payload)
                        flush()
                    }
                    assertThrows(IllegalArgumentException::class.java) { connection.receiveFrame() }
                }
            }
        } finally {
            listener.close()
            executor.shutdownNow()
        }
    }

    private fun browserUpgradeRequest(): ByteArray = buildString {
        append("GET /sflp HTTP/1.1\r\n")
        append("Host: 127.0.0.1\r\n")
        append("Upgrade: websocket\r\n")
        append("Connection: Upgrade\r\n")
        append("Sec-WebSocket-Version: 13\r\n")
        append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n")
        append("Sec-WebSocket-Protocol: studyflow.sflp.v1\r\n")
        append("\r\n")
    }.toByteArray(Charsets.US_ASCII)

    private fun readHeaders(input: InputStream): String {
        val output = StringBuilder()
        var state = 0
        while (state < 4) {
            val value = input.read()
            require(value >= 0) { "Socket closed during upgrade" }
            output.append(value.toChar())
            state = when {
                state == 0 && value == '\r'.code -> 1
                state == 1 && value == '\n'.code -> 2
                state == 2 && value == '\r'.code -> 3
                state == 3 && value == '\n'.code -> 4
                value == '\r'.code -> 1
                else -> 0
            }
        }
        return output.toString()
    }

    private fun writeMaskedFrame(output: OutputStream, fin: Boolean, opcode: Int, payload: ByteArray) {
        require(payload.size <= 125)
        output.write((if (fin) 0x80 else 0) or opcode)
        output.write(0x80 or payload.size)
        val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        output.write(mask)
        payload.forEachIndexed { index, byte ->
            output.write((byte.toInt() xor mask[index % mask.size].toInt()) and 0xff)
        }
    }
}
