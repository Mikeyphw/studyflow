package com.mikeyphw.studyflow.core.tvlink

import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlContinuityState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlSession
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionResult
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPairingEngine
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPairingInfo
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPairingCrypto
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPairingOffer
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationReceipt
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvReceiverEndpoint
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSessionCredential
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWireAuthenticator
import com.mikeyphw.studyflow.core.tvcontrol.matchesCredentialBinding
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWireCodec
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWireOperation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWireProtocol
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWireRequest
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWireResponse
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object StudyFlowTvLocalLinkContract {
    const val overlay = "TVOV22"
    const val androidNsdDiscovery = true
    const val localTcpTransport = true
    const val explicitPairingCodeRequired = true
    const val pairingNonceRequired = true
    const val sessionSecretRequired = true
    const val sessionSecretNeverOnWire = true
    const val authenticatedRequestsUseHmacProof = true
    const val sessionsPersistAcrossProcessRestart = true
    const val sessionCredentialCommitBeforeSuccess = true
    const val tvAndSflpTrustUseCanonicalStore = true
    const val phoneAndTvShareOneTransportModule = true
    const val transportUsesNoCastSdk = true
    const val transportUsesNoMediaRouter = true
    const val localNetworkPermissionName = "android.permission.NEARBY_WIFI_DEVICES"
}

enum class StudyFlowTvLinkStatus {
    STOPPED,
    PERMISSION_REQUIRED,
    STARTING,
    READY,
    ERROR,
}

object StudyFlowTvLocalNetworkPermission {
    const val permission = "android.permission.NEARBY_WIFI_DEVICES"

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

data class StudyFlowTvTransportApplyResult(
    val applied: Boolean,
    val reason: String? = null,
    val continuity: StudyFlowTvControlContinuityState? = null,
    val presentationReceipt: StudyFlowTvPresentationReceipt? = null,
)

interface StudyFlowTvLocalTransportDelegate {
    fun onTransportStatusChanged(status: StudyFlowTvLinkStatus)
    fun onPairingSessionChanged(session: StudyFlowTvControlSession)
    fun applyRemoteCommand(
        command: StudyFlowTvControlCommand,
        nowEpochMillis: Long,
    ): StudyFlowTvTransportApplyResult

    fun applyPresentationSnapshot(
        snapshot: StudyFlowTvPresentationSnapshot,
        nowEpochMillis: Long,
    ): StudyFlowTvTransportApplyResult
    fun currentContinuityState(): StudyFlowTvControlContinuityState
    fun currentPresentationReceipt(): StudyFlowTvPresentationReceipt?
    fun pendingDomainAction(): StudyFlowTvDomainAction?
    fun acknowledgeDomainAction(result: StudyFlowTvDomainActionResult): Boolean
    fun lastDomainActionResult(): StudyFlowTvDomainActionResult?
}

/** Small SharedPreferences credential store. Both application manifests disable backup. */
class StudyFlowTvSessionStore(
    context: Context,
    preferenceName: String,
) {
    private val preferences = context.applicationContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    fun load(nowEpochMillis: Long): StudyFlowTvSessionCredential? {
        val sessionId = preferences.getString(KEY_SESSION_ID, null) ?: return null
        val secret = preferences.getString(KEY_SESSION_SECRET, null) ?: return null
        val tvName = preferences.getString(KEY_TV_NAME, null) ?: return null
        val phoneName = preferences.getString(KEY_PHONE_NAME, null) ?: return null
        val pairedAt = preferences.getLong(KEY_PAIRED_AT, -1L)
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT, -1L)
        if (pairedAt < 0L || expiresAt <= pairedAt) {
            clear()
            return null
        }
        val credential = runCatching {
            StudyFlowTvSessionCredential(
                sessionId = sessionId,
                sessionSecret = secret,
                tvDeviceName = tvName,
                pairedDeviceName = phoneName,
                pairedAtEpochMillis = pairedAt,
                expiresAtEpochMillis = expiresAt,
                receiverServiceName = preferences.getString(KEY_RECEIVER_SERVICE_NAME, null),
                receiverHostAddress = preferences.getString(KEY_RECEIVER_HOST_ADDRESS, null),
                receiverPort = preferences.getInt(KEY_RECEIVER_PORT, -1).takeIf { it in 1..65_535 },
                receiverHostKeyAgreementPublicKeyHex = preferences.getString(KEY_RECEIVER_HOST_KEY, null),
            )
        }.getOrNull() ?: run {
            clear()
            return null
        }
        if (!credential.isActive(nowEpochMillis)) {
            clear()
            return null
        }
        return credential
    }

