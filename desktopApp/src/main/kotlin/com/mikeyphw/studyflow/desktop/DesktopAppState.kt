package com.mikeyphw.studyflow.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import kotlinx.coroutines.runBlocking
import com.mikeyphw.studyflow.core.model.ThemeMode

class DesktopAppState(
    val services: DesktopStudyFlowServices,
    val shellState: DesktopShellState,
) {
    var selectedRoute: DesktopRoute by mutableStateOf(DesktopRoute.DASHBOARD)
        private set

    var selectedMaterialId: StudyMaterialId? by mutableStateOf(
        services.defaultPlanId?.let { planId ->
            services.libraryRepository.currentMaterials(planId).firstOrNull()?.id
        },
    )
        private set

    var searchQuery: String by mutableStateOf("compose")
        private set

    var planNameDraft: String by mutableStateOf("")
        private set

    var subjectNameDraft: String by mutableStateOf("")
        private set

    var topicNameDraft: String by mutableStateOf("")
        private set

    var structureActionMessage: String by mutableStateOf("")
        private set

    var plannerActivityTitleDraft: String by mutableStateOf("")
        private set

    var plannerActionMessage: String by mutableStateOf("")
        private set

    var sessionActionMessage: String by mutableStateOf("")
        private set

    var practiceAnswerDraft: String by mutableStateOf("")
        private set

    var practiceActionMessage: String by mutableStateOf("")
        private set

    var mockExamAnswerDraft: String by mutableStateOf("")
        private set

    var mockExamActionMessage: String by mutableStateOf("")
        private set

    var linkQrInput: String by mutableStateOf("")
        private set

    var linkManualEndpoint: String by mutableStateOf("")
        private set

    var linkActionMessage: String by mutableStateOf("")
        private set

    private var linkRevision: Int by mutableStateOf(0)

    fun selectRoute(route: DesktopRoute) {
        selectedRoute = route
    }

    fun selectMaterial(materialId: StudyMaterialId) {
        selectedMaterialId = materialId
    }

    fun planStructureState(): DesktopPlanStructureState = services.planContentController.state()

    fun updatePlanNameDraft(value: String) {
        planNameDraft = value
    }

    fun updateSubjectNameDraft(value: String) {
        subjectNameDraft = value
    }

    fun updateTopicNameDraft(value: String) {
        topicNameDraft = value
    }

    fun selectPlan(planId: com.mikeyphw.studyflow.core.model.StudyPlanId) {
        runCatching { services.planContentController.selectPlan(planId) }
            .onSuccess { structureActionMessage = "Study Plan selected" }
            .onFailure { structureActionMessage = it.message.orEmpty() }
    }

    fun createPlan() = runStructureAction("Study Plan created") {
        services.planContentController.createPlan(planNameDraft)
        planNameDraft = ""
    }

    fun renameSelectedPlan() = runStructureAction("Study Plan renamed") {
        services.planContentController.renameSelected(planNameDraft)
        planNameDraft = ""
    }

    fun archiveSelectedPlan() = runStructureAction("Study Plan archived") {
        services.planContentController.archiveSelected()
    }

    fun restoreSelectedPlan() = runStructureAction("Study Plan restored") {
        services.planContentController.restoreSelected()
    }

    fun createSubject() = runStructureAction("Subject created") {
        services.planContentController.createSubject(subjectNameDraft)
        subjectNameDraft = ""
    }

    fun archiveSubject(subjectId: SubjectId) = runStructureAction("Subject archived") {
        services.planContentController.archiveSubject(subjectId)
    }

    fun deleteSubject(subjectId: SubjectId) = runStructureAction("Subject deleted") {
        services.planContentController.deleteSubject(subjectId)
    }

    fun createTopic(subjectId: SubjectId) = runStructureAction("Topic created") {
        services.planContentController.createTopic(subjectId, topicNameDraft)
        topicNameDraft = ""
    }

    fun archiveTopic(topicId: TopicId) = runStructureAction("Topic archived") {
        services.planContentController.archiveTopic(topicId)
    }

    fun deleteTopic(topicId: TopicId) = runStructureAction("Topic deleted") {
        services.planContentController.deleteTopic(topicId)
    }

    private fun runStructureAction(success: String, action: suspend () -> Unit) {
        runCatching { runBlocking { action() } }
            .onSuccess { structureActionMessage = success }
            .onFailure { structureActionMessage = it.message.orEmpty() }
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun cycleThemeMode() {
        val next = when (settingsState().themeMode) {
            ThemeMode.SYSTEM_DEFAULT -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM_DEFAULT
        }
        services.setTheme(next)
    }

    fun cycleLanguage() {
        val next = when (settingsState().language) {
            AppLanguage.SYSTEM_DEFAULT -> AppLanguage.ENGLISH
            AppLanguage.ENGLISH -> AppLanguage.PORTUGUESE_BRAZIL
            AppLanguage.PORTUGUESE_BRAZIL -> AppLanguage.SYSTEM_DEFAULT
        }
        services.setLanguage(next)
    }

    fun dashboardState(): DesktopDashboardState = services.createDashboardState()

    fun libraryState(): DesktopLibraryScreenState = services.createLibraryState()

    fun plannerState(): DesktopPlannerScreenState = services.createPlannerState()

    fun plannerAuthoringState(): DesktopPlannerAuthoringState = services.plannerAuthoringController.state()

    fun updatePlannerActivityTitle(value: String) {
        plannerActivityTitleDraft = value
    }

    fun ensurePlannerStrategy() = runPlannerAction("Adaptive strategy ready") {
        services.plannerAuthoringController.ensureAdaptiveStrategy()
    }

    fun addPlannerRule() = runPlannerAction("Recurring study block added") {
        services.plannerAuthoringController.addEveningRule()
    }

    fun addPlannerActivity() = runPlannerAction("Planned activity added") {
        services.plannerAuthoringController.addManualActivity(plannerActivityTitleDraft)
        plannerActivityTitleDraft = ""
    }

    fun generatePlannerWeek() = runPlannerAction("Seven-day plan generated") {
        services.plannerAuthoringController.generateSevenDays()
    }

    fun applyPlannerAdaptivePreview() = runPlannerAction("Adaptive preview applied") {
        services.plannerAuthoringController.applyAdaptivePreview()
    }

    fun rebalancePlannerMissed() = runPlannerAction("Missed activities rebalanced") {
        services.plannerAuthoringController.rebalanceMissed()
    }

    private fun runPlannerAction(success: String, action: suspend () -> Unit) {
        runCatching { runBlocking { action() } }
            .onSuccess { plannerActionMessage = success }
            .onFailure { plannerActionMessage = it.message.orEmpty() }
    }

    fun sessionRuntimeState(): DesktopSessionRuntimeState = runBlocking { services.sessionRuntimeController.state() }

    fun startNextSessionActivity() = runSessionAction("Study Session started") {
        services.sessionRuntimeController.startNextActivity()
    }

    fun startQuickSession() = runSessionAction("Quick Study Session started") {
        services.sessionRuntimeController.startQuickSession()
    }

    fun pauseSession() = runSessionAction("Study Session paused") { services.sessionRuntimeController.pause() }

    fun resumeSession() = runSessionAction("Study Session resumed") { services.sessionRuntimeController.resume() }

    fun completeSession() = runSessionAction("Study Session completed") { services.sessionRuntimeController.complete() }

    fun cancelSession() = runSessionAction("Study Session cancelled") { services.sessionRuntimeController.cancel() }

    private fun runSessionAction(success: String, action: suspend () -> Unit) {
        runCatching { runBlocking { action() } }
            .onSuccess { sessionActionMessage = success }
            .onFailure { sessionActionMessage = it.message.orEmpty() }
    }

    fun practiceState(): DesktopPracticeScreenState = services.createPracticeState()

    fun practiceExecutionState(): DesktopPracticeExecutionState = services.practiceExecutionController.state()

    fun updatePracticeAnswer(value: String) {
        practiceAnswerDraft = value
    }

    fun createPracticeAttempt() = runPracticeAction("Practice Test created") {
        services.practiceExecutionController.createAttempt("Desktop practice")
    }

    fun startPracticeAttempt() = runPracticeAction("Practice Test started") {
        services.practiceExecutionController.start()
    }

    fun answerPracticeQuestion() = runPracticeAction("Answer saved") {
        services.practiceExecutionController.answerCurrent(practiceAnswerDraft, elapsedSeconds = 0L)
        practiceAnswerDraft = ""
    }

    fun finishPracticeAttempt() = runPracticeAction("Practice Test submitted") {
        services.practiceExecutionController.finish()
    }

    fun togglePracticeFavorite(questionId: com.mikeyphw.studyflow.core.model.QuestionId) =
        runPracticeAction("Favorite updated") { services.practiceExecutionController.toggleFavorite(questionId) }

    fun togglePracticeDisputed(questionId: com.mikeyphw.studyflow.core.model.QuestionId) =
        runPracticeAction("Disputed state updated") { services.practiceExecutionController.toggleDisputed(questionId) }

    private fun runPracticeAction(success: String, action: suspend () -> Unit) {
        runCatching { runBlocking { action() } }
            .onSuccess { practiceActionMessage = success }
            .onFailure { practiceActionMessage = it.message.orEmpty() }
    }

    fun mockExamStudioState(): DesktopMockExamStudioState = services.mockExamStudioController.state()

    fun updateMockExamAnswer(value: String) {
        mockExamAnswerDraft = value
    }

    fun createMockExam() = runMockExamAction("Mock Exam created") { services.mockExamStudioController.create() }

    fun startMockExam() = runMockExamAction("Mock Exam started") { services.mockExamStudioController.start() }

    fun answerMockExamQuestion() = runMockExamAction("Mock Exam answer saved") {
        services.mockExamStudioController.answerCurrent(mockExamAnswerDraft, elapsedSeconds = 0L)
        mockExamAnswerDraft = ""
    }

    fun finishMockExam() = runMockExamAction("Mock Exam submitted") { services.mockExamStudioController.finish() }

    private fun runMockExamAction(success: String, action: suspend () -> Unit) {
        runCatching { runBlocking { action() } }
            .onSuccess { mockExamActionMessage = success }
            .onFailure { mockExamActionMessage = it.message.orEmpty() }
    }

    fun analyticsState(): DesktopAnalyticsScreenState = services.createAnalyticsState()

    fun searchState(): DesktopSearchScreenState = services.createSearchState(searchQuery)

    fun settingsState(): DesktopSettingsScreenState = services.createSettingsState()

    fun updateLinkQrInput(value: String) {
        linkQrInput = value
    }

    fun updateLinkManualEndpoint(value: String) {
        linkManualEndpoint = value
    }

    fun createLinkPairingOffer() {
        val service = services.linkService ?: return
        linkActionMessage = runCatching {
            val offer = service.createPairingOffer()
            "Pairing offer ready until ${offer.expiresAtEpochMillis}"
        }.getOrElse { error -> "Pairing offer failed: ${error.message.orEmpty()}" }
        linkRevision += 1
    }

    fun pairLinkFromQr() {
        val service = services.linkService ?: return
        linkActionMessage = runCatching {
            val peer = service.pairFromQr(linkQrInput.trim())
            "Paired with ${peer.peer.displayName}"
        }.getOrElse { error -> "Pairing failed: ${error.message.orEmpty()}" }
        linkRevision += 1
    }

    fun rememberLinkEndpoint() {
        val service = services.linkService ?: return
        linkActionMessage = runCatching {
            service.rememberManualEndpoint(linkManualEndpoint.trim())
            "Endpoint remembered"
        }.getOrElse { error -> "Endpoint failed: ${error.message.orEmpty()}" }
        linkRevision += 1
    }

    fun syncLinkPeers() {
        val service = services.linkService ?: return
        val workspace = linkWorkspaceId() ?: return
        linkActionMessage = runCatching {
            val results = service.syncAll(workspace)
            "Sync complete for ${results.size} peer(s)"
        }.getOrElse { error -> "Sync failed: ${error.message.orEmpty()}" }
        linkRevision += 1
    }

    fun revokeLinkPeer(deviceId: String) {
        services.linkService?.revoke(StudyFlowDeviceId(deviceId))
        linkActionMessage = "Peer revoked"
        linkRevision += 1
    }

    fun forgetLinkPeer(deviceId: String) {
        services.linkService?.forget(StudyFlowDeviceId(deviceId))
        linkActionMessage = "Peer forgotten"
        linkRevision += 1
    }

    fun linkState(): DesktopStudyFlowLinkState? {
        linkRevision // Compose dependency for explicit UI actions.
        return services.linkService?.snapshot(linkWorkspaceId())
    }

    fun close() {
        services.linkService?.close()
    }

    private fun linkWorkspaceId(): StudyFlowWorkspaceId? = services.defaultPlanId?.let { planId ->
        StudyFlowWorkspaceId("plan-${planId.value}")
    }
}

enum class DesktopRoute(val title: String) {
    DASHBOARD("Dashboard"),
    PLAN("Plan"),
    LIBRARY("Library"),
    PLANNER("Planner"),
    PRACTICE("Practice"),
    ANALYTICS("Analytics"),
    SEARCH("Search"),
    SETTINGS("Settings"),
}

enum class DesktopScreenStatus {
    LOADING,
    CONTENT,
    EMPTY,
    ERROR,
}
