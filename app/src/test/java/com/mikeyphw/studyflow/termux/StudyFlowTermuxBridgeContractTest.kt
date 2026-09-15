package com.mikeyphw.studyflow.termux

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTermuxBridgeContractTest {
    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(8) {
            if (current.resolve("settings.gradle.kts").exists()) return current
            current = current.parent ?: return@repeat
        }
        error("Could not locate StudyFlow repository root")
    }

    private fun source(relative: String): String = repoRoot().resolve(relative).toFile().readText()

    @Test
    fun `manifest declares Termux permission package visibility and result receiver`() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        assertTrue("com.termux.permission.RUN_COMMAND" in manifest)
        assertTrue("<queries>" in manifest)
        assertTrue("<package android:name=\"com.termux\" />" in manifest)
        assertTrue("com.termux.RUN_COMMAND" in manifest)
        assertTrue(".termux.StudyFlowTermuxResultReceiver" in manifest)
    }

    @Test
    fun `run command bridge uses public Termux contract with pending result intent`() {
        val bridge = source(
            "app/src/main/java/com/mikeyphw/studyflow/termux/StudyFlowTermuxRunCommandBridge.kt",
        )
        assertTrue("com.termux.app.RunCommandService" in bridge)
        assertTrue("com.termux.RUN_COMMAND_PATH" in bridge)
        assertTrue("com.termux.RUN_COMMAND_ARGUMENTS" in bridge)
        assertTrue("com.termux.RUN_COMMAND_WORKDIR" in bridge)
        assertTrue("com.termux.RUN_COMMAND_PENDING_INTENT" in bridge)
        assertTrue("PendingIntent.FLAG_ONE_SHOT" in bridge)
        assertTrue("context.startService(intent)" in bridge)
        assertTrue("isTermuxRunCommandAvailable" in bridge)
        assertTrue("termuxWorkspaceWorkdir" in bridge)
        assertTrue("MessageDigest.getInstance(\"SHA-256\")" in bridge)
        assertTrue("data = Uri.parse" in bridge)
        assertFalse("putExtra(EXTRA_COMMAND_PATH, template.commandLine)" in bridge)
        assertFalse("stablePendingIntentRequestCode" in bridge)
    }


    @Test
    fun `result receiver persists correlated results instead of volatile cache`() {
        val receiver = source(
            "app/src/main/java/com/mikeyphw/studyflow/termux/StudyFlowTermuxResultReceiver.kt",
        )
        assertTrue("goAsync()" in receiver)
        assertTrue("recordTermuxCommandResult(result)" in receiver)
        assertTrue("requireStudyFlowAppContainer" in receiver)
        assertFalse("StudyFlowTermuxResultCache" in receiver)
        assertFalse("latestResult" in receiver)
        assertFalse("@Volatile" in receiver)
    }

    @Test
    fun `coordinator durably queues four stage pipeline before launch`() {
        val coordinator = source(
            "app/src/main/java/com/mikeyphw/studyflow/termux/StudyFlowTermuxExecutionCoordinator.kt",
        )
        assertTrue("enqueueTermuxExerciseRun" in coordinator)
        assertTrue("launchRunCommand" in coordinator)
        assertTrue("markTermuxRequestWaitingForTermux" in coordinator)
        assertTrue("CREATE_WORKSPACE, WRITE_EXERCISE_FILES" in coordinator)
        assertTrue("RUN_COMMAND and READ_RESULT" in coordinator)
    }

    @Test
    fun `unsupported shell and SQL defaults cannot create unsafe execution plans`() {
        val execution = source(
            "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/CodingExerciseTermuxExecution.kt",
        )
        assertTrue("safeCommandTemplateFor" in execution)
        assertTrue("No safe Termux command is available" in execution)
        assertFalse("ProgrammingLanguage.SHELL -> \"sh main.sh\"" in execution)
        assertFalse("ProgrammingLanguage.SQL -> \"sqlite3 < query.sql\"" in execution)
    }
}
