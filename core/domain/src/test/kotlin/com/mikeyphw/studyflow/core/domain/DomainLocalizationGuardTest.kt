package com.mikeyphw.studyflow.core.domain

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class DomainLocalizationGuardTest {
    @Test
    fun reminderAndInsightDomainSourcesDoNotRegressToRawEnglishPresentationCopy() {
        val reminderSource = source("StudyReminderPlanner.kt")
        val analyticsSource = source("StudyAnalytics.kt")
        val forbidden = listOf(
            "Due reviews",
            "review item(s) are due now",
            "Next planned study block is ready",
            "Missed study blocks",
            "Daily study summary is available",
            "Overdue reviews",
            "Review backlog needs attention",
            "Schedule drift",
            "Accuracy risk",
            "Weak areas detected",
            "Stable plan",
        )

        forbidden.forEach { rawCopy ->
            assertFalse("Raw presentation copy leaked into reminder domain: $rawCopy", reminderSource.contains(rawCopy))
            assertFalse("Raw presentation copy leaked into analytics domain: $rawCopy", analyticsSource.contains(rawCopy))
        }
    }

    private fun source(fileName: String): String {
        val userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        val candidates = generateSequence(userDir) { it.parent }.flatMap { root ->
            sequenceOf(
                root.resolve("src/main/kotlin/com/mikeyphw/studyflow/core/domain/$fileName"),
                root.resolve("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/$fileName"),
            )
        }
        val source = candidates.firstOrNull(Files::isRegularFile)
        assertNotNull("Could not locate domain source $fileName", source)
        return Files.readString(requireNotNull(source))
    }
}