    fun save(credential: StudyFlowTvSessionCredential): Boolean = preferences.edit()
        .putString(KEY_SESSION_ID, credential.sessionId)
        .putString(KEY_SESSION_SECRET, credential.sessionSecret)
        .putString(KEY_TV_NAME, credential.tvDeviceName)
        .putString(KEY_PHONE_NAME, credential.pairedDeviceName)
        .putLong(KEY_PAIRED_AT, credential.pairedAtEpochMillis)
        .putLong(KEY_EXPIRES_AT, credential.expiresAtEpochMillis)
        .putString(KEY_RECEIVER_SERVICE_NAME, credential.receiverServiceName)
        .putString(KEY_RECEIVER_HOST_ADDRESS, credential.receiverHostAddress)
        .putInt(KEY_RECEIVER_PORT, credential.receiverPort ?: -1)
        .putString(KEY_RECEIVER_HOST_KEY, credential.receiverHostKeyAgreementPublicKeyHex)
        .commit()

    fun clear(): Boolean = preferences.edit().clear().commit()

    private companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_SESSION_SECRET = "session_secret"
        const val KEY_TV_NAME = "tv_name"
        const val KEY_PHONE_NAME = "phone_name"
        const val KEY_PAIRED_AT = "paired_at"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_RECEIVER_SERVICE_NAME = "receiver_service_name"
        const val KEY_RECEIVER_HOST_ADDRESS = "receiver_host_address"
        const val KEY_RECEIVER_PORT = "receiver_port"
        const val KEY_RECEIVER_HOST_KEY = "receiver_host_key"
    }
}

/**
 * Android NSD discovery used by the phone controller. Discovery is explicit and only runs while a
 * caller has registered a listener; it never performs background scanning on its own.
 */
@Suppress("DEPRECATION")
class StudyFlowTvReceiverDiscovery(context: Context) : Closeable {
    interface Listener {
        fun onStatusChanged(status: StudyFlowTvLinkStatus)
        fun onReceiversChanged(receivers: List<StudyFlowTvReceiverEndpoint>)
    }

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val receivers = linkedMapOf<String, StudyFlowTvReceiverEndpoint>()
    private val pendingResolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolving = AtomicBoolean(false)
    private var listener: Listener? = null
    private var discovering = false
    private var desiredDiscovering = false
    private var lastFailure: String? = null

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            lastFailure = null
            publishStatus(StudyFlowTvLinkStatus.READY)
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (!serviceInfo.serviceType.startsWith(StudyFlowTvWireProtocol.serviceType.removeSuffix("."))) return
            enqueueResolve(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            synchronized(receivers) { receivers.remove(serviceInfo.serviceName) }
            publishReceivers()
        }

