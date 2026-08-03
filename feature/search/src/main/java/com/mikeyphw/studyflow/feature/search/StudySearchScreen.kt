package com.mikeyphw.studyflow.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.domain.GlobalSearchContentType
import com.mikeyphw.studyflow.core.domain.GlobalSearchResult
import com.mikeyphw.studyflow.core.domain.ManageStudyDiscovery
import com.mikeyphw.studyflow.core.domain.StudyRecommendation
import com.mikeyphw.studyflow.core.domain.StudyRecommendationPresentation
import com.mikeyphw.studyflow.core.domain.StudyRecommendationReason
import com.mikeyphw.studyflow.core.domain.StudyRecommendationReasonType
import com.mikeyphw.studyflow.core.domain.StudyRecommendationType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudySearchScreen(
    discovery: ManageStudyDiscovery,
    recommendationPlanId: StudyPlanId?,
    recentSearches: List<String>,
    onRecordRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onOpenResult: (GlobalSearchResult) -> Unit,
    onOpenRecommendation: (StudyRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTypes by remember { mutableStateOf(GlobalSearchContentType.entries.toSet()) }
    var results by remember { mutableStateOf(emptyList<GlobalSearchResult>()) }
    var recommendations by remember { mutableStateOf(emptyList<StudyRecommendation>()) }
    var searched by rememberSaveable { mutableStateOf(false) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var recommendationFailed by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchGeneration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(recommendationPlanId) {
        recommendations = emptyList()
        recommendationFailed = false
        recommendationPlanId?.let { planId ->
            try {
                recommendations = discovery.recommendations(planId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                recommendationFailed = true
            }
        }
    }

    fun runSearch(
        searchQuery: String,
        contentTypes: Set<GlobalSearchContentType> = selectedTypes,
        recordHistory: Boolean = true,
    ) {
        val normalized = searchQuery.trim().replace(Regex("\\s+"), " ")
        query = normalized
        searchJob?.cancel()
        searchGeneration += 1
        val generation = searchGeneration
        if (normalized.length < 2 || contentTypes.isEmpty()) {
            results = emptyList()
            searched = normalized.length >= 2
            searchBusy = false
            searchFailed = false
            return
        }
        searchJob = scope.launch {
            searchBusy = true
            searchFailed = false
            try {
                val found = discovery.search(normalized, contentTypes)
                if (searchGeneration == generation) {
                    results = found
                    searched = true
                    if (recordHistory) onRecordRecentSearch(normalized)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (searchGeneration == generation) {
                    results = emptyList()
                    searched = true
                    searchFailed = true
                }
            } finally {
                if (searchGeneration == generation) searchBusy = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.search_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchJob?.cancel()
                                searchGeneration += 1
                                query = ""
                                results = emptyList()
                                searched = false
                                searchBusy = false
                                searchFailed = false
                            },
                        ) {
                            Icon(Icons.Outlined.Clear, stringResource(R.string.clear_search))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
            )
            Button(
                onClick = { runSearch(query) },
                enabled = query.trim().length >= 2 && selectedTypes.isNotEmpty() && !searchBusy,
            ) {
                if (searchBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(end = 8.dp))
                }
                Text(stringResource(if (searchBusy) R.string.searching else R.string.search_action))
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlobalSearchContentType.entries.forEach { type ->
                    FilterChip(
                        selected = type in selectedTypes,
                        onClick = {
                            val updated = if (type in selectedTypes) selectedTypes - type else selectedTypes + type
                            selectedTypes = updated
                            if (searched) runSearch(query, updated, recordHistory = false)
                        },
                        label = { Text(stringResource(type.labelResource())) },
                    )
                }
            }
            if (query.isBlank() && recentSearches.isNotEmpty()) {
                StudyFlowSectionCard(title = stringResource(R.string.recent_searches)) {
                    recentSearches.forEach { recent ->
                        AssistChip(onClick = { runSearch(recent) }, label = { Text(recent) })
                    }
                    OutlinedButton(onClick = onClearRecentSearches) {
                        Text(stringResource(R.string.clear_recent_searches))
                    }
                }
            }
            if (recommendationFailed) {
                Text(
                    stringResource(R.string.recommendations_failed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (recommendations.isNotEmpty()) {
                StudyFlowSectionCard(title = stringResource(R.string.recommendations_title)) {
                    recommendations.forEach { recommendation ->
                        ResultButton(
                            title = recommendationTitle(recommendation),
                            subtitle = recommendationSubtitle(recommendation),
                            badge = stringResource(recommendation.type.labelResource()),
                            onClick = { onOpenRecommendation(recommendation) },
                        )
                    }
                }
            }
            if (searched) {
                StudyFlowSectionCard(
                    title = pluralStringResource(R.plurals.search_results, results.size, results.size),
                ) {
                    when {
                        searchFailed -> Text(
                            stringResource(R.string.search_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                        results.isEmpty() && !searchBusy -> Text(
                            stringResource(R.string.no_search_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> results.forEach { result ->
                            ResultButton(
                                title = result.title,
                                subtitle = stringResource(
                                    R.string.result_context,
                                    result.planName,
                                    result.subtitle,
                                ),
                                badge = stringResource(result.type.labelResource()),
                                onClick = { onOpenResult(result) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun recommendationTitle(recommendation: StudyRecommendation): String = when (recommendation.presentation) {
    StudyRecommendationPresentation.DUE_REVIEWS -> {
        val count = recommendation.reasons
            .firstOrNull { it.type == StudyRecommendationReasonType.DUE_REVIEW_COUNT }
            ?.count.orZero()
        pluralStringResource(R.plurals.recommendation_due_reviews, count, count)
    }
    StudyRecommendationPresentation.PLANNED_ACTIVITY -> recommendation.targetLabel
    StudyRecommendationPresentation.WEAK_TOPIC -> stringResource(
        R.string.recommendation_review_topic,
        recommendation.targetLabel,
    )
    StudyRecommendationPresentation.PRACTICE_TOPIC -> stringResource(
        R.string.recommendation_practice_topic,
        recommendation.targetLabel,
    )
    StudyRecommendationPresentation.OVERDUE_RESOURCE -> stringResource(
        R.string.recommendation_continue_resource,
        recommendation.targetLabel,
    )
}

@Composable
private fun recommendationSubtitle(recommendation: StudyRecommendation): String {
    val separator = stringResource(R.string.reason_separator)
    val localizedReasons = mutableListOf<String>()
    for (reason in recommendation.reasons) {
        localizedReasons += recommendationReason(reason)
    }
    return localizedReasons.joinToString(separator = separator)
}

@Composable
private fun recommendationReason(reason: StudyRecommendationReason): String = when (reason.type) {
    StudyRecommendationReasonType.DUE_REVIEW_COUNT -> {
        val count = reason.count.orZero()
        pluralStringResource(R.plurals.reason_due_reviews, count, count)
    }
    StudyRecommendationReasonType.SCHEDULE_ADHERENCE -> stringResource(
        R.string.reason_schedule_adherence,
        reason.percent.orZero(),
    )
    StudyRecommendationReasonType.GOAL_DEADLINE -> stringResource(
        R.string.reason_goal_deadline,
        reason.title.orEmpty(),
        reason.dateIso.orEmpty(),
    )
    StudyRecommendationReasonType.MISSED_ACTIVITY -> stringResource(R.string.reason_missed_activity)
    StudyRecommendationReasonType.NEXT_ACTIVITY -> stringResource(R.string.reason_next_activity)
    StudyRecommendationReasonType.MASTERY -> stringResource(R.string.reason_mastery, reason.percent.orZero())
    StudyRecommendationReasonType.RECURRING_ERRORS -> {
        val count = reason.count.orZero()
        pluralStringResource(R.plurals.reason_recurring_errors, count, count)
    }
    StudyRecommendationReasonType.QUESTION_ACCURACY -> stringResource(
        R.string.reason_question_accuracy,
        reason.percent.orZero(),
    )
    StudyRecommendationReasonType.AVAILABLE_QUESTIONS -> {
        val count = reason.count.orZero()
        pluralStringResource(R.plurals.reason_available_questions, count, count)
    }
    StudyRecommendationReasonType.OVERDUE_STUDY_BLOCK -> stringResource(R.string.reason_overdue_study_block)
    StudyRecommendationReasonType.RESOURCE_PROGRESS -> {
        val totalUnits = reason.totalUnits.orZero()
        pluralStringResource(
            R.plurals.reason_resource_progress,
            totalUnits,
            reason.completedUnits.orZero(),
            totalUnits,
        )
    }
    StudyRecommendationReasonType.RESOURCE_UNFINISHED -> stringResource(R.string.reason_resource_unfinished)
}

@Composable
private fun ResultButton(
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(badge, style = MaterialTheme.typography.labelSmall)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0

private fun GlobalSearchContentType.labelResource(): Int = when (this) {
    GlobalSearchContentType.PLAN -> R.string.type_plan
    GlobalSearchContentType.SUBJECT -> R.string.type_subject
    GlobalSearchContentType.TOPIC -> R.string.type_topic
    GlobalSearchContentType.QUESTION -> R.string.type_question
    GlobalSearchContentType.NOTE -> R.string.type_note
    GlobalSearchContentType.MATERIAL -> R.string.type_material
    GlobalSearchContentType.FLASHCARD -> R.string.type_flashcard
    GlobalSearchContentType.ERROR -> R.string.type_error
}

private fun StudyRecommendationType.labelResource(): Int = when (this) {
    StudyRecommendationType.NEXT_STUDY_ITEM -> R.string.next_study_item
    StudyRecommendationType.WEAK_TOPIC_REVIEW -> R.string.weak_topic_review
    StudyRecommendationType.PRACTICE_DECK -> R.string.practice_deck
    StudyRecommendationType.OVERDUE_RESOURCE -> R.string.overdue_resource
}
