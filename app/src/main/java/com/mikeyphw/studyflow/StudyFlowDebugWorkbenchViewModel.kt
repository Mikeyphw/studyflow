package com.mikeyphw.studyflow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthLabReport
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.DatabasePortabilityLabReport
import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeId
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeReport
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeReportPolicy
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeResult
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeStatus
import com.mikeyphw.studyflow.core.diagnostics.DebugWorkbenchShellPolicy
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticTimelineSnapshot
import com.mikeyphw.studyflow.core.diagnostics.ReminderLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.ReminderNotificationLabReport
import com.mikeyphw.studyflow.core.diagnostics.RuntimeSelfCheckHistory
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabMessage
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabReport
import com.mikeyphw.studyflow.core.diagnostics.SupportBundleSealReport
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.feature.settings.DebugWorkbenchActionResult
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class DebugWorkbenchUiState(
    val timeline: DiagnosticTimelineSnapshot = DiagnosticTimelineSnapshot(emptyList(), 0, 0),
    val probeReport: DebugProbeReport = DebugProbeReport.empty(),
    val safeSuiteRunning: Boolean = false,
    val actionResult: DebugWorkbenchActionResult? = null,
    val selfCheckHistory: RuntimeSelfCheckHistory = RuntimeSelfCheckHistory(),
    val supportBundleSealReport: SupportBundleSealReport = SupportBundleSealReport.notRun(),
    val reminderLabReport: ReminderNotificationLabReport = ReminderNotificationLabReport(
        status = DebugLabStatus.NOT_RUN),
    val reminderLabAction: ReminderLabActionResult? = null,
    val reminderLabRunning: Boolean = false,
    val databasePortabilityReport: DatabasePortabilityLabReport = DatabasePortabilityLabReport(
        database = DatabaseHealthLabReport(status = DebugLabStatus.NOT_RUN),
    ),
    val databasePortabilityAction: DatabasePortabilityLabActionResult? = null,
    val databasePortabilityRunning: Boolean = false,
    val studyEngineReport: StudyEngineLabReport = StudyEngineLabReport(status = DebugLabStatus.NOT_RUN),
    val studyEngineAction: StudyEngineLabActionResult? = null,
    val studyEngineRunning: Boolean = false,
)

