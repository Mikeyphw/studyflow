package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.AppShellReadinessAuditor
import com.mikeyphw.studyflow.core.domain.AppShellReadinessInput
import com.mikeyphw.studyflow.core.domain.ManageStudyAnalytics
import com.mikeyphw.studyflow.core.domain.ManageStudyDiscovery
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.ManageStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyProgress
import com.mikeyphw.studyflow.core.domain.ManagePublicExam
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.PracticeAnalytics
import com.mikeyphw.studyflow.core.domain.GoalEvidence
import com.mikeyphw.studyflow.core.domain.StudyLibrarySnapshot
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.ThemeMode
import kotlinx.coroutines.runBlocking

class DesktopStudyFlowServices(
    val planRepository: InMemoryStudyPlanRepository,
    val contentRepository: DesktopStudyContentRepository,
    val libraryRepository: InMemoryStudyLibraryRepository,
    val scheduleRepository: InMemoryStudyScheduleRepository,
    val sessionRepository: InMemoryStudySessionRepository,
    val practiceRepository: InMemoryStudyPracticeRepository,
    val reviewRepository: InMemoryStudyReviewRepository = InMemoryStudyReviewRepository(),
    val analyticsRepository: InMemoryStudyAnalyticsRepository,
    val discoveryRepository: DesktopStudyDiscoveryRepository,
    val preferencesStore: DesktopPreferencesStore,
    val platformPorts: DesktopPlatformPorts,
    val linkService: DesktopStudyFlowLinkService? = null,
    val workspacePersistence: DesktopWorkspacePersistence? = null,
    val contentModeRepository: DesktopContentModeRepository = DesktopContentModeRepository(),
    val publicExamRepository: DesktopPublicExamRepository = DesktopPublicExamRepository(
        plans = planRepository,
        content = contentRepository,
        practice = practiceRepository,
        review = reviewRepository,
    ),
    val progressRepository: DesktopStudyProgressRepository = DesktopStudyProgressRepository(),
) {
    val manageStudyPlan: ManageStudyPlan = ManageStudyPlan(planRepository)
    val manageStudyContent: ManageStudyContent = ManageStudyContent(contentRepository)
    val manageStudyLibrary: ManageStudyLibrary = ManageStudyLibrary(libraryRepository)
    val manageStudySchedule: ManageStudySchedule = ManageStudySchedule(scheduleRepository)
    val manageStudySession: ManageStudySession = ManageStudySession(sessionRepository, planRepository)
    val manageStudyPractice: ManageStudyPractice = ManageStudyPractice(practiceRepository, planRepository)
    val manageStudyProgress: ManageStudyProgress = ManageStudyProgress(progressRepository)
    val manageStudyReview: ManageStudyReview = ManageStudyReview(
        reviewRepository,
        practiceRepository,
        studyPlanRepository = planRepository,
    )
    val managePublicExam: ManagePublicExam = ManagePublicExam(publicExamRepository, manageStudySession)
    val manageStudyAnalytics: ManageStudyAnalytics = ManageStudyAnalytics(analyticsRepository)
    val manageStudyDiscovery: ManageStudyDiscovery = ManageStudyDiscovery(discoveryRepository)
    val planContentController = DesktopPlanContentController(
        planRepository = planRepository,
        contentRepository = contentRepository,
        preferencesStore = preferencesStore,
        managePlan = manageStudyPlan,
        manageContent = manageStudyContent,
        scheduleRepository = scheduleRepository,
        sessionRepository = sessionRepository,
        practiceRepository = practiceRepository,
        reviewRepository = reviewRepository,
        libraryRepository = libraryRepository,
        contentModeRepository = contentModeRepository,
        publicExamRepository = publicExamRepository,
        progressRepository = progressRepository,
    )
    val plannerAuthoringController = DesktopPlannerAuthoringController(this)
    val sessionRuntimeController = DesktopSessionRuntimeController(this)
    val practiceExecutionController = DesktopPracticeExecutionController(this)
    val mockExamStudioController = DesktopMockExamStudioController(this)
    val reviewWorkspaceController = DesktopReviewWorkspaceController(this)
    val libraryWorkspaceController = DesktopLibraryWorkspaceController(this, workspacePersistence?.root)
    val writingWorkspaceController = DesktopWritingWorkspaceController(this)
    val contentModeController = DesktopContentModeController(this, contentModeRepository, workspacePersistence?.root)
    val publicExamController = DesktopPublicExamController(this, publicExamRepository)
    val goalWorkspaceController = DesktopGoalWorkspaceController(this)
    val progressReadinessController = DesktopProgressReadinessController(this, workspacePersistence?.root)
    val portabilityController = DesktopPortabilityController(this, workspacePersistence?.root)
    val defaultPlanId: StudyPlanId?
        get() = preferencesStore.current().defaultStudyPlanId ?: planRepository.currentPlans().firstOrNull()?.id

    fun createDashboardState(): DesktopDashboardState {
        val activePlan = currentDefaultPlan()
        val librarySnapshot = activePlan?.id?.let(libraryRepository::currentSnapshot) ?: emptyLibrarySnapshot()
        val preferences = preferencesStore.current()
        val readiness = AppShellReadinessAuditor.evaluate(
            AppShellReadinessInput(
                language = preferences.language,
                themeMode = preferences.themeMode,
                dynamicColorEnabled = preferences.dynamicColorEnabled,
                hasDefaultPlan = activePlan != null,
            ),
        )
        return DesktopDashboardState(
            status = if (activePlan == null) DesktopScreenStatus.EMPTY else DesktopScreenStatus.CONTENT,
            activePlanName = activePlan?.name ?: "No active Study Plan",
            activePlanDescription = activePlan?.description.orEmpty(),
            progressPercent = activePlan?.progressPercent ?: 0,
            weeklyGoalHours = activePlan?.weeklyGoalHours ?: 0,
            studiedMinutesThisWeek = activePlan?.studiedMinutesThisWeek ?: 0,
            questionsAnswered = activePlan?.questionsAnswered ?: 0,
            averageAccuracyPercent = activePlan?.averageAccuracyPercent ?: 0,
            readinessPercent = readiness.readyPercent,
            libraryProgressPercent = librarySnapshot.progressPercent,
            materialCount = librarySnapshot.activeMaterials.size,
            favoriteCount = librarySnapshot.favoriteMaterials.size,
            videoCount = librarySnapshot.videos.size,
            domainManagers = listOf(
                "ManageStudyPlan",
                "ManageStudyContent",
                "ManageStudyLibrary",
                "ManageStudySchedule",
                "ManageStudySession",
                "ManageStudyPractice",
                "ManageStudyReview",
                "ManageStudyProgress",
                "ManagePublicExam",
                "ManageStudyAnalytics",
                "ManageStudyDiscovery",
            ),
            boundaryModules = StudyFlowDesktopBoundary.allowedSharedModules,
        )
    }

    fun createLibraryState(): DesktopLibraryScreenState {
        val activePlan = currentDefaultPlan()
        val snapshot = activePlan?.id?.let(libraryRepository::currentSnapshot) ?: emptyLibrarySnapshot()
        val materialRows = snapshot.activeMaterials.map(DesktopLibraryMaterialRow::fromMaterial)
        val videoRows = snapshot.videos.map(DesktopLibraryVideoRow::fromVideo)
        return DesktopLibraryScreenState(
            status = if (activePlan == null || materialRows.isEmpty() && videoRows.isEmpty()) {
                DesktopScreenStatus.EMPTY
            } else {
                DesktopScreenStatus.CONTENT
            },
            planName = activePlan?.name ?: "No active Study Plan",
            materials = materialRows,
            videos = videoRows,
            summary = DesktopLibrarySummary.fromSnapshot(snapshot),
        )
    }

    fun createPlannerState(): DesktopPlannerScreenState {
        val activePlan = currentDefaultPlan()
        val planId = activePlan?.id
        val activities = planId?.let(scheduleRepository::currentActivities).orEmpty()
        val sessions = planId?.let(sessionRepository::currentSessions).orEmpty()
        val activeSession = sessions.firstOrNull { session ->
            session.status.name == "RUNNING" || session.status.name == "PAUSED"
        }
        return DesktopPlannerScreenState(
            status = if (activePlan == null || activities.isEmpty() && sessions.isEmpty()) {
                DesktopScreenStatus.EMPTY
            } else {
                DesktopScreenStatus.CONTENT
            },
            planName = activePlan?.name ?: "No active Study Plan",
            plannedActivities = activities.map(DesktopPlannedActivityRow::fromActivity),
            activeSession = activeSession?.let(DesktopSessionRow::fromSession),
            recentSessions = sessions.map(DesktopSessionRow::fromSession),
            nextActivityTitle = activities.firstOrNull { activity ->
                activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)
            }?.title ?: "No next activity",
            completedCount = activities.count { activity -> activity.status == PlannedActivityStatus.COMPLETED },
            missedCount = activities.count { activity -> activity.status == PlannedActivityStatus.MISSED },
            totalPlannedMinutes = activities.sumOf { activity -> activity.plannedDurationMinutes },
        )
    }

    fun createPracticeState(): DesktopPracticeScreenState {
        val activePlan = currentDefaultPlan()
        val planId = activePlan?.id
        val questions = planId?.let(practiceRepository::currentQuestions).orEmpty()
        val tests = planId?.let(practiceRepository::currentTests).orEmpty()
        val mockExams = planId?.let(practiceRepository::currentMockExams).orEmpty()
        val summary = PracticeAnalytics.summarize(questions, tests, mockExams)
        val deck = buildDesktopPracticeDeck(questions)
        return DesktopPracticeScreenState(
            status = if (activePlan == null || questions.isEmpty()) {
                DesktopScreenStatus.EMPTY
            } else {
                DesktopScreenStatus.CONTENT
            },
            summary = DesktopPracticeSummary.fromSummary(summary),
            deck = deck.map(DesktopQuestionRow::fromQuestion),
            tests = tests.map(DesktopPracticeAttemptRow::fromTest),
            currentQuestion = deck.firstOrNull()?.let(DesktopQuestionRow::fromQuestion),
        )
    }

    fun createAnalyticsState(): DesktopAnalyticsScreenState {
        val activePlan = currentDefaultPlan()
        val snapshot = activePlan?.id?.let(analyticsRepository::currentSnapshot)
        return snapshot?.let(DesktopAnalyticsScreenState::fromSnapshot) ?: DesktopAnalyticsScreenState(
            status = DesktopScreenStatus.EMPTY,
            studiedHoursSevenDays = 0.0,
            completedSessions = 0,
            scheduleAdherencePercent = 0,
            questionAccuracyPercent = 0,
            answeredQuestions = 0,
            libraryProgressPercent = 0,
            consistencyScorePercent = 0,
            dueReviewCount = 0,
            overdueReviewCount = 0,
            weakAreas = emptyList(),
            insights = emptyList(),
        )
    }

    fun createSearchState(query: String): DesktopSearchScreenState {
        val activePlan = currentDefaultPlan()
        val trimmed = query.trim()
        val results = if (trimmed.length >= 2) {
            runBlocking { manageStudyDiscovery.search(trimmed).map(DesktopSearchResultRow::fromResult) }
        } else {
            emptyList()
        }
        if (results.isNotEmpty()) preferencesStore.recordSearch(trimmed)
        val recommendations = activePlan?.id?.let { planId ->
            runBlocking {
                manageStudyDiscovery.recommendations(planId).map(DesktopRecommendationRow::fromRecommendation)
            }
        }.orEmpty()
        return DesktopSearchScreenState(
            status = if (activePlan == null) DesktopScreenStatus.EMPTY else DesktopScreenStatus.CONTENT,
            query = query,
            results = results,
            recommendations = recommendations,
            recentSearches = preferencesStore.current().recentSearches,
        )
    }

    fun createSettingsState(): DesktopSettingsScreenState {
        val activePlan = currentDefaultPlan()
        return DesktopSettingsScreenState.fromPreferences(
            preferences = preferencesStore.current(),
            defaultPlanLabel = activePlan?.name ?: "No active Study Plan",
        )
    }

    fun setTheme(themeMode: ThemeMode) {
        preferencesStore.setTheme(themeMode)
    }

    fun setLanguage(language: AppLanguage) {
        preferencesStore.setLanguage(language)
    }

    fun currentDefaultPlanForDesktop(): StudyPlan? = currentDefaultPlan()

    private fun currentDefaultPlan(): StudyPlan? {
        val plans = planRepository.currentPlans()
        return defaultPlanId?.let { planId -> plans.firstOrNull { plan -> plan.id == planId } }
            ?: plans.firstOrNull()
    }

    private fun emptyLibrarySnapshot(): StudyLibrarySnapshot = StudyLibrarySnapshot(
        materials = emptyList(),
        videos = emptyList(),
        annexes = emptyList(),
    )
}

