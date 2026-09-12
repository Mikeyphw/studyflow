package com.mikeyphw.studyflow

import android.os.SystemClock
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.mikeyphw.studyflow.core.data.createStudyFlowDataLayer
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorderProvider
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthLabReport
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticStore
import com.mikeyphw.studyflow.core.domain.CreateStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageAdaptiveReschedule
import com.mikeyphw.studyflow.core.domain.ManageExamReadiness
import com.mikeyphw.studyflow.core.domain.ManagePublicExam
import com.mikeyphw.studyflow.core.domain.ManageAnnexFileStorage
import com.mikeyphw.studyflow.core.domain.ManageStudyAnalytics
import com.mikeyphw.studyflow.core.domain.ManageStudyCloudSync
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyDiscovery
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.ManageStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageStudyPlanPortability
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyProgress
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudyResourceViewer
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.i18n.AppPreferencesRepository
import com.mikeyphw.studyflow.core.i18n.DataStoreAppPreferencesRepository
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator
import com.mikeyphw.studyflow.link.AndroidStudyFlowAttentionLedgerStorage
import com.mikeyphw.studyflow.link.AndroidStudyFlowLinkPeer
import com.mikeyphw.studyflow.tvcontrol.StudyFlowTvPresentationSource
import com.mikeyphw.studyflow.tvcontrol.StudyFlowTvDomainActionExecutor
import com.mikeyphw.studyflow.tvcontrol.SharedPreferencesStudyFlowTvDomainActionJournal
import com.mikeyphw.studyflow.tvcontrol.DefaultStudyFlowTvCanonicalMutationGateway
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedger
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrenceId
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionWorkspace
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.tvlink.StudyFlowTvLocalTransportClient
import com.mikeyphw.studyflow.core.tvlink.StudyFlowTvReceiverDiscovery
import com.mikeyphw.studyflow.reminders.StudyReminderDeliveryCoordinator
import com.mikeyphw.studyflow.reminders.StudyReminderOccurrenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppInitializationState {
    data object Initializing : AppInitializationState

    data object Ready : AppInitializationState

    data class Failed(val causeType: String) : AppInitializationState
}

internal fun Context.requireStudyFlowAppContainer(): AppContainer =
    ((applicationContext as? StudyFlowApplication)?.container)
        ?: error("StudyFlow process container is unavailable.")

class StudyFlowApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val diagnosticRecorder = createStudyFlowDiagnosticRecorder(this)
        container = diagnosticRecorder.recordStep(
            area = DiagnosticArea.APP_LIFECYCLE,
            action = "application-container-create",
            safeDetails = mapOf(
                "version" to BuildConfig.VERSION_NAME,
                "buildType" to BuildConfig.BUILD_TYPE,
            ),
        ) {
            DefaultAppContainer(
                application = this,
                diagnosticRecorder = diagnosticRecorder,
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::container.isInitialized) container.refreshLocalizedSurfaces()
    }
}

interface AppContainer : DiagnosticRecorderProvider {
    val initializationState: StateFlow<AppInitializationState>
    fun retryInitialization()
    val appPreferencesRepository: AppPreferencesRepository
    suspend fun updateAppLanguage(language: AppLanguage)
    fun refreshLocalizedSurfaces()
    val reminderDeliveryCoordinator: StudyReminderDeliveryCoordinator
    val attentionLedger: StudyFlowAttentionLedger
    val createStudyPlan: CreateStudyPlan
    val samplePlanProvisioner: SamplePlanProvisioner
    val manageStudyPlan: ManageStudyPlan
    val manageStudyContent: ManageStudyContent
    val manageStudyDiscovery: ManageStudyDiscovery
    val manageStudyCloudSync: ManageStudyCloudSync
    val manageStudySchedule: ManageStudySchedule
    val manageStudySession: ManageStudySession
    val managePublicExam: ManagePublicExam
    val manageStudyPractice: ManageStudyPractice
    val manageStudyLibrary: ManageStudyLibrary
    val manageStudyReview: ManageStudyReview
    val manageStudyResourceViewer: ManageStudyResourceViewer
    val manageStudyProgress: ManageStudyProgress
    val manageStudyAnalytics: ManageStudyAnalytics
    val manageExamReadiness: ManageExamReadiness
    val manageAdaptiveReschedule: ManageAdaptiveReschedule
    val manageAnnexFileStorage: ManageAnnexFileStorage
    val manageStudyPlanPortability: ManageStudyPlanPortability
    val tvPresentationSource: StudyFlowTvPresentationSource
    val tvDomainActionExecutor: StudyFlowTvDomainActionExecutor
    val tvReceiverDiscovery: StudyFlowTvReceiverDiscovery
    val tvLinkClient: StudyFlowTvLocalTransportClient
    val linkPeer: AndroidStudyFlowLinkPeer
    suspend fun inspectDatabaseHealth(): DatabaseHealthLabReport
}

