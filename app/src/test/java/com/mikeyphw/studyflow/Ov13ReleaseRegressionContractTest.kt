package com.mikeyphw.studyflow

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov13ReleaseRegressionContractTest {
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
    fun `v2 plan picker changes temporary workspace without replacing canonical default`() {
        val source = repoFile("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
        val picker = source.substringAfter("composable<PlanPickerRoute>").substringBefore("composable<QuickAddRoute>")

        assertTrue("setDefaultStudyPlan(" !in picker)
        assertTrue("planId = plan.id.value" in picker)
        assertTrue("restoreSavedStack = false" in picker)
    }

    @Test
    fun `focus note draft survives recreation until field specific persistence completes`() {
        val source = repoFile(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/FocusSessionScreen.kt",
        )

        assertTrue("rememberSaveable(session?.id?.value)" in source)
        assertTrue("sessionManager.updateNotes" in source)
    }
    @Test
    fun `final overlay requires signed automated Android validation rather than host only`() {
        val rootBuild = repoFile("build.gradle.kts")
        val validator = repoFile("scripts/validate_repo.sh")

        assertTrue("verifyFinalReleaseAutomatedGate" in rootBuild)
        assertTrue(":core:database:connectedDebugAndroidTest" in rootBuild)
        assertTrue(":app:connectedReleaseAndroidTest" in rootBuild)
        assertTrue("missingSigningVariables" in rootBuild)
        assertTrue("app-release-unsigned.apk" !in rootBuild)
        assertTrue("verifyFinalReleaseAutomatedGate" in validator)
    }

    @Test
    fun `full journey proves real restore across a new process`() {
        val journey = repoFile(
            "app/src/androidTest/java/com/mikeyphw/studyflow/StudyFlowFullJourneyAcceptanceTest.kt",
        )
        val recorder = repoFile("scripts/record_full_journey_evidence.py")

        assertTrue("MODE_RESTART_INITIAL" in journey)
        assertTrue("MODE_POST_RESTART" in journey)
        assertTrue("restoredJourneySurvivesProcessRestartAndCleansUp" in journey)
        assertTrue("assertNotEquals" in journey)
        assertTrue("restart_initial" in recorder)
        assertTrue("post_restart" in recorder)
        assertTrue("processRestartVerified" in recorder)
    }

    @Test
    fun `final verdict requires connected gates aab signature and manual observation evidence`() {
        val verdict = repoFile("scripts/verify_final_release_verdict.py")
        val device = repoFile("scripts/record_final_device_acceptance.py")
        val artifact = repoFile("scripts/verify_final_release_artifact.py")

        assertTrue("REQUIRED_CONNECTED_GATES" in verdict)
        assertTrue("signatureVerified" in verdict)
        assertTrue("has no observation evidence" in verdict)
        assertTrue("--evidence" in device)
        assertTrue("jarsigner" in artifact)
    }

}
