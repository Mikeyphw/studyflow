package com.mikeyphw.studyflow.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.domain.WritingCompletionChecklist
import com.mikeyphw.studyflow.core.domain.WritingRubric
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyGoalId
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.nio.file.Path
import com.mikeyphw.studyflow.core.model.ThemeMode

class DesktopAppState(
    val services: DesktopStudyFlowServices,
    val shellState: DesktopShellState,
) {
    private var preferencesRevision: Int by mutableStateOf(0)
    private var attentionRevision: Int by mutableStateOf(0)

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

    var flashcardFrontDraft: String by mutableStateOf("")
        private set

    var flashcardBackDraft: String by mutableStateOf("")
        private set

    var reviewActionMessage: String by mutableStateOf("")
        private set

    var libraryPathDraft: String by mutableStateOf("")
        private set

    var libraryWebTitleDraft: String by mutableStateOf("")
        private set

    var libraryWebUrlDraft: String by mutableStateOf("")
        private set

    var libraryActionMessage: String by mutableStateOf("")
        private set

    var writingPromptDraft: String by mutableStateOf("")
        private set

    var writingTextDraft: String by mutableStateOf("")
        private set

    var writingThemeScore: String by mutableStateOf("0")
        private set
    var writingTextTypeScore: String by mutableStateOf("0")
        private set
    var writingCohesionScore: String by mutableStateOf("0")
        private set
    var writingArgumentationScore: String by mutableStateOf("0")
        private set
    var writingLanguageScore: String by mutableStateOf("0")
        private set

    var writingTaskReviewed: Boolean by mutableStateOf(false)
        private set
    var writingStructureReviewed: Boolean by mutableStateOf(false)
        private set
    var writingCohesionReviewed: Boolean by mutableStateOf(false)
        private set
    var writingLanguageReviewed: Boolean by mutableStateOf(false)
        private set

    var writingActionMessage: String by mutableStateOf("")
        private set

    var languageReviewAnswerDraft: String by mutableStateOf("")
        private set

    var contentModeActionMessage: String by mutableStateOf("")
        private set

    var publicExamOrganizationDraft: String by mutableStateOf("")
        private set
    var publicExamRoleDraft: String by mutableStateOf("")
        private set
    var publicExamBoardDraft: String by mutableStateOf("")
        private set
    var publicExamDateDraft: String by mutableStateOf("")
        private set
    var publicExamEditalDraft: String by mutableStateOf("")
        private set
    var publicExamLegislationDraft: String by mutableStateOf("")
        private set
    var publicExamActionMessage: String by mutableStateOf("")
        private set

    var progressActionMessage: String by mutableStateOf("")
        private set

    var goalTitleDraft: String by mutableStateOf("")
        private set
    var goalTargetDraft: String by mutableStateOf("5")
        private set
    var goalTypeDraft: GoalType by mutableStateOf(GoalType.STUDY_TIME)
        private set
    var goalPeriodDraft: GoalPeriod by mutableStateOf(GoalPeriod.WEEKLY)
        private set
    var goalActionMessage: String by mutableStateOf("")
        private set

    var portabilityPathDraft: String by mutableStateOf("")
        private set
    var portabilityPasswordDraft: String by mutableStateOf("")
        private set

    private var portabilityPasswordSecret: CharArray = CharArray(0)
    var portabilityActionMessage: String by mutableStateOf("")
        private set

    var linkQrInput: String by mutableStateOf("")
        private set

    var linkManualEndpoint: String by mutableStateOf("")
        private set

    var linkActionMessage: String by mutableStateOf("")
        private set

    private var linkRevision: Int by mutableStateOf(0)

    private val uiActions = DesktopUiActionRunner()

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
        uiActions.launch(
            actionName = success,
            onSuccess = { structureActionMessage = success },
            onFailure = { error -> structureActionMessage = error.cause?.message ?: error.message.orEmpty() },
            action = action,
        )
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
        preferencesRevision += 1
    }

    fun cycleLanguage() {
        val next = when (settingsState().language) {
            AppLanguage.SYSTEM_DEFAULT -> AppLanguage.ENGLISH
            AppLanguage.ENGLISH -> AppLanguage.PORTUGUESE_BRAZIL
            AppLanguage.PORTUGUESE_BRAZIL -> AppLanguage.SYSTEM_DEFAULT
        }
        services.setLanguage(next)
        preferencesRevision += 1
    }

    fun currentLanguage(): AppLanguage {
        preferencesRevision
        return settingsState().language
    }

    fun currentThemeMode(): ThemeMode {
        preferencesRevision
        return settingsState().themeMode
    }

    fun dashboardState(): DesktopDashboardState = services.createDashboardState()

    fun libraryState(): DesktopLibraryScreenState = services.createLibraryState()

    fun libraryViewerState(): DesktopMaterialViewerState =
        services.libraryWorkspaceController.viewerState(selectedMaterialId)

    fun updateLibraryPath(value: String) { libraryPathDraft = value }
    fun updateLibraryWebTitle(value: String) { libraryWebTitleDraft = value }
    fun updateLibraryWebUrl(value: String) { libraryWebUrlDraft = value }

    fun chooseLibraryImportFile() {
        services.platformPorts.fileDialog.chooseImportPath()?.let(::updateLibraryPath)
    }

    fun openSelectedMaterialExternally() = runLibraryAction("Opened with native Desktop handler") {
        val target = requireNotNull(services.libraryWorkspaceController.externalTarget(selectedMaterialId)) {
            "Select a file or web material first."
        }
        if (target.startsWith("https://") || target.startsWith("http://")) {
            services.platformPorts.filesystem.openUrl(target)
        } else {
            services.platformPorts.filesystem.openPath(target)
        }
    }

    fun importLibraryFile() = runLibraryAction("Local file imported") {
        val material = services.libraryWorkspaceController.importLocalFile(libraryPathDraft)
        selectedMaterialId = material.id
        libraryPathDraft = ""
    }

    fun addLibraryWebResource() = runLibraryAction("Web resource added") {
        val material = services.libraryWorkspaceController.addWebResource(libraryWebTitleDraft, libraryWebUrlDraft)
        selectedMaterialId = material.id
        libraryWebTitleDraft = ""
        libraryWebUrlDraft = ""
    }

    fun toggleSelectedMaterialFavorite() = runLibraryAction("Favorite updated") {
        val materialId = requireNotNull(selectedMaterialId) { "Select a material first." }
        services.libraryWorkspaceController.toggleFavorite(materialId)
    }

    private fun runLibraryAction(success: String, action: suspend () -> Unit) {
        uiActions.launch(
            actionName = success,
            onSuccess = {
                libraryActionMessage = success
                services.platformPorts.notifications.showInfo(StudyFlowDesktopBoundary.appName, success)
            },
            onFailure = { error -> libraryActionMessage = error.cause?.message ?: error.message.orEmpty() },
            action = action,
        )
    }

    fun contentModeState(): DesktopContentModeState = services.contentModeController.state()

    fun setContentMode(mode: StudyContentMode) = runContentModeAction("Content mode updated") {
        services.contentModeController.setMode(mode)
    }

    fun ensureProgrammingSample() = runContentModeAction("Programming sample created") {
        services.contentModeController.ensureProgrammingSample()
    }

    fun runProgrammingExercise() {
        runCatching { services.contentModeController.runFirstProgrammingExercise() }
            .onSuccess { contentModeActionMessage = "Programming run finished" }
            .onFailure { contentModeActionMessage = it.message.orEmpty() }
    }

    fun updateLanguageReviewAnswer(value: String) { languageReviewAnswerDraft = value }

    fun ensureLanguageSample() = runContentModeAction("Language sample created") {
        services.contentModeController.ensureLanguageSample()
    }

    fun submitLanguageReview() {
        runCatching { services.contentModeController.reviewFirstLanguageExpression(languageReviewAnswerDraft) }
            .onSuccess {
                contentModeActionMessage = "Language review recorded"
                languageReviewAnswerDraft = ""
            }
            .onFailure { contentModeActionMessage = it.message.orEmpty() }
    }

    private fun runContentModeAction(success: String, action: suspend () -> Unit) {
        uiActions.launch(
            actionName = success,
            onSuccess = { contentModeActionMessage = success },
            onFailure = { error -> contentModeActionMessage = error.cause?.message ?: error.message.orEmpty() },
            action = action,
        )
    }

    fun publicExamState(): DesktopPublicExamState = services.publicExamController.state().also { state ->
        state.target?.let { target ->
            if (publicExamOrganizationDraft.isBlank()) publicExamOrganizationDraft = target.organization
            if (publicExamRoleDraft.isBlank()) publicExamRoleDraft = target.role
            if (publicExamBoardDraft.isBlank()) publicExamBoardDraft = target.examBoard
            if (publicExamDateDraft.isBlank()) publicExamDateDraft = target.confirmedExamDateIso.orEmpty()
        }
    }

    fun updatePublicExamOrganization(value: String) { publicExamOrganizationDraft = value }
    fun updatePublicExamRole(value: String) { publicExamRoleDraft = value }
    fun updatePublicExamBoard(value: String) { publicExamBoardDraft = value }
    fun updatePublicExamDate(value: String) { publicExamDateDraft = value }
    fun updatePublicExamEdital(value: String) { publicExamEditalDraft = value }
    fun updatePublicExamLegislation(value: String) { publicExamLegislationDraft = value }

    fun savePublicExamTarget() = runPublicExamAction("Public-exam target saved") {
        services.publicExamController.saveTarget(
            publicExamOrganizationDraft,
            publicExamRoleDraft,
            publicExamBoardDraft,
            "",
            publicExamDateDraft,
        )
    }

    fun applyPublicExamEdital() = runPublicExamAction("Edital revision applied") {
        services.publicExamController.applyEdital("Desktop edital", publicExamEditalDraft)
    }

    fun importPublicExamLegislation() = runPublicExamAction("Legislation imported") {
        services.publicExamController.importLegislation("Desktop legislation", publicExamLegislationDraft)
    }

    fun togglePublicExamProvisionRead() = runPublicExamAction("Legislation read state updated") {
        services.publicExamController.toggleFirstProvisionRead()
    }

    fun buildPublicExamCycle() = runPublicExamAction("Study cycle rebuilt") {
        services.publicExamController.buildCycle()
    }

    fun startPublicExamCycleItem() = runPublicExamAction("Study cycle session started") {
        services.publicExamController.startCurrentCycleItem() ?: error("No eligible study-cycle item.")
    }

    fun skipPublicExamCycleItem() = runPublicExamAction("Study cycle item skipped") {
        services.publicExamController.skipCycleItem()
    }

    private fun runPublicExamAction(success: String, action: suspend () -> Unit) {
        uiActions.launch(
            actionName = success,
            onSuccess = { publicExamActionMessage = success },
            onFailure = { error -> publicExamActionMessage = error.cause?.message ?: error.message.orEmpty() },
            action = action,
        )
    }

    fun writingWorkspaceState(): DesktopWritingWorkspaceState = services.writingWorkspaceController.state().also { state ->
        state.record?.let { record ->
            if (writingPromptDraft.isBlank()) writingPromptDraft = record.prompt
            if (writingTextDraft.isBlank()) writingTextDraft = record.draftText
        }
    }

    fun updateWritingPrompt(value: String) { writingPromptDraft = value }
    fun updateWritingDraft(value: String) { writingTextDraft = value }
    fun updateWritingThemeScore(value: String) { writingThemeScore = value.filter(Char::isDigit).take(2) }
    fun updateWritingTextTypeScore(value: String) { writingTextTypeScore = value.filter(Char::isDigit).take(2) }
    fun updateWritingCohesionScore(value: String) { writingCohesionScore = value.filter(Char::isDigit).take(2) }
    fun updateWritingArgumentationScore(value: String) {
        writingArgumentationScore = value.filter(Char::isDigit).take(2)
    }
    fun updateWritingLanguageScore(value: String) { writingLanguageScore = value.filter(Char::isDigit).take(2) }
    fun toggleWritingTaskReviewed() { writingTaskReviewed = !writingTaskReviewed }
    fun toggleWritingStructureReviewed() { writingStructureReviewed = !writingStructureReviewed }
    fun toggleWritingCohesionReviewed() { writingCohesionReviewed = !writingCohesionReviewed }
    fun toggleWritingLanguageReviewed() { writingLanguageReviewed = !writingLanguageReviewed }

    fun writingRubricTotal(): Int = listOf(
        writingThemeScore,
        writingTextTypeScore,
        writingCohesionScore,
        writingArgumentationScore,
        writingLanguageScore,
    ).sumOf { it.toIntOrNull()?.coerceIn(0, 20) ?: 0 }

    fun startWritingSession() = runWritingAction("Writing Session started") {
        services.writingWorkspaceController.start(writingPromptDraft)
    }

    fun saveWritingDraft() = runWritingAction("Writing draft saved") {
        services.writingWorkspaceController.saveDraft(writingPromptDraft, writingTextDraft)
    }

    fun completeWritingSession() = runWritingAction("Writing Session completed") {
        services.writingWorkspaceController.complete(
            prompt = writingPromptDraft,
            draftText = writingTextDraft,
            rubric = WritingRubric(
                score(writingThemeScore),
                score(writingTextTypeScore),
                score(writingCohesionScore),
                score(writingArgumentationScore),
                score(writingLanguageScore),
            ),
            checklist = WritingCompletionChecklist(
                taskReviewed = writingTaskReviewed,
                structureReviewed = writingStructureReviewed,
                cohesionReviewed = writingCohesionReviewed,
                languageReviewed = writingLanguageReviewed,
            ),
        )
        writingTextDraft = ""
        writingTaskReviewed = false
        writingStructureReviewed = false
        writingCohesionReviewed = false
        writingLanguageReviewed = false
    }

    private fun score(raw: String): Int = raw.toIntOrNull()?.coerceIn(0, 20) ?: 0

    private fun runWritingAction(success: String, action: suspend () -> Unit) {
        uiActions.launch(
            actionName = success,
            onSuccess = { writingActionMessage = success },
            onFailure = { error -> writingActionMessage = error.cause?.message ?: error.message.orEmpty() },
            action = action,
        )
    }

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
        uiActions.launch(
            actionName = success,
            onSuccess = { plannerActionMessage = success },
            onFailure = { error -> plannerActionMessage = error.cause?.message ?: error.message.orEmpty() },
            action = action,
        )
    }

    fun sessionRuntimeState(): DesktopSessionRuntimeState = services.sessionRuntimeController.state()

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
        uiActions.launch(
            actionName = success,
            onSuccess = { sessionActionMessage = success },
            onFailure = { error -> sessionActionMessage = error.cause?.message ?: error.message.orEmpty() },
            action = action,
        )
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
        uiActions.launch(
            actionName = success,
            onSuccess = { practiceActionMessage = success },
            onFailure = { error -> practiceActionMessage = error.cause?.message ?: error.message.orEmpty() },
            action = action,
        )
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
        uiActions.launch(
            actionName = success,
            onSuccess = { mockExamActionMessage = success },
            onFailure = { error -> mockExamActionMessage = error.cause?.message ?: error.message.orEmpty() },
            action = action,
        )
    }

    fun reviewWorkspaceState(): DesktopReviewWorkspaceState = services.reviewWorkspaceController.state()

    fun updateFlashcardFront(value: String) { flashcardFrontDraft = value }
    fun updateFlashcardBack(value: String) { flashcardBackDraft = value }

    fun createFlashcard() = runReviewAction("Flashcard created") {
        services.reviewWorkspaceController.createFlashcard(flashcardFrontDraft, flashcardBackDraft)
        flashcardFrontDraft = ""
        flashcardBackDraft = ""
    }

    fun gradeFirstReview() = runReviewAction("Review graded") { services.reviewWorkspaceController.gradeFirstDue(4) }
    fun skipFirstReview() = runReviewAction("Review skipped") { services.reviewWorkspaceController.skipFirstDue() }
    fun suspendFirstFlashcard() = runReviewAction("Flashcard suspended") {
        services.reviewWorkspaceController.suspendFirstFlashcard()
    }
    fun reclassifyNotebookError() = runReviewAction("Error reclassified") {
        services.reviewWorkspaceController.reclassifyFirstError(
            com.mikeyphw.studyflow.core.model.ErrorCategory.CONCEPT_CONFUSION,
        )
    }
    fun resolveNotebookError() = runReviewAction("Error resolved") {
        services.reviewWorkspaceController.resolveFirstError()
    }

    private fun runReviewAction(success: String, action: suspend () -> Unit) {
        uiActions.launch(
            actionName = success,
            onSuccess = { reviewActionMessage = success },
            onFailure = { error -> reviewActionMessage = error.cause?.message ?: error.message.orEmpty() },
            action = action,
        )
    }

    fun analyticsState(): DesktopAnalyticsScreenState = services.createAnalyticsState()

    fun progressReadinessState(): DesktopProgressReadinessState = services.progressReadinessController.state()

    fun updateGoalTitleDraft(value: String) { goalTitleDraft = value }

    fun updateGoalTargetDraft(value: String) { goalTargetDraft = value }

    fun cycleGoalType() {
        val values = GoalType.entries
        goalTypeDraft = values[(values.indexOf(goalTypeDraft) + 1) % values.size]
    }

    fun cycleGoalPeriod() {
        val values = GoalPeriod.entries
        goalPeriodDraft = values[(values.indexOf(goalPeriodDraft) + 1) % values.size]
    }

    fun createGoal() = runGoalAction("Goal created") {
        val target = requireNotNull(goalTargetDraft.toDoubleOrNull()) { "Goal target must be a number." }
        services.goalWorkspaceController.createGoal(goalTypeDraft, goalPeriodDraft, goalTitleDraft, target)
        goalTitleDraft = ""
    }

    fun incrementGoal(goalId: StudyGoalId) = runGoalAction("Goal progress updated") {
        services.goalWorkspaceController.incrementCustomGoal(goalId, 1.0)
    }

    fun archiveGoal(goalId: StudyGoalId) = runGoalAction("Goal archived") {
        services.goalWorkspaceController.archive(goalId)
    }

    fun restoreGoal(goalId: StudyGoalId) = runGoalAction("Goal restored") {
        services.goalWorkspaceController.restore(goalId)
    }

    fun deleteGoal(goalId: StudyGoalId) = runGoalAction("Goal deleted") {
        services.goalWorkspaceController.delete(goalId)
    }

    private fun runGoalAction(success: String, action: suspend () -> Unit) {
        uiActions.launch(
            actionName = success,
            onSuccess = { goalActionMessage = success },
            onFailure = { error -> goalActionMessage = error.cause?.message ?: error.message.orEmpty() },
            action = action,
        )
    }

    fun applyStudySprint() {
        uiActions.launch(
            actionName = "Apply study sprint",
            onSuccess = { progressActionMessage = "Applied study sprint changes" },
            onFailure = { error -> progressActionMessage = error.cause?.message ?: error.message.orEmpty() },
        ) {
            services.progressReadinessController.applyCurrentSprint()
        }
    }

    fun exportExamReport() {
        runCatching { services.progressReadinessController.exportCurrentExamReport() }
            .onSuccess { path -> progressActionMessage = "Exam report saved to $path" }
            .onFailure { error -> progressActionMessage = error.message.orEmpty() }
    }

    fun choosePortabilityImportFile() {
        services.platformPorts.fileDialog.chooseImportPath()?.let(::updatePortabilityPath)
    }

    fun choosePortabilityExportFile() {
        services.platformPorts.fileDialog.chooseExportPath("studyflow-backup.studyplan")?.let(::updatePortabilityPath)
    }

    fun handleDeepLink(uri: String): Boolean = DesktopDeepLinkRouter.route(uri, this)

    fun updatePortabilityPath(value: String) { portabilityPathDraft = value }

    fun updatePortabilityPassword(value: String) {
        portabilityPasswordSecret.fill('\u0000')
        portabilityPasswordSecret = value.toCharArray()
        portabilityPasswordDraft = "•".repeat(value.length)
    }

    fun exportPortableStructure() = runPortabilityAction("Portable Study Plan exported") {
        val destination = Path.of(portabilityPathDraft.trim())
        services.portabilityController.exportPortableStructure(destination)
    }

    fun importPortableStructure() = runPortabilityAction("Portable Study Plan imported") {
        services.portabilityController.importPortableStructure(Path.of(portabilityPathDraft.trim()))
    }

    fun createEncryptedWorkspaceBackup() = runPortabilityAction("Encrypted Desktop backup created") {
        val secret = portabilityPasswordSecret.copyOf()
        try {
            services.portabilityController.createEncryptedWorkspaceBackup(Path.of(portabilityPathDraft.trim()), secret)
        } finally {
            secret.fill('\u0000')
            portabilityPasswordSecret.fill('\u0000')
            portabilityPasswordSecret = CharArray(0)
            portabilityPasswordDraft = ""
        }
    }

    fun restoreEncryptedWorkspaceBackup() = runPortabilityAction("Workspace restored and memory reloaded") {
        val secret = portabilityPasswordSecret.copyOf()
        try {
            services.portabilityController.restoreEncryptedWorkspaceBackup(Path.of(portabilityPathDraft.trim()), secret)
        } finally {
            secret.fill('\u0000')
            portabilityPasswordSecret.fill('\u0000')
            portabilityPasswordSecret = CharArray(0)
            portabilityPasswordDraft = ""
        }
    }

    fun migrateDesktopWorkspace() = runPortabilityAction("Desktop workspace schema migrated") {
        services.portabilityController.migrateWorkspaceSchema()
    }

    private fun runPortabilityAction(success: String, action: () -> Any?) {
        portabilityActionMessage = runCatching(action).fold(
            onSuccess = {
                services.platformPorts.notifications.showInfo(StudyFlowDesktopBoundary.appName, success)
                success
            },
            onFailure = { error -> error.message.orEmpty() },
        )
    }

    fun searchState(): DesktopSearchScreenState = services.createSearchState(searchQuery)

    fun settingsState(): DesktopSettingsScreenState {
        attentionRevision
        return services.createSettingsState()
    }

    fun toggleDesktopReminders() {
        services.attentionRuntime?.toggleReminders()
        attentionRevision += 1
    }

    fun toggleDesktopNativeNotifications() {
        services.attentionRuntime?.toggleNativeNotifications()
        attentionRevision += 1
    }

    fun toggleDesktopTrayDashboard() {
        services.attentionRuntime?.toggleTrayDashboard()
        attentionRevision += 1
    }

    fun toggleDesktopReminderPause() {
        services.attentionRuntime?.togglePaused()
        attentionRevision += 1
    }

    fun reconcileDesktopAttention() {
        services.attentionRuntime?.reconcileNow(recovery = true)
        attentionRevision += 1
    }

    fun testDesktopNotification() {
        services.attentionRuntime?.showTestNotification()
        attentionRevision += 1
    }

    fun toggleDesktopReminderCategory(category: DesktopReminderCategoryPreference) {
        services.attentionRuntime?.toggleCategory(category)
        attentionRevision += 1
    }

    fun cycleDesktopAttentionFocusProfile() {
        services.attentionRuntime?.cycleFocusProfile()
        attentionRevision += 1
    }

    fun toggleDesktopAttentionQuietHours() {
        services.attentionRuntime?.toggleFocusQuietHours()
        attentionRevision += 1
    }

    fun exportDesktopAttentionCalendar() {
        val exported = runCatching { services.attentionRuntime?.exportCalendar() }.getOrNull()
        val message = exported?.let { "StudyFlow calendar exported to $it" }
            ?: "Calendar export cancelled or unavailable"
        services.platformPorts.notifications.showInfo(StudyFlowDesktopBoundary.appName, message)
    }

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
            "Pairing offer ready until ${DesktopStudyFlowLinkUxPolicy.formatPairingExpiry(offer.expiresAtEpochMillis)}"
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
            DesktopStudyFlowLinkUxPolicy.syncSummary(service.syncAll(workspace))
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
        services.attentionRuntime?.close()
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
    REVIEWS("Reviews"),
    WRITING("Writing"),
    MODES("Modes"),
    PUBLIC_EXAM("Concurso"),
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
