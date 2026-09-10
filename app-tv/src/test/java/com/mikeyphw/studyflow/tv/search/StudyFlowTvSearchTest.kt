package com.mikeyphw.studyflow.tv.search

import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvSearchTest {
    @Test
    fun matchingRanksTitleBeforeSubtitleAndDpadClamps() {
        val docs = listOf(
            StudyFlowTvSearchDocument("1", "Constitution", "Law", StudyFlowTvDestination.LIBRARY),
            StudyFlowTvSearchDocument("2", "Review", "Constitution questions", StudyFlowTvDestination.REVIEWS),
        )
        val initial = StudyFlowTvSearchState(visible = true, documents = docs, results = docs)
        val searched = StudyFlowTvSearch.updateQuery(initial, "constitution")
        assertEquals("1", searched.results.first().id)
        assertEquals(1, StudyFlowTvSearch.move(searched, 9).selectedIndex)
        assertEquals(0, StudyFlowTvSearch.move(searched, -9).selectedIndex)
        assertTrue(StudyFlowTvSearch.repositorySearchForbidden)
    }
}
