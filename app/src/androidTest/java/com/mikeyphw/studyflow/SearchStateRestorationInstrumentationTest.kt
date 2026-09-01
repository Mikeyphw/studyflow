package com.mikeyphw.studyflow

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.mikeyphw.studyflow.core.domain.GlobalSearchContentType
import com.mikeyphw.studyflow.core.domain.GlobalSearchResult
import com.mikeyphw.studyflow.core.domain.ManageStudyDiscovery
import com.mikeyphw.studyflow.core.domain.StudyDiscoveryRepository
import com.mikeyphw.studyflow.core.domain.StudyRecommendation
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.feature.search.R as SearchR
import com.mikeyphw.studyflow.feature.search.StudySearchScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SearchStateRestorationInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun queryAndFilterIntentRestoreAndSearchIsRerunAfterSavedStateRecreation() {
        val repository = RecordingSearchRepository()
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                StudySearchScreen(
                    discovery = ManageStudyDiscovery(repository),
                    recommendationPlanId = null,
                    recentSearches = emptyList(),
                    onRecordRecentSearch = {},
                    onClearRecentSearches = {},
                    onOpenResult = {},
                    onOpenRecommendation = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("algebra")
        val planFilter = composeRule.activity.getString(SearchR.string.type_plan)
        composeRule.onNodeWithText(planFilter).performClick()
        val search = composeRule.activity.getString(SearchR.string.search_action)
        composeRule.onNodeWithText(search).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) { repository.calls.value >= 1 }
        assertEquals("algebra", repository.lastQuery)
        assertFalse(GlobalSearchContentType.PLAN in repository.lastTypes)

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitUntil(timeoutMillis = 3_000L) { repository.calls.value >= 2 }

        assertEquals("algebra", repository.lastQuery)
        assertFalse(GlobalSearchContentType.PLAN in repository.lastTypes)
        composeRule.onNode(hasSetTextAction()).assertTextContains("algebra")
    }
    @Test
    fun failedSearchRetriesInPlaceAndRerunsTheSameQuery() {
        val repository = RecordingSearchRepository(failFirstSearch = true)
        composeRule.setContent {
            MaterialTheme {
                StudySearchScreen(
                    discovery = ManageStudyDiscovery(repository),
                    recommendationPlanId = null,
                    recentSearches = emptyList(),
                    onRecordRecentSearch = {},
                    onClearRecentSearches = {},
                    onOpenResult = {},
                    onOpenRecommendation = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("algebra")
        composeRule.onNodeWithText(composeRule.activity.getString(SearchR.string.search_action)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(SearchR.string.search_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(SearchR.string.retry)).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) { repository.calls.value >= 2 }

        assertEquals("algebra", repository.lastQuery)
        composeRule.onNodeWithText("Algebra").assertIsDisplayed()
    }

    @Test
    fun failedRecommendationsRetryInPlace() {
        val repository = RecordingSearchRepository(failFirstRecommendation = true)
        composeRule.setContent {
            MaterialTheme {
                StudySearchScreen(
                    discovery = ManageStudyDiscovery(repository),
                    recommendationPlanId = StudyPlanId("plan-1"),
                    recentSearches = emptyList(),
                    onRecordRecentSearch = {},
                    onClearRecentSearches = {},
                    onOpenResult = {},
                    onOpenRecommendation = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(SearchR.string.recommendations_failed))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(SearchR.string.retry)).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) { repository.recommendationCalls.value >= 2 }
        assertFalse(repository.recommendationCalls.value < 2)
    }

}

private class RecordingSearchRepository(
    private val failFirstSearch: Boolean = false,
    private val failFirstRecommendation: Boolean = false,
) : StudyDiscoveryRepository {
    val calls = MutableStateFlow(0)
    var lastQuery: String = ""
    var lastTypes: Set<GlobalSearchContentType> = emptySet()
    private val revision = MutableStateFlow(0L)
    val recommendationCalls = MutableStateFlow(0)

    override fun observeRevision(): Flow<Long> = revision

    override suspend fun search(
        query: String,
        contentTypes: Set<GlobalSearchContentType>,
        includeArchived: Boolean,
    ): List<GlobalSearchResult> {
        lastQuery = query
        lastTypes = contentTypes.toSet()
        calls.value += 1
        if (failFirstSearch && calls.value == 1) error("injected search failure")
        return listOf(
            GlobalSearchResult(
                id = "topic-1",
                studyPlanId = StudyPlanId("plan-1"),
                planName = "Plan",
                type = GlobalSearchContentType.TOPIC,
                title = "Algebra",
                subtitle = "Topic",
                score = 100,
                updatedAtEpochMillis = 1L,
            ),
        )
    }

    override suspend fun recommendations(
        studyPlanId: StudyPlanId,
        nowEpochMillis: Long,
    ): List<StudyRecommendation> {
        recommendationCalls.value += 1
        if (failFirstRecommendation && recommendationCalls.value == 1) {
            error("injected recommendation failure")
        }
        return emptyList()
    }
}
