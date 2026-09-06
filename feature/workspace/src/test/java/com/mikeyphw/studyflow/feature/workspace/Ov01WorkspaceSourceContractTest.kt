package com.mikeyphw.studyflow.feature.workspace

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov01WorkspaceSourceContractTest {
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
    fun `today primary card uses temporal selection and dated metadata for future day`() {
        val today = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
        )
        assertTrue("selectTodayOrNextScheduledActivity(" in today)
        assertTrue("includeDate = nextActivity.isFutureDay" in today)
        assertTrue("nextActivity.activity.title" in today)
        assertFalse("readySchedule.pendingActivities.minByOrNull" in today)
    }

    @Test
    fun `priority evidence no longer assigns every subject session to every topic`() {
        val plan = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
        )
        assertTrue("session.contributesToTopicEvidence(topic.id, subject.id)" in plan)
        assertFalse("session.topicId == topic.id || session.subjectId == subject.id" in plan)
    }
}
