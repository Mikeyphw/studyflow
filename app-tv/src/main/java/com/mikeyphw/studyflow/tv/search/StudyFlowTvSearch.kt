package com.mikeyphw.studyflow.tv.search

import com.mikeyphw.studyflow.core.link.StudyFlowInputDelegationOffer
import com.mikeyphw.studyflow.core.link.StudyFlowInputDelegationResult
import com.mikeyphw.studyflow.core.link.StudyFlowInputEvent
import com.mikeyphw.studyflow.core.link.StudyFlowInputEventKind
import com.mikeyphw.studyflow.tv.control.StudyFlowTvControlState
import com.mikeyphw.studyflow.tv.link.StudyFlowTvControlInputReceiver
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination

data class StudyFlowTvSearchDocument(
    val id: String,
    val title: String,
    val subtitle: String,
    val destination: StudyFlowTvDestination,
    val sourceId: String? = null,
)

data class StudyFlowTvSearchState(
    val visible: Boolean = false,
    val query: String = "",
    val documents: List<StudyFlowTvSearchDocument> = emptyList(),
    val results: List<StudyFlowTvSearchDocument> = emptyList(),
    val selectedIndex: Int = 0,
    val delegatedInputActive: Boolean = false,
) {
    val selected: StudyFlowTvSearchDocument? get() = results.getOrNull(selectedIndex)
}

object StudyFlowTvSearch {
    const val overlay = "TVOV30"
    const val maxResults = 30
    const val dpadNavigation = true
    const val sflpDelegatedTextInput = true
    const val repositorySearchForbidden = true

    fun documents(state: StudyFlowTvControlState): List<StudyFlowTvSearchDocument> = buildList {
        add(StudyFlowTvSearchDocument("today", "Today", state.today.nextBlockTitle, StudyFlowTvDestination.TODAY))
        add(StudyFlowTvSearchDocument("session", state.studySession.title, state.studySession.subject,
            StudyFlowTvDestination.STUDY_SESSION, state.studySession.sessionId))
        state.questionPractice.questions.forEach { question ->
            add(
                StudyFlowTvSearchDocument(
                    "question:${question.sourceId}",
                    question.prompt,
                    state.questionPractice.subject,
                    StudyFlowTvDestination.PRACTICE,
                    question.sourceId,
                ),
            )
        }
        state.review.items.forEach { item ->
            add(StudyFlowTvSearchDocument("review:${item.sourceId}", item.title, "${item.subject} · ${item.reason}",
                StudyFlowTvDestination.REVIEWS, item.sourceId))
        }
        state.progress.goals.forEach { goal ->
            add(StudyFlowTvSearchDocument("goal:${goal.sourceId}", goal.title, "Goal · ${goal.progressPercent}%",
                StudyFlowTvDestination.PROGRESS, goal.sourceId))
        }
        state.schedule.items.forEach { item ->
            add(StudyFlowTvSearchDocument("schedule:${item.sourceId}", item.title, item.timeLabel,
                StudyFlowTvDestination.SCHEDULE, item.sourceId))
        }
        state.library.references.forEach { item ->
            add(StudyFlowTvSearchDocument("library:${item.sourceId}", item.title, "${item.subject} · ${item.sourceLabel}",
                StudyFlowTvDestination.LIBRARY, item.sourceId))
        }
    }.distinctBy { it.id }

    fun open(state: StudyFlowTvControlState): StudyFlowTvSearchState {
        val docs = documents(state)
        return StudyFlowTvSearchState(visible = true, documents = docs, results = docs.take(maxResults))
    }

    fun updateQuery(state: StudyFlowTvSearchState, value: String): StudyFlowTvSearchState {
        val query = value.trim().take(160)
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val results = if (terms.isEmpty()) state.documents.take(maxResults) else state.documents
            .mapNotNull { document -> score(document, terms)?.let { score -> score to document } }
            .sortedWith(
                compareByDescending<Pair<Int, StudyFlowTvSearchDocument>> { it.first }
                    .thenBy { it.second.title },
            )
            .take(maxResults)
            .map { it.second }
        return state.copy(query = query, results = results, selectedIndex = 0)
    }

    fun move(state: StudyFlowTvSearchState, delta: Int): StudyFlowTvSearchState {
        if (state.results.isEmpty()) return state.copy(selectedIndex = 0)
        return state.copy(selectedIndex = (state.selectedIndex + delta).coerceIn(0, state.results.lastIndex))
    }

    private fun score(document: StudyFlowTvSearchDocument, terms: List<String>): Int? {
        val title = document.title.lowercase()
        val subtitle = document.subtitle.lowercase()
        if (terms.any { it !in title && it !in subtitle }) return null
        return terms.sumOf { term ->
            when {
                title == term -> 100
                title.startsWith(term) -> 70
                term in title -> 45
                else -> 20
            }
        }
    }
}

class StudyFlowTvDelegatedSearchInput(
    private val receiver: StudyFlowTvControlInputReceiver,
) {
    fun accept(offer: StudyFlowInputDelegationOffer): Boolean =
        receiver.acceptInput(offer) is StudyFlowInputDelegationResult.Accepted

    fun apply(state: StudyFlowTvSearchState, event: StudyFlowInputEvent): StudyFlowTvSearchState {
        if (receiver.applyInput(event) !is StudyFlowInputDelegationResult.Accepted) return state
        return when (event.kind) {
            StudyFlowInputEventKind.TEXT_REPLACE, StudyFlowInputEventKind.TEXT_COMMIT ->
                StudyFlowTvSearch.updateQuery(state.copy(delegatedInputActive = true), event.text.orEmpty())
            StudyFlowInputEventKind.BACK -> state.copy(visible = false, delegatedInputActive = false)
            else -> state
        }
    }
}
