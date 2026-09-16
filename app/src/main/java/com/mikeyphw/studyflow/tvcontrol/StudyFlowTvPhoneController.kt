package com.mikeyphw.studyflow.tvcontrol

import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlContinuityState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlProtocol
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlOrigin
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionMapper
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionProtocol
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionResult
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvReceiverEndpoint
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSessionCredential
import com.mikeyphw.studyflow.core.tvcontrol.matchesCredentialBinding
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWireResponse
import com.mikeyphw.studyflow.core.tvlink.StudyFlowTvLinkStatus
import com.mikeyphw.studyflow.core.tvlink.StudyFlowTvLocalTransportClient
import com.mikeyphw.studyflow.core.tvlink.StudyFlowTvReceiverDiscovery
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** TVOV23 phone-controller product contract. */
object StudyFlowTvPhoneControllerContract {
    const val overlay = "TVOV23"
    const val settingsEntryRequired = true
    const val explicitReceiverSelectionRequired = true
    const val sixDigitPairingRequired = true
    const val persistedSessionResumeRequired = true
    const val continuityRefreshRequired = true
    const val revokeAndForgetRequired = true
    const val allTvDestinationsControllable = true
    const val sessionQuestionMockAndReviewControlsRequired = true
    const val presentationCastSynchronizationDeferredToTvov24 = false
    const val livePresentationSynchronizationImplemented = true
    const val explicitStartStopCastRequired = true
    const val reconnectResyncRequired = true
    const val domainMutationPollingIndependentOfCast = true

    val navigationSurfaces: Set<StudyFlowTvSurface> = StudyFlowTvSurface.entries.toSet()

    val requiredControlActions: Set<StudyFlowTvControlAction> = setOf(
        StudyFlowTvControlAction.OPEN,
        StudyFlowTvControlAction.START,
        StudyFlowTvControlAction.PAUSE,
        StudyFlowTvControlAction.RESUME,
        StudyFlowTvControlAction.NEXT,
        StudyFlowTvControlAction.PREVIOUS,
        StudyFlowTvControlAction.SELECT_ANSWER,
        StudyFlowTvControlAction.SHOW_EXPLANATION,
        StudyFlowTvControlAction.MARK_FOR_REVIEW,
        StudyFlowTvControlAction.SUBMIT,
        StudyFlowTvControlAction.REVIEW_RESULTS,
        StudyFlowTvControlAction.COMPLETE,
        StudyFlowTvControlAction.BACK,
        StudyFlowTvControlAction.STOP_CAST,
        StudyFlowTvControlAction.REVOKE_PAIRING,
    )
}

enum class StudyFlowTvPhoneControllerOperation {
    IDLE,
    CHECK_PAIRING,
    PAIR,
    COMMAND,
    REFRESH,
    REVOKE,
}

data class StudyFlowTvPhoneControllerState(
    val linkStatus: StudyFlowTvLinkStatus = StudyFlowTvLinkStatus.STOPPED,
    val receivers: List<StudyFlowTvReceiverEndpoint> = emptyList(),
    val selectedReceiver: StudyFlowTvReceiverEndpoint? = null,
    val credential: StudyFlowTvSessionCredential? = null,
    val continuity: StudyFlowTvControlContinuityState? = null,
    val pairingActive: Boolean? = null,
    val pairingExpiresAtEpochMillis: Long? = null,
    val operation: StudyFlowTvPhoneControllerOperation = StudyFlowTvPhoneControllerOperation.IDLE,
    val castSync: StudyFlowTvCastSyncState = StudyFlowTvCastSyncState(),
    val lastMessage: String? = null,
) {
    val paired: Boolean get() = credential != null
    val busy: Boolean get() = operation != StudyFlowTvPhoneControllerOperation.IDLE
}

