package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.AppShellReadinessAuditor
import com.mikeyphw.studyflow.core.domain.AppShellReadinessInput
import com.mikeyphw.studyflow.core.domain.ManageStudyAnalytics
import com.mikeyphw.studyflow.core.domain.ManageStudyDiscovery
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.ManageStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.PracticeAnalytics
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
    val analyticsRepository: InMemoryStudyAnalyticsRepository,
    val discoveryRepository: DesktopStudyDiscoveryRepository,
    val preferencesStore: DesktopPreferencesStore,
    val platformPorts: DesktopPlatformPorts,
    val linkService: DesktopStudyFlowLinkService? = null,
    val workspacePersistence: DesktopWorkspacePersistence? = null,
) {
    val manageStudyPlan: ManageStudyPlan = ManageStudyPlan(planRepository)
    val manageStudyContent: ManageStudyContent = ManageStudyContent(contentRepository)
    val manageStudyLibrary: ManageStudyLibrary = ManageStudyLibrary(libraryRepository)
    val manageStudySchedule: ManageStudySchedule = ManageStudySchedule(scheduleRepository)
    val manageStudySession: ManageStudySession = ManageStudySession(sessionRepository, planRepository)
    val manageStudyPractice: ManageStudyPractice = ManageStudyPractice(practiceRepository, planRepository)
    val manageStudyAnalytics: ManageStudyAnalytics = ManageStudyAnalytics(analyticsRepository)
    val manageStudyDiscovery: ManageStudyDiscovery = ManageStudyDiscovery(discoveryRepository)
    val planContentController = DesktopPlanContentController(
        planRepository = planRepository,
        contentRepository = contentRepository,
        preferencesStore = preferencesStore,
        managePlan = manageStudyPlan,
        manageContent = manageStudyContent,
    )
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
    val libraryRepository = InMemoryStudyLibraryRepository(
        materials = sample.materials,
        videos = sample.videos,
        annexes = sample.annexes,
    )
    val scheduleRepository = InMemoryStudyScheduleRepository(
        strategies = sample.strategies,
        scheduleRules = sample.scheduleRules,
        plannedActivities = sample.plannedActivities,
    )
    val sessionRepository = InMemoryStudySessionRepository(sample.sessions)
    val practiceRepository = InMemoryStudyPracticeRepository(
        questions = sample.questions,
        tests = sample.tests,
        mockExams = sample.mockExams,
    )
    val analyticsRepository = InMemoryStudyAnalyticsRepository(
        plans = planRepository,
        library = libraryRepository,
        schedule = scheduleRepository,
        sessions = sessionRepository,
        practice = practiceRepository,
    )
    val discoveryRepository = DesktopStudyDiscoveryRepository(
        plans = planRepository,
        library = libraryRepository,
        schedule = scheduleRepository,
        practice = practiceRepository,
        analytics = analyticsRepository,
    )
    return DesktopStudyFlowServices(
        planRepository = planRepository,
        contentRepository = contentRepository,
        libraryRepository = libraryRepository,
        scheduleRepository = scheduleRepository,
        sessionRepository = sessionRepository,
        practiceRepository = practiceRepository,
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
    )
}

fun InMemoryStudyLibraryRepository.currentSnapshot(planId: StudyPlanId): StudyLibrarySnapshot = StudyLibrarySnapshot(
    materials = currentMaterials(planId),
    videos = currentVideos(planId),
    annexes = currentAnnexes(planId),
)
