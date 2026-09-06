package com.mikeyphw.studyflow.feature.subjects

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov03ResponsiveSyllabusContractTest {
    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(8) {
            if (current.resolve("settings.gradle.kts").exists()) return current
            current = current.parent ?: return@repeat
        }
        error("Could not locate StudyFlow repository root")
    }

    @Test
    fun `syllabus wraps dense controls and keeps administration in overflow menus`() {
        val source = repoRoot().resolve(
            "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt",
        ).toFile().readText()

        assertTrue(source.contains("FlowRow("))
        assertFalse(source.contains(".horizontalScroll(rememberScrollState())"))
        assertTrue(source.contains("Icons.Outlined.MoreVert"))
        assertTrue(source.contains("DropdownMenuItem("))
        assertTrue(source.contains("subject = visible.subject"))
        assertTrue(source.contains("visibleTopicCount = visible.visibleTopicCount"))
        assertTrue(source.contains("topicTree = visible.topicTree"))
        assertTrue(source.contains("if (visible.subject.archived)"))
        assertTrue(source.contains("R.string.more_actions"))
    }
}