        override fun onDiscoveryStopped(serviceType: String) = publishStatus(StudyFlowTvLinkStatus.STOPPED)

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            discovering = false
            lastFailure = "discover:$errorCode"
            publishStatus(StudyFlowTvLinkStatus.ERROR)
            scheduleRestartAfterFailure()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            discovering = false
            lastFailure = "stop:$errorCode"
            publishStatus(StudyFlowTvLinkStatus.ERROR)
            scheduleRestartAfterFailure()
        }
    }

    fun start(listener: Listener): Boolean {
        this.listener = listener
        desiredDiscovering = true
        if (!StudyFlowTvLocalNetworkPermission.isGranted(appContext)) {
            lastFailure = "local-network-permission-required"
            publishStatus(StudyFlowTvLinkStatus.PERMISSION_REQUIRED)
            return false
        }
        if (discovering) {
            publishReceivers()
            return true
        }
        publishStatus(StudyFlowTvLinkStatus.STARTING)
        return try {
            discovering = true
            nsdManager.discoverServices(
                StudyFlowTvWireProtocol.serviceType,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener,
            )
            true
        } catch (_: SecurityException) {
            discovering = false
            publishStatus(StudyFlowTvLinkStatus.PERMISSION_REQUIRED)
            false
        } catch (_: RuntimeException) {
            discovering = false
            lastFailure = "discover-runtime"
            publishStatus(StudyFlowTvLinkStatus.ERROR)
            scheduleRestartAfterFailure()
            false
        }
    }

    fun stop() {
        desiredDiscovering = false
        pendingResolveQueue.clear()
        resolving.set(false)
        if (discovering) {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
        discovering = false
        synchronized(receivers) { receivers.clear() }
        publishReceivers()
        publishStatus(StudyFlowTvLinkStatus.STOPPED)
        listener = null
    }

    override fun close() = stop()

    fun lastFailureReason(): String? = lastFailure

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        pendingResolveQueue.add(serviceInfo)
        drainResolveQueue()
    }

    private fun retryResolve(serviceInfo: NsdServiceInfo) {
        if (!desiredDiscovering || !StudyFlowTvLocalNetworkPermission.isGranted(appContext)) return
        mainHandler.postDelayed({ enqueueResolve(serviceInfo) }, RESOLVE_RETRY_DELAY_MILLIS)
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

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        try {
                            val host = serviceInfo.host?.hostAddress ?: return
                            val port = serviceInfo.port
                            if (port !in 1..65_535) return
                            if (host == "127.0.0.1" || host == "0.0.0.0" || host == "::1") return
                            synchronized(receivers) {
                                receivers[serviceInfo.serviceName] = StudyFlowTvReceiverEndpoint(
                                    serviceName = serviceInfo.serviceName,
                                    hostAddress = host,
                                    port = port,
                                )
                            }
                            lastFailure = null
                            publishReceivers()
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
        val currentListener = listener ?: return
        if (!desiredDiscovering || !StudyFlowTvLocalNetworkPermission.isGranted(appContext)) return
        mainHandler.postDelayed(
            {
                if (desiredDiscovering && !discovering) start(currentListener)
            },
            DISCOVERY_RESTART_DELAY_MILLIS,
        )
    }

    private fun publishStatus(status: StudyFlowTvLinkStatus) {
        val current = listener ?: return
        mainHandler.post { current.onStatusChanged(status) }
    }

    private fun publishReceivers() {
        val current = listener ?: return
        val snapshot = synchronized(receivers) { receivers.values.sortedBy { it.serviceName }.toList() }
        mainHandler.post { current.onReceiversChanged(snapshot) }
    }

    private companion object {
        const val DISCOVERY_RESTART_DELAY_MILLIS = 2_000L
        const val RESOLVE_RETRY_DELAY_MILLIS = 750L
    }
}

/** Blocking one-request-per-connection client. Callers own their IO dispatcher/thread. */
class StudyFlowTvLocalTransportClient(context: Context) {
    private val appContext = context.applicationContext
    private val sessionStore = StudyFlowTvSessionStore(appContext, PHONE_SESSION_PREFERENCES)

    fun savedCredential(nowEpochMillis: Long = System.currentTimeMillis()): StudyFlowTvSessionCredential? =
        sessionStore.load(nowEpochMillis)

    fun pairingInfo(
        endpoint: StudyFlowTvReceiverEndpoint,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): StudyFlowTvWireResponse = exchange(
        endpoint,
        StudyFlowTvWireRequest(
            requestId = requestId(),
            operation = StudyFlowTvWireOperation.PAIRING_INFO,
            issuedAtEpochMillis = nowEpochMillis,
        ),
    )

