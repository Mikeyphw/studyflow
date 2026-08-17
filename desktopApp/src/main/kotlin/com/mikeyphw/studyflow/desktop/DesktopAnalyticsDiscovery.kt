package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.GlobalSearchContentType
import com.mikeyphw.studyflow.core.domain.GlobalSearchRanker
import com.mikeyphw.studyflow.core.domain.GlobalSearchResult
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsCalculator
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsRepository
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.domain.StudyDiscoveryRepository
import com.mikeyphw.studyflow.core.domain.StudyInsight
import com.mikeyphw.studyflow.core.domain.StudyInsightKind
import com.mikeyphw.studyflow.core.domain.StudyInsightSeverity
import com.mikeyphw.studyflow.core.domain.StudyRecommendation
import com.mikeyphw.studyflow.core.domain.StudyRecommendationDestination
import com.mikeyphw.studyflow.core.domain.StudyRecommendationPresentation
import com.mikeyphw.studyflow.core.domain.StudyRecommendationReason
import com.mikeyphw.studyflow.core.domain.StudyRecommendationReasonType
import com.mikeyphw.studyflow.core.domain.StudyRecommendationType
import com.mikeyphw.studyflow.core.domain.WeakStudyArea
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.RecentSearchHistory
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class InMemoryStudyAnalyticsRepository(
    private val plans: InMemoryStudyPlanRepository,
    private val library: InMemoryStudyLibraryRepository,
    private val schedule: InMemoryStudyScheduleRepository,
    private val sessions: InMemoryStudySessionRepository,
    private val practice: InMemoryStudyPracticeRepository,
) : StudyAnalyticsRepository {
    private val revision = MutableStateFlow(0L)

    override fun observeSnapshot(planId: StudyPlanId): Flow<StudyAnalyticsSnapshot> = revision.map {
        currentSnapshot(planId)
    }

    fun currentSnapshot(planId: StudyPlanId): StudyAnalyticsSnapshot {
        val plan = plans.currentPlans().firstOrNull { it.id == planId }
        val activities = schedule.currentActivities(planId)
        val sessionRows = sessions.currentSessions(planId)
        val questions = practice.currentQuestions(planId)
        val tests = practice.currentTests(planId)
        val mockExams = practice.currentMockExams(planId)
        val materials = library.currentMaterials(planId)
        val activeMaterials = materials.filterNot(StudyMaterial::archived)
        val completed = activities.count { it.status == PlannedActivityStatus.COMPLETED }
        val scheduled = activities.count { it.status != PlannedActivityStatus.CANCELLED }.coerceAtLeast(1)
        val answered = tests.sumOf { it.answeredCount } + mockExams.sumOf { it.answeredCount }
        val correct = tests.sumOf { it.correctCount } + mockExams.sumOf { it.correctCount }
        val libraryCompleted = activeMaterials.sumOf { it.completedUnits }
        val libraryUnits = activeMaterials
            .sumOf { material -> material.totalUnits ?: material.completedUnits.coerceAtLeast(1) }
            .coerceAtLeast(1)
        val weakAreas = questions
            .groupBy { question -> question.tags.firstOrNull() ?: question.source ?: "General practice" }
            .map { (label, grouped) ->
                val averageDifficulty = grouped.map(Question::difficulty).average().toInt().coerceIn(1, 5)
                WeakStudyArea(
                    label = label.replaceFirstChar(Char::uppercase),
                    progressPercent = (100 - averageDifficulty * 12).coerceIn(0, 100),
                    masteryPercent = (
                        100 - grouped.count(Question::disputed) * 20 - averageDifficulty * 8
                    ).coerceIn(0, 100),
                    priority = averageDifficulty,
                )
            }
            .sortedWith(compareBy<WeakStudyArea> { it.masteryPercent }.thenByDescending { it.priority })
            .take(3)
        val base = StudyAnalyticsSnapshot(
            studyPlanId = planId,
            studiedMinutesLastSevenDays = plan?.studiedMinutesThisWeek
                ?: sessionRows.sumOf { it.grossDurationSeconds }.div(60).toInt(),
            completedSessionsLastSevenDays = sessionRows.count { it.status.name == "COMPLETED" },
            scheduleAdherencePercent = StudyAnalyticsCalculator.percent(completed, scheduled),
            questionAccuracyPercent = StudyAnalyticsCalculator.percent(correct, answered),
            answeredQuestions = answered,
            dueReviewCount = questions.count(Question::favorite),
            overdueReviewCount = activities.count { it.status == PlannedActivityStatus.MISSED },
            retentionEaseAverage = 2.7,
            goalCompletionPercent = plan?.progressPercent ?: 0,
            contentProgressPercent = plan?.progressPercent ?: 0,
            libraryProgressPercent = StudyAnalyticsCalculator.percent(libraryCompleted, libraryUnits),
            consistencyScorePercent = StudyAnalyticsCalculator.consistencyScore(
                activeStudyDays = sessionRows.map { it.startedAtEpochMillis / DAY_MILLIS }.distinct().size,
            ),
            weakAreas = weakAreas,
            insights = emptyList(),
        )
        return base.copy(insights = StudyAnalyticsCalculator.buildInsights(base))
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}

class DesktopStudyDiscoveryRepository(
    private val plans: InMemoryStudyPlanRepository,
    private val library: InMemoryStudyLibraryRepository,
    private val schedule: InMemoryStudyScheduleRepository,
    private val practice: InMemoryStudyPracticeRepository,
    private val analytics: InMemoryStudyAnalyticsRepository,
) : StudyDiscoveryRepository {
    private val revision = MutableStateFlow(0L)

    override fun observeRevision(): Flow<Long> = revision

    override suspend fun search(
        query: String,
        contentTypes: Set<GlobalSearchContentType>,
        includeArchived: Boolean,
    ): List<GlobalSearchResult> {
        val plansRows = plans.currentPlans()
        val planById = plansRows.associateBy(StudyPlan::id)
        val rows = buildList {
            plansRows.forEach { plan ->
                add(
                    GlobalSearchResult(
                        id = plan.id.value,
                        studyPlanId = plan.id,
                        planName = plan.name,
                        type = GlobalSearchContentType.PLAN,
                        title = plan.name,
                        subtitle = plan.description,
                        score = 0,
                        updatedAtEpochMillis = plan.updatedAtEpochMillis,
                    ),
                )
                library.currentMaterials(plan.id).filter { includeArchived || !it.archived }.forEach { material ->
                    add(
                        GlobalSearchResult(
                            id = material.id.value,
                            studyPlanId = plan.id,
                            planName = plan.name,
                            type = GlobalSearchContentType.MATERIAL,
                            title = material.title,
                            subtitle = material.description,
                            score = 0,
                            updatedAtEpochMillis = material.updatedAtEpochMillis,
                        ),
                    )
                }
                schedule.currentActivities(plan.id).forEach { activity ->
                    add(
                        GlobalSearchResult(
                            id = activity.id.value,
                            studyPlanId = plan.id,
                            planName = plan.name,
                            type = GlobalSearchContentType.TOPIC,
                            title = activity.title,
                            subtitle = activity.type.name.replace('_', ' '),
                            score = 0,
                            updatedAtEpochMillis = activity.scheduledStartEpochMillis ?: plan.updatedAtEpochMillis,
                        ),
                    )
                }
                practice.currentQuestions(plan.id).forEach { question ->
                    add(
                        GlobalSearchResult(
                            id = question.id.value,
                            studyPlanId = plan.id,
                            planName = planById[question.studyPlanId]?.name ?: plan.name,
                            type = GlobalSearchContentType.QUESTION,
                            title = question.statement,
                            subtitle = question.tags.joinToString(", ").ifBlank { question.source.orEmpty() },
                            score = 0,
                            updatedAtEpochMillis = question.updatedAtEpochMillis,
                        ),
                    )
                }
            }
        }
        return rows
            .asSequence()
            .filter { result -> result.type in contentTypes }
            .map { result -> result.copy(score = GlobalSearchRanker.rank(query, result.title, result.subtitle)) }
            .filter { result -> result.score > 0 }
            .sortedWith(
                compareByDescending<GlobalSearchResult> { it.score }
                    .thenByDescending { it.updatedAtEpochMillis },
            )
            .take(20)
            .toList()
    }

    override suspend fun recommendations(
        studyPlanId: StudyPlanId,
        nowEpochMillis: Long,
    ): List<StudyRecommendation> {
        val snapshot = analytics.currentSnapshot(studyPlanId)
        val nextActivity = schedule.currentActivities(studyPlanId).firstOrNull { activity ->
            activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)
        }
        val weakArea = snapshot.weakAreas.firstOrNull()
        return buildList {
            nextActivity?.let { activity ->
                add(
                    StudyRecommendation(
                        id = "desktop-next:${activity.id.value}",
                        studyPlanId = studyPlanId,
                        type = StudyRecommendationType.NEXT_STUDY_ITEM,
                        presentation = StudyRecommendationPresentation.PLANNED_ACTIVITY,
                        targetLabel = activity.title,
                        reasons = listOf(
                            StudyRecommendationReason(
                                type = if (activity.status == PlannedActivityStatus.MISSED) {
                                    StudyRecommendationReasonType.MISSED_ACTIVITY
                                } else {
                                    StudyRecommendationReasonType.NEXT_ACTIVITY
                                },
                                title = activity.title,
                            ),
                        ),
                        destination = StudyRecommendationDestination.SCHEDULE,
                        targetId = activity.id.value,
                        score = if (activity.status == PlannedActivityStatus.MISSED) 900 else 650,
                    ),
                )
            }
            weakArea?.let { area ->
                add(
                    StudyRecommendation(
                        id = "desktop-weak:${area.label}",
                        studyPlanId = studyPlanId,
                        type = StudyRecommendationType.WEAK_TOPIC_REVIEW,
                        presentation = StudyRecommendationPresentation.WEAK_TOPIC,
                        targetLabel = area.label,
                        reasons = listOf(
                            StudyRecommendationReason(
                                type = StudyRecommendationReasonType.MASTERY,
                                percent = area.masteryPercent,
                                title = area.label,
                            ),
                        ),
                        destination = StudyRecommendationDestination.PRACTICE,
                        targetId = area.label,
                        score = 720,
                    ),
                )
            }
        }.sortedByDescending(StudyRecommendation::score)
    }
}

class DesktopPreferencesStore(
    initialPreferences: AppPreferences = AppPreferences(dynamicColorEnabled = false),
) {
    private val preferences = MutableStateFlow(initialPreferences)

    val changes: Flow<AppPreferences> = preferences

    fun current(): AppPreferences = preferences.value

    fun setTheme(themeMode: ThemeMode) {
        preferences.value = preferences.value.copy(themeMode = themeMode)
    }

    fun setLanguage(language: AppLanguage) {
        preferences.value = preferences.value.copy(language = language)
    }

    fun recordSearch(query: String) {
        preferences.value = preferences.value.copy(
            recentSearches = RecentSearchHistory.record(preferences.value.recentSearches, query),
        )
    }
}
