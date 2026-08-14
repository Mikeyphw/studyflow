package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.domain.GlobalSearchContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexedStudyDiscoveryQueryTest {
    @Test
    fun `match expression preserves unicode tokens deduplicates and bounds work`() {
        val expression = IndexedStudyDiscoveryQuery.matchExpression(
            "ação revisão ação um dois três quatro cinco seis sete oito nove",
        )

        assertEquals(
            "ação* revisão* um* dois* três* quatro* cinco* seis*",
            expression,
        )
    }

    @Test
    fun `search is fts backed sql bounded and rechecks ownership visibility`() {
        val query = IndexedStudyDiscoveryQuery.search(
            query = "revisão",
            contentTypes = GlobalSearchContentType.entries.toSet(),
            includeArchived = false,
        )

        assertNotNull(query)
        val sql = requireNotNull(query).sql
        assertTrue("MATCH ?" in sql)
        assertTrue("LIMIT 320" in sql)
        assertTrue("JOIN study_plans" in sql)
        assertTrue("status NOT IN ('ARCHIVED', 'TRASHED')" in sql)
        assertTrue("s.archived = 0" in sql)
    }
}
