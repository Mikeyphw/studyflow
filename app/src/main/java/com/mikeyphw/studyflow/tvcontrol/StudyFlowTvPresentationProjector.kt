package com.mikeyphw.studyflow.tvcontrol

import com.mikeyphw.studyflow.core.domain.GoalEvidence
import com.mikeyphw.studyflow.core.domain.GoalProgressCalculator
import com.mikeyphw.studyflow.core.domain.PracticeExecutionEngine
import com.mikeyphw.studyflow.core.domain.StudyGoalProgressEngine
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.domain.StudySessionTimer
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvActivityPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvLibraryPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvLibraryReferencePresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMaterialViewerKind
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMaterialViewerPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMockExamPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMockExamSectionPresentation
import com.mikeyphw.studyflow.core.domain.ExamBlueprintCodec
import com.mikeyphw.studyflow.core.domain.PracticeAttemptCodec
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationAvailability
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationProtocol
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvProgressPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvQuestionAnswerPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvQuestionChoicePresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvQuestionPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvQuestionSetPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvReviewItemPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvReviewPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvScheduleItemPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSchedulePresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvStudySessionPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvTodayPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvTransferableMaterialSource
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWeakAreaPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvGoalPresentation

/** Canonical phone/data-owner input for one presentation projection. */
data class StudyFlowTvPresentationProjectionInput(
    val snapshotId: String,
    val generatedAtEpochMillis: Long,
    val requestedPlanId: String,
    val plan: StudyPlan?,
    val syllabus: StudySyllabus,
    val schedule: StudySchedule,
    val sessions: List<StudySession>,
    val questions: List<Question>,
    val tests: List<StudyTest>,
    val mockExams: List<MockExam>,
    val reviewItems: List<ReviewItem>,
    val flashcards: List<Flashcard>,
    val errorEntries: List<ErrorEntry>,
    val analytics: StudyAnalyticsSnapshot,
    val materials: List<StudyMaterial>,
    val goals: List<StudyGoal> = emptyList(),
)

/**
 * TVOV21 real-data projection boundary.
 *
 * This code lives in the phone application because that process owns the repositories. It maps
 * canonical domain models into the data-only :core:tvcontrol presentation schema. It does not
 * perform transport, pairing, database reads, or TV rendering.
 */
object StudyFlowTvPresentationProjector {
    const val overlay = "OV20"
    const val projectsCanonicalDomainModels = true
    const val databaseRowsNeverCrossBoundary = true
    const val arbitraryUiTreesNeverCrossBoundary = true
    const val transportDeferred = false
    const val phoneLocalMaterialUrisForbidden = true
    const val presentationFeedsFailureIsolated = true

