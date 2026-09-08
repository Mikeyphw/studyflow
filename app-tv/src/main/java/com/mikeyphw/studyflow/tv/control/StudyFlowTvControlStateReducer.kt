package com.mikeyphw.studyflow.tv.control

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlContinuityState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlOrigin
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlSession
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPairingState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPlaybackState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.library.StudyFlowTvLibraryAction
import com.mikeyphw.studyflow.tv.library.StudyFlowTvLibrarySnapshot
import com.mikeyphw.studyflow.tv.library.StudyFlowTvLibrarySurface
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamAction
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamState
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamStatus
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamStudio
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvNavigationShell
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvPracticeAction
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvQuestionPractice
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvQuestionPracticeState
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressAnalytics
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressSnapshot
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewAction
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewNotebook
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewState
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewStatus
import com.mikeyphw.studyflow.tv.schedule.StudyFlowTvScheduleAction
import com.mikeyphw.studyflow.tv.schedule.StudyFlowTvScheduleSnapshot
import com.mikeyphw.studyflow.tv.schedule.StudyFlowTvScheduleSurface
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionAction
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionState
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionStatus
import com.mikeyphw.studyflow.tv.session.StudyFlowTvStudySessionExperience
import com.mikeyphw.studyflow.tv.settings.StudyFlowTvSettingsAction
import com.mikeyphw.studyflow.tv.settings.StudyFlowTvSettingsState
import com.mikeyphw.studyflow.tv.settings.StudyFlowTvSettingsSurface
import com.mikeyphw.studyflow.tv.today.StudyFlowTvTodaySnapshot
import com.mikeyphw.studyflow.tv.today.StudyFlowTvTodayWorkspace

/** Complete in-memory TV presentation model controlled by the TV remote or a paired phone. */
data class StudyFlowTvControlState(
    val destination: StudyFlowTvDestination = StudyFlowTvNavigationShell.defaultDestination(),
    val session: StudyFlowTvControlSession = StudyFlowTvControlSession(),
    val today: StudyFlowTvTodaySnapshot = StudyFlowTvTodayWorkspace.defaultSnapshot(),
    val studySession: StudyFlowTvSessionState = StudyFlowTvStudySessionExperience.defaultState(),
    val questionPractice: StudyFlowTvQuestionPracticeState = StudyFlowTvQuestionPractice.defaultState(),
    val mockExam: StudyFlowTvMockExamState = StudyFlowTvMockExamStudio.defaultState(),
    val review: StudyFlowTvReviewState = StudyFlowTvReviewNotebook.defaultState(),
    val progress: StudyFlowTvProgressSnapshot = StudyFlowTvProgressAnalytics.defaultSnapshot(),
    val schedule: StudyFlowTvScheduleSnapshot = StudyFlowTvScheduleSurface.defaultSnapshot(),
    val library: StudyFlowTvLibrarySnapshot = StudyFlowTvLibrarySurface.defaultSnapshot(),
    val settings: StudyFlowTvSettingsState = StudyFlowTvSettingsSurface.defaultState(),
    val commandRevision: Long = 0,
    val lastCommandId: String? = null,
    val lastCommandIssuedAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long = 0,
)

data class StudyFlowTvControlReduction(
    val state: StudyFlowTvControlState,
    val applied: Boolean,
    val reason: String? = null,
)

/**
 * TVOV16 deterministic reducer. No network transport lives here: callers provide a command and its
 * origin, and both local and paired-phone paths converge on this exact state transition function.
 */
object StudyFlowTvControlStateReducer {
    const val overlay = "TVOV16"
    const val localAndPairedPhoneUseSameReducer = true
    const val transportDeferred = true
    const val monotonicRevisionRequired = true
    const val staleAndDuplicateCommandsRejected = true
    const val expiredAndRevokedPairingsRejected = true
    const val controlledSurfaceCount = 10

    fun defaultState(session: StudyFlowTvControlSession = StudyFlowTvControlSession()): StudyFlowTvControlState =
        StudyFlowTvControlState(
            session = session,
            settings = StudyFlowTvSettingsState(
                pairingState = session.pairingState,
                pairedDeviceName = session.pairedDeviceName,
            ),
        )

