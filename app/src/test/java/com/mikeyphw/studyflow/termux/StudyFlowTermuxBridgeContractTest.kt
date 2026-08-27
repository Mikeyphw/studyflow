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
    fun `manifest declares Termux permission without broad package visibility and result receiver`() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        assertTrue("com.termux.permission.RUN_COMMAND" in manifest)
        assertFalse("<queries>" in manifest)
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
        assertFalse("putExtra(EXTRA_COMMAND_PATH, template.commandLine)" in bridge)
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
