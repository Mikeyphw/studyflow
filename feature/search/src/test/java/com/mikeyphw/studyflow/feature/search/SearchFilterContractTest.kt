package com.mikeyphw.studyflow.feature.search

import com.mikeyphw.studyflow.core.domain.GlobalSearchContentType
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFilterContractTest {
    @Test
    fun allPromisedContentTypesAreExposed() {
        assertEquals(8, GlobalSearchContentType.entries.size)
    }
}