internal class StudyFlowDebugWorkbenchViewModel(
    context: Context,
    private val container: AppContainer,
    private val clipboardLabel: String,
    chooserTitle: String,
) : ViewModel() {
    private val appContext = context.applicationContext
    @Volatile private var defaultPlanId: StudyPlanId? = null
    private val historyStore = RuntimeSelfCheckHistoryStore(appContext)
    private val debugActions = StudyFlowDebugWorkbenchActions.android(
        context = appContext,
        store = container.diagnosticRecorder,
        clipboardLabel = clipboardLabel,
        chooserTitle = chooserTitle,
    )
    private val probeRegistry = StudyFlowDebugProbeRegistry(
        context = appContext,
        container = container,
        defaultPlanId = { defaultPlanId },
    )
    private val selfCheckSuite = StudyFlowRuntimeSelfCheckSuite(
        runProbe = probeRegistry::run,
        recorder = container.diagnosticRecorder,
    )
    private val reminderLab = StudyFlowReminderNotificationLab(
        context = appContext,
        container = container,
        defaultPlanId = { defaultPlanId },
    )
    private val databasePortabilityLab = StudyFlowDatabasePortabilityLab(
        context = appContext,
        container = container,
        defaultPlanId = { defaultPlanId },
    )
    private val studyEngineLab = StudyFlowStudyEngineLab(
        container = container,
        defaultPlanId = { defaultPlanId },
    )
    private val probeMutex = Mutex()
    private val _uiState = MutableStateFlow(
        DebugWorkbenchUiState(selfCheckHistory = historyStore.load()),
    )
    val uiState: StateFlow<DebugWorkbenchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refreshDiagnosticsInternal() }
    }

    fun updateDefaultPlanId(value: StudyPlanId?) {
        defaultPlanId = value
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(actionResult = null) }
            val success = refreshDiagnosticsInternal()
            _uiState.update {
                it.copy(actionResult = if (success) DebugWorkbenchActionResult
                    .REFRESHED else DebugWorkbenchActionResult.REFRESH_FAILED)
            }
        }
    }

    fun copyStatus() {
        viewModelScope.launch {
            val report = DebugWorkbenchShellPolicy.evaluate(container.diagnosticRecorder, _uiState.value.timeline)
            _uiState.update {
                it.copy(
                    actionResult = if (copyText(report.toClipboardReport())) {
                        DebugWorkbenchActionResult.STATUS_COPIED
                    } else {
                        DebugWorkbenchActionResult.COPY_FAILED
                    },
                )
            }
        }
    }

    fun copyTimeline() {
        viewModelScope.launch {
            val result = try {
                val text = debugActions.copyTimeline()
                if (copyText(text)) DebugWorkbenchActionResult
                    .TIMELINE_COPIED else DebugWorkbenchActionResult.COPY_FAILED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DebugWorkbenchActionResult.COPY_FAILED
            }
            _uiState.update { it.copy(actionResult = result) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val cleared = try {
                debugActions.clearHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (cleared) {
                val emptyHistory = RuntimeSelfCheckHistory()
                historyStore.save(emptyHistory)
                _uiState.update { it.copy(selfCheckHistory = emptyHistory) }
                refreshDiagnosticsInternal()
            }
            _uiState.update {
                it.copy(actionResult = if (cleared) DebugWorkbenchActionResult
                    .HISTORY_CLEARED else DebugWorkbenchActionResult.HISTORY_BUSY)
            }
        }
    }

    fun exportSupportBundle() {
        viewModelScope.launch {
            val result = debugActions.exportAndShare()
            _uiState.update {
                it.copy(
                    actionResult = result,
                    supportBundleSealReport = debugActions.supportBundleSealSnapshot(),
                )
            }
        }
    }

    fun runProbe(id: DebugProbeId) {
        viewModelScope.launch {
            probeMutex.withLock {
                if (_uiState.value.safeSuiteRunning) return@withLock
                _uiState.update { it.copy(probeReport = DebugProbeReportPolicy.markRunning(it.probeReport, id)) }
                try {
                    val result = probeRegistry.run(id)
                    _uiState.update { it.copy(probeReport = DebugProbeReportPolicy.complete(it.probeReport, result)) }
                } catch (cancelled: CancellationException) {
                    _uiState.update {
                        it.copy(
                            probeReport = DebugProbeReportPolicy.complete(
                                it.probeReport,
                                DebugProbeResult(
                                    id = id,
                                    status = DebugProbeStatus.CANCELLED,
                                    completedAtEpochMillis = System.currentTimeMillis(),
                                ),
                            ),
                        )
                    }
                    throw cancelled
                } finally {
                    refreshDiagnosticsInternal()
                }
            }
        }
    }

    fun runSafeSuite() {
        viewModelScope.launch {
            probeMutex.withLock {
                if (_uiState.value.safeSuiteRunning || _uiState.value.probeReport.running.isNotEmpty()) return@withLock
                _uiState.update { it.copy(safeSuiteRunning = true) }
                try {
                    val (report, run) = selfCheckSuite.run(_uiState.value.probeReport)
                    recordSelfCheck(report, run)
                } catch (cancelled: RuntimeSelfCheckCancellation) {
                    recordSelfCheck(cancelled.report, cancelled.run)
                    throw cancelled
                } finally {
                    _uiState.update { it.copy(safeSuiteRunning = false) }
                    refreshDiagnosticsInternal()
                }
            }
        }
    }

    fun inspectReminders() = launchReminderLab { lab -> lab.inspect().let { (report, result) ->
        _uiState.update { it.copy(reminderLabReport = report, reminderLabAction = result) }
    } }

    fun reconcileReminders() = launchReminderLab { lab ->
        val result = lab.reconcile()
        val report = lab.inspect().first
        _uiState.update { it.copy(reminderLabReport = report, reminderLabAction = result) }
    }

    fun postDiagnosticNotification() = launchReminderLab { lab ->
        val result = lab.postDiagnosticNotification()
        val report = lab.inspect().first
        _uiState.update { it.copy(reminderLabReport = report, reminderLabAction = result) }
    }

    fun inspectDatabase() = launchDatabaseLab { lab ->
        val (report, result) = lab.inspectDatabase(_uiState.value.databasePortabilityReport)
        _uiState.update { it.copy(databasePortabilityReport = report, databasePortabilityAction = result) }
    }

    fun runPortabilityDrills() = launchDatabaseLab { lab ->
        val (report, result) = lab.runPortabilityDrills(_uiState.value.databasePortabilityReport)
        _uiState.update { it.copy(databasePortabilityReport = report, databasePortabilityAction = result) }
    }

    fun inspectStudyEngine() {
        if (_uiState.value.studyEngineRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(studyEngineRunning = true) }
            try {
                val (report, result) = studyEngineLab.inspect()
                _uiState.update { it.copy(studyEngineReport = report, studyEngineAction = result) }
            } catch (cancelled: CancellationException) {
                _uiState.update {
                    it.copy(
                        studyEngineReport = it.studyEngineReport.copy(
                            status = DebugLabStatus.CANCELLED,
                            message = StudyEngineLabMessage.INSPECTION_FAILED,
                        ),
                    )
                }
                throw cancelled
            } finally {
                _uiState.update { it.copy(studyEngineRunning = false) }
                refreshDiagnosticsInternal()
            }
        }
    }

    private fun launchReminderLab(block: suspend (StudyFlowReminderNotificationLab) -> Unit) {
        if (_uiState.value.reminderLabRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(reminderLabRunning = true) }
            try { block(reminderLab) } finally {
                _uiState.update { it.copy(reminderLabRunning = false) }
                refreshDiagnosticsInternal()
            }
        }
    }

    private fun launchDatabaseLab(block: suspend (StudyFlowDatabasePortabilityLab) -> Unit) {
        if (_uiState.value.databasePortabilityRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(databasePortabilityRunning = true) }
            try { block(databasePortabilityLab) } finally {
                _uiState.update { it.copy(databasePortabilityRunning = false) }
                refreshDiagnosticsInternal()
            }
        }
    }

    private fun recordSelfCheck(report: DebugProbeReport, run: com.mikeyphw.studyflow.core.diagnostics
        .RuntimeSelfCheckRun) {
        val history = _uiState.value.selfCheckHistory.record(run)
        historyStore.save(history)
        _uiState.update { it.copy(probeReport = report, selfCheckHistory = history) }
    }

    private suspend fun refreshDiagnosticsInternal(): Boolean = try {
        val timeline = debugActions.loadTimeline()
        _uiState.update { it.copy(timeline = timeline) }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private fun copyText(text: String): Boolean = try {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, text))
        true
    } catch (_: Exception) {
        false
    }

    companion object {
        fun factory(
            context: Context,
            container: AppContainer,
            clipboardLabel: String,
            chooserTitle: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StudyFlowDebugWorkbenchViewModel(
                context = context,
                container = container,
                clipboardLabel = clipboardLabel,
                chooserTitle = chooserTitle,
            ) as T
        }
    }
}
