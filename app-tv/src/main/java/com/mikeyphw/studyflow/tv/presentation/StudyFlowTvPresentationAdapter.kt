package com.mikeyphw.studyflow.tv.presentation

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationAvailability
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationReceipt
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.control.StudyFlowTvControlState
import com.mikeyphw.studyflow.tv.library.StudyFlowTvLibraryReference
import com.mikeyphw.studyflow.tv.library.StudyFlowTvLibraryReferenceId
import com.mikeyphw.studyflow.tv.library.StudyFlowTvLibraryReferenceKind
import com.mikeyphw.studyflow.tv.library.StudyFlowTvLibrarySnapshot
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamSection
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamSectionId
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamState
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvPracticeChoice
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvPracticeChoiceId
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvPracticeQuestion
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvPracticeQuestionId
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvQuestionPracticeState
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressMetric
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvGoal
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressMetricId
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressSnapshot
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressSubject
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressSubjectId
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewId
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewItem
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewState
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewStatus
import com.mikeyphw.studyflow.tv.schedule.StudyFlowTvScheduleItem
import com.mikeyphw.studyflow.tv.schedule.StudyFlowTvScheduleItemId
import com.mikeyphw.studyflow.tv.schedule.StudyFlowTvScheduleItemKind
import com.mikeyphw.studyflow.tv.schedule.StudyFlowTvScheduleSnapshot
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionSegment
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionSegmentId
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionState
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionStatus
import com.mikeyphw.studyflow.tv.settings.StudyFlowTvSettingsState
import com.mikeyphw.studyflow.tv.today.StudyFlowTvTodaySnapshot

/**
 * TV-side adapter from the shared typed presentation schema into the existing native TV view model.
 * No repository or Android phone dependency is introduced into app-tv.
 */
object StudyFlowTvPresentationAdapter {
    const val overlay = "TVOV21"
    const val consumesTypedPresentationOnly = true
    const val runtimeFixtureDefaultsForbidden = true
    const val databaseAndRepositoryDependenciesForbidden = true

    fun waitingState(existing: StudyFlowTvControlState? = null): StudyFlowTvControlState {
        val waiting = if (existing == null) {
            StudyFlowTvControlState(
                today = waitingToday(),
                studySession = waitingSession(),
                questionPractice = waitingPractice(),
                mockExam = waitingMockExam(),
                review = waitingReview(),
                progress = waitingProgress(),
                schedule = waitingSchedule(),
                library = waitingLibrary(),
                settings = StudyFlowTvSettingsState(),
            )
        } else {
            existing.copy(
                today = waitingToday(),
                studySession = waitingSession(),
                questionPractice = waitingPractice(),
                mockExam = waitingMockExam(),
                review = waitingReview(),
                progress = waitingProgress(),
                schedule = waitingSchedule(),
                library = waitingLibrary(),
            )
        }
        return waiting
    }

    fun applySnapshot(
        current: StudyFlowTvControlState,
        snapshot: StudyFlowTvPresentationSnapshot,
    ): StudyFlowTvControlState {
        if (snapshot.availability != StudyFlowTvPresentationAvailability.READY) return waitingState(current)
        return current.copy(
            today = snapshot.today?.let(::todayState) ?: waitingToday(),
            studySession = snapshot.studySession?.let { sessionState(it, snapshot.generatedAtEpochMillis) } ?: waitingSession(),
            questionPractice = snapshot.questionSet?.let(::practiceState) ?: waitingPractice(),
            mockExam = snapshot.mockExam?.let { mockExamState(it, snapshot.generatedAtEpochMillis) } ?: waitingMockExam(),
            review = snapshot.review?.let(::reviewState) ?: waitingReview(),
            progress = snapshot.progress?.let(::progressState) ?: waitingProgress(),
            schedule = snapshot.schedule?.let(::scheduleState) ?: waitingSchedule(),
            library = snapshot.library?.let(::libraryState) ?: waitingLibrary(),
        )
    }

