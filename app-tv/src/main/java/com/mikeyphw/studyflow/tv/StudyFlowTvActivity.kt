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
import com.mikeyphw.studyflow.tv.home.StudyFlowTvHomeAction
import com.mikeyphw.studyflow.tv.home.StudyFlowTvHomeCard
import com.mikeyphw.studyflow.tv.home.StudyFlowTvHomeCardId
import com.mikeyphw.studyflow.tv.home.StudyFlowTvHomeSurface
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvNavigationShell
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionAction
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionSegment
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionSegmentId
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionState
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionStatus
import com.mikeyphw.studyflow.tv.session.StudyFlowTvStudySessionExperience
import com.mikeyphw.studyflow.tv.today.StudyFlowTvTodayBlock
import com.mikeyphw.studyflow.tv.today.StudyFlowTvTodayBlockId
import com.mikeyphw.studyflow.tv.today.StudyFlowTvTodayWorkspace

class StudyFlowTvActivity : Activity() {
    private var selectedDestination: StudyFlowTvDestination = StudyFlowTvNavigationShell.defaultDestination()
    private var studySessionState: StudyFlowTvSessionState = StudyFlowTvStudySessionExperience.defaultState()
    private lateinit var contentTitle: TextView
    private lateinit var contentBody: TextView
    private lateinit var contentCards: LinearLayout
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
            if (command == StudyFlowTvRemoteCommand.PLAY_PAUSE && selectedDestination == StudyFlowTvDestination.STUDY_SESSION) {
                toggleStudySessionPlayback()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun createContentView(): LinearLayout {
        val navigationButtons = linkedMapOf<StudyFlowTvDestination, Button>()
        val primaryAction = tvActionButton(R.string.tv_app_primary_action).apply {
            setOnClickListener { selectDestination(StudyFlowTvHomeSurface.destinationForAction(StudyFlowTvHomeAction.CONTINUE_STUDYING)) }
        }
        val pairPhoneAction = tvActionButton(R.string.tv_app_pair_phone_action).apply {
            setOnClickListener { selectDestination(StudyFlowTvHomeSurface.destinationForAction(StudyFlowTvHomeAction.PAIR_PHONE)) }
        }
        val practiceAction = tvActionButton(R.string.tv_app_practice_action).apply {
            setOnClickListener { selectDestination(StudyFlowTvHomeSurface.destinationForAction(StudyFlowTvHomeAction.START_PRACTICE)) }
        }

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
        contentCards = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(0, dp(26), 0, 0)
        }

        val contentPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(48), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            addView(contentTitle)
            addView(contentBody)
            addView(contentCards)
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
        when (destination) {
            StudyFlowTvDestination.HOME -> renderHomeDestination()
            StudyFlowTvDestination.TODAY -> renderTodayDestination()
            StudyFlowTvDestination.STUDY_SESSION -> renderStudySessionDestination()
            else -> renderStaticDestination(destination)
        }
        navButtons.forEach { (itemDestination, button) ->
            button.isSelected = itemDestination == destination
        }
    }

    private fun renderStaticDestination(destination: StudyFlowTvDestination) {
        contentCards.removeAllViews()
        contentCards.visibility = View.GONE
        contentTitle.text = getString(destination.titleResId())
        contentBody.text = getString(destination.bodyResId())
    }