    fun reduce(
        state: StudyFlowTvControlState,
        command: StudyFlowTvControlCommand,
        origin: StudyFlowTvControlOrigin,
        nowEpochMillis: Long,
    ): StudyFlowTvControlReduction {
        if (command.commandId == state.lastCommandId) {
            return StudyFlowTvControlReduction(state, applied = false, reason = "duplicate-command")
        }
        val lastIssued = state.lastCommandIssuedAtEpochMillis
        if (lastIssued != null && command.issuedAtEpochMillis < lastIssued) {
            return StudyFlowTvControlReduction(state, applied = false, reason = "stale-command")
        }
        if (origin == StudyFlowTvControlOrigin.PAIRED_PHONE && !state.session.acceptsPairedPhoneCommands(nowEpochMillis)) {
            // Snapshot the nullable cross-module property before checking it. Kotlin cannot smart-cast
            // a public property declared in another module because an overriding/custom getter could
            // theoretically return a different value between reads.
            val expiresAtEpochMillis = state.session.expiresAtEpochMillis
            val expired = state.session.pairingState == StudyFlowTvPairingState.PAIRED &&
                expiresAtEpochMillis != null &&
                nowEpochMillis >= expiresAtEpochMillis
            val rejectedState = if (expired) {
                val revoked = state.session.revoke(nowEpochMillis)
                state.copy(
                    session = revoked,
                    settings = state.settings.copy(
                        pairingState = StudyFlowTvPairingState.REVOKED,
                        pairedDeviceName = null,
                    ),
                    updatedAtEpochMillis = nowEpochMillis,
                )
            } else {
                state
            }
            return StudyFlowTvControlReduction(
                rejectedState,
                applied = false,
                reason = if (expired) "expired-pairing" else "unpaired-or-revoked",
            )
        }

        val transitioned = applyCommand(state, command, nowEpochMillis)
        return StudyFlowTvControlReduction(
            state = transitioned.copy(
                commandRevision = state.commandRevision + 1,
                lastCommandId = command.commandId,
                lastCommandIssuedAtEpochMillis = command.issuedAtEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            ),
            applied = true,
        )
    }

    fun continuityState(state: StudyFlowTvControlState): StudyFlowTvControlContinuityState =
        StudyFlowTvControlContinuityState(
            currentDestination = state.destination.route,
            currentSurface = state.destination.surface,
            activeSurfaceTitle = activeSurfaceTitle(state),
            activeItemId = activeItemId(state),
            playbackState = playbackState(state),
            commandRevision = state.commandRevision,
            lastCommandId = state.lastCommandId,
            lastCommandIssuedAtEpochMillis = state.lastCommandIssuedAtEpochMillis,
            updatedAtEpochMillis = state.updatedAtEpochMillis,
            pairingState = state.session.pairingState,
            sessionExpiresAtEpochMillis = state.session.expiresAtEpochMillis,
        )

    private fun applyCommand(
        state: StudyFlowTvControlState,
        command: StudyFlowTvControlCommand,
        nowEpochMillis: Long,
    ): StudyFlowTvControlState {
        if (command.action == StudyFlowTvControlAction.REVOKE_PAIRING) {
            val revoked = state.session.revoke(nowEpochMillis)
            return state.copy(
                destination = StudyFlowTvDestination.SETTINGS,
                session = revoked,
                settings = StudyFlowTvSettingsSurface.applyAction(
                    state.settings,
                    StudyFlowTvSettingsAction.REVOKE_PAIRING,
                ),
            )
        }
        if (command.action == StudyFlowTvControlAction.OPEN) {
            return applyOpen(state, command)
        }

        return when (command.surface) {
            StudyFlowTvSurface.HOME -> state.copy(destination = StudyFlowTvDestination.HOME)
            StudyFlowTvSurface.TODAY -> state.copy(destination = StudyFlowTvDestination.TODAY)
            StudyFlowTvSurface.STUDY_SESSION -> applyStudySession(state, command.action)
            StudyFlowTvSurface.QUESTION_SET -> applyPractice(state, command)
            StudyFlowTvSurface.REVIEW -> applyReview(state, command.action)
            StudyFlowTvSurface.MOCK_EXAM -> applyMockExam(state, command.action)
            StudyFlowTvSurface.PROGRESS -> applyBackOrStay(state, command.action, StudyFlowTvDestination.PROGRESS)
            StudyFlowTvSurface.SCHEDULE -> applySchedule(state, command.action)
            StudyFlowTvSurface.LIBRARY -> applyLibrary(state, command.action)
            StudyFlowTvSurface.SETTINGS -> applyBackOrStay(state, command.action, StudyFlowTvDestination.SETTINGS)
        }
    }

