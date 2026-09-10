package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.StudyEvidenceEventKind
import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyGoalId
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DesktopDov24ApplicableParityAcceptanceTest {
    @Test
    fun goalsRestartAnalyticsEvidencePurgeAndNativePortsSealApplicableParity() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov24-workspace-")
            try {
                val firstPersistence = DesktopWorkspacePersistence.open(
                    root = root,
                    seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create()),
                )
                val firstPorts = Dov24RecordingPlatformPorts()
                val first = StudyFlowDesktopBootstrap.createAppState(
                    platformPorts = firstPorts,
                    workspacePersistence = firstPersistence,
                )
                val planId = requireNotNull(first.services.defaultPlanId)

                val evidenceGoal = first.services.manageStudyProgress.createGoal(
                    id = StudyGoalId("dov24-session-goal"),
                    studyPlanId = planId,
                    subjectId = null,
                    topicId = null,
                    type = GoalType.SESSION_COUNT,
                    period = GoalPeriod.CUSTOM,
                    title = "Complete one persisted session",
                    targetValue = 1.0,
                    startsOnIso = "2020-01-01",
                    endsOnIso = "2035-01-01",
                )
                val evaluatedEvidenceGoal = first.services.progressRepository.currentGoals(planId)
                    .first { it.id == evidenceGoal.id }
                assertTrue(evaluatedEvidenceGoal.currentValue >= 1.0)

                val customGoal = first.services.manageStudyProgress.createGoal(
                    id = StudyGoalId("dov24-custom-goal"),
                    studyPlanId = planId,
                    subjectId = null,
                    topicId = null,
                    type = GoalType.CUSTOM,
                    period = GoalPeriod.CUSTOM,
                    title = "Ship DOV24",
                    targetValue = 1.0,
                    startsOnIso = "2020-01-01",
                    endsOnIso = "2035-01-01",
                )
                first.services.manageStudyProgress.updateGoalProgress(planId, customGoal.id, 1.0)

                val firstProgress = first.progressReadinessState()
                assertTrue(
                    firstProgress.goals.any {
                        it.id == customGoal.id && it.completedAtEpochMillis != null
                    },
                )
                assertTrue(
                    firstProgress.evidence.any {
                        it.kind == StudyEvidenceEventKind.GOAL_COMPLETED
                    },
                )
                assertEquals(
                    100,
                    first.services.analyticsRepository.currentSnapshot(planId).goalCompletionPercent,
                )
                assertTrue(first.handleDeepLink("studyflow://route/analytics"))
                assertEquals(DesktopRoute.ANALYTICS, first.selectedRoute)

                val reopened = StudyFlowDesktopBootstrap.createAppState(
                    platformPorts = Dov24RecordingPlatformPorts(),
                    workspacePersistence = DesktopWorkspacePersistence.open(
                        root = root,
                        seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create()),
                    ),
                )
                assertEquals(2, reopened.services.progressRepository.currentGoals(planId).size)
                assertTrue(
                    reopened.progressReadinessState().evidence.any {
                        it.kind == StudyEvidenceEventKind.GOAL_COMPLETED
                    },
                )
                assertTrue(reopened.services.platformPorts is Dov24RecordingPlatformPorts)

                reopened.services.planContentController.deleteSelectedPermanently()
                assertFalse(reopened.services.progressRepository.currentGoals(planId).any())
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }
}

private class Dov24RecordingPlatformPorts : DesktopPlatformPorts {
    override val fileDialog: DesktopFileDialogPort = object : DesktopFileDialogPort {
        override fun chooseImportPath(): String? = null
        override fun chooseExportPath(suggestedFileName: String): String? = null
    }

    override val filesystem: DesktopFilesystemPort = object : DesktopFilesystemPort {
        override fun openPath(path: String) = Unit
        override fun openUrl(url: String) = Unit
    }

    override val notifications: DesktopNotificationPort = object : DesktopNotificationPort {
        override fun showInfo(title: String, message: String) = Unit
    }
}