    private fun renderHomeDestination() {
        contentCards.visibility = View.VISIBLE
        contentCards.removeAllViews()
        contentTitle.text = getString(R.string.tv_content_title_home)
        contentBody.text = getString(R.string.tv_content_body_home)
        contentCards.addView(sectionLabel(R.string.tv_home_section_title))
        StudyFlowTvHomeSurface.cards.chunked(2).forEach { rowCards ->
            contentCards.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    rowCards.forEach { card -> addView(tvHomeCard(card)) }
                },
            )
        }
    }

    private fun renderTodayDestination() {
        val workspace = StudyFlowTvTodayWorkspace.workspaceForSnapshot(StudyFlowTvTodayWorkspace.defaultSnapshot())
        contentCards.visibility = View.VISIBLE
        contentCards.removeAllViews()
        contentTitle.text = getString(R.string.tv_content_title_today)
        contentBody.text = getString(
            R.string.tv_today_status_format,
            workspace.snapshot.examName,
            workspace.snapshot.dateLabel,
            workspace.snapshot.nextBlockMinutes,
            workspace.snapshot.dueReviewCount,
            workspace.snapshot.practiceQuestionCount,
            workspace.snapshot.weeklyProgressPercent,
        )
        contentCards.addView(sectionLabel(R.string.tv_today_section_title))
        workspace.blocks.chunked(2).forEach { rowBlocks ->
            contentCards.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    rowBlocks.forEach { block -> addView(tvTodayBlock(block)) }
                },
            )
        }
    }

    private fun renderStudySessionDestination() {
        contentCards.visibility = View.VISIBLE
        contentCards.removeAllViews()
        contentTitle.text = getString(R.string.tv_content_title_study_session)
        contentBody.text = getString(
            R.string.tv_session_status_format,
            studySessionState.title,
            studySessionState.subject,
            studySessionState.status.name.lowercase(),
            studySessionState.progressPercent,
            studySessionState.elapsedMinutes,
            studySessionState.targetMinutes,
        )
        contentCards.addView(sectionLabel(R.string.tv_session_section_title))
        studySessionState.segments.chunked(2).forEach { rowSegments ->
            contentCards.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    rowSegments.forEach { segment -> addView(tvSessionSegment(segment)) }
                },
            )
        }
        contentCards.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(12), 0, 0)
                StudyFlowTvStudySessionExperience.availableActions(studySessionState)
                    .forEach { action -> addView(tvSessionAction(action)) }
            },
        )
    }

    private fun toggleStudySessionPlayback() {
        studySessionState = StudyFlowTvStudySessionExperience.applyAction(
            studySessionState,
            when (studySessionState.status) {
                StudyFlowTvSessionStatus.RUNNING -> StudyFlowTvSessionAction.PAUSE
                StudyFlowTvSessionStatus.PAUSED -> StudyFlowTvSessionAction.RESUME
                StudyFlowTvSessionStatus.READY,
                StudyFlowTvSessionStatus.COMPLETED,
                -> StudyFlowTvSessionAction.START
            },
        )
        renderStudySessionDestination()
    }

    private fun tvHomeCard(card: StudyFlowTvHomeCard): Button = tvCardButton(
        title = getString(card.titleResId()),
        body = getString(card.bodyResId()),
        onClick = { selectDestination(card.destination) },
    )

    private fun tvTodayBlock(block: StudyFlowTvTodayBlock): Button = tvCardButton(
        title = getString(block.titleResId()),
        body = getString(block.bodyResId()),
        onClick = { selectDestination(StudyFlowTvTodayWorkspace.destinationForAction(block.action)) },
    )

    private fun tvSessionSegment(segment: StudyFlowTvSessionSegment): Button = tvCardButton(
        title = getString(segment.titleResId()),
        body = getString(R.string.tv_session_segment_body_format, segment.minutes),
        onClick = {
            studySessionState = StudyFlowTvStudySessionExperience.applyAction(
                studySessionState,
                StudyFlowTvSessionAction.NEXT_SEGMENT,
            )
            renderStudySessionDestination()
        },
    )

    private fun tvSessionAction(action: StudyFlowTvSessionAction): Button = tvActionButton(action.labelResId()).apply {
        setOnClickListener {
            if (action == StudyFlowTvSessionAction.RETURN_TODAY) {
                selectDestination(StudyFlowTvDestination.TODAY)
            } else {
                studySessionState = StudyFlowTvStudySessionExperience.applyAction(studySessionState, action)
                renderStudySessionDestination()
            }
        }
    }

    private fun tvCardButton(
        title: String,
        body: String,
        onClick: () -> Unit,
    ): Button = Button(this).apply {
        id = View.generateViewId()
        text = getString(R.string.tv_home_card_format, title, body)
        textSize = 17f
        isAllCaps = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(Color.WHITE)
        setPadding(dp(22), dp(16), dp(22), dp(16))
        applyStudyFlowTvDpadFocus()
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(14)
            bottomMargin = dp(14)
        }
    }

    private fun sectionLabel(labelResId: Int): TextView = TextView(this).apply {
        text = getString(labelResId)
        setTextColor(Color.rgb(226, 232, 240))
        textSize = 18f
        gravity = Gravity.START
        setPadding(0, 0, 0, dp(12))
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
        StudyFlowTvDestination.STUDY_SESSION -> R.string.tv_nav_study_session
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
        StudyFlowTvDestination.STUDY_SESSION -> R.string.tv_content_title_study_session
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
        StudyFlowTvDestination.STUDY_SESSION -> R.string.tv_content_body_study_session
        StudyFlowTvDestination.PRACTICE -> R.string.tv_content_body_practice
        StudyFlowTvDestination.REVIEWS -> R.string.tv_content_body_reviews
        StudyFlowTvDestination.MOCK_EXAM -> R.string.tv_content_body_mock_exam
        StudyFlowTvDestination.PROGRESS -> R.string.tv_content_body_progress
        StudyFlowTvDestination.SCHEDULE -> R.string.tv_content_body_schedule
        StudyFlowTvDestination.SETTINGS -> R.string.tv_content_body_settings
    }

    private fun StudyFlowTvHomeCard.titleResId(): Int = when (id) {
        StudyFlowTvHomeCardId.TODAY -> R.string.tv_home_card_today_title
        StudyFlowTvHomeCardId.PRACTICE -> R.string.tv_home_card_practice_title
        StudyFlowTvHomeCardId.REVIEWS -> R.string.tv_home_card_reviews_title
        StudyFlowTvHomeCardId.MOCK_EXAM -> R.string.tv_home_card_mock_exam_title
    }

    private fun StudyFlowTvHomeCard.bodyResId(): Int = when (id) {
        StudyFlowTvHomeCardId.TODAY -> R.string.tv_home_card_today_body
        StudyFlowTvHomeCardId.PRACTICE -> R.string.tv_home_card_practice_body
        StudyFlowTvHomeCardId.REVIEWS -> R.string.tv_home_card_reviews_body
        StudyFlowTvHomeCardId.MOCK_EXAM -> R.string.tv_home_card_mock_exam_body
    }

    private fun StudyFlowTvTodayBlock.titleResId(): Int = when (id) {
        StudyFlowTvTodayBlockId.NEXT_STUDY_BLOCK -> R.string.tv_today_block_next_title
        StudyFlowTvTodayBlockId.DUE_REVIEWS -> R.string.tv_today_block_reviews_title
        StudyFlowTvTodayBlockId.QUESTION_PRACTICE -> R.string.tv_today_block_practice_title
        StudyFlowTvTodayBlockId.MOCK_EXAM_PREP -> R.string.tv_today_block_mock_title
    }

    private fun StudyFlowTvTodayBlock.bodyResId(): Int = when (id) {
        StudyFlowTvTodayBlockId.NEXT_STUDY_BLOCK -> R.string.tv_today_block_next_body
        StudyFlowTvTodayBlockId.DUE_REVIEWS -> R.string.tv_today_block_reviews_body
        StudyFlowTvTodayBlockId.QUESTION_PRACTICE -> R.string.tv_today_block_practice_body
        StudyFlowTvTodayBlockId.MOCK_EXAM_PREP -> R.string.tv_today_block_mock_body
    }

    private fun StudyFlowTvSessionSegment.titleResId(): Int = when (id) {
        StudyFlowTvSessionSegmentId.WARM_UP -> R.string.tv_session_segment_warmup_title
        StudyFlowTvSessionSegmentId.FOCUS_BLOCK -> R.string.tv_session_segment_focus_title
        StudyFlowTvSessionSegmentId.CHECKPOINT -> R.string.tv_session_segment_checkpoint_title
        StudyFlowTvSessionSegmentId.SUMMARY -> R.string.tv_session_segment_summary_title
    }

    private fun StudyFlowTvSessionAction.labelResId(): Int = when (this) {
        StudyFlowTvSessionAction.START -> R.string.tv_session_action_start
        StudyFlowTvSessionAction.PAUSE -> R.string.tv_session_action_pause
        StudyFlowTvSessionAction.RESUME -> R.string.tv_session_action_resume
        StudyFlowTvSessionAction.NEXT_SEGMENT -> R.string.tv_session_action_next
        StudyFlowTvSessionAction.COMPLETE -> R.string.tv_session_action_complete
        StudyFlowTvSessionAction.RETURN_TODAY -> R.string.tv_session_action_return_today
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
