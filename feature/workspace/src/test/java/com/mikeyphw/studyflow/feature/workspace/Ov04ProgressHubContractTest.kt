package com.mikeyphw.studyflow.feature.workspace

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov04ProgressHubContractTest {
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
    fun `Progress hub exposes four functional segments`() {
        val progress = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
        )
        assertTrue("private enum class ProgressHubSegment" in progress)
        assertTrue("SUMMARY" in progress)
        assertTrue("PERFORMANCE" in progress)
        assertTrue("GOALS" in progress)
        assertTrue("TOOLS" in progress)
        assertTrue("periods = ProgressHubSegment.entries" in progress)
        assertTrue("if (selectedSegment.usesPeriod)" in progress)
        assertTrue("selectedSegment == ProgressHubSegment.SUMMARY" in progress)
        assertTrue("selectedSegment == ProgressHubSegment.PERFORMANCE" in progress)
        assertTrue("selectedSegment == ProgressHubSegment.GOALS" in progress)
        assertTrue("selectedSegment == ProgressHubSegment.TOOLS" in progress)
        assertTrue(
            "this == ProgressHubSegment.SUMMARY || this == ProgressHubSegment.PERFORMANCE" in progress,
        )
    }

    @Test
    fun `segment labels and short root labels are bilingual`() {
        val en = source("feature/workspace/src/main/res/values/strings.xml")
        val pt = source("feature/workspace/src/main/res/values-pt-rBR/strings.xml")
        assertTrue(">Summary</string>" in en)
        assertTrue(">Performance</string>" in en)
        assertTrue(">Goals</string>" in en)
        assertTrue(">Tools</string>" in en)
        assertTrue(">Resumo</string>" in pt)
        assertTrue(">Desempenho</string>" in pt)
        assertTrue(">Metas</string>" in pt)
        assertTrue(">Ferramentas</string>" in pt)

        val practice = source("feature/practice/src/main/res/values/strings.xml")
        val progress = source("feature/progress/src/main/res/values/strings.xml")
        val dashboard = source("feature/dashboard/src/main/res/values/strings.xml")
        assertTrue("<string name=\"practice_title\">Practice</string>" in practice)
        assertTrue("<string name=\"progress_title\">Progress</string>" in progress)
        assertTrue("<string name=\"practice\">Practice</string>" in dashboard)
        assertTrue("<string name=\"progress_center\">Progress</string>" in dashboard)
        assertFalse("<string name=\"practice_title\">Questions and mocks</string>" in practice)
        assertFalse("<string name=\"progress_title\">Goals and notes</string>" in progress)
    }
}
