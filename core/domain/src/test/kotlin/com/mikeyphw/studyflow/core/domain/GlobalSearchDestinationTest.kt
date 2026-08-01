package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalSearchDestinationTest {
    @Test
    fun everySearchTypeMapsToItsMatchingFeatureDestination() {
        val expected = mapOf(
            GlobalSearchContentType.PLAN to GlobalSearchDestination.DASHBOARD,
            GlobalSearchContentType.SUBJECT to GlobalSearchDestination.SUBJECTS,
            GlobalSearchContentType.TOPIC to GlobalSearchDestination.SUBJECTS,
            GlobalSearchContentType.QUESTION to GlobalSearchDestination.PRACTICE,
            GlobalSearchContentType.NOTE to GlobalSearchDestination.PROGRESS,
            GlobalSearchContentType.MATERIAL to GlobalSearchDestination.LIBRARY,
            GlobalSearchContentType.FLASHCARD to GlobalSearchDestination.REVIEWS,
            GlobalSearchContentType.ERROR to GlobalSearchDestination.REVIEWS,
        )

        assertEquals(GlobalSearchContentType.entries.toSet(), expected.keys)
        expected.forEach { (type, destination) -> assertEquals(destination, type.destination()) }
    }
}