    private fun applyOpen(
        state: StudyFlowTvControlState,
        command: StudyFlowTvControlCommand,
    ): StudyFlowTvControlState {
        val routeDestination = command.targetId
            ?.let { route -> StudyFlowTvNavigationShell.allDestinations.firstOrNull { it.route == route } }
        if (routeDestination != null) {
            return state.copy(destination = routeDestination)
        }

        return when (command.surface) {
            StudyFlowTvSurface.REVIEW -> {
                val index = state.review.items.indexOfFirst { it.sourceId == command.targetId || it.id.name.lowercase() == command.targetId }
                state.copy(
                    destination = StudyFlowTvDestination.REVIEWS,
                    review = if (index >= 0) {
                        state.review.copy(
                            activeReviewIndex = index,
                            status = StudyFlowTvReviewStatus.REVIEWING,
                            explanationVisible = false,
                        )
                    } else {
                        state.review
                    },
                )
            }
            StudyFlowTvSurface.SCHEDULE -> {
                val index = state.schedule.items.indexOfFirst { it.sourceId == command.targetId || it.id.name.lowercase() == command.targetId }
                state.copy(
                    destination = StudyFlowTvDestination.SCHEDULE,
                    schedule = if (index >= 0) state.schedule.copy(activeItemIndex = index) else state.schedule,
                )
            }
            StudyFlowTvSurface.LIBRARY -> {
                val index = state.library.references.indexOfFirst { it.sourceId == command.targetId || it.id.name.lowercase() == command.targetId }
                state.copy(
                    destination = StudyFlowTvDestination.LIBRARY,
                    library = if (index >= 0) state.library.copy(activeReferenceIndex = index) else state.library,
                )
            }
            StudyFlowTvSurface.SETTINGS -> applySettingsOpen(state, command.targetId)
            else -> state.copy(
                destination = StudyFlowTvNavigationShell.destinationForSurface(command.surface)
                    ?: StudyFlowTvNavigationShell.defaultDestination(),
            )
        }
    }

    private fun applySettingsOpen(state: StudyFlowTvControlState, targetId: String?): StudyFlowTvControlState {
        val action = when (targetId) {
            "manage-pairing" -> StudyFlowTvSettingsAction.MANAGE_PAIRING
            "focus-visibility" -> StudyFlowTvSettingsAction.TOGGLE_HIGH_VISIBILITY_FOCUS
            "about" -> StudyFlowTvSettingsAction.OPEN_ABOUT
            else -> null
        }
        if (action == null) return state.copy(destination = StudyFlowTvDestination.SETTINGS)
        val nextSettings = StudyFlowTvSettingsSurface.applyAction(state.settings, action)
        val nextSession = if (action == StudyFlowTvSettingsAction.MANAGE_PAIRING && state.session.pairingState != StudyFlowTvPairingState.PAIRED) {
            state.session.copy(pairingState = StudyFlowTvPairingState.PAIRING_CODE_VISIBLE)
        } else {
            state.session
        }
        return state.copy(
            destination = StudyFlowTvDestination.SETTINGS,
            session = nextSession,
            settings = nextSettings.copy(
                pairingState = nextSession.pairingState,
                pairedDeviceName = nextSession.pairedDeviceName,
            ),
        )
    }

    private fun applyStudySession(
        state: StudyFlowTvControlState,
        action: StudyFlowTvControlAction,
    ): StudyFlowTvControlState {
        if (action == StudyFlowTvControlAction.BACK) return state.copy(destination = StudyFlowTvDestination.TODAY)
        val localAction = when (action) {
            StudyFlowTvControlAction.START -> StudyFlowTvSessionAction.START
            StudyFlowTvControlAction.PAUSE -> StudyFlowTvSessionAction.PAUSE
            StudyFlowTvControlAction.RESUME -> StudyFlowTvSessionAction.RESUME
            StudyFlowTvControlAction.NEXT -> StudyFlowTvSessionAction.NEXT_SEGMENT
            StudyFlowTvControlAction.COMPLETE -> StudyFlowTvSessionAction.COMPLETE
            else -> null
        }
        return state.copy(
            destination = StudyFlowTvDestination.STUDY_SESSION,
            studySession = localAction?.let { StudyFlowTvStudySessionExperience.applyAction(state.studySession, it) }
                ?: state.studySession,
        )
    }