    private fun todayState(source: com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvTodayPresentation): StudyFlowTvTodaySnapshot {
        val next = source.nextActivity
        return StudyFlowTvTodaySnapshot(
            examName = source.planTitle,
            dateLabel = "Today",
            nextBlockTitle = next?.title ?: "No scheduled study block",
            nextBlockMinutes = next?.plannedDurationMinutes ?: 1,
            dueReviewCount = source.dueReviewCount,
            practiceQuestionCount = source.availableQuestionCount,
            weeklyProgressPercent = source.weeklyProgressPercent,
            mockExamScheduled = source.mockExamScheduled,
        )
    }

    private fun sessionState(
        source: com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvStudySessionPresentation,
        projectedAtEpochMillis: Long,
    ): StudyFlowTvSessionState =
        StudyFlowTvSessionState(
            sessionId = source.sessionId,
            title = source.title,
            subject = source.subjectLabel,
            status = when (source.status.uppercase()) {
                "RUNNING" -> StudyFlowTvSessionStatus.RUNNING
                "PAUSED" -> StudyFlowTvSessionStatus.PAUSED
                "COMPLETED", "CANCELLED" -> StudyFlowTvSessionStatus.COMPLETED
                else -> StudyFlowTvSessionStatus.READY
            },
            segments = listOf(
                StudyFlowTvSessionSegment(
                    id = StudyFlowTvSessionSegmentId.FOCUS_BLOCK,
                    minutes = source.targetMinutes,
                    row = 0,
                    column = 1,
                ),
            ),
            activeSegmentIndex = 0,
            elapsedMinutes = source.elapsedMinutes.coerceAtMost(source.targetMinutes),
            targetMinutes = source.targetMinutes,
            projectedAtEpochMillis = projectedAtEpochMillis,
        )

    private fun practiceState(source: com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvQuestionSetPresentation): StudyFlowTvQuestionPracticeState {
        val questionSlots = StudyFlowTvPracticeQuestionId.entries
        val projected = source.questions.take(questionSlots.size).mapIndexedNotNull { index, question ->
            val choices = question.choices.take(StudyFlowTvPracticeChoiceId.entries.size).mapIndexed { choiceIndex, choice ->
                StudyFlowTvPracticeChoice(StudyFlowTvPracticeChoiceId.entries[choiceIndex], choice.label)
            }
            val correctIndex = question.choices.indexOfFirst { it.optionId == question.correctOptionId }
            if (correctIndex !in choices.indices || choices.size < 2) return@mapIndexedNotNull null
            StudyFlowTvPracticeQuestion(
                id = questionSlots[index],
                sourceId = question.questionId,
                prompt = question.prompt,
                choices = choices,
                correctChoiceId = StudyFlowTvPracticeChoiceId.entries[correctIndex],
                explanation = question.explanation.ifBlank { "Explanation is available from the StudyFlow source." },
                row = index / 2,
                column = 1 + (index % 2),
            )
        }
        if (projected.isEmpty()) return waitingPractice()
        val sourceIndexById = projected.mapIndexed { index, question -> question.sourceId to index }.toMap()
        val selectedChoices = source.answers.mapNotNull { answer ->
            val questionIndex = sourceIndexById[answer.questionId] ?: return@mapNotNull null
            val slot = projected[questionIndex]
            val sourceQuestion = source.questions.firstOrNull { it.questionId == answer.questionId } ?: return@mapNotNull null
            val optionIndex = sourceQuestion.choices.indexOfFirst { it.optionId == answer.chosenOptionId }
            val choice = StudyFlowTvPracticeChoiceId.entries.getOrNull(optionIndex) ?: return@mapNotNull null
            slot.id to choice
        }.toMap()
        val activeIndex = source.currentQuestionId
            ?.let(sourceIndexById::get)
            ?: projected.indexOfFirst { it.id !in selectedChoices }.takeIf { it >= 0 }
            ?: projected.lastIndex
        return StudyFlowTvQuestionPracticeState(
            questionSetId = source.questionSetId,
            title = source.title,
            subject = source.subjectLabel,
            questions = projected,
            activeQuestionIndex = activeIndex,
            selectedChoices = selectedChoices,
            explanationVisible = source.submitted,
            completed = source.submitted,
            canPersistActions = source.started && !source.submitted,
            availabilityReason = if (source.started && !source.submitted) null else "Practice actions require an open canonical attempt.",
        )
    }

