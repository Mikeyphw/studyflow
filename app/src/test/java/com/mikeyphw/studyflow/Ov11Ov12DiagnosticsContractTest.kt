package com.mikeyphw.studyflow

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov11Ov12DiagnosticsContractTest {
    private fun repoFile(path: String): String {
        var current = File(System.getProperty("user.dir"))
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate $path")
    }

    @Test
    fun `debug workbench owns running jobs in viewmodel and labels status as snapshot`() {
        val app = repoFile("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
        val viewModel = repoFile("app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchViewModel.kt")
        val strings = repoFile("feature/settings/src/main/res/values/strings.xml")

        assertTrue("StudyFlowDebugWorkbenchViewModel.factory" in app)
        assertTrue("collectAsStateWithLifecycle" in app)
        assertTrue("viewModelScope" in viewModel)
        assertTrue("probeMutex" in viewModel)
        assertTrue("suiteRunning" in viewModel)
        assertFalse(Regex("rememberSaveable[\\s\\S]{0,160}(suite|probe|lab).*Running", RegexOption
            .IGNORE_CASE).containsMatchIn(app))
        assertTrue("Status snapshot" in strings)
        assertFalse(">Live status<" in strings)
    }

    @Test
    fun `routine repository mutations emit sanitized diagnostic boundaries`() {
        val dataLayer = repoFile("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt")
        val helper = repoFile(
            "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/DiagnosticRepositoryMutation.kt")
        val files = listOf(
            "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanRepository.kt",
            "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudySessionRepository.kt",
            "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPracticeRepository.kt",
            "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyReviewRepository.kt",
            "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyScheduleRepository.kt",
            "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanPortabilityRepository.kt",
        ).joinToString("\n") { repoFile(it) }

        assertTrue("diagnosticRecorder: DiagnosticRecorder" in dataLayer)
        assertTrue("CancellationException" in helper)
        assertTrue("throw cancelled" in helper)
        listOf(
            "plan-settings-write",
            "session-create",
            "session-transition",
            "practice-submit",
            "mock-exam-submit",
            "review-grade",
            "review-skip",
            "schedule-generate",
            "plan-export",
            "plan-import",
            "snapshot-restore",
        ).forEach { action -> assertTrue("missing $action", action in files) }
    }

    @Test
    fun `reminder lab separates planning platform and durable scheduling evidence`() {
        val source = repoFile("app/src/main/java/com/mikeyphw/studyflow/StudyFlowReminderNotificationLab.kt")
        assertTrue("plannerStatus" in source)
        assertTrue("platformStatus" in source)
        assertTrue("schedulingStatus" in source)
        assertTrue("StudyReminderOccurrenceStore" in source)
        assertTrue("productionPipelineVerified" in source)
        assertTrue("scheduledBookkeepingPresent" in source)
        assertFalse("!settings.reviewNotificationsEnabled" in source)
        assertTrue("runProductionPipelineNotificationCanary" in source)
        assertTrue("context.sendBroadcast(" in source)
        assertTrue("markProductionPipelineCanary" in source)
        assertFalse("NotificationCompat.Builder" in source)
    }
    @Test
    fun `runtime smoke delegates to behavioral debug probes instead of duplicating false-green canaries`() {
        val app = repoFile("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")

        assertTrue("StudyFlowDebugProbeRegistry(" in app)
        assertTrue("RuntimeSmokeProbeKind.BACKUP_DRY_RUN -> DebugProbeId.BACKUP_DRY_RUN" in app)
        assertFalse("Backup inspect and restore dry-run completed with zero writes." in app)
        assertFalse("ByteArrayOutputStream" in app)
        assertFalse("manageStudyDiscovery.search(\"study\")" in app)
    }

}
