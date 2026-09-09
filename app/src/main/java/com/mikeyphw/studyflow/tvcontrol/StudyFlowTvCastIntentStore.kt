package com.mikeyphw.studyflow.tvcontrol

import android.content.Context
import com.mikeyphw.studyflow.core.model.StudyPlanId

enum class StudyFlowTvPersistedCastIntentKind { ACTIVE, STOP_PENDING }

data class StudyFlowTvPersistedCastIntent(
    val planId: StudyPlanId,
    val kind: StudyFlowTvPersistedCastIntentKind,
)

/** TVOV26 persists only explicit cast lifecycle intent; canonical study data stays in repositories. */
interface StudyFlowTvCastIntentStore {
    fun load(): StudyFlowTvPersistedCastIntent?
    fun saveActive(planId: StudyPlanId)
    fun saveStopPending(planId: StudyPlanId)
    fun clear()
}

class SharedPreferencesStudyFlowTvCastIntentStore(context: Context) : StudyFlowTvCastIntentStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    override fun load(): StudyFlowTvPersistedCastIntent? {
        val planId = preferences.getString(KEY_PLAN_ID, null)?.takeIf(String::isNotBlank) ?: return null
        val kind = preferences.getString(KEY_KIND, null)?.let { value ->
            runCatching { StudyFlowTvPersistedCastIntentKind.valueOf(value) }.getOrNull()
        } ?: return null
        return StudyFlowTvPersistedCastIntent(StudyPlanId(planId), kind)
    }

    override fun saveActive(planId: StudyPlanId) = save(planId, StudyFlowTvPersistedCastIntentKind.ACTIVE)

    override fun saveStopPending(planId: StudyPlanId) = save(planId, StudyFlowTvPersistedCastIntentKind.STOP_PENDING)

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private fun save(planId: StudyPlanId, kind: StudyFlowTvPersistedCastIntentKind) {
        preferences.edit()
            .putString(KEY_PLAN_ID, planId.value)
            .putString(KEY_KIND, kind.name)
            .apply()
    }

    private companion object {
        const val PREFERENCE_NAME = "studyflow_tv_cast_intent"
        const val KEY_PLAN_ID = "plan_id"
        const val KEY_KIND = "kind"
    }
}

object NoOpStudyFlowTvCastIntentStore : StudyFlowTvCastIntentStore {
    override fun load(): StudyFlowTvPersistedCastIntent? = null
    override fun saveActive(planId: StudyPlanId) = Unit
    override fun saveStopPending(planId: StudyPlanId) = Unit
    override fun clear() = Unit
}