/** Android-link seam kept small so command/state policy stays testable without a TV device. */
interface StudyFlowTvPhoneLinkGateway {
    fun savedCredential(nowEpochMillis: Long = System.currentTimeMillis()): StudyFlowTvSessionCredential?
    fun startDiscovery(listener: StudyFlowTvReceiverDiscovery.Listener): Boolean
    fun stopDiscovery()
    fun pairingInfo(endpoint: StudyFlowTvReceiverEndpoint): StudyFlowTvWireResponse
    fun pair(
        endpoint: StudyFlowTvReceiverEndpoint,
        pairingCode: String,
        phoneDeviceName: String,
    ): StudyFlowTvWireResponse
    fun sendCommand(endpoint: StudyFlowTvReceiverEndpoint, command: StudyFlowTvControlCommand): StudyFlowTvWireResponse
    fun sendPresentation(
        endpoint: StudyFlowTvReceiverEndpoint,
        snapshot: StudyFlowTvPresentationSnapshot,
    ): StudyFlowTvWireResponse
    fun fetchContinuity(endpoint: StudyFlowTvReceiverEndpoint): StudyFlowTvWireResponse
    fun acknowledgeDomainAction(
        endpoint: StudyFlowTvReceiverEndpoint,
        result: StudyFlowTvDomainActionResult,
    ): StudyFlowTvWireResponse
    fun revoke(endpoint: StudyFlowTvReceiverEndpoint): StudyFlowTvWireResponse
    fun clearSavedSession()
}

class DefaultStudyFlowTvPhoneLinkGateway(
    private val discovery: StudyFlowTvReceiverDiscovery,
    private val client: StudyFlowTvLocalTransportClient,
) : StudyFlowTvPhoneLinkGateway {
    override fun savedCredential(nowEpochMillis: Long): StudyFlowTvSessionCredential? =
        client.savedCredential(nowEpochMillis)
    override fun startDiscovery(listener: StudyFlowTvReceiverDiscovery.Listener): Boolean = discovery.start(listener)
    override fun stopDiscovery() = discovery.stop()
    override fun pairingInfo(endpoint: StudyFlowTvReceiverEndpoint): StudyFlowTvWireResponse =
        client.pairingInfo(endpoint)
    override fun pair(
        endpoint: StudyFlowTvReceiverEndpoint,
        pairingCode: String,
        phoneDeviceName: String,
    ): StudyFlowTvWireResponse = client.pair(endpoint, pairingCode, phoneDeviceName)
    override fun sendCommand(
        endpoint: StudyFlowTvReceiverEndpoint,
        command: StudyFlowTvControlCommand,
    ): StudyFlowTvWireResponse = client.sendCommand(endpoint, command)
    override fun sendPresentation(
        endpoint: StudyFlowTvReceiverEndpoint,
        snapshot: StudyFlowTvPresentationSnapshot,
    ): StudyFlowTvWireResponse = client.sendPresentation(endpoint, snapshot)
    override fun fetchContinuity(endpoint: StudyFlowTvReceiverEndpoint): StudyFlowTvWireResponse =
        client.fetchContinuity(endpoint)
    override fun acknowledgeDomainAction(
        endpoint: StudyFlowTvReceiverEndpoint,
        result: StudyFlowTvDomainActionResult,
    ): StudyFlowTvWireResponse = client.acknowledgeDomainAction(endpoint, result)
    override fun revoke(endpoint: StudyFlowTvReceiverEndpoint): StudyFlowTvWireResponse = client.revoke(endpoint)
    override fun clearSavedSession() { client.clearSavedSession() }
}

object StudyFlowTvPhoneCommandFactory {
    fun command(
        surface: StudyFlowTvSurface,
        action: StudyFlowTvControlAction,
        targetId: String? = null,
        optionId: String? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
        commandId: String = "phone-${UUID.randomUUID()}",
    ): StudyFlowTvControlCommand = StudyFlowTvControlCommand(
        commandId = commandId,
        protocolVersion = StudyFlowTvControlProtocol.version,
        surface = surface,
        action = action,
        targetId = targetId,
        optionId = optionId,
        issuedAtEpochMillis = nowEpochMillis,
        requiresUserPresence = true,
    )

