package com.mikeyphw.studyflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchHistoryTest {
    @Test
    fun recordDeduplicatesCaseInsensitivelyAndKeepsNewestFirst() {
        val updated = RecentSearchHistory.record(listOf("Room", "Compose"), " room ")
        assertEquals(listOf("room", "Compose"), updated)
    }

    @Test
    fun encodeAndDecodeRespectTheHistoryLimit() {
        val searches = (1..12).map { "query $it" }
        assertEquals(searches.take(8), RecentSearchHistory.decode(RecentSearchHistory.encode(searches)))
    }
}
