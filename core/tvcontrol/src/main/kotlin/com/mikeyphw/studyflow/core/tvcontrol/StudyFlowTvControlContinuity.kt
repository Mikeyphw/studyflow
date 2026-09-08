package com.mikeyphw.studyflow.core.tvcontrol

import kotlinx.serialization.Serializable

/** Shared deterministic session/state schema introduced in TVOV16. */
@Serializable
enum class StudyFlowTvControlOrigin {
    LOCAL_REMOTE,
    PAIRED_PHONE,
}

@Serializable
enum class StudyFlowTvPlaybackState {
    IDLE,
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,
    SUBMITTED,
    RESULTS,
    VIEWING,
}

@Serializable
data class StudyFlowTvControlSession(
    val sessionId: String? = null,
    val pairedDeviceName: String? = null,
    val pairingState: StudyFlowTvPairingState = StudyFlowTvPairingState.UNPAIRED,
    val pairedAtEpochMillis: Long? = null,
    val expiresAtEpochMillis: Long? = null,
    val revokedAtEpochMillis: Long? = null,
) {
    init {
        if (pairingState == StudyFlowTvPairingState.PAIRED) {
            require(!sessionId.isNullOrBlank()) { "Paired TV control session must have a session id." }
            require(!pairedDeviceName.isNullOrBlank()) { "Paired TV control session must identify the paired device." }
            require(pairedAtEpochMillis != null) { "Paired TV control session must have a paired timestamp." }
            require(expiresAtEpochMillis != null && expiresAtEpochMillis > pairedAtEpochMillis) {
                "Paired TV control session must expire after pairing."
            }
        }
        if (pairingState == StudyFlowTvPairingState.REVOKED) {
            require(revokedAtEpochMillis != null) { "Revoked TV control session must record revocation time." }
        }
    }

    fun acceptsPairedPhoneCommands(nowEpochMillis: Long): Boolean =
        pairingState == StudyFlowTvPairingState.PAIRED &&
            expiresAtEpochMillis != null &&
            nowEpochMillis < expiresAtEpochMillis

    fun revoke(nowEpochMillis: Long): StudyFlowTvControlSession = copy(
        pairingState = StudyFlowTvPairingState.REVOKED,
        revokedAtEpochMillis = nowEpochMillis,
    )

    companion object {
        fun paired(
            sessionId: String,
            pairedDeviceName: String,
            pairedAtEpochMillis: Long,
            expiresAtEpochMillis: Long,
        ): StudyFlowTvControlSession = StudyFlowTvControlSession(
            sessionId = sessionId,
            pairedDeviceName = pairedDeviceName,
            pairingState = StudyFlowTvPairingState.PAIRED,
            pairedAtEpochMillis = pairedAtEpochMillis,
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }
}

/** Serializable projection a controller can use to resume the TV presentation deterministically. */
@Serializable
data class StudyFlowTvControlContinuityState(
    val currentDestination: String,
    val currentSurface: StudyFlowTvSurface,
    val activeSurfaceTitle: String,
    val activeItemId: String? = null,
    val playbackState: StudyFlowTvPlaybackState = StudyFlowTvPlaybackState.IDLE,
    val commandRevision: Long = 0,
    val lastCommandId: String? = null,
    val lastCommandIssuedAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long = 0,
    val pairingState: StudyFlowTvPairingState = StudyFlowTvPairingState.UNPAIRED,
    val sessionExpiresAtEpochMillis: Long? = null,
) {
    init {
        require(currentDestination.isNotBlank()) { "TV continuity destination must not be blank." }
        require(activeSurfaceTitle.isNotBlank()) { "TV continuity surface title must not be blank." }
        require(commandRevision >= 0) { "TV continuity revision must be non-negative." }
        require(updatedAtEpochMillis >= 0) { "TV continuity update time must be non-negative." }
    }
}