    fun project(input: StudyFlowTvPresentationProjectionInput): StudyFlowTvPresentationSnapshot {
        val plan = input.plan ?: return StudyFlowTvPresentationSnapshot.waitingForPlan(
            studyPlanId = input.requestedPlanId,
            generatedAtEpochMillis = input.generatedAtEpochMillis,
        )
        val failedFeeds = linkedSetOf<String>()
        val subjectNames = isolatePresentationFeed("subjects", failedFeeds) {
            input.syllabus.subjects.associate { it.id to it.name }
        } ?: emptyMap()
        val pendingActivities = isolatePresentationFeed("today", failedFeeds) {
            input.schedule.plannedActivities
            .filter { it.status == PlannedActivityStatus.PLANNED || it.status == PlannedActivityStatus.IN_PROGRESS }
            .sortedWith(
                compareBy<PlannedActivity> { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
                    .thenBy { it.title },
            )
        } ?: emptyList()
        val nextActivity = pendingActivities.firstOrNull()
        val weeklyGoalMinutes = plan.weeklyGoalHours * 60
        val weeklyProgress = percent(input.analytics.studiedMinutesLastSevenDays, weeklyGoalMinutes)
        val goalEvidence = GoalEvidence(
            sessions = input.sessions,
            tests = input.tests,
            mockExams = input.mockExams,
            topics = input.syllabus.topics,
        )
        val projectedGoals = isolatePresentationFeed("goals", failedFeeds) {
            input.goals
                .asSequence()
                .map { goal ->
                    StudyGoalProgressEngine.evaluate(goal, goalEvidence, input.generatedAtEpochMillis)
                }
                .sortedWith(
                    compareBy<StudyGoal> { it.archived }
                        .thenBy { it.completedAtEpochMillis != null }
                        .thenBy { it.title },
                )
                .take(StudyFlowTvPresentationProtocol.maxTvGoals)
                .map { goal ->
                    StudyFlowTvGoalPresentation(
                        goalId = goal.id.value,
                        title = goal.title,
                        goalType = goal.type.name,
                        currentValue = goal.currentValue,
                        targetValue = goal.targetValue,
                        progressPercent = GoalProgressCalculator.goalPercent(goal),
                        completed = goal.completedAtEpochMillis != null,
                        paused = goal.archived,
                        canComplete = goal.type == GoalType.CUSTOM && goal.completedAtEpochMillis == null && !goal.archived,
                    )
                }
                .toList()
        } ?: emptyList()

        return StudyFlowTvPresentationSnapshot(
            snapshotId = input.snapshotId,
            studyPlanId = plan.id.value,
            planTitle = plan.name,
            generatedAtEpochMillis = input.generatedAtEpochMillis,
            availability = StudyFlowTvPresentationAvailability.READY,
            today = isolatePresentationFeed("today-card", failedFeeds) {
                StudyFlowTvTodayPresentation(
                    planTitle = plan.name,
                    nextActivity = nextActivity?.toPresentation(subjectNames),
                    dueReviewCount = input.analytics.dueReviewCount,
                    availableQuestionCount = input.questions.size,
                    weeklyProgressPercent = weeklyProgress,
                    mockExamScheduled = pendingActivities.any { it.type == StudyActivityType.MOCK_EXAM },
                )
            },
            studySession = isolatePresentationFeed("study-session", failedFeeds) {
                projectSession(
                    input.sessions,
                    input.schedule.plannedActivities,
                    subjectNames,
                    input.generatedAtEpochMillis,
                )
            },
            questionSet = isolatePresentationFeed("question-set", failedFeeds) {
                projectQuestionSet(plan, input.tests, input.questions, subjectNames)
            },
            mockExam = isolatePresentationFeed("mock-exam", failedFeeds) {
                projectMockExam(input.mockExams, input.generatedAtEpochMillis)
            },
            review = isolatePresentationFeed("review", failedFeeds) {
                projectReview(
                    plan = plan,
                    reviewItems = input.reviewItems,
                    flashcards = input.flashcards,
                    errors = input.errorEntries,
                    questions = input.questions,
                    subjectNames = subjectNames,
                    nowEpochMillis = input.generatedAtEpochMillis,
                )
            },
            progress = isolatePresentationFeed("progress", failedFeeds) {
                StudyFlowTvProgressPresentation(
                    planTitle = plan.name,
                    studiedMinutesLastSevenDays = input.analytics.studiedMinutesLastSevenDays,
                    weeklyGoalMinutes = weeklyGoalMinutes,
                    weeklyProgressPercent = weeklyProgress,
                    questionAccuracyPercent = input.analytics.questionAccuracyPercent,
                    answeredQuestions = input.analytics.answeredQuestions,
                    dueReviewCount = input.analytics.dueReviewCount,
                    contentProgressPercent = input.analytics.contentProgressPercent,
                    consistencyScorePercent = input.analytics.consistencyScorePercent,
                    weakAreas = input.analytics.weakAreas.mapIndexed { index, area ->
                        StudyFlowTvWeakAreaPresentation(
                            itemId = "weak-area-$index-${area.label.stableSlug()}",
                            label = area.label,
                            progressPercent = area.progressPercent,
                            masteryPercent = area.masteryPercent,
                            priority = area.priority,
                        )
                    },
                    goals = projectedGoals,
                )
            },
            schedule = isolatePresentationFeed("schedule", failedFeeds) {
                StudyFlowTvSchedulePresentation(
                    scheduleId = "schedule-${plan.id.value}",
                    title = "${plan.name} schedule",
                    items = input.schedule.plannedActivities
                        .sortedWith(
                            compareBy<PlannedActivity> { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
                                .thenBy { it.title },
                        )
                        .take(StudyFlowTvPresentationProtocol.maxTvScheduleItems)
                        .map { activity -> activity.toSchedulePresentation(subjectNames) },
                )
            },
            library = isolatePresentationFeed("library", failedFeeds) {
                StudyFlowTvLibraryPresentation(
                    libraryId = "library-${plan.id.value}",
                    title = "${plan.name} library",
                    references = input.materials
                        .asSequence()
                        .filterNot(StudyMaterial::archived)
                        .sortedWith(
                            compareByDescending<StudyMaterial> { it.favorite }
                                .thenByDescending { it.primary }
                                .thenBy { it.title },
                        )
                        .take(StudyFlowTvPresentationProtocol.maxTvLibraryReferences)
                        .map { material -> material.toPresentation(subjectNames) }
                        .toList(),
                )
            },
            failedFeeds = failedFeeds.toList(),
        )
    }

    private fun projectSession(
        sessions: List<StudySession>,
        activities: List<PlannedActivity>,
        subjectNames: Map<SubjectId, String>,
        nowEpochMillis: Long,
    ): StudyFlowTvStudySessionPresentation? {
        val session = sessions.firstOrNull { session ->
            session.status == StudySessionStatus.RUNNING || session.status == StudySessionStatus.PAUSED
        }
            ?: sessions.maxByOrNull { it.startedAtEpochMillis }
            ?: return null
        val activity = session.plannedActivityId?.let { id -> activities.firstOrNull { it.id == id } }
        val elapsedMinutes = (StudySessionTimer.activeSeconds(session, nowEpochMillis) / 60L).toInt()
        val targetMinutes = session.targetDurationMinutes
            ?: activity?.plannedDurationMinutes
            ?: elapsedMinutes.coerceAtLeast(1)
        return StudyFlowTvStudySessionPresentation(
            sessionId = session.id.value,
            title = activity?.title ?: session.type.displayLabel(),
            subjectLabel = subjectLabel(session.subjectId, subjectNames),
            status = session.status.name,
            elapsedMinutes = elapsedMinutes.coerceAtMost(targetMinutes),
            targetMinutes = targetMinutes,
            questionsAnswered = session.questionsAnswered,
            correctAnswers = session.correctAnswers,
        )
    }

    private fun projectQuestionSet(
        plan: StudyPlan,
        tests: List<StudyTest>,
        questions: List<Question>,
        subjectNames: Map<SubjectId, String>,
    ): StudyFlowTvQuestionSetPresentation {
        val test = tests.firstOrNull { it.startedAtEpochMillis != null && it.submittedAtEpochMillis == null }
            ?: tests.firstOrNull { it.submittedAtEpochMillis == null }
            ?: tests.maxByOrNull { it.submittedAtEpochMillis ?: it.startedAtEpochMillis ?: Long.MIN_VALUE }
        val execution = test?.let { candidate ->
            runCatching { PracticeExecutionEngine().stateForTest(candidate, questions) }.getOrNull()
        }
        val orderedQuestions = execution?.questions ?: questions
        val projectedQuestions = orderedQuestions.asSequence()
            .mapNotNull { question -> question.toPresentation(subjectNames) }
            .take(StudyFlowTvPresentationProtocol.maxTvQuestions)
            .toList()
        val projectedById = projectedQuestions.associateBy { it.questionId }
        val answers = execution?.answers.orEmpty().mapNotNull { answer ->
            val domainQuestion = questions.firstOrNull { it.id == answer.questionId } ?: return@mapNotNull null
            val optionIndex = domainQuestion.options.indexOfFirst { option ->
                option.trim().equals(answer.chosenAnswer.trim(), ignoreCase = true)
            }
            val projectedQuestion = projectedById[answer.questionId.value] ?: return@mapNotNull null
            val option = projectedQuestion.choices.getOrNull(optionIndex) ?: return@mapNotNull null
            StudyFlowTvQuestionAnswerPresentation(
                questionId = answer.questionId.value,
                chosenOptionId = option.optionId,
                correct = answer.correct,
                answeredAtEpochMillis = answer.answeredAtEpochMillis,
            )
        }
        return StudyFlowTvQuestionSetPresentation(
            questionSetId = test?.id?.value ?: "questions-${plan.id.value}",
            title = test?.title ?: "Question practice",
            subjectLabel = projectedQuestions.map { it.subjectLabel }.distinct().singleOrNull() ?: "Mixed subjects",
            questions = projectedQuestions,
            started = test?.startedAtEpochMillis != null,
            submitted = test?.submittedAtEpochMillis != null,
            currentQuestionId = execution?.currentQuestion?.id?.value?.takeIf(projectedById::containsKey),
            answers = answers,
        )
    }

    private fun projectMockExam(
        mockExams: List<MockExam>,
        nowEpochMillis: Long,
    ): StudyFlowTvMockExamPresentation? {
        val mock = selectTvMockExam(mockExams) ?: return null
        val totalMinutes = (mock.timeLimitSeconds / 60L).toInt().coerceAtLeast(1)
        val pausedAtEpochMillis = PracticeAttemptCodec.tvMockPausedAtEpochMillis(mock.configurationJson)
        val elapsedMinutes = mock.startedAtEpochMillis?.let { started ->
            val end = mock.submittedAtEpochMillis ?: pausedAtEpochMillis ?: nowEpochMillis
            ((end - started).coerceAtLeast(0L) / 60_000L).toInt().coerceAtMost(totalMinutes)
        } ?: 0
        val sections = mock.toTvMockSections(totalMinutes)
        return StudyFlowTvMockExamPresentation(
            mockExamId = mock.id.value,
            title = mock.title,
            status = when {
                mock.submittedAtEpochMillis != null -> "SUBMITTED"
                pausedAtEpochMillis != null -> "PAUSED"
                mock.startedAtEpochMillis != null -> "RUNNING"
                else -> "READY"
            },
            totalMinutes = totalMinutes,
            elapsedMinutes = elapsedMinutes,
            questionCount = mock.questionCount,
            answeredCount = mock.answeredCount,
            correctCount = mock.correctCount,
            scorePercent = mock.scorePercent?.toInt()?.coerceIn(0, 100),
            sections = sections,
            currentSectionId = sections.firstOrNull { section -> section.answeredCount < section.questionCount }?.sectionId
                ?: sections.lastOrNull()?.sectionId,
            canPersistActions = mock.submittedAtEpochMillis == null,
        )
    }

    internal fun selectTvMockExam(mockExams: List<MockExam>): MockExam? = mockExams
        .sortedWith(
            compareByDescending<MockExam> { it.startedAtEpochMillis != null && it.submittedAtEpochMillis == null }
                .thenByDescending { it.startedAtEpochMillis == null && it.submittedAtEpochMillis == null }
                .thenByDescending { it.startedAtEpochMillis ?: it.submittedAtEpochMillis ?: Long.MIN_VALUE }
                .thenByDescending { it.submittedAtEpochMillis ?: Long.MIN_VALUE }
                .thenBy { it.title },
        )
        .firstOrNull()

    private fun MockExam.toTvMockSections(totalMinutes: Int): List<StudyFlowTvMockExamSectionPresentation> {
        val frozen = ExamBlueprintCodec.decode(configurationJson)
        val blueprintSections = frozen?.sections.orEmpty()
        val rawSections = if (blueprintSections.isNotEmpty()) {
            blueprintSections.map { section ->
                section.key to section.questionCount
            }
        } else {
            listOf("mock" to questionCount)
        }
        val boundedSections = rawSections.take(StudyFlowTvPresentationProtocol.maxTvMockExamSections)
        var remainingQuestions = questionCount
        var remainingMinutes = totalMinutes
        var remainingAnswered = answeredCount
        var remainingCorrect = correctCount
        return boundedSections.mapIndexed { index, (key, rawCount) ->
            val sectionCount = if (index == boundedSections.lastIndex) remainingQuestions else rawCount.coerceIn(1, remainingQuestions)
            val sectionMinutes = if (index == boundedSections.lastIndex) remainingMinutes.coerceAtLeast(1) else
                ((sectionCount.toDouble() / questionCount.toDouble()) * totalMinutes.toDouble()).toInt().coerceAtLeast(1)
                    .coerceAtMost((remainingMinutes - (boundedSections.lastIndex - index)).coerceAtLeast(1))
            val sectionAnswered = remainingAnswered.coerceIn(0, sectionCount)
            val sectionCorrect = remainingCorrect.coerceIn(0, sectionAnswered)
            remainingQuestions = (remainingQuestions - sectionCount).coerceAtLeast(0)
            remainingMinutes = (remainingMinutes - sectionMinutes).coerceAtLeast(0)
            remainingAnswered = (remainingAnswered - sectionAnswered).coerceAtLeast(0)
            remainingCorrect = (remainingCorrect - sectionCorrect).coerceAtLeast(0)
            StudyFlowTvMockExamSectionPresentation(
                sectionId = key.stableSlug().ifBlank { "section-${index + 1}" },
                title = key.replace('-', ' ').replace('_', ' ').replaceFirstChar(Char::uppercase),
                questionCount = sectionCount,
                totalMinutes = sectionMinutes,
                answeredCount = sectionAnswered,
                correctCount = sectionCorrect,
            )
        }.ifEmpty {
            listOf(
                StudyFlowTvMockExamSectionPresentation(
                    sectionId = "mock",
                    title = title,
                    questionCount = questionCount,
                    totalMinutes = totalMinutes,
                    answeredCount = answeredCount,
                    correctCount = correctCount,
                ),
            )
        }
    }

    private fun projectReview(
        plan: StudyPlan,
        reviewItems: List<ReviewItem>,
        flashcards: List<Flashcard>,
        errors: List<ErrorEntry>,
        questions: List<Question>,
        subjectNames: Map<SubjectId, String>,
        nowEpochMillis: Long,
    ): StudyFlowTvReviewPresentation {
        val flashcardsById = flashcards.associateBy { it.id }
        val errorsById = errors.associateBy { it.id }
        val questionsById = questions.associateBy { it.id }
        val projected = reviewItems.asSequence()
            .filter { it.completedAtEpochMillis == null }
            .sortedBy { it.dueAtEpochMillis }
            .take(StudyFlowTvPresentationProtocol.maxTvReviewItems)
            .map { item ->
                val flashcard = item.flashcardId?.let(flashcardsById::get)
                val error = item.errorEntryId?.let(errorsById::get)
                val question = item.questionId?.let(questionsById::get)
                val prompt = flashcard?.front
                    ?: question?.statement
                    ?: error?.explanation?.takeIf(String::isNotBlank)
                    ?: "Scheduled review"
                val explanation = flashcard?.back
                    ?: question?.explanation
                    ?: error?.correctAnswer
                    ?: "Open this review on the phone for the full source context."
                StudyFlowTvReviewItemPresentation(
                    reviewItemId = item.id.value,
                    questionId = item.questionId?.value,
                    title = prompt.take(96),
                    subjectLabel = subjectLabel(item.subjectId, subjectNames),
                    prompt = prompt,
                    explanation = explanation,
                    reason = "${item.method.name.lowercase().replace('_', ' ')} · ${item.status.name.lowercase()}",
                    confidencePercent = ((item.easeFactor / 3.0) * 100.0).toInt().coerceIn(0, 100),
                    dueAtEpochMillis = item.dueAtEpochMillis.coerceAtLeast(0L),
                    status = if (item.dueAtEpochMillis <= nowEpochMillis) "DUE" else item.status.name,
                    errorEntryId = error?.id?.value,
                    errorCategory = error?.category?.name,
                )
            }
            .toList()
        val representedErrors = projected.mapNotNull { it.errorEntryId }.toSet()
        val remainingSlots = (StudyFlowTvPresentationProtocol.maxTvReviewItems - projected.size).coerceAtLeast(0)
        val standaloneErrors = errors.asSequence()
            .filter { it.id.value !in representedErrors }
            .sortedByDescending { it.updatedAtEpochMillis }
            .take(remainingSlots)
            .map { error ->
                val question = error.questionId?.let { questionsById[it] }
                val prompt = question?.statement
                    ?: error.explanation.takeIf(String::isNotBlank)
                    ?: "Error Notebook entry"
                StudyFlowTvReviewItemPresentation(
                    reviewItemId = "error:${error.id.value}",
                    questionId = error.questionId?.value,
                    title = prompt.take(96),
                    subjectLabel = subjectLabel(error.subjectId, subjectNames),
                    prompt = prompt,
                    explanation = question?.explanation ?: error.explanation,
                    reason = "error notebook · ${error.category.name.lowercase().replace('_', ' ')}",
                    confidencePercent = (100 - error.recurrenceCount * 12).coerceIn(20, 85),
                    dueAtEpochMillis = error.updatedAtEpochMillis.coerceAtLeast(0L),
                    status = "ERROR_NOTEBOOK",
                    errorEntryId = error.id.value,
                    errorCategory = error.category.name,
                )
            }
            .toList()
        return StudyFlowTvReviewPresentation(
            queueId = "review-${plan.id.value}",
            title = "${plan.name} reviews",
            items = projected + standaloneErrors,
        )
    }

    private fun PlannedActivity.toPresentation(subjectNames: Map<SubjectId, String>): StudyFlowTvActivityPresentation =
        StudyFlowTvActivityPresentation(
            activityId = id.value,
            title = title,
            subjectLabel = subjectLabel(subjectId, subjectNames),
            activityType = type.name,
            plannedDurationMinutes = plannedDurationMinutes,
            scheduledStartEpochMillis = scheduledStartEpochMillis,
            status = status.name,
            targetSurface = type.targetSurface(),
        )

    private fun PlannedActivity.toSchedulePresentation(
        subjectNames: Map<SubjectId, String>,
    ): StudyFlowTvScheduleItemPresentation =
        StudyFlowTvScheduleItemPresentation(
            activityId = id.value,
            title = title,
            subjectLabel = subjectLabel(subjectId, subjectNames),
            activityType = type.name,
            status = status.name,
            plannedDurationMinutes = plannedDurationMinutes,
            scheduledStartEpochMillis = scheduledStartEpochMillis,
            targetSurface = type.targetSurface(),
        )

    private fun Question.toPresentation(subjectNames: Map<SubjectId, String>): StudyFlowTvQuestionPresentation? {
        if (options.size !in 2..StudyFlowTvPresentationProtocol.maxQuestionChoices) return null
        val choiceTexts = options.map(String::trim)
        val correctIndex = choiceTexts.indexOfFirst { it.equals(correctAnswer.trim(), ignoreCase = true) }
        if (correctIndex < 0) return null
        return StudyFlowTvQuestionPresentation(
            questionId = id.value,
            prompt = statement,
            choices = choiceTexts.mapIndexed { index, label ->
                StudyFlowTvQuestionChoicePresentation(optionId = index.toString(), label = label)
            },
            correctOptionId = correctIndex.toString(),
            explanation = explanation,
            subjectLabel = subjectLabel(subjectId, subjectNames),
            sourceLabel = listOfNotNull(examBoard, organization, year?.toString()).joinToString(" · ").ifBlank { null },
        )
    }

    private fun StudyMaterial.toPresentation(
        subjectNames: Map<SubjectId, String>,
    ): StudyFlowTvLibraryReferencePresentation {
        val source = author?.takeIf(String::isNotBlank) ?: type.name.lowercase().replace('_', ' ')
        val body = description.trim().ifBlank {
            tags.joinToString(", ").ifBlank { "Open on the phone for the complete source." }
        }
        return StudyFlowTvLibraryReferencePresentation(
            referenceId = id.value,
            title = title,
            subjectLabel = subjectLabel(subjectId, subjectNames),
            sourceLabel = source,
            excerpt = body.take(320),
            referenceKind = type.name,
            relatedSurface = StudyFlowTvSurface.LIBRARY,
            // TVOV27 compatibility token: viewer = StudyFlowTvMaterialViewerPresentation
            viewer = transferableViewerFor(this, body),
        )
    }

    private fun transferableViewerFor(
        material: StudyMaterial,
        body: String,
    ): StudyFlowTvMaterialViewerPresentation {
        val rawUri = material.sourceUri
        val safeSourceUri = rawUri?.takeIf(StudyFlowTvTransferableMaterialSource::isTvResolvableUri)
        val inlineText = body.take(StudyFlowTvPresentationProtocol.maxInlineMaterialTextChars)
            .takeIf { material.type == com.mikeyphw.studyflow.core.model.StudyMaterialType.NOTE ||
                material.type == com.mikeyphw.studyflow.core.model.StudyMaterialType.BOOK }
        val transferId = transferIdFor(material, rawUri, inlineText)
        return StudyFlowTvMaterialViewerPresentation(
            kind = when (material.type) {
                com.mikeyphw.studyflow.core.model.StudyMaterialType.NOTE,
                com.mikeyphw.studyflow.core.model.StudyMaterialType.BOOK,
                -> StudyFlowTvMaterialViewerKind.TEXT
                com.mikeyphw.studyflow.core.model.StudyMaterialType.PDF -> StudyFlowTvMaterialViewerKind.PDF
                com.mikeyphw.studyflow.core.model.StudyMaterialType.VIDEO -> StudyFlowTvMaterialViewerKind.MEDIA
                com.mikeyphw.studyflow.core.model.StudyMaterialType.WEBSITE -> safeSourceUri?.let(::viewerKindForUri)
                    ?: StudyFlowTvMaterialViewerKind.UNAVAILABLE
                com.mikeyphw.studyflow.core.model.StudyMaterialType.LOCAL_FILE,
                com.mikeyphw.studyflow.core.model.StudyMaterialType.COURSE,
                com.mikeyphw.studyflow.core.model.StudyMaterialType.OTHER,
                -> safeSourceUri?.let(::viewerKindForUri) ?: transferId?.let { kindFromMaterialType(material.type, rawUri) }
                    ?: StudyFlowTvMaterialViewerKind.UNAVAILABLE
            },
            sourceUri = safeSourceUri,
            transferId = transferId,
            mimeType = mimeTypeFor(material.type, safeSourceUri ?: rawUri),
            inlineText = inlineText,
        )
    }

    private fun transferIdFor(
        material: StudyMaterial,
        rawUri: String?,
        inlineText: String?,
    ): String? = when {
        inlineText != null -> null
        rawUri == null -> null
        StudyFlowTvTransferableMaterialSource.isTvResolvableUri(rawUri) -> null
        else -> "plan-${material.studyPlanId.value}-material-${material.id.value}"
    }

    private fun kindFromMaterialType(
        type: com.mikeyphw.studyflow.core.model.StudyMaterialType,
        rawUri: String?,
    ): StudyFlowTvMaterialViewerKind = when (type) {
        com.mikeyphw.studyflow.core.model.StudyMaterialType.PDF -> StudyFlowTvMaterialViewerKind.PDF
        com.mikeyphw.studyflow.core.model.StudyMaterialType.VIDEO -> StudyFlowTvMaterialViewerKind.MEDIA
        com.mikeyphw.studyflow.core.model.StudyMaterialType.LOCAL_FILE,
        com.mikeyphw.studyflow.core.model.StudyMaterialType.COURSE,
        com.mikeyphw.studyflow.core.model.StudyMaterialType.OTHER,
        -> rawUri?.let(::viewerKindForUri) ?: StudyFlowTvMaterialViewerKind.UNAVAILABLE
        else -> StudyFlowTvMaterialViewerKind.UNAVAILABLE
    }

    private fun <T> isolatePresentationFeed(
        name: String,
        failedFeeds: MutableSet<String>,
        block: () -> T,
    ): T? = runCatching(block).getOrElse {
        failedFeeds += name
        null
    }

    private fun viewerKindForUri(uri: String): StudyFlowTvMaterialViewerKind {
        val lower = uri.substringBefore('?').substringBefore('#').lowercase()
        return when {
            lower.endsWith(".pdf") -> StudyFlowTvMaterialViewerKind.PDF
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".webp") -> StudyFlowTvMaterialViewerKind.IMAGE
            lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m4v") ||
                lower.endsWith(".mp3") || lower.endsWith(".m4a") -> StudyFlowTvMaterialViewerKind.MEDIA
            uri.startsWith("http://") || uri.startsWith("https://") -> StudyFlowTvMaterialViewerKind.WEB
            else -> StudyFlowTvMaterialViewerKind.UNAVAILABLE
        }
    }

    private fun mimeTypeFor(
        type: com.mikeyphw.studyflow.core.model.StudyMaterialType,
        uri: String?,
    ): String? = when (type) {
        com.mikeyphw.studyflow.core.model.StudyMaterialType.PDF -> "application/pdf"
        com.mikeyphw.studyflow.core.model.StudyMaterialType.VIDEO -> "video/*"
        com.mikeyphw.studyflow.core.model.StudyMaterialType.NOTE,
        com.mikeyphw.studyflow.core.model.StudyMaterialType.BOOK,
        -> "text/plain"
        else -> when (viewerKindForUri(uri.orEmpty())) {
            StudyFlowTvMaterialViewerKind.PDF -> "application/pdf"
            StudyFlowTvMaterialViewerKind.IMAGE -> "image/*"
            StudyFlowTvMaterialViewerKind.MEDIA -> "video/*"
            StudyFlowTvMaterialViewerKind.WEB -> "text/html"
            StudyFlowTvMaterialViewerKind.TEXT -> "text/plain"
            StudyFlowTvMaterialViewerKind.UNAVAILABLE -> null
        }
    }

    private fun StudyActivityType.targetSurface(): StudyFlowTvSurface = when (this) {
        StudyActivityType.QUESTIONS -> StudyFlowTvSurface.QUESTION_SET
        StudyActivityType.REVIEW, StudyActivityType.FLASHCARDS -> StudyFlowTvSurface.REVIEW
        StudyActivityType.MOCK_EXAM -> StudyFlowTvSurface.MOCK_EXAM
        else -> StudyFlowTvSurface.STUDY_SESSION
    }

    private fun StudyActivityType.displayLabel(): String =
        name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

    private fun subjectLabel(subjectId: SubjectId?, subjectNames: Map<SubjectId, String>): String =
        subjectId?.let(subjectNames::get)?.takeIf(String::isNotBlank) ?: "General"

    private fun percent(numerator: Int, denominator: Int): Int =
        if (denominator <= 0) 0 else ((numerator.toDouble() / denominator.toDouble()) * 100.0).toInt().coerceIn(0, 100)

    private fun String.stableSlug(): String = lowercase()
        .map { character -> if (character.isLetterOrDigit()) character else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifBlank { "item" }
}
