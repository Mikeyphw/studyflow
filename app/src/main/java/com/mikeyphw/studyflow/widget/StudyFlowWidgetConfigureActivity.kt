package com.mikeyphw.studyflow.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mikeyphw.studyflow.AppInitializationState
import com.mikeyphw.studyflow.R
import com.mikeyphw.studyflow.requireStudyFlowAppContainer
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StudyFlowWidgetConfigureActivity : AppCompatActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var plans: List<StudyPlan> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContentView(R.layout.widget_configure)
        val modeSpinner = findViewById<Spinner>(R.id.widget_config_mode)
        val planSpinner = findViewById<Spinner>(R.id.widget_config_plan)
        val reviewsCheck = findViewById<CheckBox>(R.id.widget_config_reviews)
        val saveButton = findViewById<Button>(R.id.widget_config_save)
        val retryButton = findViewById<Button>(R.id.widget_config_retry)
        val progress = findViewById<ProgressBar>(R.id.widget_config_progress)
        val empty = findViewById<TextView>(R.id.widget_config_empty)

        val configuration = StudyFlowWidgetConfigurationStore(this).load(appWidgetId)
        val modes = StudyFlowWidgetMode.entries
        modeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            modes.map(::modeLabel),
        )
        modeSpinner.setSelection(modes.indexOf(configuration.mode).coerceAtLeast(0))
        reviewsCheck.isChecked = configuration.showReviewCount
        saveButton.isEnabled = false

        lateinit var loadPlans: (Boolean) -> Unit
        loadPlans = { retry ->
            progress.visibility = View.VISIBLE
            empty.visibility = View.GONE
            retryButton.visibility = View.GONE
            saveButton.isEnabled = false
            lifecycleScope.launch {
                val container = requireStudyFlowAppContainer()
                if (retry) container.retryInitialization()
                val initialization = container.initializationState.first { it !is AppInitializationState.Initializing }
                if (initialization is AppInitializationState.Ready) {
                    plans = container.manageStudyPlan.plans.first()
                        .filter { it.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED) }
                        .sortedBy { it.name.lowercase() }
                } else {
                    plans = emptyList()
                }
                val planLabels = listOf(getString(R.string.widget_config_default_plan)) + plans.map { it.name }
                planSpinner.adapter = ArrayAdapter(
                    this@StudyFlowWidgetConfigureActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    planLabels,
                )
                val selectedIndex = configuration.studyPlanId
                    ?.let { id -> plans.indexOfFirst { it.id.value == id }.takeIf { it >= 0 }?.plus(1) }
                    ?: 0
                planSpinner.setSelection(selectedIndex)
                progress.visibility = View.GONE
                if (initialization is AppInitializationState.Failed) {
                    empty.setText(R.string.widget_config_load_failed)
                    empty.visibility = View.VISIBLE
                    retryButton.visibility = View.VISIBLE
                    saveButton.isEnabled = false
                } else {
                    empty.setText(R.string.widget_config_no_plans)
                    empty.visibility = if (plans.isEmpty()) View.VISIBLE else View.GONE
                    retryButton.visibility = View.GONE
                    saveButton.isEnabled = true
                }
            }
        }
        retryButton.setOnClickListener { loadPlans(true) }
        loadPlans(false)


        saveButton.setOnClickListener {
            val selectedMode = modes.getOrElse(modeSpinner.selectedItemPosition) { StudyFlowWidgetMode.TODAY }
            val selectedPlan = plans.getOrNull(planSpinner.selectedItemPosition - 1)?.id?.value
            StudyFlowWidgetConfigurationStore(this).save(
                StudyFlowWidgetConfiguration(
                    appWidgetId = appWidgetId,
                    mode = selectedMode,
                    studyPlanId = selectedPlan,
                    showReviewCount = reviewsCheck.isChecked,
                ),
            )
            StudyFlowWidgetUpdateCoordinator.refreshAll(this)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }

    private fun modeLabel(mode: StudyFlowWidgetMode): String = getString(
        when (mode) {
            StudyFlowWidgetMode.TODAY -> R.string.widget_mode_today
            StudyFlowWidgetMode.NEXT -> R.string.widget_mode_next
            StudyFlowWidgetMode.REVIEWS -> R.string.widget_mode_reviews
            StudyFlowWidgetMode.SESSION -> R.string.widget_mode_session
            StudyFlowWidgetMode.COMPACT -> R.string.widget_mode_compact
        },
    )
}