    fun pair(
        endpoint: StudyFlowTvReceiverEndpoint,
        pairingCode: String,
        phoneDeviceName: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): StudyFlowTvWireResponse {
        require(pairingCode.length == 6 && pairingCode.all(Char::isDigit)) { "Pairing code must contain six digits." }
        require(phoneDeviceName.isNotBlank()) { "Phone device name must not be blank." }
        val infoResponse = pairingInfo(endpoint, nowEpochMillis)
        val pairingInfo = infoResponse.pairingInfo
        if (!infoResponse.ok || pairingInfo?.pairingActive != true || pairingInfo.sessionNonce.isNullOrBlank()) {
            return StudyFlowTvWireResponse(
                requestId = infoResponse.requestId,
                ok = false,
                reason = infoResponse.reason ?: "pairing-not-active",
                respondedAtEpochMillis = nowEpochMillis,
            )
        }
        val hostKey = pairingInfo.hostKeyAgreementPublicKeyHex
            ?: return StudyFlowTvWireResponse(
                requestId = infoResponse.requestId,
                ok = false,
                reason = "pairing-host-key-missing",
                respondedAtEpochMillis = nowEpochMillis,
            )
        val keyMaterial = StudyFlowTvPairingCrypto.createClientKeyMaterial()
        val response = exchange(
            endpoint,
            StudyFlowTvWireRequest(
                requestId = requestId(),
                operation = StudyFlowTvWireOperation.PAIR,
                pairingCode = pairingCode,
                sessionNonce = pairingInfo.sessionNonce,
                phoneDeviceName = phoneDeviceName,
                clientKeyAgreementPublicKeyHex = keyMaterial.publicKeyX509Hex,
                issuedAtEpochMillis = nowEpochMillis,
            ),
        )
        if (response.ok) {
            val grant = response.encryptedCredential
                ?: return response.copy(ok = false, reason = "encrypted-credential-missing")
            if (grant.hostKeyAgreementPublicKeyHex != hostKey) {
                return response.copy(ok = false, reason = "pairing-host-key-mismatch", encryptedCredential = null)
            }
            val decrypted = runCatching {
                StudyFlowTvPairingCrypto.decryptCredential(
                    grant = grant,
                    sessionNonce = pairingInfo.sessionNonce,
                    keyMaterial = keyMaterial,
                )
            }.getOrElse {
                return response.copy(ok = false, reason = "pairing-credential-decrypt-failed", encryptedCredential = null)
            }
            if (!sessionStore.save(decrypted.bindToReceiver(endpoint, hostKey))) {
                return response.copy(ok = false, reason = "pairing-credential-save-failed", encryptedCredential = null)
            }
        }
        return response
    }

    fun sendCommand(
        endpoint: StudyFlowTvReceiverEndpoint,
        command: StudyFlowTvControlCommand,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): StudyFlowTvWireResponse = authenticatedExchange(endpoint, StudyFlowTvWireOperation.COMMAND, nowEpochMillis) {
        copy(command = command)
    }

    fun sendPresentation(
        endpoint: StudyFlowTvReceiverEndpoint,
        snapshot: StudyFlowTvPresentationSnapshot,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): StudyFlowTvWireResponse = authenticatedExchange(
        endpoint,
        StudyFlowTvWireOperation.PRESENTATION,
        nowEpochMillis,
    ) {
        copy(presentation = snapshot)
    }

    fun fetchContinuity(
        endpoint: StudyFlowTvReceiverEndpoint,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): StudyFlowTvWireResponse = authenticatedExchange(
        endpoint,
        StudyFlowTvWireOperation.CONTINUITY,
        nowEpochMillis,
    ) { this }

    fun acknowledgeDomainAction(
        endpoint: StudyFlowTvReceiverEndpoint,
        result: StudyFlowTvDomainActionResult,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): StudyFlowTvWireResponse = authenticatedExchange(
        endpoint,
        StudyFlowTvWireOperation.DOMAIN_ACTION_RESULT,
        nowEpochMillis,
    ) {
        copy(domainActionResult = result)
    }

    fun revoke(
        endpoint: StudyFlowTvReceiverEndpoint,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): StudyFlowTvWireResponse {
        val response = authenticatedExchange(endpoint, StudyFlowTvWireOperation.REVOKE, nowEpochMillis) { this }
        if (response.ok) sessionStore.clear()
        return response
    }

    fun clearSavedSession() = sessionStore.clear()

    private fun authenticatedExchange(
        endpoint: StudyFlowTvReceiverEndpoint,
        operation: StudyFlowTvWireOperation,
        nowEpochMillis: Long,
        transform: StudyFlowTvWireRequest.() -> StudyFlowTvWireRequest,
    ): StudyFlowTvWireResponse {
        val requestId = requestId()
        val credential = sessionStore.load(nowEpochMillis)
            ?: return StudyFlowTvWireResponse(
                requestId = requestId,
                ok = false,
                reason = "no-active-session",
                respondedAtEpochMillis = nowEpochMillis,
            )
        if (!endpoint.matchesCredentialBinding(credential)) {
            return StudyFlowTvWireResponse(
                requestId = requestId,
                ok = false,
                reason = "receiver-binding-mismatch",
                respondedAtEpochMillis = nowEpochMillis,
            )
        }
        val base = StudyFlowTvWireRequest(
            requestId = requestId,
            operation = operation,
            sessionId = credential.sessionId,
            issuedAtEpochMillis = nowEpochMillis,
        )
        val request = StudyFlowTvWireAuthenticator.sign(base.transform(), credential)
        return exchange(endpoint, request)
    }