    private fun applyPractice(
        state: StudyFlowTvControlState,
        command: StudyFlowTvControlCommand,
    ): StudyFlowTvControlState {
        if (command.action == StudyFlowTvControlAction.BACK) return state.copy(destination = StudyFlowTvDestination.TODAY)
        val localAction = when (command.action) {
            StudyFlowTvControlAction.SELECT_ANSWER -> when (command.optionId?.uppercase()) {
                "A" -> StudyFlowTvPracticeAction.SELECT_A
                "B" -> StudyFlowTvPracticeAction.SELECT_B
                "C" -> StudyFlowTvPracticeAction.SELECT_C
                "D" -> StudyFlowTvPracticeAction.SELECT_D
                else -> null
            }
            StudyFlowTvControlAction.PREVIOUS -> StudyFlowTvPracticeAction.PREVIOUS_QUESTION
            StudyFlowTvControlAction.NEXT -> StudyFlowTvPracticeAction.NEXT_QUESTION
            StudyFlowTvControlAction.SHOW_EXPLANATION -> StudyFlowTvPracticeAction.SHOW_EXPLANATION
            StudyFlowTvControlAction.COMPLETE -> StudyFlowTvPracticeAction.FINISH_SET
            else -> null
        }
        return state.copy(
            destination = StudyFlowTvDestination.PRACTICE,
            questionPractice = localAction?.let { StudyFlowTvQuestionPractice.applyAction(state.questionPractice, it) }
                ?: state.questionPractice,
        )
    }

    private fun applyMockExam(
        state: StudyFlowTvControlState,
        action: StudyFlowTvControlAction,
    ): StudyFlowTvControlState {
        if (action == StudyFlowTvControlAction.BACK) return state.copy(destination = StudyFlowTvDestination.HOME)
        val localAction = when (action) {
            StudyFlowTvControlAction.START -> StudyFlowTvMockExamAction.START
            StudyFlowTvControlAction.PAUSE -> StudyFlowTvMockExamAction.PAUSE
            StudyFlowTvControlAction.RESUME -> StudyFlowTvMockExamAction.RESUME
            StudyFlowTvControlAction.NEXT -> StudyFlowTvMockExamAction.NEXT_SECTION
            StudyFlowTvControlAction.MARK_FOR_REVIEW -> StudyFlowTvMockExamAction.MARK_FOR_REVIEW
            StudyFlowTvControlAction.SUBMIT -> StudyFlowTvMockExamAction.SUBMIT
            StudyFlowTvControlAction.REVIEW_RESULTS -> StudyFlowTvMockExamAction.REVIEW_RESULTS
            else -> null
        }
        return state.copy(
            destination = StudyFlowTvDestination.MOCK_EXAM,
            mockExam = localAction?.let { StudyFlowTvMockExamStudio.applyAction(state.mockExam, it) } ?: state.mockExam,
        )
    }

    private fun applyReview(
        state: StudyFlowTvControlState,
        action: StudyFlowTvControlAction,
    ): StudyFlowTvControlState {
        if (action == StudyFlowTvControlAction.BACK) return state.copy(destination = StudyFlowTvDestination.TODAY)
        val localAction = when (action) {
            StudyFlowTvControlAction.START -> StudyFlowTvReviewAction.START_REVIEW
            StudyFlowTvControlAction.NEXT -> StudyFlowTvReviewAction.NEXT_ITEM
            StudyFlowTvControlAction.MARK_FOR_REVIEW -> StudyFlowTvReviewAction.ADD_ERROR_NOTE
            StudyFlowTvControlAction.SHOW_EXPLANATION -> StudyFlowTvReviewAction.SHOW_EXPLANATION
            StudyFlowTvControlAction.COMPLETE -> StudyFlowTvReviewAction.COMPLETE_REVIEW
            else -> null
        }
        return state.copy(
            destination = StudyFlowTvDestination.REVIEWS,
            review = localAction?.let { StudyFlowTvReviewNotebook.applyAction(state.review, it) } ?: state.review,
        )
    }

    private fun applySchedule(
        state: StudyFlowTvControlState,
        action: StudyFlowTvControlAction,
    ): StudyFlowTvControlState {
        val localAction = when (action) {
            StudyFlowTvControlAction.PREVIOUS -> StudyFlowTvScheduleAction.PREVIOUS_ITEM
            StudyFlowTvControlAction.NEXT -> StudyFlowTvScheduleAction.NEXT_ITEM
            else -> null
        }
        return state.copy(
            destination = if (action == StudyFlowTvControlAction.BACK) StudyFlowTvDestination.HOME else StudyFlowTvDestination.SCHEDULE,
            schedule = localAction?.let { StudyFlowTvScheduleSurface.applyAction(state.schedule, it) } ?: state.schedule,
        )
    }

