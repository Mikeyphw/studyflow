package com.mikeyphw.studyflow

import android.os.SystemClock
import android.app.Application
import android.content.Context
import com.mikeyphw.studyflow.core.data.createStudyFlowDataLayer
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorderProvider
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthLabReport
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticStore
import com.mikeyphw.studyflow.core.domain.CreateStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageAdaptiveReschedule
import com.mikeyphw.studyflow.core.domain.ManageExamReadiness
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
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator
import com.mikeyphw.studyflow.reminders.StudyReminderDeliveryCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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
}

interface AppContainer : DiagnosticRecorderProvider {
    val initializationState: StateFlow<AppInitializationState>
    fun retryInitialization()
    val appPreferencesRepository: AppPreferencesRepository
    val reminderDeliveryCoordinator: StudyReminderDeliveryCoordinator
    val createStudyPlan: CreateStudyPlan
    val samplePlanProvisioner: SamplePlanProvisioner
    val manageStudyPlan: ManageStudyPlan
    val manageStudyContent: ManageStudyContent
    val manageStudyDiscovery: ManageStudyDiscovery
    val manageStudyCloudSync: ManageStudyCloudSync
    val manageStudySchedule: ManageStudySchedule
    val manageStudySession: ManageStudySession
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
    suspend fun inspectDatabaseHealth(): DatabaseHealthLabReport
}

private class DefaultAppContainer(
    application: Application,
    override val diagnosticRecorder: DiagnosticStore,
) : AppContainer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _initializationState = MutableStateFlow<AppInitializationState>(AppInitializationState.Initializing)
    override val initializationState: StateFlow<AppInitializationState> = _initializationState.asStateFlow()
    private var initializationJob: Job? = null
    private val dataLayer = createStudyFlowDataLayer(
        context = application,
        applicationVersion = BuildConfig.VERSION_NAME,
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
    override val reminderDeliveryCoordinator = StudyReminderDeliveryCoordinator(
        context = application,
        studyPlanRepository = studyPlanRepository,
        studyScheduleRepository = studyScheduleRepository,
        studyReviewRepository = studyReviewRepository,
        studyPracticeRepository = studyPracticeRepository,
    )
    override val createStudyPlan = CreateStudyPlan(studyPlanRepository)
    override val samplePlanProvisioner = SamplePlanProvisioner(
        applicationScope = applicationScope,
        repository = dataLayer.samplePlanProvisioningRepository,
        preferencesRepository = appPreferencesRepository,
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
    override val manageStudyPractice = ManageStudyPractice(studyPracticeRepository, studyPlanRepository)
    override val manageStudyLibrary = ManageStudyLibrary(studyLibraryRepository)
    override val manageStudyReview = ManageStudyReview(studyReviewRepository, studyPracticeRepository)
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

    override suspend fun inspectDatabaseHealth(): DatabaseHealthLabReport =
        dataLayer.inspectDatabaseHealth()

    init {
        diagnosticRecorder.recordStep(
            area = DiagnosticArea.NOTIFICATIONS,
            action = "notification-channel-bootstrap",
        ) {
            reminderDeliveryCoordinator.createNotificationChannels()
        }
        retryInitialization()
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
                _initializationState.value = AppInitializationState.Ready
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
