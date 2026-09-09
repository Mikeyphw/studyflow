package com.mikeyphw.studyflow.core.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTransportTest {
    @Test
    fun manualEndpointParserSupportsTcpIpv6WebSocketAndRelay() {
        assertEquals(
            "sflp+tcp://192.168.1.10:4711",
            StudyFlowManualEndpointParser.parse("192.168.1.10:4711").canonicalUri(),
        )
        assertEquals(
            "sflp+tcp://[::1]:4711",
            StudyFlowManualEndpointParser.parse("[::1]:4711").canonicalUri(),
        )
        assertEquals(
            "ws://study.local:8080/link",
            StudyFlowManualEndpointParser.parse("ws://study.local:8080/link").canonicalUri(),
        )
        assertEquals(
            "sflp+relay://relay.example/workspaces/demo",
            StudyFlowManualEndpointParser.parse("sflp+relay://relay.example/workspaces/demo").canonicalUri(),
        )
        assertEquals(
            4711,
            StudyFlowManualEndpointParser.parse("study.local", defaultTcpPort = 4711).port,
        )
    }

    @Test
    fun lengthPrefixedFramesRoundTripAndRejectOversizePayloads() {
        val frame = "hello-sflp".toByteArray()
        val bytes = ByteArrayOutputStream()
        StudyFlowLengthPrefixedFrameCodec.write(DataOutputStream(bytes), frame)
        val decoded = StudyFlowLengthPrefixedFrameCodec.read(
            DataInputStream(ByteArrayInputStream(bytes.toByteArray())),
        )
        assertEquals("hello-sflp", decoded?.toString(Charsets.UTF_8))
        assertTrue(
            runCatching {
                StudyFlowLengthPrefixedFrameCodec.write(
                    DataOutputStream(ByteArrayOutputStream()),
                    ByteArray(StudyFlowTransportContract.MaxFrameBytes + 1),
                )
            }.isFailure,
        )
    }

    @Test
    fun nativeTcpTransportCarriesRealFramedTrafficOverLoopback() {
        val transport = StudyFlowTcpTransport()
        val listener = transport.listen(
            StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, "127.0.0.1", 0),
            StudyFlowTransportOptions(readTimeoutMillis = 5_000),
        )
        val received = AtomicReference<String?>()
        val accepted = CountDownLatch(1)
        val worker = Thread {
            listener.accept()?.use { connection ->
                received.set(connection.receiveFrame()?.toString(Charsets.UTF_8))
                connection.sendFrame("pong".toByteArray())
                accepted.countDown()
            }
        }
        worker.start()
        transport.connect(listener.boundEndpoint, StudyFlowTransportOptions(readTimeoutMillis = 5_000)).use { client ->
            client.sendFrame("ping".toByteArray())
            assertEquals("pong", client.receiveFrame()?.toString(Charsets.UTF_8))
        }
        assertTrue(accepted.await(5, TimeUnit.SECONDS))
        assertEquals("ping", received.get())
        listener.close()
        worker.join(5_000)
        assertFalse(listener.isOpen)
    }

    @Test
    fun adapterTransportSupportsWebSocketAndRelayWithoutPlatformApisInCore() {
        val requested = AtomicReference<StudyFlowTransportEndpoint?>()
        val connection = MemoryConnection(
            remoteEndpoint = StudyFlowTransportEndpoint(
                StudyFlowTransportScheme.WEBSOCKET,
                "browser.local",
                8080,
                "/link",
            ),
        )
        val adapter = StudyFlowAdapterTransport(
            schemes = setOf(
                StudyFlowTransportScheme.WEBSOCKET,
                StudyFlowTransportScheme.WEBSOCKET_SECURE,
                StudyFlowTransportScheme.RELAY,
            ),
            dialer = StudyFlowTransportDialer { endpoint, _ ->
                requested.set(endpoint)
                connection.copyFor(endpoint)
            },
        )
        val registry = StudyFlowTransportRegistry(listOf(StudyFlowTcpTransport(), adapter))
        val endpoint = StudyFlowManualEndpointParser.parse("wss://example.test/studyflow")
        registry.connect(endpoint).use { opened ->
            assertEquals(endpoint, requested.get())
            opened.sendFrame("hello".toByteArray())
        }
        assertEquals(endpoint.scheme, registry.resolve(endpoint).schemes.first { it == endpoint.scheme })
    }

    @Test
    fun mdnsContractAndCompositeDiscoveryDeduplicateTheSameDevice() {
        val peer = StudyFlowLinkPeerDescriptor(
            deviceId = StudyFlowDeviceId("device-desktop"),
            displayName = "Desk",
            clientKind = StudyFlowLinkClientKind.DESKTOP,
            capabilities = listOf(
                StudyFlowLinkCapabilities.WorkspaceRead,
                StudyFlowLinkCapabilities.WorkspaceSync,
            ),
        )
        val advertisement = StudyFlowMdnsContract.advertise(peer, port = 4711)
        assertEquals(StudyFlowMdnsContract.ServiceType, advertisement.serviceType)
        val mdns = StudyFlowMdnsContract.resolve(
            serviceName = advertisement.serviceName,
            host = "192.168.1.2",
            port = advertisement.port,
            txt = advertisement.txt,
            source = StudyFlowDiscoverySource.MDNS,
            nowEpochMillis = 1_000,
        )
        assertNotNull(mdns)
        assertEquals(peer.deviceId, mdns?.deviceId)
        assertEquals(peer.clientKind, mdns?.clientKind)

        val duplicate = requireNotNull(mdns).copy(
            discoveryId = "android-nsd-copy",
            source = StudyFlowDiscoverySource.ANDROID_NSD,
            endpoints = listOf(
                StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, "desk.local", 4711),
            ),
        )
        val composite = StudyFlowCompositeDiscovery(
            listOf(
                FixedDiscovery(listOf(mdns)),
                FixedDiscovery(listOf(duplicate)),
            ),
        )
        val merged = composite.snapshot(nowEpochMillis = 1_001)
        assertEquals(1, merged.size)
        assertEquals(2, merged.single().endpoints.size)
    }

    @Test
    fun manualDiscoveryRemembersAndForgetsEndpoints() {
        val discovery = StudyFlowManualDiscovery()
        val record = discovery.remember("10.0.0.5:5123", nowEpochMillis = 100)
        assertEquals(1, discovery.snapshot(101).size)
        assertTrue(discovery.forget(record.discoveryId))
        assertTrue(discovery.snapshot(102).isEmpty())
    }

    private class FixedDiscovery(
        private val records: List<StudyFlowDiscoveredPeer>,
    ) : StudyFlowDiscovery {
        override fun snapshot(nowEpochMillis: Long): List<StudyFlowDiscoveredPeer> = records
    }

    private class MemoryConnection(
        override val remoteEndpoint: StudyFlowTransportEndpoint,
        private val sent: MutableList<ByteArray> = mutableListOf(),
    ) : StudyFlowConnection {
        private var open = true
        override val localEndpoint: StudyFlowTransportEndpoint? = null
        override val isOpen: Boolean get() = open

        override fun sendFrame(frame: ByteArray) {
            check(open)
            sent += frame.copyOf()
        }

        override fun receiveFrame(): ByteArray? = null

        override fun close() {
            open = false
        }

        fun copyFor(endpoint: StudyFlowTransportEndpoint): MemoryConnection = MemoryConnection(endpoint, sent)
    }
}
