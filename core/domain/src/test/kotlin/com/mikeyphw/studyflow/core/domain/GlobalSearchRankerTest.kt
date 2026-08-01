package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchRankerTest {
    @Test
    fun exactTitleRanksAboveSubtitleMatch() {
        val exact = GlobalSearchRanker.rank("room", "Room", "Database")
        val subtitle = GlobalSearchRanker.rank("room", "Database", "Room persistence")
        assertTrue(exact > subtitle)
    }
}
