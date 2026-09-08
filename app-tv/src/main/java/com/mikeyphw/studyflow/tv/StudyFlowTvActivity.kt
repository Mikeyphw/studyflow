package com.mikeyphw.studyflow.tv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvRemoteCommand
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvRemoteKeyMapper
import com.mikeyphw.studyflow.tv.focus.applyStudyFlowTvDpadFocus
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvNavigationShell

class StudyFlowTvActivity : Activity() {
    private var selectedDestination: StudyFlowTvDestination = StudyFlowTvNavigationShell.defaultDestination()
    private lateinit var contentTitle: TextView
    private lateinit var contentBody: TextView
    private lateinit var navButtons: Map<StudyFlowTvDestination, Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val command = StudyFlowTvRemoteKeyMapper.commandForKeyCode(event.keyCode)
            if (command == StudyFlowTvRemoteCommand.BACK && selectedDestination != StudyFlowTvDestination.HOME) {
                selectDestination(StudyFlowTvDestination.HOME)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun createContentView(): LinearLayout {
        val navigationButtons = linkedMapOf<StudyFlowTvDestination, Button>()
        val primaryAction = tvActionButton(R.string.tv_app_primary_action)
        val pairPhoneAction = tvActionButton(R.string.tv_app_pair_phone_action)
        val practiceAction = tvActionButton(R.string.tv_app_practice_action)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(11, 18, 32))
            setPadding(dp(56), dp(48), dp(56), dp(48))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val navRail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(292), ViewGroup.LayoutParams.MATCH_PARENT)
            addView(
                TextView(context).apply {
                    text = getString(R.string.tv_app_title)
                    setTextColor(Color.WHITE)
                    textSize = 28f
                    gravity = Gravity.START
                    setPadding(0, 0, 0, dp(28))
                },
            )
        }

        StudyFlowTvNavigationShell.primaryDestinations.forEach { destination ->
            val button = tvNavigationButton(destination)
            navigationButtons[destination] = button
            navRail.addView(button)
        }

        wireNavigationFocus(navigationButtons, primaryAction)
        navButtons = navigationButtons

        contentTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 34f
            gravity = Gravity.START
        }
        contentBody = TextView(this).apply {
            setTextColor(Color.rgb(199, 210, 254))
            textSize = 20f
            gravity = Gravity.START
            setPadding(0, dp(22), 0, 0)
        }

        val contentPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(48), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            addView(contentTitle)
            addView(contentBody)
            addView(
                TextView(context).apply {
                    text = getString(R.string.tv_shell_remote_hint)
                    setTextColor(Color.rgb(148, 163, 184))
                    textSize = 16f
                    gravity = Gravity.START
                    setPadding(0, dp(18), 0, 0)
                },
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    setPadding(0, dp(34), 0, 0)
                    addView(primaryAction)
                    addView(pairPhoneAction)
                    addView(practiceAction)
                },
            )
        }

        primaryAction.nextFocusLeftId = navigationButtons[selectedDestination]?.id ?: View.NO_ID
        pairPhoneAction.nextFocusLeftId = primaryAction.id
        pairPhoneAction.nextFocusRightId = practiceAction.id
        practiceAction.nextFocusLeftId = pairPhoneAction.id
        primaryAction.nextFocusRightId = pairPhoneAction.id

        root.addView(navRail)
        root.addView(contentPanel)

        renderDestination(selectedDestination)
        // TVOV04 compatibility marker: post { primaryAction.requestFocus() }
        root.post { navigationButtons[selectedDestination]?.requestFocus() }
        return root
    }

    private fun wireNavigationFocus(
        buttons: Map<StudyFlowTvDestination, Button>,
        contentAction: Button,
    ) {
        val destinations = StudyFlowTvNavigationShell.primaryDestinations
        destinations.forEachIndexed { index, destination ->
            val button = buttons.getValue(destination)
            button.nextFocusRightId = contentAction.id
            if (index > 0) {
                button.nextFocusUpId = buttons.getValue(destinations[index - 1]).id
            }
            if (index < destinations.lastIndex) {
                button.nextFocusDownId = buttons.getValue(destinations[index + 1]).id
            }
        }
    }

    private fun selectDestination(destination: StudyFlowTvDestination) {
        selectedDestination = destination
        renderDestination(destination)
    }

    private fun renderDestination(destination: StudyFlowTvDestination) {
        contentTitle.text = getString(destination.titleResId())
        contentBody.text = getString(destination.bodyResId())
        navButtons.forEach { (itemDestination, button) ->
            button.isSelected = itemDestination == destination
        }
    }

    private fun tvNavigationButton(destination: StudyFlowTvDestination): Button = Button(this).apply {
        id = View.generateViewId()
        text = getString(destination.labelResId())
        textSize = 18f
        isAllCaps = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(Color.WHITE)
        setPadding(dp(20), dp(14), dp(20), dp(14))
        applyStudyFlowTvDpadFocus()
        setOnClickListener { selectDestination(destination) }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(5)
            bottomMargin = dp(5)
        }
    }

    private fun tvActionButton(labelResId: Int): Button = Button(this).apply {
        id = View.generateViewId()
        text = getString(labelResId)
        textSize = 18f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setPadding(dp(32), dp(18), dp(32), dp(18))
        applyStudyFlowTvDpadFocus()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginEnd = dp(12)
        }
    }

    private fun StudyFlowTvDestination.labelResId(): Int = when (this) {
        StudyFlowTvDestination.HOME -> R.string.tv_nav_home
        StudyFlowTvDestination.TODAY -> R.string.tv_nav_today
        StudyFlowTvDestination.PRACTICE -> R.string.tv_nav_practice
        StudyFlowTvDestination.REVIEWS -> R.string.tv_nav_reviews
        StudyFlowTvDestination.MOCK_EXAM -> R.string.tv_nav_mock_exam
        StudyFlowTvDestination.PROGRESS -> R.string.tv_nav_progress
        StudyFlowTvDestination.SCHEDULE -> R.string.tv_nav_schedule
        StudyFlowTvDestination.SETTINGS -> R.string.tv_nav_settings
    }

    private fun StudyFlowTvDestination.titleResId(): Int = when (this) {
        StudyFlowTvDestination.HOME -> R.string.tv_content_title_home
        StudyFlowTvDestination.TODAY -> R.string.tv_content_title_today
        StudyFlowTvDestination.PRACTICE -> R.string.tv_content_title_practice
        StudyFlowTvDestination.REVIEWS -> R.string.tv_content_title_reviews
        StudyFlowTvDestination.MOCK_EXAM -> R.string.tv_content_title_mock_exam
        StudyFlowTvDestination.PROGRESS -> R.string.tv_content_title_progress
        StudyFlowTvDestination.SCHEDULE -> R.string.tv_content_title_schedule
        StudyFlowTvDestination.SETTINGS -> R.string.tv_content_title_settings
    }

    private fun StudyFlowTvDestination.bodyResId(): Int = when (this) {
        StudyFlowTvDestination.HOME -> R.string.tv_content_body_home
        StudyFlowTvDestination.TODAY -> R.string.tv_content_body_today
        StudyFlowTvDestination.PRACTICE -> R.string.tv_content_body_practice
        StudyFlowTvDestination.REVIEWS -> R.string.tv_content_body_reviews
        StudyFlowTvDestination.MOCK_EXAM -> R.string.tv_content_body_mock_exam
        StudyFlowTvDestination.PROGRESS -> R.string.tv_content_body_progress
        StudyFlowTvDestination.SCHEDULE -> R.string.tv_content_body_schedule
        StudyFlowTvDestination.SETTINGS -> R.string.tv_content_body_settings
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
