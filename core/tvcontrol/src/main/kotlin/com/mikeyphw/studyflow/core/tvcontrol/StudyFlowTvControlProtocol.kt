package com.mikeyphw.studyflow.core.tvcontrol

import kotlinx.serialization.Serializable

/**
 * Shared phone-to-TV control and presentation-cast protocol introduced in TVOV03.
 *
 * The protocol is intentionally small and data-only. It describes StudyFlow intents and
 * presentation state; it does not expose database rows, file paths, raw screen mirroring, or
 * transport details. Discovery, pairing transport, and real cast transport are added later.
 */
object StudyFlowTvControlProtocol {
    const val version = 1
    const val pairingCodeLength = 6
    const val pairingCodeTtlSeconds = 120
    const val requiresRevocablePairing = true
    const val transportsDeferredUntilImplementation = false

    val controllableSurfaces: Set<StudyFlowTvSurface> = StudyFlowTvSurface.entries.toSet()

    val castableSurfaces: Set<StudyFlowTvSurface> = setOf(
        StudyFlowTvSurface.STUDY_SESSION,
        StudyFlowTvSurface.QUESTION_SET,
        StudyFlowTvSurface.REVIEW,
        StudyFlowTvSurface.MOCK_EXAM,
        StudyFlowTvSurface.PROGRESS,
        StudyFlowTvSurface.SCHEDULE,
        StudyFlowTvSurface.LIBRARY,
    )

    fun isControllable(surface: StudyFlowTvSurface): Boolean = surface in controllableSurfaces

    fun isCastable(surface: StudyFlowTvSurface): Boolean = surface in castableSurfaces
}

@Serializable
enum class StudyFlowTvSurface {
    HOME,
    TODAY,
    STUDY_SESSION,
    QUESTION_SET,
    REVIEW,
    MOCK_EXAM,
    PROGRESS,
    SCHEDULE,
    LIBRARY,
    SETTINGS,
}

@Serializable
enum class StudyFlowTvControlAction {
    OPEN,
    START,
    PAUSE,
    RESUME,
    NEXT,
    PREVIOUS,
    SELECT_ANSWER,
    SHOW_EXPLANATION,
    MARK_FOR_REVIEW,
    SUBMIT,
    REVIEW_RESULTS,
    COMPLETE,
    BACK,
    STOP_CAST,
    REVOKE_PAIRING,
}

@Serializable
enum class StudyFlowTvPairingState {
    UNPAIRED,
    PAIRING_CODE_VISIBLE,
    PAIRED,
    REVOKED,
}

@Serializable
data class StudyFlowTvPairingToken(
    val tvDeviceName: String,
    val pairingCode: String,
    val sessionNonce: String,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    init {
        require(tvDeviceName.isNotBlank()) { "TV device name must not be blank." }
        require(pairingCode.length == StudyFlowTvControlProtocol.pairingCodeLength) {
            "TV pairing code must have ${StudyFlowTvControlProtocol.pairingCodeLength} digits."
        }
        require(pairingCode.all { it.isDigit() }) { "TV pairing code must contain digits only." }
        require(sessionNonce.length >= 16) { "TV pairing session nonce must be at least 16 characters." }
        require(expiresAtEpochMillis > issuedAtEpochMillis) { "TV pairing token must expire after it is issued." }
    }
}

@Serializable
data class StudyFlowTvControlCommand(
    val commandId: String,
    val protocolVersion: Int = StudyFlowTvControlProtocol.version,
    val surface: StudyFlowTvSurface,
    val action: StudyFlowTvControlAction,
    val targetId: String? = null,
    val optionId: String? = null,
    val issuedAtEpochMillis: Long,
    val requiresUserPresence: Boolean = true,
) {
    init {
        require(commandId.isNotBlank()) { "TV control command id must not be blank." }
        require(protocolVersion == StudyFlowTvControlProtocol.version) { "Unsupported TV control protocol version." }
        require(StudyFlowTvControlProtocol.isControllable(surface)) { "Surface is not controllable: $surface" }
        require(requiresUserPresence) { "TV control commands must require user presence." }
    }
}

@Serializable
data class StudyFlowTvCastState(
    val castId: String,
    val protocolVersion: Int = StudyFlowTvControlProtocol.version,
    val surface: StudyFlowTvSurface,
    val title: String,
    val subtitle: String? = null,
    val progressPercent: Int? = null,
    val activeItemId: String? = null,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(castId.isNotBlank()) { "TV cast id must not be blank." }
        require(protocolVersion == StudyFlowTvControlProtocol.version) { "Unsupported TV control protocol version." }
        require(StudyFlowTvControlProtocol.isCastable(surface)) { "Surface is not castable: $surface" }
        require(title.isNotBlank()) { "TV cast title must not be blank." }
        require(progressPercent == null || progressPercent in 0..100) {
            "TV cast progress must be between 0 and 100."
        }
    }
}