private class DefaultAppContainer(
    private val application: Application,
    override val diagnosticRecorder: DiagnosticStore,
) : AppContainer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _initializationState = MutableStateFlow<AppInitializationState>(AppInitializationState.Initializing)
    override val initializationState: StateFlow<AppInitializationState> = _initializationState.asStateFlow()
    private var initializationJob: Job? = null
    private var widgetInvalidationJob: Job? = null
    private var reminderInvalidationJob: Job? = null
    private val dataLayer = createStudyFlowDataLayer(
        context = application,
        applicationVersion = BuildConfig.VERSION_NAME,
        diagnosticRecorder = diagnosticRecorder,
    )

    private val studyPlanRepository = dataLayer.studyPlanRepository
    private val studyContentRepository = dataLayer.studyContentRepository
    private val studyDiscoveryRepository = dataLayer.studyDiscoveryRepository
    private val studyCloudSyncRepository = dataLayer.studyCloudSyncRepository
    private val studyScheduleRepository = dataLayer.studyScheduleRepository
    private val studySessionRepository = dataLayer.studySessionRepository
    private val studyPracticeRepository = dataLayer.studyPracticeRepository
    private val studyLibraryRepository = dataLayer.studyLibraryRepository
    private val studyReviewRepository = dataLayer.studyReviewRepository
    private val studyProgressRepository = dataLayer.studyProgressRepository
    private val studyAnalyticsRepository = dataLayer.studyAnalyticsRepository
    private val annexFileStorageRepository = dataLayer.annexFileStorageRepository
    override val appPreferencesRepository: AppPreferencesRepository =
        DataStoreAppPreferencesRepository(application)
    override suspend fun updateAppLanguage(language: AppLanguage) {
        applicationScope.async {
            appPreferencesRepository.setLanguage(language)
            reminderDeliveryCoordinator.refreshLocalizedPresentation()
        }.await()
    }

    override fun refreshLocalizedSurfaces() {
        applicationScope.launch {
            try {
                reminderDeliveryCoordinator.refreshLocalizedPresentation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Locale refresh is recoverable; startup/data readiness remains authoritative.
            }
        }
    }

    override val attentionLedger = StudyFlowAttentionLedger(
        AndroidStudyFlowAttentionLedgerStorage(application),
    )
    override val reminderDeliveryCoordinator = StudyReminderDeliveryCoordinator(
        context = application,
        studyPlanRepository = studyPlanRepository,
        studyScheduleRepository = studyScheduleRepository,
        studyReviewRepository = studyReviewRepository,
        studyPracticeRepository = studyPracticeRepository,
        studySessionRepository = studySessionRepository,
        attentionLedger = attentionLedger,
        linkPeerProvider = { linkPeer },
    )
    override val createStudyPlan = CreateStudyPlan(studyPlanRepository)
    override val samplePlanProvisioner = SamplePlanProvisioner(
        applicationScope = applicationScope,
        repository = dataLayer.samplePlanProvisioningRepository,
        preferencesRepository = appPreferencesRepository,
        journal = SharedPreferencesSamplePlanProvisioningJournal(application),
    )
    override val manageStudyPlan = ManageStudyPlan(studyPlanRepository)
    override val manageStudyContent = ManageStudyContent(studyContentRepository)
    override val manageStudyDiscovery = ManageStudyDiscovery(studyDiscoveryRepository)
    override val manageStudyCloudSync = ManageStudyCloudSync(studyCloudSyncRepository)
    override val manageStudySchedule = ManageStudySchedule(studyScheduleRepository)
    override val manageStudySession = ManageStudySession(
        repository = studySessionRepository,
        studyPlanRepository = studyPlanRepository,
        monotonicClockMillis = SystemClock::elapsedRealtime,
    )
    override val managePublicExam = ManagePublicExam(
        repository = dataLayer.publicExamRepository,
        sessionManager = manageStudySession,
    )
    override val manageStudyPractice = ManageStudyPractice(studyPracticeRepository, studyPlanRepository)
    override val manageStudyLibrary = ManageStudyLibrary(studyLibraryRepository)
    override val manageStudyReview = ManageStudyReview(
        studyReviewRepository,
        studyPracticeRepository,
        studyPlanRepository = studyPlanRepository,
    )
    override val manageStudyResourceViewer = ManageStudyResourceViewer(studyLibraryRepository)
    override val manageStudyProgress = ManageStudyProgress(studyProgressRepository)
    override val manageStudyAnalytics = ManageStudyAnalytics(studyAnalyticsRepository)
    override val manageExamReadiness = ManageExamReadiness(
        analyticsRepository = studyAnalyticsRepository,
        scheduleRepository = studyScheduleRepository,
        practiceRepository = studyPracticeRepository,
    )
    override val manageAdaptiveReschedule = ManageAdaptiveReschedule(
        analyticsRepository = studyAnalyticsRepository,
        scheduleRepository = studyScheduleRepository,
        readinessManager = manageExamReadiness,
        studyPlanRepository = studyPlanRepository,
    )
    override val manageAnnexFileStorage = ManageAnnexFileStorage(annexFileStorageRepository)
    override val manageStudyPlanPortability =
        ManageStudyPlanPortability(dataLayer.studyPlanPortabilityRepository)
    override val tvPresentationSource = StudyFlowTvPresentationSource(
        planRepository = studyPlanRepository,
        contentRepository = studyContentRepository,
        scheduleRepository = studyScheduleRepository,
        sessionRepository = studySessionRepository,
        practiceRepository = studyPracticeRepository,
        reviewRepository = studyReviewRepository,
        analyticsRepository = studyAnalyticsRepository,
        libraryRepository = studyLibraryRepository,
        progressRepository = studyProgressRepository,
    )
    override val tvDomainActionExecutor = StudyFlowTvDomainActionExecutor(
        gateway = DefaultStudyFlowTvCanonicalMutationGateway(
            sessionManager = manageStudySession,
            practiceManager = manageStudyPractice,
            reviewManager = manageStudyReview,
            progressManager = manageStudyProgress,
            sessionRepository = studySessionRepository,
            practiceRepository = studyPracticeRepository,
            scheduleRepository = studyScheduleRepository,
        ),
        journal = SharedPreferencesStudyFlowTvDomainActionJournal(application),
    )
    override val tvReceiverDiscovery = StudyFlowTvReceiverDiscovery(application)
    override val tvLinkClient = StudyFlowTvLocalTransportClient(application)
    override val linkPeer = AndroidStudyFlowLinkPeer(application)

    override suspend fun inspectDatabaseHealth(): DatabaseHealthLabReport =
        dataLayer.inspectDatabaseHealth()

    init {
        diagnosticRecorder.recordStep(
            area = DiagnosticArea.NOTIFICATIONS,
            action = "notification-channel-bootstrap",
        ) {
            reminderDeliveryCoordinator.createNotificationChannels()
        }
        linkPeer.runtime.setNotificationDeliveryHandler(reminderDeliveryCoordinator::receiveRemoteNotification)
        retryInitialization()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startWidgetInvalidationObserver() {
        if (widgetInvalidationJob?.isActive == true) return
        widgetInvalidationJob = applicationScope.launch {
            combine(appPreferencesRepository.preferences, manageStudyPlan.plans) { preferences, plans ->
                Triple(preferences.language, preferences.defaultStudyPlanId, plans)
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, _, plans) ->
                    val activePlans = plans.filter { plan ->
                        plan.status !in setOf(
                            com.mikeyphw.studyflow.core.model.StudyPlanStatus.ARCHIVED,
                            com.mikeyphw.studyflow.core.model.StudyPlanStatus.TRASHED,
                        )
                    }
                    val domainFlows = activePlans.flatMap { plan ->
                        listOf(
                            manageStudySchedule.observeSchedule(plan.id).map { Unit },
                            manageStudyReview.observeQueue(plan.id).map { Unit },
                            manageStudySession.observeSessions(plan.id).map { Unit },
                            manageStudyPlan.observeSettings(plan.id).map { Unit },
                        )
                    }
                    if (domainFlows.isEmpty()) flowOf(Unit) else merge(*domainFlows.toTypedArray())
                }
                .collect { StudyFlowWidgetUpdateCoordinator.refreshAll(application) }
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun startReminderInvalidationObserver() {
        if (reminderInvalidationJob?.isActive == true) return
        reminderInvalidationJob = applicationScope.launch {
            manageStudyPlan.plans
                .distinctUntilChanged()
                .flatMapLatest { plans ->
                    val activePlans = plans.filter { plan ->
                        plan.status !in setOf(
                            com.mikeyphw.studyflow.core.model.StudyPlanStatus.ARCHIVED,
                            com.mikeyphw.studyflow.core.model.StudyPlanStatus.TRASHED,
                            com.mikeyphw.studyflow.core.model.StudyPlanStatus.COMPLETED,
                        )
                    }
                    val flows = activePlans.flatMap { plan ->
                        listOf(
                            manageStudySchedule.observeSchedule(plan.id).map { Unit },
                            manageStudyReview.observeQueue(plan.id).map { Unit },
                            manageStudySession.observeSessions(plan.id).map { Unit },
                            manageStudyPlan.observeSettings(plan.id).map { Unit },
                        )
                    }
                    if (flows.isEmpty()) flowOf(Unit) else merge(*flows.toTypedArray())
                }
                .debounce(500)
                .collect {
                    runCatching { reminderDeliveryCoordinator.reconcileAll("domain-change") }
                }
        }
    }

    private suspend fun migrateLegacyReminderStateToAttentionLedger() {
        val occurrenceStore = StudyReminderOccurrenceStore(application)
        val now = System.currentTimeMillis()
        manageStudyPlan.plans.first().forEach { plan ->
            val workspaceId = StudyFlowAttentionWorkspace.forPlanId(plan.id.value)
            occurrenceStore.scheduled(plan.id).forEach { scheduled ->
                attentionLedger.upsertScheduled(
                    occurrence = StudyFlowAttentionOccurrence(
                        occurrenceId = StudyFlowAttentionOccurrenceId.fromLegacy(scheduled.occurrenceId),
                        workspaceId = workspaceId,
                        category = when (scheduled.type) {
                            com.mikeyphw.studyflow.core.domain.ReminderNotificationType.DUE_REVIEWS ->
                                StudyFlowAttentionCategory.DUE_REVIEW
                            com.mikeyphw.studyflow.core.domain.ReminderNotificationType.PLANNED_ACTIVITY ->
                                StudyFlowAttentionCategory.PLANNED_STUDY
                            com.mikeyphw.studyflow.core.domain.ReminderNotificationType.MISSED_ACTIVITY ->
                                StudyFlowAttentionCategory.MISSED_ACTIVITY
                            com.mikeyphw.studyflow.core.domain.ReminderNotificationType.DAILY_SUMMARY ->
                                StudyFlowAttentionCategory.DAILY_SUMMARY
                            com.mikeyphw.studyflow.core.domain.ReminderNotificationType.SESSION_ENDING ->
                                StudyFlowAttentionCategory.SESSION_ENDING
                            com.mikeyphw.studyflow.core.domain.ReminderNotificationType.SESSION_COMPLETED ->
                                StudyFlowAttentionCategory.SESSION_COMPLETED
                            com.mikeyphw.studyflow.core.domain.ReminderNotificationType.EXAM_COUNTDOWN ->
                                StudyFlowAttentionCategory.EXAM_COUNTDOWN
                        },
                        scheduledAtEpochMillis = scheduled.fireAtEpochMillis,
                        originalScheduledAtEpochMillis = scheduled.scheduledAtEpochMillis,
                        sourceDeviceId = linkPeer.localDeviceId,
                    ),
                    atEpochMillis = now,
                )
            }
            attentionLedger.migrateLegacyDeliveredAliases(
                occurrenceStore.deliveredOccurrenceIds(plan.id),
            )
        }
    }

    override fun retryInitialization() {
        if (_initializationState.value is AppInitializationState.Ready) return
        if (initializationJob?.isActive == true) return
        _initializationState.value = AppInitializationState.Initializing
        initializationJob = applicationScope.launch {
            try {
                diagnosticRecorder.recordSuspendStep(
                    area = DiagnosticArea.INITIALIZATION,
                    action = "data-layer-initialize",
                ) {
                    dataLayer.initialize()
                }
                diagnosticRecorder.recordSuspendStep(
                    area = DiagnosticArea.REMINDERS,
                    action = "attention-ledger-legacy-migration",
                ) {
                    migrateLegacyReminderStateToAttentionLedger()
                }
                samplePlanProvisioner.recoverPendingCompletion()
                try {
                    diagnosticRecorder.recordStep(
                        area = DiagnosticArea.SYNC_FOUNDATION,
                        action = "sflp-peer-start",
                    ) {
                        linkPeer.start()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // SFLP peer availability is recoverable; local StudyFlow remains usable offline.
                }
                _initializationState.value = AppInitializationState.Ready
                startWidgetInvalidationObserver()
                startReminderInvalidationObserver()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _initializationState.value = AppInitializationState.Failed(error.javaClass.simpleName)
                return@launch
            }

            var reconciliationCompleted = false
            try {
                diagnosticRecorder.recordSuspendStep(
                    area = DiagnosticArea.REMINDERS,
                    action = "startup-reminder-reconciliation",
                ) {
                    reminderDeliveryCoordinator.reconcileAll()
                }
                reconciliationCompleted = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The core data layer is ready; diagnostics capture optional startup service failures.
            }
            if (!reconciliationCompleted) {
                try {
                    diagnosticRecorder.recordSuspendStep(
                        area = DiagnosticArea.WIDGET,
                        action = "startup-widget-refresh-fallback",
                    ) {
                        StudyFlowWidgetUpdateCoordinator.refreshAll(application)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The widget is a recoverable secondary surface and must not invalidate data readiness.
                }
            }
        }
    }
}
