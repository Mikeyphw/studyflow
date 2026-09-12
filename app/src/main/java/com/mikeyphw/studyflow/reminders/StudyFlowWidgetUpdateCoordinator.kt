package com.mikeyphw.studyflow.reminders

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.mikeyphw.studyflow.AppInitializationState
import com.mikeyphw.studyflow.requireStudyFlowAppContainer
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.widget.StudyFlowTodayWidgetProvider
import com.mikeyphw.studyflow.widget.StudyFlowWidgetConfiguration
import com.mikeyphw.studyflow.widget.StudyFlowWidgetConfigurationStore
import com.mikeyphw.studyflow.widget.StudyFlowWidgetFreshnessScheduler
import com.mikeyphw.studyflow.widget.StudyFlowWidgetMode
import com.mikeyphw.studyflow.widget.StudyFlowWidgetSnapshotProjector
import com.mikeyphw.studyflow.widget.TodayWidgetRenderState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val WidgetUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val WidgetRefreshRunning = AtomicBoolean(false)
private val WidgetRefreshDirty = AtomicBoolean(false)
private val WidgetRefreshRequestedGeneration = AtomicLong(0L)
private val WidgetRefreshWaiterLock = Any()
private val WidgetRefreshWaiters = mutableListOf<WidgetRefreshWaiter>()

data class WidgetRefreshResult(
    val boundInstanceCount: Int,
    val state: String,
    val configuredInstanceCount: Int = 0,
    val modeCounts: Map<StudyFlowWidgetMode, Int> = emptyMap(),
    val nextRefreshEpochMillis: Long? = null,
)

private data class WidgetRefreshWaiter(
    val generation: Long,
    val deferred: CompletableDeferred<WidgetRefreshResult>,
)

object StudyFlowWidgetUpdateCoordinator {
    fun refreshAll(context: Context) {
        requestRefresh(context.applicationContext, waiter = null)
    }

    suspend fun refreshAllAndAwait(context: Context): WidgetRefreshResult {
        val deferred = CompletableDeferred<WidgetRefreshResult>()
        requestRefresh(context.applicationContext, deferred)
        return deferred.await()
    }

    private fun requestRefresh(context: Context, waiter: CompletableDeferred<WidgetRefreshResult>?) {
        val requestedGeneration = WidgetRefreshRequestedGeneration.incrementAndGet()
        if (waiter != null) {
            synchronized(WidgetRefreshWaiterLock) {
                WidgetRefreshWaiters += WidgetRefreshWaiter(requestedGeneration, waiter)
            }
        }
        WidgetRefreshDirty.set(true)
        if (!WidgetRefreshRunning.compareAndSet(false, true)) return
        WidgetUpdateScope.launch { runRefreshLoop(context) }
    }

    private suspend fun runRefreshLoop(context: Context) {
        try {
            do {
                WidgetRefreshDirty.set(false)
                val generation = WidgetRefreshRequestedGeneration.get()
                val result = refreshGeneration(context)
                completeRefreshGeneration(generation, result)
            } while (WidgetRefreshDirty.get())
        } catch (failure: Exception) {
            failRefreshWaiters(failure)
        } finally {
            WidgetRefreshRunning.set(false)
            if (WidgetRefreshDirty.get()) requestRefresh(context, waiter = null)
        }
    }