    private fun mockExamState(
        source: com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMockExamPresentation,
        projectedAtEpochMillis: Long,
    ): StudyFlowTvMockExamState {
        val slots = StudyFlowTvMockExamSectionId.entries
        val projectedSections = source.sections.take(slots.size).mapIndexed { index, section ->
            StudyFlowTvMockExamSection(
                id = slots[index],
                sourceId = section.sectionId,
                title = section.title,
                questionCount = section.questionCount,
                minutes = section.totalMinutes,
                row = index / 2,
                column = 1 + (index % 2),
                answeredCount = section.answeredCount,
                correctCount = section.correctCount,
            )
        }.ifEmpty {
            listOf(
                StudyFlowTvMockExamSection(
                    id = StudyFlowTvMockExamSectionId.GENERAL_KNOWLEDGE,
                    sourceId = source.mockExamId,
                    title = source.title,
                    questionCount = source.questionCount,
                    minutes = source.totalMinutes,
                    row = 0,
                    column = 1,
                    answeredCount = source.answeredCount,
                    correctCount = source.correctCount,
                ),
            )
        }
        val activeIndex = source.currentSectionId
            ?.let { id -> projectedSections.indexOfFirst { it.sourceId == id } }
            ?.takeIf { it >= 0 }
            ?: projectedSections.indexOfFirst { it.answeredCount < it.questionCount }.takeIf { it >= 0 }
            ?: projectedSections.lastIndex
        return StudyFlowTvMockExamState(
            examId = source.mockExamId,
            title = source.title,
            sections = projectedSections,
            status = when (source.status.uppercase()) {
                "RUNNING" -> StudyFlowTvMockExamStatus.RUNNING
                "PAUSED" -> StudyFlowTvMockExamStatus.PAUSED
                "SUBMITTED" -> StudyFlowTvMockExamStatus.SUBMITTED
                "RESULTS" -> StudyFlowTvMockExamStatus.RESULTS
                else -> StudyFlowTvMockExamStatus.READY
            },
            activeSectionIndex = activeIndex,
            elapsedMinutes = source.elapsedMinutes,
            correctAnswerCount = source.correctCount,
            answeredQuestionCount = source.answeredCount,
            canPersistActions = source.canPersistActions,
            projectedAtEpochMillis = projectedAtEpochMillis,
        )
    }

    private fun reviewState(source: com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvReviewPresentation): StudyFlowTvReviewState {
        val slots = StudyFlowTvReviewId.entries
        val items = source.items.take(slots.size).mapIndexed { index, item ->
            StudyFlowTvReviewItem(
                id = slots[index],
                sourceId = item.reviewItemId,
                title = item.title,
                subject = item.subjectLabel,
                reason = item.reason,
                confidencePercent = item.confidencePercent,
                dueNow = item.status.equals("DUE", ignoreCase = true),
                errorEntryId = item.errorEntryId,
                errorCategory = item.errorCategory,
                questionId = item.questionId,
            )
        }
        if (items.isEmpty()) return waitingReview()
        return StudyFlowTvReviewState(
            status = StudyFlowTvReviewStatus.READY,
            activeReviewIndex = 0,
            items = items,
        )
    }

