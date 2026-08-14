package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageStudyDiscoveryTest {
    @Test
    fun searchNormalizesWhitespaceAndPreservesTheRequestedFilterPolicy() = runBlocking {
        val repository = RecordingDiscoveryRepository()
        val discovery = ManageStudyDiscovery(repository)
        val types = setOf(GlobalSearchContentType.NOTE, GlobalSearchContentType.MATERIAL)

        discovery.search("  civil   law  ", types, includeArchived = true)

        assertEquals("civil law", repository.query)
        assertEquals(types, repository.contentTypes)
        assertTrue(repository.includeArchived)
    }

    @Test
    fun searchRejectsQueriesThatCannotProduceAUsefulResult() = runBlocking {
        val repository = RecordingDiscoveryRepository()
        val discovery = ManageStudyDiscovery(repository)

        assertTrue(discovery.search(" x ").isEmpty())
        assertTrue(discovery.search("valid", emptySet()).isEmpty())
        assertEquals(0, repository.searchCalls)
    }

    @Test
    fun unicodeNormalizerHandlesCompatibilityCharactersAndWhitespace() {
        assertEquals("árvore abc", UnicodeSearchNormalizer.normalize("  ÁRVORE   ＡＢＣ  "))
        assertTrue(GlobalSearchRanker.rank("árvore", "Árvore Constitucional", "") > 0)
    }

    private class RecordingDiscoveryRepository : StudyDiscoveryRepository {
        var query: String? = null
        var contentTypes: Set<GlobalSearchContentType>? = null
        var includeArchived: Boolean = false
        var searchCalls: Int = 0

        override suspend fun search(
            query: String,
            contentTypes: Set<GlobalSearchContentType>,
            includeArchived: Boolean,
        ): List<GlobalSearchResult> {
            searchCalls++
            this.query = query
            this.contentTypes = contentTypes
            this.includeArchived = includeArchived
            return emptyList()
        }

        override suspend fun recommendations(
            studyPlanId: StudyPlanId,
            nowEpochMillis: Long,
        ): List<StudyRecommendation> = emptyList()
    }
}