fun createDesktopStudyFlowServices(
    platformPorts: DesktopPlatformPorts = NoOpDesktopPlatformPorts,
    linkService: DesktopStudyFlowLinkService? = null,
    workspacePersistence: DesktopWorkspacePersistence? = null,
): DesktopStudyFlowServices {
    val sample = DesktopStudyFlowSampleData.create()
    val persisted = workspacePersistence?.current()
    val planRepository = InMemoryStudyPlanRepository(
        initialPlans = persisted?.plans ?: sample.plans,
        initialSettings = persisted?.planSettings ?: sample.plans.associate { plan ->
            plan.id to com.mikeyphw.studyflow.core.model.StudyPlanSettings.defaults(plan.id)
        },
        onChanged = { plans, settings -> workspacePersistence?.updatePlans(plans, settings) },
    )
    val contentRepository = DesktopStudyContentRepository(
        initialState = DesktopStudyContentState(
            targets = persisted?.studyTargets.orEmpty(),
            examStages = persisted?.examStages.orEmpty(),
            subjects = persisted?.subjects ?: sample.subjects,
            topics = persisted?.topics ?: sample.topics,
        ),
        plans = planRepository,
        onChanged = { content ->
            workspacePersistence?.updateContent(
                content.targets,
                content.examStages,
                content.subjects,
                content.topics,
            )
        },
    )
    val libraryPersistence = workspacePersistence?.let { persistence ->
        DesktopLibraryPersistence.open(
            persistence.root,
            DesktopLibrarySnapshot(sample.materials, sample.videos, sample.annexes),
        )
    }
    val persistedLibrary = libraryPersistence?.current()
    val libraryRepository = InMemoryStudyLibraryRepository(
        materials = persistedLibrary?.materials ?: sample.materials,
        videos = persistedLibrary?.videos ?: sample.videos,
        annexes = persistedLibrary?.annexes ?: sample.annexes,
        onChanged = { materials, videos, annexes ->
            libraryPersistence?.update(DesktopLibrarySnapshot(materials, videos, annexes))
        },
        onImportedFilesRemoved = { materialIds ->
            workspacePersistence?.let { removeDesktopImportedLibraryFiles(it.root, materialIds) }
        },
    )
    val scheduleRepository = InMemoryStudyScheduleRepository(
        strategies = persisted?.strategies ?: sample.strategies,
        scheduleRules = persisted?.scheduleRules ?: sample.scheduleRules,
        plannedActivities = persisted?.plannedActivities ?: sample.plannedActivities,
        onChanged = { strategies, rules, activities ->
            workspacePersistence?.updateSchedule(strategies, rules, activities)
        },
    )
    val sessionRepository = InMemoryStudySessionRepository(
        sessions = persisted?.sessions ?: sample.sessions,
        scheduleRepository = scheduleRepository,
        onChanged = { sessions -> workspacePersistence?.updateSessions(sessions) },
    )
    val practicePersistence = workspacePersistence?.let { persistence ->
        DesktopPracticePersistence.open(
            persistence.root,
            DesktopPracticeSnapshot(sample.questions, sample.tests, sample.mockExams),
        )
    }
    val persistedPractice = practicePersistence?.current()
    val practiceRepository = InMemoryStudyPracticeRepository(
        questions = persistedPractice?.questions ?: sample.questions,
        tests = persistedPractice?.tests ?: sample.tests,
        mockExams = persistedPractice?.mockExams ?: sample.mockExams,
        onChanged = { questions, tests, mockExams ->
            practicePersistence?.update(DesktopPracticeSnapshot(questions, tests, mockExams))
        },
    )
    val reviewPersistence = workspacePersistence?.let { DesktopReviewPersistence.open(it.root) }
    val persistedReview = reviewPersistence?.current()
    val reviewRepository = InMemoryStudyReviewRepository(
        reviews = persistedReview?.reviews.orEmpty(),
        flashcards = persistedReview?.flashcards.orEmpty(),
        errors = persistedReview?.errors.orEmpty(),
        onChanged = { reviews, flashcards, errors ->
            reviewPersistence?.update(DesktopReviewSnapshot(reviews, flashcards, errors))
        },
    )

    val contentModePersistence = workspacePersistence?.let { DesktopContentModePersistence.open(it.root) }
    val contentModeRepository = DesktopContentModeRepository(
        initial = contentModePersistence?.current() ?: DesktopContentModeSnapshot(),
        onChanged = { snapshot -> contentModePersistence?.update(snapshot) },
    )

    val publicExamPersistence = workspacePersistence?.let { DesktopPublicExamPersistence.open(it.root) }
    val publicExamRepository = DesktopPublicExamRepository(
        initial = publicExamPersistence?.current() ?: DesktopPublicExamSnapshot(),
        plans = planRepository,
        content = contentRepository,
        practice = practiceRepository,
        review = reviewRepository,
        onChanged = { snapshot -> publicExamPersistence?.update(snapshot) },
    )
    sessionRepository.configureCycleIntegration(
        recordBinding = { sessionId, binding -> publicExamRepository.recordSessionBinding(sessionId, binding) },
        onTerminal = { planId, sessionId, status -> publicExamRepository.onSessionTerminal(planId, sessionId, status) },
    )

    val progressRepository = DesktopStudyProgressRepository(
        root = workspacePersistence?.root,
        evidenceProvider = { planId ->
            GoalEvidence(
                sessions = sessionRepository.currentSessions(planId),
                tests = practiceRepository.currentTests(planId),
                mockExams = practiceRepository.currentMockExams(planId),
                topics = contentRepository.currentSyllabus(planId).topics,
            )
        },
    )

    val analyticsRepository = InMemoryStudyAnalyticsRepository(
        plans = planRepository,
        library = libraryRepository,
        schedule = scheduleRepository,
        sessions = sessionRepository,
        practice = practiceRepository,
        progress = progressRepository,
    )
    val discoveryRepository = DesktopStudyDiscoveryRepository(
        plans = planRepository,
        library = libraryRepository,
        schedule = scheduleRepository,
        practice = practiceRepository,
        analytics = analyticsRepository,
    )
    val services = DesktopStudyFlowServices(
        planRepository = planRepository,
        contentRepository = contentRepository,
        libraryRepository = libraryRepository,
        scheduleRepository = scheduleRepository,
        sessionRepository = sessionRepository,
        practiceRepository = practiceRepository,
        reviewRepository = reviewRepository,
        analyticsRepository = analyticsRepository,
        discoveryRepository = discoveryRepository,
        preferencesStore = DesktopPreferencesStore(
            initialPreferences = persisted?.preferences ?: AppPreferences(
                dynamicColorEnabled = false,
                defaultStudyPlanId = sample.plans.firstOrNull()?.id,
            ),
            onChanged = { preferences -> workspacePersistence?.updatePreferences(preferences) },
        ),
        platformPorts = platformPorts,
        linkService = linkService,
        workspacePersistence = workspacePersistence,
        contentModeRepository = contentModeRepository,
        publicExamRepository = publicExamRepository,
        progressRepository = progressRepository,
    )
    practiceRepository.attachWrongAnswerSink { wrongAnswers ->
        wrongAnswers.forEach { capture ->
            services.manageStudyReview.recordWrongAnswer(
                capture.question,
                capture.category,
                capture.chosenAnswer,
            )
        }
    }
    return services
}

fun InMemoryStudyLibraryRepository.currentSnapshot(planId: StudyPlanId): StudyLibrarySnapshot = StudyLibrarySnapshot(
    materials = currentMaterials(planId),
    videos = currentVideos(planId),
    annexes = currentAnnexes(planId),
)
