package com.mikeyphw.studyflow.tv.progress

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvCastState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvNavigationShell

/**
 * Structured Android TV Progress and analytics surface introduced in TVOV12.
 *
 * The TV progress surface is presentation-state only. It exposes weekly progress, accuracy,
 * volume, readiness, and weak-subject summaries with remote/phone navigation actions while avoiding
 * direct Room, repository, feature-module, Compose, AndroidX TV UI, MediaRouter, and Cast SDK use.
 */
enum class StudyFlowTvProgressMetricId {
    WEEKLY_PROGRESS,
    ACCURACY,
    QUESTION_VOLUME,
    READINESS,
}

enum class StudyFlowTvProgressSubjectId {
    CONSTITUTIONAL_LAW,
    ADMINISTRATIVE_LAW,
    PORTUGUESE,
    INFORMATICS,
}

enum class StudyFlowTvProgressAction {
    OPEN_TODAY,
    OPEN_PRACTICE,
    OPEN_REVIEWS,
    OPEN_MOCK_EXAM,
}

data class StudyFlowTvProgressMetric(
    val id: StudyFlowTvProgressMetricId,
    val title: String,
    val valueLabel: String,
    val trendLabel: String,
    val percent: Int,
) {
    init {
        require(title.isNotBlank()) { "TV progress metric title must not be blank." }
        require(valueLabel.isNotBlank()) { "TV progress metric value must not be blank." }
        require(trendLabel.isNotBlank()) { "TV progress metric trend must not be blank." }
        require(percent in 0..100) { "TV progress metric percent must be between 0 and 100." }
    }
}

data class StudyFlowTvProgressSubject(
    val id: StudyFlowTvProgressSubjectId,
    val title: String,
    val readinessPercent: Int,
    val weakArea: String,
    val recommendedDestination: StudyFlowTvDestination,
) {
    init {
        require(title.isNotBlank()) { "TV progress subject title must not be blank." }
        require(readinessPercent in 0..100) { "TV progress readiness must be between 0 and 100." }
        require(weakArea.isNotBlank()) { "TV progress weak area must not be blank." }
    }
}

data class StudyFlowTvProgressSnapshot(
    val examName: String,
    val weekLabel: String,
    val metrics: List<StudyFlowTvProgressMetric>,
    val subjects: List<StudyFlowTvProgressSubject>,
) {
    init {
        require(examName.isNotBlank()) { "TV progress exam name must not be blank." }
        require(weekLabel.isNotBlank()) { "TV progress week label must not be blank." }
        require(metrics.isNotEmpty()) { "TV progress metrics must not be empty." }
        require(subjects.isNotEmpty()) { "TV progress subjects must not be empty." }
    }

    val averageReadinessPercent: Int = subjects.sumOf { it.readinessPercent } / subjects.size
    val weakestSubject: StudyFlowTvProgressSubject = subjects.minBy { it.readinessPercent }
    val weeklyProgressPercent: Int = metrics.first { it.id == StudyFlowTvProgressMetricId.WEEKLY_PROGRESS }.percent
}

object StudyFlowTvProgressAnalytics {
    const val overlay = "TVOV12"
    const val ownsProgressAnalyticsSurface = true
    const val weeklyProgressAccuracyVolumeReadinessVisible = true
    const val weakSubjectSummaryVisible = true
    const val remoteCanOpenRecommendedNextActions = true
    const val pairedPhoneCanOpenProgressDestinations = true
    const val emitsCastStateWithoutMirroring = true
    const val usesSharedControlProtocol = true
    const val metricCount = 4
    const val subjectCount = 4
    const val actionCount = 4
    const val metricRow = 0
    const val subjectRow = 2
    const val actionRow = 4

