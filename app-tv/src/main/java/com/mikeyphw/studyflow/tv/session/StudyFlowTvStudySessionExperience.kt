package com.mikeyphw.studyflow.tv.session

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvCastState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole

/**
 * Structured Android TV study-session experience introduced in TVOV08.
 *
 * The TV session model is a functional state machine for start, pause, resume, next-step, and
 * completion commands. It can be driven by the TV remote or by paired-phone commands and can emit
 * a small StudyFlow cast state without screen mirroring or database payloads.
 */
enum class StudyFlowTvSessionStatus {
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,
}

enum class StudyFlowTvSessionSegmentId {
    WARM_UP,
    FOCUS_BLOCK,
    CHECKPOINT,
    SUMMARY,
}

enum class StudyFlowTvSessionAction {
    START,
    PAUSE,
    RESUME,
    NEXT_SEGMENT,
    COMPLETE,
    RETURN_TODAY,
}

data class StudyFlowTvSessionSegment(
    val id: StudyFlowTvSessionSegmentId,
    val minutes: Int,
    val row: Int,
    val column: Int,
) {
    init {
        require(minutes > 0) { "TV session segment duration must be positive." }
        require(row >= 0) { "TV session segment row must be non-negative." }
        require(column >= 0) { "TV session segment column must be non-negative." }
    }
}

data class StudyFlowTvSessionState(
    val sessionId: String,
    val title: String,
    val subject: String,
    val status: StudyFlowTvSessionStatus,
    val segments: List<StudyFlowTvSessionSegment>,
    val activeSegmentIndex: Int,
    val elapsedMinutes: Int,
    val targetMinutes: Int,
) {
    init {
        require(sessionId.isNotBlank()) { "TV session id must not be blank." }
        require(title.isNotBlank()) { "TV session title must not be blank." }
        require(subject.isNotBlank()) { "TV session subject must not be blank." }
        require(segments.isNotEmpty()) { "TV session must contain at least one segment." }
        require(activeSegmentIndex in segments.indices) { "TV session active segment is outside the segment list." }
        require(elapsedMinutes >= 0) { "TV session elapsed time must be non-negative." }
        require(targetMinutes > 0) { "TV session target time must be positive." }
        require(elapsedMinutes <= targetMinutes) { "TV session elapsed time must not exceed target." }
    }

    val progressPercent: Int
        get() = ((elapsedMinutes * 100) / targetMinutes).coerceIn(0, 100)

    val activeSegment: StudyFlowTvSessionSegment
        get() = segments[activeSegmentIndex]
}

object StudyFlowTvStudySessionExperience {
    const val overlay = "TVOV08"
    const val ownsStudySessionSurface = true
    const val remoteCanStartPauseResumeAndComplete = true
    const val pairedPhoneCanStartPauseResumeAndComplete = true
    const val emitsCastStateWithoutMirroring = true
    const val usesSharedControlProtocol = true
    const val segmentCount = 4
    const val actionCount = 6
    const val segmentColumnStart = 1
    const val actionRow = 4

    fun defaultState(): StudyFlowTvSessionState = StudyFlowTvSessionState(
        sessionId = "tv-session-default",
        title = "Focused study block",
        subject = "Next priority subject",
        status = StudyFlowTvSessionStatus.READY,
        segments = defaultSegments(),
        activeSegmentIndex = 0,
        elapsedMinutes = 0,
        targetMinutes = 45,
    )

    fun defaultSegments(): List<StudyFlowTvSessionSegment> = listOf(
        StudyFlowTvSessionSegment(StudyFlowTvSessionSegmentId.WARM_UP, minutes = 5, row = 0, column = segmentColumnStart),
        StudyFlowTvSessionSegment(StudyFlowTvSessionSegmentId.FOCUS_BLOCK, minutes = 30, row = 0, column = segmentColumnStart + 1),
        StudyFlowTvSessionSegment(StudyFlowTvSessionSegmentId.CHECKPOINT, minutes = 5, row = 1, column = segmentColumnStart),
        StudyFlowTvSessionSegment(StudyFlowTvSessionSegmentId.SUMMARY, minutes = 5, row = 1, column = segmentColumnStart + 1),
    )