    private fun progressState(source: com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvProgressPresentation): StudyFlowTvProgressSnapshot {
        val metrics = listOf(
            StudyFlowTvProgressMetric(
                StudyFlowTvProgressMetricId.WEEKLY_PROGRESS,
                "Weekly progress",
                "${source.weeklyProgressPercent}%",
                "${source.studiedMinutesLastSevenDays}/${source.weeklyGoalMinutes} min",
                source.weeklyProgressPercent,
            ),
            StudyFlowTvProgressMetric(
                StudyFlowTvProgressMetricId.ACCURACY,
                "Accuracy",
                "${source.questionAccuracyPercent}%",
                "${source.answeredQuestions} answered",
                source.questionAccuracyPercent,
            ),
            StudyFlowTvProgressMetric(
                StudyFlowTvProgressMetricId.QUESTION_VOLUME,
                "Question volume",
                source.answeredQuestions.toString(),
                "${source.dueReviewCount} reviews due",
                source.questionAccuracyPercent,
            ),
            StudyFlowTvProgressMetric(
                StudyFlowTvProgressMetricId.READINESS,
                "Content progress",
                "${source.contentProgressPercent}%",
                "consistency ${source.consistencyScorePercent}%",
                source.contentProgressPercent,
            ),
        )
        val subjectSlots = StudyFlowTvProgressSubjectId.entries
        val weakAreas = source.weakAreas.take(subjectSlots.size).mapIndexed { index, area ->
            StudyFlowTvProgressSubject(
                id = subjectSlots[index],
                title = area.label,
                readinessPercent = area.masteryPercent,
                weakArea = "progress ${area.progressPercent}% · priority ${area.priority}",
                recommendedDestination = if (area.masteryPercent < 60) StudyFlowTvDestination.REVIEWS else StudyFlowTvDestination.PRACTICE,
            )
        }.ifEmpty {
            listOf(
                StudyFlowTvProgressSubject(
                    id = StudyFlowTvProgressSubjectId.CONSTITUTIONAL_LAW,
                    title = "No weak areas",
                    readinessPercent = source.contentProgressPercent,
                    weakArea = "StudyFlow has no weak-area signal for this snapshot",
                    recommendedDestination = StudyFlowTvDestination.TODAY,
                ),
            )
        }
        return StudyFlowTvProgressSnapshot(
            examName = source.planTitle,
            weekLabel = "Current StudyFlow evidence",
            metrics = metrics,
            subjects = weakAreas,
            goals = source.goals.map { goal ->
                StudyFlowTvGoal(
                    sourceId = goal.goalId,
                    title = goal.title,
                    goalType = goal.goalType,
                    progressPercent = goal.progressPercent,
                    completed = goal.completed,
                    paused = goal.paused,
                    canComplete = goal.canComplete,
                )
            },
        )
    }

    private fun scheduleState(source: com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSchedulePresentation): StudyFlowTvScheduleSnapshot {
        val slots = StudyFlowTvScheduleItemId.entries
        val items = source.items.take(slots.size).mapIndexed { index, item ->
            StudyFlowTvScheduleItem(
                id = slots[index],
                sourceId = item.activityId,
                title = item.title,
                timeLabel = formatScheduleTimeLabel(item.scheduledStartEpochMillis, item.plannedDurationMinutes),
                kind = when (item.activityType.uppercase()) {
                    "REVIEW", "FLASHCARDS" -> StudyFlowTvScheduleItemKind.REVIEW_WINDOW
                    "QUESTIONS" -> StudyFlowTvScheduleItemKind.PRACTICE_SET
                    "MOCK_EXAM" -> StudyFlowTvScheduleItemKind.MOCK_EXAM
                    else -> StudyFlowTvScheduleItemKind.STUDY_BLOCK
                },
                destination = destinationFor(item.targetSurface),
                row = index / 2,
                column = 1 + (index % 2),
                scheduledStartEpochMillis = item.scheduledStartEpochMillis,
                plannedDurationMinutes = item.plannedDurationMinutes,
            )
        }
        if (items.isEmpty()) return waitingSchedule()
        return StudyFlowTvScheduleSnapshot(
            scheduleId = source.scheduleId,
            title = source.title,
            weekLabel = "Read-only StudyFlow schedule",
            items = items,
        )
    }

