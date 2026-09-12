package com.mikeyphw.studyflow.widget

import android.appwidget.AppWidgetManager
import android.content.Context

class StudyFlowWidgetConfigurationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(StoreName, Context.MODE_PRIVATE)

    fun load(appWidgetId: Int): StudyFlowWidgetConfiguration {
        val prefix = keyPrefix(appWidgetId)
        if (!preferences.contains(prefix + ModeSuffix)) {
            return StudyFlowWidgetConfiguration(appWidgetId = appWidgetId)
        }
        val mode = preferences.getString(prefix + ModeSuffix, null)
            ?.let { raw -> StudyFlowWidgetMode.entries.firstOrNull { it.name == raw } }
            ?: StudyFlowWidgetMode.TODAY
        return StudyFlowWidgetConfiguration(
            appWidgetId = appWidgetId,
            mode = mode,
            studyPlanId = preferences.getString(prefix + PlanSuffix, null)?.takeIf(String::isNotBlank),
            showReviewCount = preferences.getBoolean(prefix + ReviewsSuffix, true),
        )
    }

    fun save(configuration: StudyFlowWidgetConfiguration) {
        require(configuration.appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID)
        val prefix = keyPrefix(configuration.appWidgetId)
        preferences.edit()
            .putInt(SchemaVersionKey, SchemaVersion)
            .putString(prefix + ModeSuffix, configuration.mode.name)
            .putString(prefix + PlanSuffix, configuration.studyPlanId.orEmpty())
            .putBoolean(prefix + ReviewsSuffix, configuration.showReviewCount)
            .apply()
    }

    fun delete(appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        preferences.edit().apply {
            appWidgetIds.forEach { id ->
                val prefix = keyPrefix(id)
                remove(prefix + ModeSuffix)
                remove(prefix + PlanSuffix)
                remove(prefix + ReviewsSuffix)
            }
        }.apply()
    }

    fun restore(oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val editor = preferences.edit()
        repeat(minOf(oldWidgetIds.size, newWidgetIds.size)) { index ->
            val oldId = oldWidgetIds[index]
            val newId = newWidgetIds[index]
            val old = load(oldId)
            val newPrefix = keyPrefix(newId)
            editor.putString(newPrefix + ModeSuffix, old.mode.name)
            editor.putString(newPrefix + PlanSuffix, old.studyPlanId.orEmpty())
            editor.putBoolean(newPrefix + ReviewsSuffix, old.showReviewCount)
            val oldPrefix = keyPrefix(oldId)
            editor.remove(oldPrefix + ModeSuffix)
            editor.remove(oldPrefix + PlanSuffix)
            editor.remove(oldPrefix + ReviewsSuffix)
        }
        editor.putInt(SchemaVersionKey, SchemaVersion).apply()
    }

    fun configuredCount(appWidgetIds: IntArray): Int = appWidgetIds.count { id ->
        preferences.contains(keyPrefix(id) + ModeSuffix)
    }

    private fun keyPrefix(appWidgetId: Int) = "widget_${appWidgetId}_"

    private companion object {
        const val StoreName = "studyflow_widget_configuration_v2"
        const val SchemaVersionKey = "schema_version"
        const val SchemaVersion = 2
        const val ModeSuffix = "mode"
        const val PlanSuffix = "plan"
        const val ReviewsSuffix = "show_reviews"
    }
}
