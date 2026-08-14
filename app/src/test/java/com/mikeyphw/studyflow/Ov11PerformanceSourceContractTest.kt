package com.mikeyphw.studyflow

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov11PerformanceSourceContractTest {
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
    fun `application composition owns the only production room graph`() {
        val dataLayer = repoFile("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt")
        val application = repoFile("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt")
        val receiver = repoFile("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderReceiver.kt")
        val widget = repoFile("app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowTodayWidgetProvider.kt")

        assertEquals(1, Regex("StudyFlowDatabase\\.create\\(").findAll(dataLayer).count())
        assertFalse("StudyFlowDatabase.create(" in application)
        assertFalse("createStudyFlowDataLayer(" in receiver)
        assertFalse("createStudyFlowDataLayer(" in widget)
    }

    @Test
    fun `global discovery is indexed sql bounded instead of eight full table scans`() {
        val dao = repoFile("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyDiscoveryDao.kt")
        val index = repoFile("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowSearchIndex.kt")
        val query = repoFile("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/IndexedStudyDiscoveryQuery.kt")

        assertTrue("USING fts4" in index)
        assertTrue("MATCH ?" in query)
        assertTrue("CandidateLimit = 320" in query)
        assertTrue("suspend fun searchIndexed" in dao)
        assertFalse("instr(lower(" in dao)
    }

    @Test
    fun `progress reuses loaded snapshots and review rendering is lazy preindexed`() {
        val progress = repoFile("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt")
        val review = repoFile("feature/reviews/src/main/java/com/mikeyphw/studyflow/feature/reviews/StudyReviewScreen.kt")

        assertFalse("observeReadiness(" in progress)
        assertFalse("observePreview(" in progress)
        assertTrue("ExamReadinessEngine.calculate(" in progress)
        assertTrue("AdaptiveReschedulePlanner.preview(" in progress)
        assertTrue("LazyColumn" in review)
        assertTrue("ReviewItemContentIndex" in review)
        assertFalse("errors.filter" in review)
    }

    @Test
    fun `analytics uses sql aggregates and package annex reads have streaming staging`() {
        val analytics = repoFile("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyAnalyticsRepository.kt")
        val analyticsDao = repoFile("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyAnalyticsDao.kt")
        val codec = repoFile("core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageCodec.kt")
        val portability = repoFile("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanPortabilityRepository.kt")

        assertTrue("shareIn(" in analytics)
        assertTrue("StudyAnalyticsDao" in analytics)
        assertTrue("SELECT" in analyticsDao)
        assertTrue("readArchiveStreaming" in codec)
        assertTrue("stagingDirectory" in codec)
        assertTrue("readArchiveStreaming" in portability)
    }
}