    private fun applyLibrary(
        state: StudyFlowTvControlState,
        action: StudyFlowTvControlAction,
    ): StudyFlowTvControlState {
        val localAction = when (action) {
            StudyFlowTvControlAction.PREVIOUS -> StudyFlowTvLibraryAction.PREVIOUS_REFERENCE
            StudyFlowTvControlAction.NEXT -> StudyFlowTvLibraryAction.NEXT_REFERENCE
            else -> null
        }
        return state.copy(
            destination = if (action == StudyFlowTvControlAction.BACK) StudyFlowTvDestination.HOME else StudyFlowTvDestination.LIBRARY,
            library = localAction?.let { StudyFlowTvLibrarySurface.applyAction(state.library, it) } ?: state.library,
        )
    }

    private fun applyBackOrStay(
        state: StudyFlowTvControlState,
        action: StudyFlowTvControlAction,
        destination: StudyFlowTvDestination,
    ): StudyFlowTvControlState = state.copy(
        destination = if (action == StudyFlowTvControlAction.BACK) StudyFlowTvDestination.HOME else destination,
    )

    private fun activeSurfaceTitle(state: StudyFlowTvControlState): String = when (state.destination) {
        StudyFlowTvDestination.HOME -> "Home"
        StudyFlowTvDestination.TODAY -> "Today"
        StudyFlowTvDestination.STUDY_SESSION -> state.studySession.title
        StudyFlowTvDestination.PRACTICE -> state.questionPractice.title
        StudyFlowTvDestination.REVIEWS -> "Review and error notebook"
        StudyFlowTvDestination.MOCK_EXAM -> state.mockExam.title
        StudyFlowTvDestination.PROGRESS -> "Progress and analytics"
        StudyFlowTvDestination.SCHEDULE -> state.schedule.title
        StudyFlowTvDestination.LIBRARY -> state.library.activeReference.title
        StudyFlowTvDestination.SETTINGS -> "Settings"
    }

    private fun activeItemId(state: StudyFlowTvControlState): String? = when (state.destination) {
        StudyFlowTvDestination.STUDY_SESSION -> state.studySession.activeSegment.id.name.lowercase()
        StudyFlowTvDestination.PRACTICE -> state.questionPractice.activeQuestion.sourceId
        StudyFlowTvDestination.REVIEWS -> state.review.activeReview.sourceId
        StudyFlowTvDestination.MOCK_EXAM -> state.mockExam.activeSection.sourceId
        StudyFlowTvDestination.SCHEDULE -> state.schedule.activeItem.sourceId
        StudyFlowTvDestination.LIBRARY -> state.library.activeReference.sourceId
        else -> null
    }

    private fun playbackState(state: StudyFlowTvControlState): StudyFlowTvPlaybackState = when (state.destination) {
        StudyFlowTvDestination.STUDY_SESSION -> when (state.studySession.status) {
            StudyFlowTvSessionStatus.READY -> StudyFlowTvPlaybackState.READY
            StudyFlowTvSessionStatus.RUNNING -> StudyFlowTvPlaybackState.RUNNING
            StudyFlowTvSessionStatus.PAUSED -> StudyFlowTvPlaybackState.PAUSED
            StudyFlowTvSessionStatus.COMPLETED -> StudyFlowTvPlaybackState.COMPLETED
        }
        StudyFlowTvDestination.MOCK_EXAM -> when (state.mockExam.status) {
            StudyFlowTvMockExamStatus.READY -> StudyFlowTvPlaybackState.READY
            StudyFlowTvMockExamStatus.RUNNING -> StudyFlowTvPlaybackState.RUNNING
            StudyFlowTvMockExamStatus.PAUSED -> StudyFlowTvPlaybackState.PAUSED
            StudyFlowTvMockExamStatus.SUBMITTED -> StudyFlowTvPlaybackState.SUBMITTED
            StudyFlowTvMockExamStatus.RESULTS -> StudyFlowTvPlaybackState.RESULTS
        }
        StudyFlowTvDestination.PRACTICE -> if (state.questionPractice.completed) {
            StudyFlowTvPlaybackState.COMPLETED
        } else {
            StudyFlowTvPlaybackState.VIEWING
        }
        StudyFlowTvDestination.REVIEWS -> if (state.review.status == StudyFlowTvReviewStatus.COMPLETED) {
            StudyFlowTvPlaybackState.COMPLETED
        } else {
            StudyFlowTvPlaybackState.VIEWING
        }
        else -> StudyFlowTvPlaybackState.VIEWING
    }
}
