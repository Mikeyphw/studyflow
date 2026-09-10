package com.mikeyphw.studyflow.desktop

import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopProgressReadinessIntegrationTest {
    @Test
    fun derivedReadinessReportRunbookAndSprintUsePersistentWorkspaceTruth() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov19-")
            try {
                val seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create())
                val first = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                first.publicExamController.saveTarget(
                    organization = "Banco do Brasil",
                    role = "Agente Comercial",
                    examBoard = "Cesgranrio",
                    noticeUrl = "",
                    examDateIso = LocalDate.now().plusDays(7).toString(),
                )
                first.plannerAuthoringController.addManualActivity("DOV19 sprint candidate")

                val state = first.progressReadinessController.state()
                assertNotNull(state.readiness)
                assertNotNull(state.weeklyReview)
                assertNotNull(state.cleanupReport)
                assertNotNull(state.examReport)
                assertTrue(assertNotNull(state.runbook).applicable)
                assertTrue(assertNotNull(state.sprintPreview).candidateCount > 0)
                val planId = assertNotNull(state.plan).id
                assertEquals(first.defaultPlanId, planId)
                assertEquals(state.evidence.size, state.examReport.evidenceCount)
                assertTrue(!state.examReport.freeformNotesIncluded)

                val reportPath = first.progressReadinessController.exportCurrentExamReport()
                assertTrue(Files.isRegularFile(reportPath))
                val reportHtml = Files.readString(reportPath)
                assertTrue("Metric provenance" in reportHtml)
                assertTrue("Watched desktop layout walkthrough" !in reportHtml)

                val beforeVersion = first.scheduleRepository.currentSchedule(planId)
                val changed = first.progressReadinessController.applyCurrentSprint()
                assertTrue(changed > 0)
                val afterVersion = first.scheduleRepository.currentSchedule(planId)
                assertTrue(afterVersion != beforeVersion)

                val reopened = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val reopenedState = reopened.progressReadinessController.state()
                assertEquals(planId, reopenedState.plan?.id)
                assertNotNull(reopenedState.readiness)
                assertNotNull(reopenedState.examReport)
                assertTrue(reopenedState.evidence.isNotEmpty())
                val reportFiles = Files.list(root.resolve("exam-reports"))
                try {
                    assertTrue(reportFiles.findAny().isPresent)
                } finally {
                    reportFiles.close()
                }
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }
}
