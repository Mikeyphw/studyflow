package com.mikeyphw.studyflow.tvcontrol

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlContinuityState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlProtocol
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvReceiverEndpoint
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSessionCredential
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWireResponse
import com.mikeyphw.studyflow.core.tvlink.StudyFlowTvLinkStatus
import com.mikeyphw.studyflow.core.tvlink.StudyFlowTvLocalTransportClient
import com.mikeyphw.studyflow.core.tvlink.StudyFlowTvReceiverDiscovery
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    const val presentationCastSynchronizationDeferredToTvov24 = true

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
    fun pair(endpoint: StudyFlowTvReceiverEndpoint, pairingCode: String, phoneDeviceName: String): StudyFlowTvWireResponse
    fun sendCommand(endpoint: StudyFlowTvReceiverEndpoint, command: StudyFlowTvControlCommand): StudyFlowTvWireResponse
    fun fetchContinuity(endpoint: StudyFlowTvReceiverEndpoint): StudyFlowTvWireResponse
    fun revoke(endpoint: StudyFlowTvReceiverEndpoint): StudyFlowTvWireResponse
    fun clearSavedSession()
}

class DefaultStudyFlowTvPhoneLinkGateway(
    private val discovery: StudyFlowTvReceiverDiscovery,
    private val client: StudyFlowTvLocalTransportClient,
) : StudyFlowTvPhoneLinkGateway {
    override fun savedCredential(nowEpochMillis: Long): StudyFlowTvSessionCredential? = client.savedCredential(nowEpochMillis)
    override fun startDiscovery(listener: StudyFlowTvReceiverDiscovery.Listener): Boolean = discovery.start(listener)
    override fun stopDiscovery() = discovery.stop()
    override fun pairingInfo(endpoint: StudyFlowTvReceiverEndpoint): StudyFlowTvWireResponse = client.pairingInfo(endpoint)
    override fun pair(
        endpoint: StudyFlowTvReceiverEndpoint,
        pairingCode: String,
        phoneDeviceName: String,
    ): StudyFlowTvWireResponse = client.pair(endpoint, pairingCode, phoneDeviceName)
    override fun sendCommand(
        endpoint: StudyFlowTvReceiverEndpoint,
        command: StudyFlowTvControlCommand,
    ): StudyFlowTvWireResponse = client.sendCommand(endpoint, command)
    override fun fetchContinuity(endpoint: StudyFlowTvReceiverEndpoint): StudyFlowTvWireResponse = client.fetchContinuity(endpoint)
    override fun revoke(endpoint: StudyFlowTvReceiverEndpoint): StudyFlowTvWireResponse = client.revoke(endpoint)
    override fun clearSavedSession() = client.clearSavedSession()
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
        val pairedName = credential?.tvDeviceName
        if (!pairedName.isNullOrBlank()) {
            receivers.firstOrNull { it.serviceName == pairedName }?.let { return it }
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : StudyFlowTvReceiverDiscovery.Listener, Closeable {
    private val mutableState = MutableStateFlow(
        StudyFlowTvPhoneControllerState(credential = gateway.savedCredential()),
    )
    val state: StateFlow<StudyFlowTvPhoneControllerState> = mutableState.asStateFlow()

    fun startDiscovery() {
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
    }

    fun selectReceiver(endpoint: StudyFlowTvReceiverEndpoint) {
        mutableState.value = mutableState.value.copy(
            selectedReceiver = endpoint,
            pairingActive = null,
            pairingExpiresAtEpochMillis = null,
            lastMessage = null,
        )
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
        if (normalizedCode.length != StudyFlowTvControlProtocol.pairingCodeLength || !normalizedCode.all(Char::isDigit)) {
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
        val endpoint = requireSelectedReceiver() ?: return
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
        launchOperation(StudyFlowTvPhoneControllerOperation.COMMAND) {
            val response = gateway.sendCommand(endpoint, command)
            applyResponse(response, successMessage = "command-applied")
        }
    }

    fun refreshContinuity() {
        val endpoint = requireSelectedReceiver() ?: return
        if (mutableState.value.credential == null) {
            mutableState.value = mutableState.value.copy(lastMessage = "pair-first")
            return
        }
        launchOperation(StudyFlowTvPhoneControllerOperation.REFRESH) {
            applyResponse(gateway.fetchContinuity(endpoint), successMessage = "continuity-refreshed")
        }
    }

    fun revoke() {
        val endpoint = requireSelectedReceiver() ?: return
        launchOperation(StudyFlowTvPhoneControllerOperation.REVOKE) {
            val response = gateway.revoke(endpoint)
            if (response.ok) gateway.clearSavedSession()
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
        mutableState.value = mutableState.value.copy(
            credential = null,
            continuity = null,
            lastMessage = "local-session-forgotten",
        )
    }

    override fun close() {
        gateway.stopDiscovery()
        scope.cancel()
    }

    private fun requireSelectedReceiver(): StudyFlowTvReceiverEndpoint? {
        val endpoint = mutableState.value.selectedReceiver
        if (endpoint == null) {
            mutableState.value = mutableState.value.copy(lastMessage = "receiver-required")
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
            lastMessage = if (response.ok) successMessage else response.reason ?: "request-failed",
        )
    }
}
