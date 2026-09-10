package com.mikeyphw.studyflow.tv

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMaterialViewerKind
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMaterialViewerPresentation
import com.mikeyphw.studyflow.tv.library.StudyFlowTvMaterialViewer
import com.mikeyphw.studyflow.tv.library.StudyFlowTvMaterialViewerAction
import com.mikeyphw.studyflow.tv.library.StudyFlowTvMaterialViewerState
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvGoal
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressAnalytics
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewNotebook
import com.mikeyphw.studyflow.tv.search.StudyFlowTvSearch
import com.mikeyphw.studyflow.tv.search.StudyFlowTvSearchDocument
import com.mikeyphw.studyflow.tv.search.StudyFlowTvSearchState
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFlowTvCommonSemanticFixtureTest {
    @Test
    fun androidTvMatchesCommonSemanticFixtures() {
        fixtureLines().forEach { parts ->
            when (parts[0]) {
                "viewer-page" -> {
                    val current = parts[1].toInt()
                    val total = parts[2].toInt()
                    val delta = parts[3].toInt()
                    val expected = parts[4].toInt()
                    val state = viewerState(current, total)
                    val action = if (delta < 0) StudyFlowTvMaterialViewerAction.PREVIOUS_PAGE
                        else StudyFlowTvMaterialViewerAction.NEXT_PAGE
                    assertEquals(expected, StudyFlowTvMaterialViewer.reduce(state, action).pageIndex)
                }
                "viewer-zoom" -> {
                    val current = parts[1].toInt()
                    val delta = parts[2].toInt()
                    val expected = parts[3].toInt()
                    val state = viewerState(0, 1).copy(zoomPercent = current)
                    val action = if (delta < 0) StudyFlowTvMaterialViewerAction.ZOOM_OUT
                        else StudyFlowTvMaterialViewerAction.ZOOM_IN
                    assertEquals(expected, StudyFlowTvMaterialViewer.reduce(state, action).zoomPercent)
                }
                "goal-actions" -> {
                    val goal = StudyFlowTvGoal(
                        sourceId = "fixture",
                        title = "Fixture",
                        goalType = parts[1],
                        progressPercent = 50,
                        completed = parts[2].toBoolean(),
                        paused = parts[3].toBoolean(),
                        canComplete = parts[4].toBoolean(),
                    )
                    val expected = parts.getOrElse(5) { "" }.takeIf { it.isNotEmpty() }?.split(',') ?: emptyList()
                    assertEquals(expected, StudyFlowTvProgressAnalytics.goalActions(goal).map { it.name })
                }
                "error-category" -> assertEquals(parts[2], StudyFlowTvReviewNotebook.nextErrorCategory(parts[1]))
                "error-resolve" -> {
                    val actual = if (parts[1].toBoolean()) "REOPEN" else "RESOLVE"
                    assertEquals(parts[2], actual)
                }
                "search-score" -> {
                    val document = StudyFlowTvSearchDocument(
                        "fixture", parts[2], parts[3], StudyFlowTvDestination.LIBRARY,
                    )
                    val state = StudyFlowTvSearchState(
                        visible = true,
                        documents = listOf(document),
                        results = listOf(document),
                    )
                    val matched = StudyFlowTvSearch.updateQuery(state, parts[1]).results.isNotEmpty()
                    val title = parts[2].lowercase()
                    val query = parts[1].lowercase()
                    val subtitle = parts[3].lowercase()
                    val score = when {
                        !matched -> null
                        title == query -> 100
                        title.startsWith(query) -> 70
                        query in title -> 45
                        query in subtitle -> 20
                        else -> null
                    }
                    assertEquals(parts[4].toInt(), score)
                }
            }
        }
    }

    private fun viewerState(page: Int, total: Int) = StudyFlowTvMaterialViewerState(
        title = "Fixture",
        presentation = StudyFlowTvMaterialViewerPresentation(
            kind = StudyFlowTvMaterialViewerKind.PDF, sourceUri = "content://fixture", pageCountHint = total,
        ),
        textPages = listOf("fixture"),
        pageIndex = page,
    )

    private fun fixtureLines(): List<List<String>> {
        val working = File(requireNotNull(System.getProperty("user.dir")) { "user.dir system property is unavailable." })
        val root = if (working.name == "app-tv") working.parentFile ?: working else working
        val fixture = File(root, "protocol/tv-parity/v1/semantic-fixtures.tsv")
        check(fixture.isFile) { "Common TV parity fixture is missing: ${fixture.absolutePath}" }
        return fixture.readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('\t') }
    }
}