    private fun formatScheduleTimeLabel(
        scheduledStartEpochMillis: Long?,
        plannedDurationMinutes: Int,
    ): String = scheduledStartEpochMillis?.let { millis ->
        val formatted = Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault()))
        "$formatted · $plannedDurationMinutes min"
    } ?: "Unscheduled · $plannedDurationMinutes min"

    private fun libraryState(source: com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvLibraryPresentation): StudyFlowTvLibrarySnapshot {
        val slots = StudyFlowTvLibraryReferenceId.entries
        val references = source.references.take(slots.size).mapIndexed { index, reference ->
            StudyFlowTvLibraryReference(
                id = slots[index],
                sourceId = reference.referenceId,
                title = reference.title,
                subject = reference.subjectLabel,
                sourceLabel = reference.sourceLabel,
                excerpt = reference.excerpt,
                kind = when (reference.referenceKind.uppercase()) {
                    "NOTE" -> StudyFlowTvLibraryReferenceKind.SUMMARY
                    "BOOK", "PDF", "WEBSITE", "LOCAL_FILE" -> StudyFlowTvLibraryReferenceKind.LEGISLATION
                    else -> StudyFlowTvLibraryReferenceKind.CHECKLIST
                },
                relatedDestination = destinationFor(reference.relatedSurface),
                viewer = reference.viewer,
                row = index / 2,
                column = 1 + (index % 2),
            )
        }
        if (references.isEmpty()) return waitingLibrary()
        return StudyFlowTvLibrarySnapshot(
            libraryId = source.libraryId,
            title = source.title,
            collectionLabel = "Projected from the active StudyFlow plan",
            references = references,
        )
    }

    private fun destinationFor(surface: StudyFlowTvSurface): StudyFlowTvDestination = when (surface) {
        StudyFlowTvSurface.HOME -> StudyFlowTvDestination.HOME
        StudyFlowTvSurface.TODAY -> StudyFlowTvDestination.TODAY
        StudyFlowTvSurface.STUDY_SESSION -> StudyFlowTvDestination.STUDY_SESSION
        StudyFlowTvSurface.QUESTION_SET -> StudyFlowTvDestination.PRACTICE
        StudyFlowTvSurface.REVIEW -> StudyFlowTvDestination.REVIEWS
        StudyFlowTvSurface.MOCK_EXAM -> StudyFlowTvDestination.MOCK_EXAM
        StudyFlowTvSurface.PROGRESS -> StudyFlowTvDestination.PROGRESS
        StudyFlowTvSurface.SCHEDULE -> StudyFlowTvDestination.SCHEDULE
        StudyFlowTvSurface.LIBRARY -> StudyFlowTvDestination.LIBRARY
        StudyFlowTvSurface.SETTINGS -> StudyFlowTvDestination.HOME
    }

    private fun waitingToday() = StudyFlowTvTodaySnapshot(
        examName = "StudyFlow",
        dateLabel = "Waiting",
        nextBlockTitle = "Waiting for StudyFlow data",
        nextBlockMinutes = 1,
        dueReviewCount = 0,
        practiceQuestionCount = 0,
        weeklyProgressPercent = 0,
        mockExamScheduled = false,
    )

    private fun waitingSession() = StudyFlowTvSessionState(
        sessionId = "waiting-session",
        title = "Waiting for StudyFlow data",
        subject = "No active presentation snapshot",
        status = StudyFlowTvSessionStatus.READY,
        segments = listOf(StudyFlowTvSessionSegment(StudyFlowTvSessionSegmentId.FOCUS_BLOCK, 1, 0, 1)),
        activeSegmentIndex = 0,
        elapsedMinutes = 0,
        targetMinutes = 1,
    )

    private fun waitingPractice() = StudyFlowTvQuestionPracticeState(
        questionSetId = "waiting-question-set",
        title = "Waiting for StudyFlow data",
        subject = "No question set received",
        questions = listOf(
            StudyFlowTvPracticeQuestion(
                id = StudyFlowTvPracticeQuestionId.QUESTION_ONE,
                sourceId = "waiting-question",
                prompt = "Pair or cast from the StudyFlow phone app to load real questions.",
                choices = listOf(
                    StudyFlowTvPracticeChoice(StudyFlowTvPracticeChoiceId.A, "Waiting"),
                    StudyFlowTvPracticeChoice(StudyFlowTvPracticeChoiceId.B, "No data yet"),
                ),
                correctChoiceId = StudyFlowTvPracticeChoiceId.A,
                explanation = "No StudyFlow question snapshot has been received.",
                row = 0,
                column = 1,
            ),
        ),
        canPersistActions = false,
        availabilityReason = "Waiting for a canonical question-set projection.",
    )

    private fun waitingMockExam() = StudyFlowTvMockExamState(
        examId = "waiting-mock",
        title = "Waiting for StudyFlow data",
        sections = listOf(
            StudyFlowTvMockExamSection(
                id = StudyFlowTvMockExamSectionId.GENERAL_KNOWLEDGE,
                sourceId = "waiting-mock",
                title = "No mock exam received",
                questionCount = 1,
                minutes = 1,
                row = 0,
                column = 1,
            ),
        ),
        canPersistActions = false,
    )

    private fun waitingReview() = StudyFlowTvReviewState(
        status = StudyFlowTvReviewStatus.READY,
        activeReviewIndex = 0,
        items = listOf(
            StudyFlowTvReviewItem(
                id = StudyFlowTvReviewId.CONSTITUTION_FLASHCARDS,
                sourceId = "waiting-review",
                title = "Waiting for StudyFlow data",
                subject = "No review queue received",
                reason = "Pair or cast from the phone app",
                confidencePercent = 0,
                dueNow = false,
            ),
        ),
    )

    private fun waitingProgress() = StudyFlowTvProgressSnapshot(
        examName = "StudyFlow",
        weekLabel = "Waiting for data",
        metrics = listOf(
            StudyFlowTvProgressMetric(StudyFlowTvProgressMetricId.WEEKLY_PROGRESS, "Weekly progress", "—", "waiting", 0),
            StudyFlowTvProgressMetric(StudyFlowTvProgressMetricId.ACCURACY, "Accuracy", "—", "waiting", 0),
            StudyFlowTvProgressMetric(StudyFlowTvProgressMetricId.QUESTION_VOLUME, "Question volume", "—", "waiting", 0),
            StudyFlowTvProgressMetric(StudyFlowTvProgressMetricId.READINESS, "Readiness", "—", "waiting", 0),
        ),
        subjects = listOf(
            StudyFlowTvProgressSubject(
                StudyFlowTvProgressSubjectId.CONSTITUTIONAL_LAW,
                "No weak-area data",
                0,
                "Pair or cast from the phone app",
                StudyFlowTvDestination.TODAY,
            ),
        ),
    )

    private fun waitingSchedule() = StudyFlowTvScheduleSnapshot(
        scheduleId = "waiting-schedule",
        title = "Waiting for StudyFlow data",
        weekLabel = "No schedule received",
        items = listOf(
            StudyFlowTvScheduleItem(
                id = StudyFlowTvScheduleItemId.FOCUS_BLOCK,
                sourceId = "waiting-schedule-item",
                title = "No schedule received",
                timeLabel = "Pair or cast from the phone app",
                kind = StudyFlowTvScheduleItemKind.BUFFER,
                destination = StudyFlowTvDestination.TODAY,
                row = 0,
                column = 1,
            ),
        ),
    )

    private fun waitingLibrary() = StudyFlowTvLibrarySnapshot(
        libraryId = "waiting-library",
        title = "Waiting for StudyFlow data",
        collectionLabel = "No library snapshot received",
        references = listOf(
            StudyFlowTvLibraryReference(
                id = StudyFlowTvLibraryReferenceId.CONSTITUTION_ARTICLE_FIVE,
                sourceId = "waiting-library-reference",
                title = "No reference material received",
                subject = "StudyFlow",
                sourceLabel = "Phone-owned library",
                excerpt = "Pair or cast from the StudyFlow phone app to display real reference material.",
                kind = StudyFlowTvLibraryReferenceKind.CHECKLIST,
                relatedDestination = StudyFlowTvDestination.HOME,
                row = 0,
                column = 1,
            ),
        ),
    )
}

/** Monotonic TV-side holder for presentation snapshots; transport will feed it in a later overlay. */
class StudyFlowTvPresentationInbox {
    var current: StudyFlowTvPresentationSnapshot? = null
        private set

    fun apply(snapshot: StudyFlowTvPresentationSnapshot): Boolean {
        val previous = current
        if (previous != null && snapshot.snapshotId == previous.snapshotId) return false
        // OV18: snapshot freshness is identity/revision based, not wall-clock based. The paired
        // phone and TV can be unsynchronized, so an older generatedAt value must not discard a
        // distinct canonical presentation snapshot.
        current = snapshot
        return true
    }

    fun clear() {
        current = null
    }

    fun receipt(): StudyFlowTvPresentationReceipt? = current?.let { snapshot ->
        StudyFlowTvPresentationReceipt(
            snapshotId = snapshot.snapshotId,
            generatedAtEpochMillis = snapshot.generatedAtEpochMillis,
        )
    }

    fun isReady(): Boolean = current?.availability == StudyFlowTvPresentationAvailability.READY
}