    fun availableActions(state: StudyFlowTvSessionState): List<StudyFlowTvSessionAction> = when (state.status) {
        StudyFlowTvSessionStatus.READY -> listOf(
            StudyFlowTvSessionAction.START,
            StudyFlowTvSessionAction.RETURN_TODAY,
        )
        StudyFlowTvSessionStatus.RUNNING -> listOf(
            StudyFlowTvSessionAction.PAUSE,
            StudyFlowTvSessionAction.NEXT_SEGMENT,
            StudyFlowTvSessionAction.COMPLETE,
            StudyFlowTvSessionAction.RETURN_TODAY,
        )
        StudyFlowTvSessionStatus.PAUSED -> listOf(
            StudyFlowTvSessionAction.RESUME,
            StudyFlowTvSessionAction.COMPLETE,
            StudyFlowTvSessionAction.RETURN_TODAY,
        )
        StudyFlowTvSessionStatus.COMPLETED -> listOf(
            StudyFlowTvSessionAction.RETURN_TODAY,
        )
    }

    fun applyAction(
        state: StudyFlowTvSessionState,
        action: StudyFlowTvSessionAction,
    ): StudyFlowTvSessionState = when (action) {
        StudyFlowTvSessionAction.START -> state.copy(status = StudyFlowTvSessionStatus.RUNNING)
        StudyFlowTvSessionAction.PAUSE -> state.copy(status = StudyFlowTvSessionStatus.PAUSED)
        StudyFlowTvSessionAction.RESUME -> state.copy(status = StudyFlowTvSessionStatus.RUNNING)
        StudyFlowTvSessionAction.NEXT_SEGMENT -> advanceSegment(state)
        StudyFlowTvSessionAction.COMPLETE -> state.copy(
            status = StudyFlowTvSessionStatus.COMPLETED,
            activeSegmentIndex = state.segments.lastIndex,
            elapsedMinutes = state.targetMinutes,
        )
        StudyFlowTvSessionAction.RETURN_TODAY -> state
    }

    fun focusItems(state: StudyFlowTvSessionState): List<StudyFlowTvFocusItem> =
        state.segments.map { segment ->
            StudyFlowTvFocusItem(
                id = "session-segment-${segment.id.name.lowercase()}",
                role = StudyFlowTvFocusRole.CONTENT_CARD,
                row = segment.row,
                column = segment.column,
            )
        } + availableActions(state).mapIndexed { index, action ->
            StudyFlowTvFocusItem(
                id = "session-action-${action.name.lowercase()}",
                role = StudyFlowTvFocusRole.PRIMARY_ACTION,
                row = actionRow,
                column = segmentColumnStart + index,
            )
        }

    fun commandForAction(
        action: StudyFlowTvSessionAction,
        state: StudyFlowTvSessionState,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand = StudyFlowTvControlCommand(
        commandId = commandId,
        surface = StudyFlowTvSurface.STUDY_SESSION,
        action = controlActionFor(action),
        targetId = state.sessionId,
        issuedAtEpochMillis = issuedAtEpochMillis,
    )

    fun castStateFor(
        state: StudyFlowTvSessionState,
        castId: String,
        updatedAtEpochMillis: Long,
    ): StudyFlowTvCastState = StudyFlowTvCastState(
        castId = castId,
        surface = StudyFlowTvSurface.STUDY_SESSION,
        title = state.title,
        subtitle = "${state.subject} · ${state.activeSegment.id.name.lowercase().replace('_', ' ')}",
        progressPercent = state.progressPercent,
        activeItemId = state.activeSegment.id.name.lowercase(),
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun advanceSegment(state: StudyFlowTvSessionState): StudyFlowTvSessionState {
        val nextIndex = (state.activeSegmentIndex + 1).coerceAtMost(state.segments.lastIndex)
        val addedMinutes = state.segments[state.activeSegmentIndex].minutes
        return state.copy(
            status = if (nextIndex == state.segments.lastIndex) StudyFlowTvSessionStatus.RUNNING else state.status,
            activeSegmentIndex = nextIndex,
            elapsedMinutes = (state.elapsedMinutes + addedMinutes).coerceAtMost(state.targetMinutes),
        )
    }

    private fun controlActionFor(action: StudyFlowTvSessionAction): StudyFlowTvControlAction = when (action) {
        StudyFlowTvSessionAction.START -> StudyFlowTvControlAction.START
        StudyFlowTvSessionAction.PAUSE -> StudyFlowTvControlAction.PAUSE
        StudyFlowTvSessionAction.RESUME -> StudyFlowTvControlAction.RESUME
        StudyFlowTvSessionAction.NEXT_SEGMENT -> StudyFlowTvControlAction.NEXT
        StudyFlowTvSessionAction.COMPLETE -> StudyFlowTvControlAction.COMPLETE
        StudyFlowTvSessionAction.RETURN_TODAY -> StudyFlowTvControlAction.BACK
    }
}
