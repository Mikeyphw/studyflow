package com.mikeyphw.studyflow.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopPlannerAuthoringPersistenceTest {
    @Test
    fun authoredScheduleAndAdaptiveApplySurviveRestart() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov10-")
            try {
                val seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create())
                val persistence = DesktopWorkspacePersistence.open(root, seed)
                val first = createDesktopStudyFlowServices(workspacePersistence = persistence)
                val controller = first.plannerAuthoringController
                val before = controller.state().activities

                controller.ensureAdaptiveStrategy()
                val occupiedDays = controller.state().rules.mapNotNull { it.dayOfWeek }.toSet()
                val testDay = (1..7).first { it !in occupiedDays }
                controller.addEveningRule(dayOfWeek = testDay, startMinuteOfDay = 19 * 60)
                controller.addManualActivity("Persistent planner activity")
                val preview = controller.state().adaptivePreview
                assertNotNull(preview)
                if (preview.executableChangeCount > 0) controller.applyAdaptivePreview()

                val reopened = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val state = reopened.plannerAuthoringController.state()
                assertTrue(state.rules.any { it.dayOfWeek == testDay && it.startMinuteOfDay == 19 * 60 })
                assertTrue(state.activities.any { it.title == "Persistent planner activity" })
                assertTrue(state.activities.size >= before.size)
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }
}
