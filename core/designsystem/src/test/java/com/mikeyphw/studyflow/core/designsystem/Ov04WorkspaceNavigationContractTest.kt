package com.mikeyphw.studyflow.core.designsystem

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov04WorkspaceNavigationContractTest {
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
    fun `plan switcher is compact without losing plan context`() {
        val components = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowWorkspaceComponents.kt",
        )
        val header = components
            .substringAfter("fun StudyFlowWorkspaceHeader(")
            .substringBefore("fun shouldStackWorkspacePair(")
        assertTrue(".widthIn(max = 360.dp)" in header)
        assertTrue("stateDescription = planSupportingText" in header)
        assertTrue("planName.orEmpty()" in header)
        assertTrue("Icons.Outlined.ArrowDropDown" in header)
    }

    @Test
    fun `compact dock keeps short labels single line and touch safe`() {
        val shell = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowShellPrimitives.kt",
        )
        assertTrue(".heightIn(min = 48.dp)" in shell)
        assertTrue("softWrap = false" in shell)
        assertTrue("overflow = TextOverflow.Ellipsis" in shell)
        assertTrue("maxLines = 1" in shell)
    }
}