    fun preferredReceiver(
        receivers: List<StudyFlowTvReceiverEndpoint>,
        credential: StudyFlowTvSessionCredential?,
        current: StudyFlowTvReceiverEndpoint?,
    ): StudyFlowTvReceiverEndpoint? {
        if (current != null) {
            receivers.firstOrNull { it == current }?.let { return it }
        }
        if (credential != null) {
            receivers.firstOrNull { it.matchesCredentialBinding(credential) }?.let { return it }
            if (credential.receiverServiceName == null && credential.receiverHostAddress == null && credential.receiverPort == null) {
                val pairedName = credential.tvDeviceName
                receivers.firstOrNull { it.serviceName == pairedName }?.let { return it }
            }
            return null
        }
        return receivers.singleOrNull()
    }
}

/**
 * Foreground-only phone controller. Discovery lives only while the controller screen is present;
 * the expiring credential itself remains in the TVOV22 private session store.
 */
class StudyFlowTvPhoneController(
    private val gateway: StudyFlowTvPhoneLinkGateway,
    private val presentationFeed: StudyFlowTvPresentationFeed,
    private val domainActionExecutor: StudyFlowTvDomainActionExecutor,
    private val defaultPlanId: StudyPlanId? = null,
    private val castIntentStore: StudyFlowTvCastIntentStore = NoOpStudyFlowTvCastIntentStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : StudyFlowTvReceiverDiscovery.Listener, Closeable {
    private var castCollectionJob: Job? = null
    private var castDeliveryJob: Job? = null
    private var domainSnapshotJob: Job? = null
    private var domainPollJob: Job? = null
    private var latestCastSnapshot: StudyFlowTvPresentationSnapshot? = null
    private var latestDomainSnapshot: StudyFlowTvPresentationSnapshot? = null
    private var observedDomainPlanId: StudyPlanId? = null
    private var castDeliveryRequested = false
    private var persistedCastResumeAttempted = false

    private val mutableState = MutableStateFlow(
        StudyFlowTvPhoneControllerState(credential = gateway.savedCredential()),
    )
    val state: StateFlow<StudyFlowTvPhoneControllerState> = mutableState.asStateFlow()

    init {
        observeDomainSnapshot(defaultPlanId)
    }

    fun startDiscovery() {
        startDomainActionPoll()
        resumePersistedCastIfEligible()
        val started = gateway.startDiscovery(this)
        if (!started && mutableState.value.linkStatus == StudyFlowTvLinkStatus.STOPPED) {
            mutableState.value = mutableState.value.copy(linkStatus = StudyFlowTvLinkStatus.ERROR)
        }
    }

    fun stopDiscovery() = gateway.stopDiscovery()

    override fun onStatusChanged(status: StudyFlowTvLinkStatus) {
        mutableState.value = mutableState.value.copy(linkStatus = status)
    }

    override fun onReceiversChanged(receivers: List<StudyFlowTvReceiverEndpoint>) {
        val state = mutableState.value
        mutableState.value = state.copy(
            receivers = receivers,
            selectedReceiver = StudyFlowTvPhoneCommandFactory.preferredReceiver(
                receivers = receivers,
                credential = state.credential,
                current = state.selectedReceiver,
            ),
        )
        requestCastDelivery()
    }

    fun selectReceiver(endpoint: StudyFlowTvReceiverEndpoint) {
        mutableState.value = mutableState.value.copy(
            selectedReceiver = endpoint,
            pairingActive = null,
            pairingExpiresAtEpochMillis = null,
            lastMessage = null,
        )
        requestCastDelivery()
    }

    fun checkPairing() {
        val endpoint = requireSelectedReceiver() ?: return
        launchOperation(StudyFlowTvPhoneControllerOperation.CHECK_PAIRING) {
            val response = gateway.pairingInfo(endpoint)
            val info = response.pairingInfo
            applyResponse(
                response = response,
                successMessage = if (info?.pairingActive == true) "pairing-ready" else "pairing-not-active",
                pairingActive = info?.pairingActive,
                pairingExpiresAtEpochMillis = info?.expiresAtEpochMillis,
            )
        }
    }

    fun pair(pairingCode: String, phoneDeviceName: String) {
        val endpoint = requireSelectedReceiver() ?: return
        val normalizedCode = pairingCode.trim()
        if (
            normalizedCode.length != StudyFlowTvControlProtocol.pairingCodeLength ||
            !normalizedCode.all(Char::isDigit)
        ) {
            mutableState.value = mutableState.value.copy(lastMessage = "pairing-code-invalid")
            return
        }
        launchOperation(StudyFlowTvPhoneControllerOperation.PAIR) {
            val response = gateway.pair(endpoint, normalizedCode, phoneDeviceName.trim())
            applyResponse(
                response = response,
                successMessage = "paired",
                credential = gateway.savedCredential(),
                pairingActive = false,
            )
        }
    }

    fun open(surface: StudyFlowTvSurface) = send(surface, StudyFlowTvControlAction.OPEN)

    fun send(
        surface: StudyFlowTvSurface,
        action: StudyFlowTvControlAction,
        targetId: String? = null,
        optionId: String? = null,
    ) {
        val endpoint = requireAuthorizedReceiver() ?: return
        if (mutableState.value.credential == null) {
            mutableState.value = mutableState.value.copy(lastMessage = "pair-first")
            return
        }
        val command = StudyFlowTvPhoneCommandFactory.command(
            surface = surface,
            action = action,
            targetId = targetId,
            optionId = optionId,
        )
        if (StudyFlowTvDomainActionProtocol.requiresPersistence(command)) {
            val domainAction = StudyFlowTvDomainActionMapper.fromCommand(
                command = command,
                snapshot = latestDomainSnapshot,
                origin = StudyFlowTvControlOrigin.PAIRED_PHONE,
            )
            if (domainAction == null) {
                mutableState.value = mutableState.value.copy(lastMessage = "domain-context-required")
                return
            }
            launchOperation(StudyFlowTvPhoneControllerOperation.COMMAND) {
                val result = domainActionExecutor.execute(domainAction)
                mutableState.value = mutableState.value.copy(
                    lastMessage = if (result.ok) "domain-persisted" else result.reason ?: "domain-action-failed",
                )
                if (result.ok) requestCastDelivery()
            }
            return
        }
        launchOperation(StudyFlowTvPhoneControllerOperation.COMMAND) {
            val response = gateway.sendCommand(endpoint, command)
            applyResponse(response, successMessage = "command-applied")
        }
    }

    fun startCast(planId: StudyPlanId?) {
        if (planId == null) {
            mutableState.value = mutableState.value.copy(
                castSync = StudyFlowTvCastSyncState(status = StudyFlowTvCastSyncStatus.WAITING_FOR_PLAN),
                lastMessage = "cast-plan-required",
            )
            return
        }
        if (mutableState.value.credential == null) {
            mutableState.value = mutableState.value.copy(
                castSync = StudyFlowTvCastSyncState(
                    enabled = false,
                    status = StudyFlowTvCastSyncStatus.WAITING_FOR_PAIRING,
                    planId = planId.value,
                ),
                lastMessage = "pair-first",
            )
            return
        }

        castIntentStore.saveActive(planId)
        observeDomainSnapshot(planId)
        castCollectionJob?.cancel()
        latestCastSnapshot = null
        mutableState.value = mutableState.value.copy(
            castSync = StudyFlowTvCastSyncState(
                enabled = true,
                status = if (mutableState.value.selectedReceiver == null) {
                    StudyFlowTvCastSyncStatus.WAITING_FOR_RECEIVER
                } else {
                    StudyFlowTvCastSyncStatus.SYNCING
                },
                planId = planId.value,
            ),
            lastMessage = "cast-started",
        )
        startDomainActionPoll()
        castCollectionJob = scope.launch {
            try {
                presentationFeed.observe(planId).collect { snapshot ->
                    latestCastSnapshot = snapshot
                    val cast = mutableState.value.castSync
                    mutableState.value = mutableState.value.copy(
                        castSync = cast.copy(
                            latestSnapshotId = snapshot.snapshotId,
                            status = if (mutableState.value.selectedReceiver == null) {
                                StudyFlowTvCastSyncStatus.WAITING_FOR_RECEIVER
                            } else {
                                StudyFlowTvCastSyncStatus.SYNCING
                            },
                            lastError = null,
                        ),
                    )
                    requestCastDelivery()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    castSync = mutableState.value.castSync.copy(
                        status = StudyFlowTvCastSyncStatus.ERROR,
                        lastError = "presentation-${error.javaClass.simpleName.lowercase()}",
                    ),
                )
            }
        }
    }

    fun resyncCast() {
        if (!mutableState.value.castSync.enabled) {
            mutableState.value = mutableState.value.copy(lastMessage = "cast-not-active")
            return
        }
        requestCastDelivery()
    }

    fun stopCast() {
        val castPlanId = mutableState.value.castSync.planId?.takeIf(String::isNotBlank)?.let(::StudyPlanId)
        if (mutableState.value.credential != null && castPlanId != null) {
            castIntentStore.saveStopPending(castPlanId)
        } else {
            castIntentStore.clear()
        }
        castCollectionJob?.cancel()
        castCollectionJob = null
        latestCastSnapshot = null
        val paired = mutableState.value.credential != null
        mutableState.value = mutableState.value.copy(
            castSync = mutableState.value.castSync.copy(
                enabled = false,
                status = if (paired) StudyFlowTvCastSyncStatus.STOPPING else StudyFlowTvCastSyncStatus.STOPPED,
                remoteStopPending = paired,
                latestSnapshotId = null,
                lastError = null,
            ),
            lastMessage = "cast-stop-requested",
        )
        requestCastDelivery()
    }

    fun refreshContinuity() {
        val endpoint = requireAuthorizedReceiver() ?: return
        if (mutableState.value.credential == null) {
            mutableState.value = mutableState.value.copy(lastMessage = "pair-first")
            return
        }
        launchOperation(StudyFlowTvPhoneControllerOperation.REFRESH) {
            val response = gateway.fetchContinuity(endpoint)
            applyResponse(response, successMessage = "continuity-refreshed")
            handlePendingDomainAction(endpoint, response)
        }
    }

    fun revoke() {
        val endpoint = requireSelectedReceiver() ?: return
        launchOperation(StudyFlowTvPhoneControllerOperation.REVOKE) {
            val response = gateway.revoke(endpoint)
            if (response.ok) {
                gateway.clearSavedSession()
                castIntentStore.clear()
                castCollectionJob?.cancel()
                castCollectionJob = null
                domainPollJob?.cancel()
                domainPollJob = null
                latestCastSnapshot = null
                mutableState.value = mutableState.value.copy(castSync = StudyFlowTvCastSyncState())
            }
            applyResponse(
                response = response,
                successMessage = "pairing-revoked",
                credential = null,
                continuity = null,
            )
        }
    }

    fun forgetSavedSession() {
        gateway.clearSavedSession()
        castIntentStore.clear()
        castCollectionJob?.cancel()
        castCollectionJob = null
        domainPollJob?.cancel()
        domainPollJob = null
        latestCastSnapshot = null
        mutableState.value = mutableState.value.copy(
            credential = null,
            continuity = null,
            castSync = StudyFlowTvCastSyncState(),
            lastMessage = "local-session-forgotten",
        )
    }

    private fun resumePersistedCastIfEligible() {
        if (persistedCastResumeAttempted) return
        persistedCastResumeAttempted = true
        val persisted = castIntentStore.load() ?: return
        val currentPlanId = defaultPlanId
        if (currentPlanId == null || currentPlanId != persisted.planId || mutableState.value.credential == null) {
            castIntentStore.clear()
            return
        }
        when (persisted.kind) {
            StudyFlowTvPersistedCastIntentKind.ACTIVE -> {
                startCast(currentPlanId)
                mutableState.value = mutableState.value.copy(lastMessage = "cast-resumed-after-restart")
            }
            StudyFlowTvPersistedCastIntentKind.STOP_PENDING -> {
                mutableState.value = mutableState.value.copy(
                    castSync = StudyFlowTvCastSyncState(
                        enabled = false,
                        status = StudyFlowTvCastSyncStatus.STOPPING,
                        planId = currentPlanId.value,
                        remoteStopPending = true,
                    ),
                    lastMessage = "cast-stop-resumed-after-restart",
                )
                requestCastDelivery()
            }
        }
    }

    private fun observeDomainSnapshot(planId: StudyPlanId?) {
        if (planId == null || observedDomainPlanId == planId) return
        observedDomainPlanId = planId
        domainSnapshotJob?.cancel()
        domainSnapshotJob = scope.launch {
            try {
                presentationFeed.observe(planId).collect { snapshot ->
                    latestDomainSnapshot = snapshot
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The controller remains usable for non-persistent navigation if projection is unavailable.
                latestDomainSnapshot = null
            }
        }
    }

    private fun startDomainActionPoll() {
        if (domainPollJob?.isActive == true) return
        domainPollJob = scope.launch {
            while (true) {
                delay(1_000L)
                val state = mutableState.value
                if (state.credential == null) continue
                val endpoint = state.selectedReceiver?.takeIf { selected ->
                    state.credential?.let { selected.matchesCredentialBinding(it) } ?: true
                } ?: continue
                try {
                    val response = withContext(ioDispatcher) { gateway.fetchContinuity(endpoint) }
                    if (StudyFlowTvCastSynchronizationPolicy.isSessionFailure(response)) {
                        invalidateSessionForCast(response.reason ?: "session-invalid")
                        return@launch
                    }
                    if (response.ok) {
                        mutableState.value = mutableState.value.copy(
                            continuity = response.continuity ?: mutableState.value.continuity,
                        )
                        handlePendingDomainAction(endpoint, response)
                        reconcilePresentationReceipt(response)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Discovery/reconnect will retry; canonical domain action ids are journaled.
                }
            }
        }
    }

    private suspend fun handlePendingDomainAction(
        endpoint: StudyFlowTvReceiverEndpoint,
        response: StudyFlowTvWireResponse,
    ) {
        val action = response.pendingDomainAction ?: return
        val result = withContext(ioDispatcher) { domainActionExecutor.execute(action) }
        val acknowledgement = withContext(ioDispatcher) { gateway.acknowledgeDomainAction(endpoint, result) }
        if (StudyFlowTvCastSynchronizationPolicy.isSessionFailure(acknowledgement)) {
            invalidateSessionForCast(acknowledgement.reason ?: "session-invalid")
            return
        }
        mutableState.value = mutableState.value.copy(
            continuity = acknowledgement.continuity ?: mutableState.value.continuity,
            lastMessage = if (result.ok) "tv-domain-action-persisted" else result.reason ?: "domain-action-failed",
        )
        if (result.ok) requestCastDelivery()
    }

    private fun requestCastDelivery() {
        castDeliveryRequested = true
        if (castDeliveryJob?.isActive == true) return
        castDeliveryJob = scope.launch {
            while (castDeliveryRequested) {
                castDeliveryRequested = false
                val state = mutableState.value
                val endpoint = state.selectedReceiver?.takeIf { selected ->
                    state.credential?.let { selected.matchesCredentialBinding(it) } ?: true
                }
                val cast = state.castSync

                if (state.credential == null) {
                    if (cast.enabled || cast.remoteStopPending) {
                        mutableState.value = state.copy(
                            castSync = cast.copy(
                                status = StudyFlowTvCastSyncStatus.WAITING_FOR_PAIRING,
                                remoteStopPending = false,
                            ),
                        )
                    }
                    continue
                }
                if (endpoint == null) {
                    if (cast.enabled || cast.remoteStopPending) {
                        mutableState.value = state.copy(
                            castSync = cast.copy(
                                status = StudyFlowTvCastSyncStatus.WAITING_FOR_RECEIVER,
                            ),
                        )
                    }
                    continue
                }

                if (cast.remoteStopPending) {
                    deliverPendingCastStop(endpoint)
                    continue
                }
                if (!cast.enabled) continue
                val snapshot = latestCastSnapshot ?: continue
                deliverLatestPresentation(endpoint, snapshot)
                if (latestCastSnapshot?.snapshotId != snapshot.snapshotId) {
                    castDeliveryRequested = true
                }
            }
        }
    }

    private suspend fun deliverLatestPresentation(
        endpoint: StudyFlowTvReceiverEndpoint,
        snapshot: StudyFlowTvPresentationSnapshot,
    ) {
        mutableState.value = mutableState.value.copy(
            castSync = mutableState.value.castSync.copy(
                status = StudyFlowTvCastSyncStatus.SYNCING,
                lastError = null,
            ),
        )
        val response = withContext(ioDispatcher) { gateway.sendPresentation(endpoint, snapshot) }
        if (StudyFlowTvCastSynchronizationPolicy.isSessionFailure(response)) {
            invalidateSessionForCast(response.reason ?: "session-invalid")
            return
        }
        val acknowledged = StudyFlowTvCastSynchronizationPolicy.acknowledges(snapshot, response)
        mutableState.value = mutableState.value.copy(
            continuity = response.continuity ?: mutableState.value.continuity,
            castSync = mutableState.value.castSync.copy(
                status = if (acknowledged) StudyFlowTvCastSyncStatus.LIVE else StudyFlowTvCastSyncStatus.ERROR,
                acknowledgedSnapshotId = response.presentationReceipt?.snapshotId,
                acknowledgedAtEpochMillis = if (acknowledged) {
                    response.respondedAtEpochMillis
                } else {
                    mutableState.value.castSync.acknowledgedAtEpochMillis
                },
                lastError = if (acknowledged) null else response.reason ?: "presentation-not-acknowledged",
            ),
            lastMessage = if (acknowledged) "cast-synchronized" else response.reason ?: "cast-sync-failed",
        )
    }

    private suspend fun deliverPendingCastStop(endpoint: StudyFlowTvReceiverEndpoint) {
        mutableState.value = mutableState.value.copy(
            castSync = mutableState.value.castSync.copy(status = StudyFlowTvCastSyncStatus.STOPPING),
        )
        val now = System.currentTimeMillis()
        val surface = mutableState.value.continuity?.currentSurface ?: StudyFlowTvSurface.HOME
        val castScope = StudyFlowTvCastSynchronizationPolicy.remoteStopScope(
            mutableState.value.castSync.planId,
            surface.name,
        )
        val response = withContext(ioDispatcher) {
            gateway.sendCommand(
                endpoint,
                StudyFlowTvPhoneCommandFactory.command(
                    surface = surface,
                    action = StudyFlowTvControlAction.STOP_CAST,
                    targetId = castScope,
                    nowEpochMillis = now,
                ),
            )
        }
        if (StudyFlowTvCastSynchronizationPolicy.isSessionFailure(response)) {
            invalidateSessionForCast(response.reason ?: "session-invalid")
            return
        }
        if (response.ok && response.applied == true && response.presentationReceipt == null) {
            castIntentStore.clear()
            mutableState.value = mutableState.value.copy(
                continuity = response.continuity ?: mutableState.value.continuity,
                castSync = StudyFlowTvCastSyncState(status = StudyFlowTvCastSyncStatus.STOPPED),
                lastMessage = "cast-stopped",
            )
        } else {
            mutableState.value = mutableState.value.copy(
                castSync = mutableState.value.castSync.copy(
                    status = StudyFlowTvCastSyncStatus.ERROR,
                    remoteStopPending = true,
                    lastError = response.reason ?: "cast-stop-not-acknowledged",
                ),
                lastMessage = response.reason ?: "cast-stop-failed",
            )
        }
    }

    private fun invalidateSessionForCast(reason: String) {
        gateway.clearSavedSession()
        castIntentStore.clear()
        castCollectionJob?.cancel()
        castCollectionJob = null
        domainPollJob?.cancel()
        domainPollJob = null
        latestCastSnapshot = null
        mutableState.value = mutableState.value.copy(
            credential = null,
            continuity = null,
            castSync = mutableState.value.castSync.copy(
                enabled = false,
                status = StudyFlowTvCastSyncStatus.WAITING_FOR_PAIRING,
                remoteStopPending = false,
                lastError = reason,
            ),
            lastMessage = reason,
        )
    }

    private fun reconcilePresentationReceipt(response: StudyFlowTvWireResponse) {
        val cast = mutableState.value.castSync
        if (!cast.enabled) return
        val latest = latestCastSnapshot ?: return
        val receipt = response.presentationReceipt
        val exactReceipt = receipt != null &&
            receipt.snapshotId == latest.snapshotId &&
            receipt.generatedAtEpochMillis == latest.generatedAtEpochMillis
        if (!exactReceipt) {
            requestCastDelivery()
        } else if (cast.status != StudyFlowTvCastSyncStatus.LIVE) {
            mutableState.value = mutableState.value.copy(
                castSync = cast.copy(
                    status = StudyFlowTvCastSyncStatus.LIVE,
                    acknowledgedSnapshotId = latest.snapshotId,
                    acknowledgedAtEpochMillis = response.respondedAtEpochMillis,
                    lastError = null,
                ),
            )
        }
    }

    override fun close() {
        gateway.stopDiscovery()
        castCollectionJob?.cancel()
        castDeliveryJob?.cancel()
        domainSnapshotJob?.cancel()
        domainPollJob?.cancel()
        scope.cancel()
    }

    private fun requireSelectedReceiver(): StudyFlowTvReceiverEndpoint? {
        val endpoint = mutableState.value.selectedReceiver
        if (endpoint == null) {
            mutableState.value = mutableState.value.copy(lastMessage = "receiver-required")
        }
        return endpoint
    }

    private fun requireAuthorizedReceiver(): StudyFlowTvReceiverEndpoint? {
        val endpoint = requireSelectedReceiver() ?: return null
        val credential = mutableState.value.credential ?: return endpoint
        if (!endpoint.matchesCredentialBinding(credential)) {
            mutableState.value = mutableState.value.copy(lastMessage = "receiver-binding-mismatch")
            return null
        }
        return endpoint
    }

    private fun launchOperation(
        operation: StudyFlowTvPhoneControllerOperation,
        block: suspend () -> Unit,
    ) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(operation = operation, lastMessage = null)
        scope.launch {
            try {
                withContext(ioDispatcher) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    lastMessage = "controller-${error.javaClass.simpleName.lowercase()}",
                )
            } finally {
                mutableState.value = mutableState.value.copy(operation = StudyFlowTvPhoneControllerOperation.IDLE)
            }
        }
    }

    private fun applyResponse(
        response: StudyFlowTvWireResponse,
        successMessage: String,
        credential: StudyFlowTvSessionCredential? = mutableState.value.credential,
        continuity: StudyFlowTvControlContinuityState? = response.continuity ?: mutableState.value.continuity,
        pairingActive: Boolean? = mutableState.value.pairingActive,
        pairingExpiresAtEpochMillis: Long? = mutableState.value.pairingExpiresAtEpochMillis,
    ) {
        val staleSession = response.reason in setOf(
            "no-active-session",
            "session-expired-or-missing",
            "session-authorization-failed",
        )
        if (staleSession) gateway.clearSavedSession()
        mutableState.value = mutableState.value.copy(
            credential = if (staleSession) null else credential,
            continuity = if (staleSession) null else continuity,
            pairingActive = pairingActive,
            pairingExpiresAtEpochMillis = pairingExpiresAtEpochMillis,
            castSync = if (staleSession) {
                mutableState.value.castSync.copy(
                    enabled = false,
                    status = StudyFlowTvCastSyncStatus.WAITING_FOR_PAIRING,
                    remoteStopPending = false,
                    lastError = response.reason,
                )
            } else {
                mutableState.value.castSync
            },
            lastMessage = if (response.ok) successMessage else response.reason ?: "request-failed",
        )
        if (!staleSession && response.ok) reconcilePresentationReceipt(response)
    }
}