    private fun exchange(
        endpoint: StudyFlowTvReceiverEndpoint,
        request: StudyFlowTvWireRequest,
    ): StudyFlowTvWireResponse {
        if (!StudyFlowTvLocalNetworkPermission.isGranted(appContext)) {
            return StudyFlowTvWireResponse(
                requestId = request.requestId,
                ok = false,
                reason = "local-network-permission-required",
                respondedAtEpochMillis = System.currentTimeMillis(),
            )
        }
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = StudyFlowTvWireProtocol.socketReadTimeoutMillis
                socket.connect(
                    InetSocketAddress(endpoint.hostAddress, endpoint.port),
                    StudyFlowTvWireProtocol.connectTimeoutMillis,
                )
                StudyFlowTvWireCodec.writeRequest(socket.getOutputStream(), request)
                StudyFlowTvWireCodec.readResponse(socket.getInputStream())
            }
        } catch (error: Exception) {
            StudyFlowTvWireResponse(
                requestId = request.requestId,
                ok = false,
                reason = "transport-${error.javaClass.simpleName.lowercase()}",
                respondedAtEpochMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun requestId(): String = "phone-${UUID.randomUUID()}"

    private companion object {
        const val PHONE_SESSION_PREFERENCES = "studyflow_tv_phone_link"
    }
}

/**
 * Local TV receiver host. It binds an ephemeral TCP port, advertises it through Android NSD, and
 * accepts only framed StudyFlow wire requests. Pairing information exposes the random nonce but not
 * the six-digit code; the code must be read from the TV by the user (or later via the TV QR link).
 */
@Suppress("DEPRECATION")
class StudyFlowTvLocalTransportHost(
    context: Context,
    private val tvDeviceName: String,
    private val delegate: StudyFlowTvLocalTransportDelegate,
) : Closeable {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pairingEngine = StudyFlowTvPairingEngine()
    private val sessionStore = StudyFlowTvSessionStore(appContext, TV_SESSION_PREFERENCES)
    private val lifecycleLock = Any()

    @Volatile
    private var status: StudyFlowTvLinkStatus = StudyFlowTvLinkStatus.STOPPED
    @Volatile
    private var currentOffer: StudyFlowTvPairingOffer? = null
    @Volatile
    private var credential: StudyFlowTvSessionCredential? = null
    @Volatile
    private var pairingAttemptCount: Int = 0
    private val consumedAuthNonces = linkedSetOf<String>()
    private var serverSocket: ServerSocket? = null
    private var acceptExecutor: ExecutorService? = null
    private var workerExecutor: ExecutorService? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    init {
        require(tvDeviceName.isNotBlank())
        credential = sessionStore.load(System.currentTimeMillis())
    }

    fun currentStatus(): StudyFlowTvLinkStatus = status

    fun currentPairingOffer(nowEpochMillis: Long = System.currentTimeMillis()): StudyFlowTvPairingOffer? {
        val offer = currentOffer
        if (offer != null && !offer.isActive(nowEpochMillis)) currentOffer = null
        return currentOffer
    }

    fun currentSession(nowEpochMillis: Long = System.currentTimeMillis()): StudyFlowTvControlSession {
        val active = credential?.takeIf { it.isActive(nowEpochMillis) }
        if (active == null && credential != null) {
            credential = null
            sessionStore.clear()
        }
        return active?.toControlSession() ?: StudyFlowTvControlSession()
    }

    fun start(): Boolean = synchronized(lifecycleLock) {
        if (serverSocket?.isClosed == false) return@synchronized true
        if (!StudyFlowTvLocalNetworkPermission.isGranted(appContext)) {
            updateStatus(StudyFlowTvLinkStatus.PERMISSION_REQUIRED)
            return@synchronized false
        }
        updateStatus(StudyFlowTvLinkStatus.STARTING)
        return@synchronized try {
            val server = ServerSocket(0)
            server.reuseAddress = true
            serverSocket = server
            acceptExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "studyflow-tv-accept") }
            workerExecutor = Executors.newFixedThreadPool(4) { runnable -> Thread(runnable, "studyflow-tv-link") }
            registerNsd(server.localPort)
            acceptExecutor?.execute { acceptLoop(server) }
            credential = sessionStore.load(System.currentTimeMillis())
            credential?.let { publishSession(it.toControlSession()) }
            true
        } catch (_: SecurityException) {
            stopLocked()
            updateStatus(StudyFlowTvLinkStatus.PERMISSION_REQUIRED)
            false
        } catch (_: Exception) {
            stopLocked()
            updateStatus(StudyFlowTvLinkStatus.ERROR)
            false
        }
    }

    fun beginPairing(nowEpochMillis: Long = System.currentTimeMillis()): StudyFlowTvPairingOffer? {
        if (!start()) return null
        val existing = credential?.takeIf { it.isActive(nowEpochMillis) }
        if (existing != null) return null
        pairingAttemptCount = 0
        return pairingEngine.createOffer(tvDeviceName, nowEpochMillis).also { currentOffer = it }
    }

    fun revokeCurrentSession(nowEpochMillis: Long = System.currentTimeMillis()): StudyFlowTvControlSession {
        val existing = credential
        credential = null
        currentOffer = null
        pairingAttemptCount = 0
        sessionStore.clear()
        val revoked = existing?.toControlSession()?.revoke(nowEpochMillis)
            ?: StudyFlowTvControlSession().copy(
                pairingState = com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPairingState.REVOKED,
                revokedAtEpochMillis = nowEpochMillis,
            )
        publishSession(revoked)
        return revoked
    }

    fun stop() = synchronized(lifecycleLock) {
        stopLocked()
        updateStatus(StudyFlowTvLinkStatus.STOPPED)
    }

    override fun close() = stop()

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val socket = try {
                server.accept()
            } catch (_: Exception) {
                break
            }
            workerExecutor?.execute { handle(socket) } ?: runCatching { socket.close() }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.tcpNoDelay = true
            client.soTimeout = StudyFlowTvWireProtocol.socketReadTimeoutMillis
            val request = try {
                StudyFlowTvWireCodec.readRequest(client.getInputStream())
            } catch (error: Exception) {
                return
            }
            val response = dispatch(request, System.currentTimeMillis())
            runCatching { StudyFlowTvWireCodec.writeResponse(client.getOutputStream(), response) }
        }
    }

    private fun dispatch(request: StudyFlowTvWireRequest, nowEpochMillis: Long): StudyFlowTvWireResponse {
        if (request.wireVersion != StudyFlowTvWireProtocol.version) {
            return response(request, false, "unsupported-wire-version", nowEpochMillis)
        }
        return when (request.operation) {
            StudyFlowTvWireOperation.PAIRING_INFO -> {
                val offer = currentPairingOffer(nowEpochMillis)
                StudyFlowTvWireResponse(
                    requestId = request.requestId,
                    ok = true,
                    pairingInfo = StudyFlowTvPairingInfo(
                        tvDeviceName = tvDeviceName,
                        pairingActive = offer != null,
                        sessionNonce = offer?.token?.sessionNonce,
                        expiresAtEpochMillis = offer?.token?.expiresAtEpochMillis,
                        hostKeyAgreementPublicKeyHex = offer?.hostKeyAgreementPublicKeyHex,
                    ),
                    respondedAtEpochMillis = nowEpochMillis,
                )
            }
            StudyFlowTvWireOperation.PAIR -> pair(request, nowEpochMillis)
            StudyFlowTvWireOperation.PING -> response(request, true, null, nowEpochMillis)
            StudyFlowTvWireOperation.COMMAND,
            StudyFlowTvWireOperation.PRESENTATION,
            StudyFlowTvWireOperation.CONTINUITY,
            StudyFlowTvWireOperation.DOMAIN_ACTION_RESULT,
            StudyFlowTvWireOperation.REVOKE,
            -> authenticatedDispatch(request, nowEpochMillis)
        }
    }

    private fun pair(request: StudyFlowTvWireRequest, nowEpochMillis: Long): StudyFlowTvWireResponse {
        val offer = currentPairingOffer(nowEpochMillis)
            ?: return response(request, false, "pairing-not-active", nowEpochMillis)
        val code = request.pairingCode ?: return response(request, false, "pairing-code-required", nowEpochMillis)
        val nonce = request.sessionNonce ?: return response(request, false, "pairing-nonce-required", nowEpochMillis)
        val phoneName = request.phoneDeviceName
            ?: return response(request, false, "phone-name-required", nowEpochMillis)
        pairingAttemptCount += 1
        if (pairingAttemptCount > StudyFlowTvWireProtocol.maxPairingAttemptsPerOffer) {
            currentOffer = null
            pairingAttemptCount = 0
            return response(request, false, "pairing-attempt-limit", nowEpochMillis)
        }
        val clientKey = request.clientKeyAgreementPublicKeyHex
            ?: return response(request, false, "pairing-client-key-required", nowEpochMillis)
        val accepted = pairingEngine.accept(offer, code, nonce, phoneName, clientKey, nowEpochMillis)
            ?: return response(request, false, "pairing-rejected", nowEpochMillis)
        if (!sessionStore.save(accepted.credential)) {
            return response(request, false, "pairing-credential-save-failed", nowEpochMillis)
        }
        credential = accepted.credential
        currentOffer = null
        pairingAttemptCount = 0
        consumedAuthNonces.clear()
        publishSession(accepted.credential.toControlSession())
        return StudyFlowTvWireResponse(
            requestId = request.requestId,
            ok = true,
            encryptedCredential = accepted.encryptedCredential,
            continuity = safeContinuity(),
            respondedAtEpochMillis = nowEpochMillis,
        )
    }

    private fun authenticatedDispatch(request: StudyFlowTvWireRequest, nowEpochMillis: Long): StudyFlowTvWireResponse {
        val activeCredential = credential?.takeIf { it.isActive(nowEpochMillis) }
        if (activeCredential == null) {
            val expiredCredential = credential
            if (expiredCredential != null) {
                credential = null
                sessionStore.clear()
                publishSession(expiredCredential.toControlSession().revoke(nowEpochMillis))
            }
            return response(request, false, "session-expired-or-missing", nowEpochMillis)
        }
        val authNonce = request.authNonceHex
        if (!StudyFlowTvWireAuthenticator.verify(request, activeCredential, nowEpochMillis)) {
            return response(request, false, "session-authorization-failed", nowEpochMillis)
        }
        synchronized(consumedAuthNonces) {
            if (authNonce == null || !consumedAuthNonces.add(authNonce)) {
                return response(request, false, "session-auth-replay", nowEpochMillis)
            }
            while (consumedAuthNonces.size > 512) consumedAuthNonces.remove(consumedAuthNonces.first())
        }
        return when (request.operation) {
            StudyFlowTvWireOperation.COMMAND -> {
                val command = request.command ?: return response(request, false, "command-required", nowEpochMillis)
                val applied = callOnMain {
                    delegate.applyRemoteCommand(command, nowEpochMillis)
                } ?: return response(request, false, "main-thread-timeout", nowEpochMillis)
                if (applied.applied && command.action == StudyFlowTvControlAction.REVOKE_PAIRING) {
                    credential = null
                    currentOffer = null
                    pairingAttemptCount = 0
                    sessionStore.clear()
                    consumedAuthNonces.clear()
                }
                response(
                    request = request,
                    ok = true,
                    reason = applied.reason,
                    nowEpochMillis = nowEpochMillis,
                    applied = applied.applied,
                    continuity = applied.continuity,
                    presentationReceipt = applied.presentationReceipt,
                )
            }
            StudyFlowTvWireOperation.PRESENTATION -> {
                val snapshot = request.presentation
                    ?: return response(request, false, "presentation-required", nowEpochMillis)
                val applied = callOnMain {
                    delegate.applyPresentationSnapshot(snapshot, nowEpochMillis)
                } ?: return response(request, false, "main-thread-timeout", nowEpochMillis)
                response(
                    request = request,
                    ok = true,
                    reason = applied.reason,
                    nowEpochMillis = nowEpochMillis,
                    applied = applied.applied,
                    continuity = applied.continuity,
                    presentationReceipt = applied.presentationReceipt,
                )
            }
            StudyFlowTvWireOperation.CONTINUITY -> response(
                request = request,
                ok = true,
                reason = null,
                nowEpochMillis = nowEpochMillis,
                continuity = safeContinuity(),
                presentationReceipt = safePresentationReceipt(),
                pendingDomainAction = safePendingDomainAction(),
                lastDomainActionResult = safeLastDomainActionResult(),
            )
            StudyFlowTvWireOperation.DOMAIN_ACTION_RESULT -> {
                val result = request.domainActionResult
                    ?: return response(request, false, "domain-action-result-required", nowEpochMillis)
                val acknowledged = callOnMain { delegate.acknowledgeDomainAction(result) }
                    ?: return response(request, false, "main-thread-timeout", nowEpochMillis)
                response(
                    request = request,
                    ok = acknowledged,
                    reason = if (acknowledged) null else "domain-action-not-pending",
                    nowEpochMillis = nowEpochMillis,
                    applied = acknowledged,
                    continuity = safeContinuity(),
                    presentationReceipt = safePresentationReceipt(),
                    pendingDomainAction = safePendingDomainAction(),
                    lastDomainActionResult = safeLastDomainActionResult(),
                )
            }
            StudyFlowTvWireOperation.REVOKE -> {
                val revoked = activeCredential.toControlSession().revoke(nowEpochMillis)
                credential = null
                currentOffer = null
                pairingAttemptCount = 0
                sessionStore.clear()
                consumedAuthNonces.clear()
                publishSession(revoked)
                response(
                    request = request,
                    ok = true,
                    reason = null,
                    nowEpochMillis = nowEpochMillis,
                    applied = true,
                    continuity = safeContinuity(),
                    presentationReceipt = safePresentationReceipt(),
                )
            }
            else -> response(request, false, "invalid-authenticated-operation", nowEpochMillis)
        }
    }

    private fun safeContinuity(): StudyFlowTvControlContinuityState? = callOnMain { delegate.currentContinuityState() }

    private fun safePresentationReceipt(): StudyFlowTvPresentationReceipt? =
        callOnMain { delegate.currentPresentationReceipt() }

    private fun safePendingDomainAction(): StudyFlowTvDomainAction? =
        callOnMain { delegate.pendingDomainAction() }

    private fun safeLastDomainActionResult(): StudyFlowTvDomainActionResult? =
        callOnMain { delegate.lastDomainActionResult() }

    private fun response(
        request: StudyFlowTvWireRequest,
        ok: Boolean,
        reason: String?,
        nowEpochMillis: Long,
        applied: Boolean? = null,
        continuity: StudyFlowTvControlContinuityState? = null,
        presentationReceipt: StudyFlowTvPresentationReceipt? = null,
        pendingDomainAction: StudyFlowTvDomainAction? = null,
        lastDomainActionResult: StudyFlowTvDomainActionResult? = null,
    ): StudyFlowTvWireResponse = StudyFlowTvWireResponse(
        requestId = request.requestId,
        ok = ok,
        reason = reason,
        applied = applied,
        continuity = continuity,
        presentationReceipt = presentationReceipt,
        pendingDomainAction = pendingDomainAction,
        lastDomainActionResult = lastDomainActionResult,
        respondedAtEpochMillis = nowEpochMillis,
    )

    private fun registerNsd(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = tvDeviceName
            serviceType = StudyFlowTvWireProtocol.serviceType
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) =
                updateStatus(StudyFlowTvLinkStatus.READY)

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) =
                updateStatus(StudyFlowTvLinkStatus.ERROR)
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun publishSession(session: StudyFlowTvControlSession) {
        mainHandler.post { delegate.onPairingSessionChanged(session) }
    }

    private fun updateStatus(newStatus: StudyFlowTvLinkStatus) {
        status = newStatus
        mainHandler.post { delegate.onTransportStatusChanged(newStatus) }
    }

    private fun <T> callOnMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block).getOrNull()
        val value = AtomicReference<T?>()
        val latch = CountDownLatch(1)
        mainHandler.post {
            value.set(runCatching(block).getOrNull())
            latch.countDown()
        }
        return if (latch.await(StudyFlowTvWireProtocol.mainThreadDispatchTimeoutMillis, TimeUnit.MILLISECONDS)) {
            value.get()
        } else {
            null
        }
    }

    private fun stopLocked() {
        registrationListener?.let { listener -> runCatching { nsdManager.unregisterService(listener) } }
        registrationListener = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptExecutor?.shutdownNow()
        acceptExecutor = null
        workerExecutor?.shutdownNow()
        workerExecutor = null
    }

    private companion object {
        const val TV_SESSION_PREFERENCES = "studyflow_tv_receiver_link"
    }
}