    private suspend fun refreshGeneration(context: Context): WidgetRefreshResult {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, StudyFlowTodayWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) {
            StudyFlowWidgetFreshnessScheduler.cancel(context)
            return WidgetRefreshResult(0, "no_instances")
        }
        val container = context.requireStudyFlowAppContainer()
        val initialization = container.initializationState.value
        if (initialization !is AppInitializationState.Ready) {
            val store = StudyFlowWidgetConfigurationStore(context)
            val loading = initialization is AppInitializationState.Initializing
            ids.forEach { appWidgetId ->
                val configuration = store.load(appWidgetId)
                StudyFlowTodayWidgetProvider.updateWidget(
                    context,
                    manager,
                    appWidgetId,
                    if (loading) TodayWidgetRenderState.Loading(configuration)
                    else TodayWidgetRenderState.DataUnavailable(configuration),
                )
            }
            val retryAt = System.currentTimeMillis() + if (loading) 60_000L else 15 * 60 * 1_000L
            StudyFlowWidgetFreshnessScheduler.schedule(context, retryAt)
            return WidgetRefreshResult(
                boundInstanceCount = ids.size,
                configuredInstanceCount = store.configuredCount(ids),
                state = if (loading) "loading" else "data_unavailable",
                nextRefreshEpochMillis = retryAt,
            )
        }
        val preferences = container.appPreferencesRepository.preferences.first()
        val plans = container.manageStudyPlan.plans.first()
        val configStore = StudyFlowWidgetConfigurationStore(context)
        val stateNames = mutableSetOf<String>()
        val modeCounts = mutableMapOf<StudyFlowWidgetMode, Int>()
        var nextRefreshAt: Long? = null

        ids.forEach { appWidgetId ->
            val configuration = configStore.load(appWidgetId)
            modeCounts[configuration.mode] = (modeCounts[configuration.mode] ?: 0) + 1
            val selectedPlanId = configuration.studyPlanId?.let(::StudyPlanId) ?: preferences.defaultStudyPlanId
            val state = when {
                selectedPlanId == null -> TodayWidgetRenderState.NoPlan(configuration)
                else -> {
                    val plan = resolveConfiguredWidgetPlan(plans, selectedPlanId)
                    if (plan == null) {
                        TodayWidgetRenderState.PlanUnavailable(configuration)
                    } else {
                        val activeSession = container.manageStudySession.observeActiveSessions().first()
                            .firstOrNull { it.studyPlanId == plan.id }
                        val now = System.currentTimeMillis()
                        val snapshot = StudyFlowWidgetSnapshotProjector.project(
                            plan = plan,
                            schedule = container.manageStudySchedule.observeSchedule(plan.id).first(),
                            reviewQueue = container.manageStudyReview.observeQueue(plan.id).first(),
                            activeSession = activeSession,
                            liveActiveSeconds = activeSession?.let(container.manageStudySession::liveActiveSeconds),
                            nowEpochMillis = now,
                        )
                        nextRefreshAt = listOfNotNull(nextRefreshAt, snapshot.nextRefreshEpochMillis).minOrNull()
                        TodayWidgetRenderState.Ready(configuration, snapshot)
                    }
                }
            }
            stateNames += state.debugName
            StudyFlowTodayWidgetProvider.updateWidget(context, manager, appWidgetId, state)
        }
        StudyFlowWidgetFreshnessScheduler.schedule(context, nextRefreshAt)
        return WidgetRefreshResult(
            boundInstanceCount = ids.size,
            configuredInstanceCount = configStore.configuredCount(ids),
            state = stateNames.sorted().joinToString("+").ifBlank { "unknown" },
            modeCounts = modeCounts.toMap(),
            nextRefreshEpochMillis = nextRefreshAt,
        )
    }

    private fun completeRefreshGeneration(generation: Long, result: WidgetRefreshResult) {
        val completed = synchronized(WidgetRefreshWaiterLock) {
            val ready = WidgetRefreshWaiters.filter { it.generation <= generation }
            WidgetRefreshWaiters.removeAll(ready.toSet())
            ready
        }
        completed.forEach { it.deferred.complete(result) }
    }

    private fun failRefreshWaiters(failure: Exception) {
        val failed = synchronized(WidgetRefreshWaiterLock) {
            WidgetRefreshWaiters.toList().also { WidgetRefreshWaiters.clear() }
        }
        failed.forEach { it.deferred.completeExceptionally(failure) }
    }
}

internal fun resolveCanonicalWidgetPlan(plans: List<StudyPlan>, defaultPlanId: StudyPlanId): StudyPlan? =
    resolveConfiguredWidgetPlan(plans, defaultPlanId)

internal fun resolveConfiguredWidgetPlan(plans: List<StudyPlan>, planId: StudyPlanId): StudyPlan? = plans.firstOrNull { candidate ->
    candidate.id == planId && candidate.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED)
}
