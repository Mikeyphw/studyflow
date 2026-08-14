package com.mikeyphw.studyflow

import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov09LocaleReminderContractTest {
    @Test
    fun `appcompat locale authority is persisted on pre android 13`() {
        val manifest = readSource(
            "src/main/AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml",
        )
        assertTrue(manifest.contains("androidx.appcompat.app.AppLocalesMetadataHolderService"))
        assertTrue(manifest.contains("android:name=\"autoStoreLocales\""))
        assertTrue(manifest.contains("android:value=\"true\""))
        assertTrue(manifest.contains("android:enabled=\"false\""))
    }

    @Test
    fun `reminder reconcile preserves bookkeeping until replacement is ready`() {
        val source = readSource(
            "src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
            "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
        )
        val reconcile = source.substringAfter("private suspend fun reconcilePlanInternal")
            .substringBefore("suspend fun deliver")
        assertTrue(reconcile.indexOf("buildRuntimePlan(planId)") < reconcile.indexOf("clearBookkeeping = false"))
        assertTrue(reconcile.indexOf("clearBookkeeping = false") < reconcile.indexOf("occurrenceStore.replaceScheduled"))
        assertTrue(reconcile.indexOf("occurrenceStore.replaceScheduled") < reconcile.indexOf("scheduleCandidate(planId, candidate)"))
        assertTrue(source.contains("persistedScheduledAtByOccurrenceId = persistedScheduledAt"))
    }

    private fun readSource(modulePath: String, rootPath: String): String {
        val candidates: List<Path> = listOf(Paths.get(modulePath), Paths.get(rootPath))
        return candidates.first { path -> path.toFile().isFile }.toFile().readText()
    }
}