    fun defaultSnapshot(): StudyFlowTvProgressSnapshot = StudyFlowTvProgressSnapshot(
        examName = "TRT Técnico Judiciário",
        weekLabel = "Week 12 readiness",
        metrics = listOf(
            StudyFlowTvProgressMetric(
                id = StudyFlowTvProgressMetricId.WEEKLY_PROGRESS,
                title = "Weekly progress",
                valueLabel = "72%",
                trendLabel = "on plan",
                percent = 72,
            ),
            StudyFlowTvProgressMetric(
                id = StudyFlowTvProgressMetricId.ACCURACY,
                title = "Accuracy",
                valueLabel = "81%",
                trendLabel = "up 6 points",
                percent = 81,
            ),
            StudyFlowTvProgressMetric(
                id = StudyFlowTvProgressMetricId.QUESTION_VOLUME,
                title = "Question volume",
                valueLabel = "180",
                trendLabel = "week target 220",
                percent = 82,
            ),
            StudyFlowTvProgressMetric(
                id = StudyFlowTvProgressMetricId.READINESS,
                title = "Readiness",
                valueLabel = "68%",
                trendLabel = "needs review pass",
                percent = 68,
            ),
        ),
        subjects = listOf(
            StudyFlowTvProgressSubject(
                id = StudyFlowTvProgressSubjectId.CONSTITUTIONAL_LAW,
                title = "Constitutional Law",
                readinessPercent = 76,
                weakArea = "rights and guarantees",
                recommendedDestination = StudyFlowTvDestination.PRACTICE,
            ),
            StudyFlowTvProgressSubject(
                id = StudyFlowTvProgressSubjectId.ADMINISTRATIVE_LAW,
                title = "Administrative Law",
                readinessPercent = 54,
                weakArea = "administrative acts",
                recommendedDestination = StudyFlowTvDestination.REVIEWS,
            ),
            StudyFlowTvProgressSubject(
                id = StudyFlowTvProgressSubjectId.PORTUGUESE,
                title = "Portuguese",
                readinessPercent = 63,
                weakArea = "agreement rules",
                recommendedDestination = StudyFlowTvDestination.REVIEWS,
            ),
            StudyFlowTvProgressSubject(
                id = StudyFlowTvProgressSubjectId.INFORMATICS,
                title = "Informatics",
                readinessPercent = 79,
                weakArea = "security basics",
                recommendedDestination = StudyFlowTvDestination.MOCK_EXAM,
            ),
        ),
    )

    val actions: List<StudyFlowTvProgressAction> = listOf(
        StudyFlowTvProgressAction.OPEN_TODAY,
        StudyFlowTvProgressAction.OPEN_PRACTICE,
        StudyFlowTvProgressAction.OPEN_REVIEWS,
        StudyFlowTvProgressAction.OPEN_MOCK_EXAM,
    )

    fun focusItems(snapshot: StudyFlowTvProgressSnapshot): List<StudyFlowTvFocusItem> =
        snapshot.metrics.mapIndexed { index, metric ->
            StudyFlowTvFocusItem(
                id = "progress-metric-${metric.id.name.lowercase()}",
                role = StudyFlowTvFocusRole.CONTENT_CARD,
                row = metricRow + (index / 2),
                column = 1 + (index % 2),
            )
        } + snapshot.subjects.mapIndexed { index, subject ->
            StudyFlowTvFocusItem(
                id = "progress-subject-${subject.id.name.lowercase()}",
                role = StudyFlowTvFocusRole.CONTENT_CARD,
                row = subjectRow + (index / 2),
                column = 1 + (index % 2),
            )
        } + actions.mapIndexed { index, action ->
            StudyFlowTvFocusItem(
                id = "progress-action-${action.name.lowercase()}",
                role = StudyFlowTvFocusRole.PRIMARY_ACTION,
                row = actionRow,
                column = 1 + index,
            )
        }

    fun destinationForAction(action: StudyFlowTvProgressAction): StudyFlowTvDestination = when (action) {
        StudyFlowTvProgressAction.OPEN_TODAY -> StudyFlowTvDestination.TODAY
        StudyFlowTvProgressAction.OPEN_PRACTICE -> StudyFlowTvDestination.PRACTICE
        StudyFlowTvProgressAction.OPEN_REVIEWS -> StudyFlowTvDestination.REVIEWS
        StudyFlowTvProgressAction.OPEN_MOCK_EXAM -> StudyFlowTvDestination.MOCK_EXAM
    }

    fun commandForAction(
        action: StudyFlowTvProgressAction,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand = StudyFlowTvNavigationShell.openCommandFor(
        destination = destinationForAction(action),
        commandId = commandId,
        issuedAtEpochMillis = issuedAtEpochMillis,
    )

    fun castStateFor(
        snapshot: StudyFlowTvProgressSnapshot,
        castId: String,
        updatedAtEpochMillis: Long,
    ): StudyFlowTvCastState = StudyFlowTvCastState(
        castId = castId,
        surface = StudyFlowTvSurface.PROGRESS,
        title = snapshot.examName,
        subtitle = "${snapshot.weekLabel} · weakest ${snapshot.weakestSubject.title}",
        progressPercent = snapshot.averageReadinessPercent,
        activeItemId = snapshot.weakestSubject.id.name.lowercase(),
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}
