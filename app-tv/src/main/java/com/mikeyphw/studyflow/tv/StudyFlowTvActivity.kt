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
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamAction
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamSection
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamState
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamStatus
import com.mikeyphw.studyflow.tv.mockexam.StudyFlowTvMockExamStudio
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvNavigationShell
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionAction
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionSegment
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionSegmentId
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionState
import com.mikeyphw.studyflow.tv.session.StudyFlowTvSessionStatus
import com.mikeyphw.studyflow.tv.session.StudyFlowTvStudySessionExperience
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvPracticeAction
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvPracticeChoice
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvQuestionPractice
import com.mikeyphw.studyflow.tv.practice.StudyFlowTvQuestionPracticeState
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressAction
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressAnalytics
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressMetric
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressMetricId
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressSubject
import com.mikeyphw.studyflow.tv.progress.StudyFlowTvProgressSubjectId
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewAction
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewId
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewItem
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewNotebook
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewState
import com.mikeyphw.studyflow.tv.reviews.StudyFlowTvReviewStatus
import com.mikeyphw.studyflow.tv.library.StudyFlowTvLibraryAction
import com.mikeyphw.studyflow.tv.library.StudyFlowTvLibraryReference
import com.mikeyphw.studyflow.tv.library.StudyFlowTvLibrarySnapshot
import com.mikeyphw.studyflow.tv.library.StudyFlowTvLibrarySurface
import com.mikeyphw.studyflow.tv.schedule.StudyFlowTvScheduleAction
import com.mikeyphw.studyflow.tv.schedule.StudyFlowTvScheduleItem
import com.mikeyphw.studyflow.tv.schedule.StudyFlowTvScheduleSnapshot
import com.mikeyphw.studyflow.tv.schedule.StudyFlowTvScheduleSurface
import com.mikeyphw.studyflow.tv.today.StudyFlowTvTodayBlock
import com.mikeyphw.studyflow.tv.today.StudyFlowTvTodayBlockId
import com.mikeyphw.studyflow.tv.today.StudyFlowTvTodayWorkspace

class StudyFlowTvActivity : Activity() {
    private var selectedDestination: StudyFlowTvDestination = StudyFlowTvNavigationShell.defaultDestination()
    private var studySessionState: StudyFlowTvSessionState = StudyFlowTvStudySessionExperience.defaultState()
    private var questionPracticeState: StudyFlowTvQuestionPracticeState = StudyFlowTvQuestionPractice.defaultState()
    private var mockExamState: StudyFlowTvMockExamState = StudyFlowTvMockExamStudio.defaultState()
    private var reviewState: StudyFlowTvReviewState = StudyFlowTvReviewNotebook.defaultState()
    private var scheduleSnapshot: StudyFlowTvScheduleSnapshot = StudyFlowTvScheduleSurface.defaultSnapshot()
    private var librarySnapshot: StudyFlowTvLibrarySnapshot = StudyFlowTvLibrarySurface.defaultSnapshot()
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
            if (command == StudyFlowTvRemoteCommand.PLAY_PAUSE && selectedDestination == StudyFlowTvDestination.MOCK_EXAM) {
                toggleMockExamPlayback()
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
            StudyFlowTvDestination.PRACTICE -> renderQuestionPracticeDestination()
            StudyFlowTvDestination.MOCK_EXAM -> renderMockExamDestination()
            StudyFlowTvDestination.REVIEWS -> renderReviewDestination()
            StudyFlowTvDestination.PROGRESS -> renderProgressDestination()
            StudyFlowTvDestination.SCHEDULE -> renderScheduleDestination()
            StudyFlowTvDestination.LIBRARY -> renderLibraryDestination()
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

    private fun renderQuestionPracticeDestination() {
        contentCards.visibility = View.VISIBLE
        contentCards.removeAllViews()
        val state = questionPracticeState
        val question = state.activeQuestion
        val answerStatus = when (StudyFlowTvQuestionPractice.isCurrentAnswerCorrect(state)) {
            true -> getString(R.string.tv_practice_answer_correct)
            false -> getString(R.string.tv_practice_answer_incorrect)
            null -> getString(R.string.tv_practice_answer_pending)
        }
        contentTitle.text = getString(R.string.tv_content_title_practice)
        contentBody.text = getString(
            R.string.tv_practice_status_format,
            state.subject,
            state.activeQuestionIndex + 1,
            state.questions.size,
            state.progressPercent,
            answerStatus,
        )
        contentCards.addView(sectionLabel(R.string.tv_practice_section_title))
        contentCards.addView(
            TextView(this).apply {
                text = question.prompt
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.START
                setPadding(0, 0, 0, dp(16))
            },
        )
        contentCards.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                question.choices.forEach { choice -> addView(tvPracticeChoice(choice)) }
            },
        )
        if (state.explanationVisible) {
            contentCards.addView(
                TextView(this).apply {
                    text = getString(R.string.tv_practice_explanation_format, question.explanation)
                    setTextColor(Color.rgb(226, 232, 240))
                    textSize = 18f
                    gravity = Gravity.START
                    setPadding(0, dp(8), 0, dp(8))
                },
            )
        }
        contentCards.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(12), 0, 0)
                StudyFlowTvQuestionPractice.availableActions(questionPracticeState)
                    .forEach { action -> addView(tvPracticeAction(action)) }
            },
        )
    }

    private fun renderMockExamDestination() {
        contentCards.visibility = View.VISIBLE
        contentCards.removeAllViews()
        val state = mockExamState
        contentTitle.text = getString(R.string.tv_content_title_mock_exam)
        contentBody.text = getString(
            R.string.tv_mock_status_format,
            state.status.name.lowercase(),
            state.activeSection.title,
            state.elapsedMinutes,
            state.totalMinutes,
            state.progressPercent,
            state.flaggedSectionIds.size,
        )
        contentCards.addView(sectionLabel(R.string.tv_mock_section_title))
        state.sections.chunked(2).forEach { rowSections ->
            contentCards.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    rowSections.forEach { section -> addView(tvMockExamSection(section)) }
                },
            )
        }
        if (state.status == StudyFlowTvMockExamStatus.RESULTS) {
            contentCards.addView(
                TextView(this).apply {
                    text = getString(R.string.tv_mock_result_format, StudyFlowTvMockExamStudio.resultSummary(state))
                    setTextColor(Color.rgb(226, 232, 240))
                    textSize = 18f
                    gravity = Gravity.START
                    setPadding(0, dp(8), 0, dp(8))
                },
            )
        }
        contentCards.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(12), 0, 0)
                StudyFlowTvMockExamStudio.availableActions(mockExamState)
                    .forEach { action -> addView(tvMockExamAction(action)) }
            },
        )
    }

    private fun renderReviewDestination() {
        contentCards.visibility = View.VISIBLE
        contentCards.removeAllViews()
        val state = reviewState
        contentTitle.text = getString(R.string.tv_content_title_reviews)
        contentBody.text = getString(
            R.string.tv_reviews_status_format,
            state.status.name.lowercase(),
            state.activeReview.subject,
            state.progressPercent,
            state.dueCount,
            state.errorNoteIds.size,
        )
        contentCards.addView(sectionLabel(R.string.tv_reviews_section_title))
        state.items.chunked(2).forEach { rowItems ->
            contentCards.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    rowItems.forEach { item -> addView(tvReviewItem(item)) }
                },
            )
        }
        if (state.explanationVisible || state.status == StudyFlowTvReviewStatus.ERROR_NOTEBOOK) {
            contentCards.addView(
                TextView(this).apply {
                    text = getString(
                        R.string.tv_reviews_note_format,
                        state.activeReview.reason,
                        state.activeReview.confidencePercent,
                    )
                    setTextColor(Color.rgb(226, 232, 240))
                    textSize = 18f
                    gravity = Gravity.START
                    setPadding(0, dp(8), 0, dp(8))
                },
            )
        }
        contentCards.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(12), 0, 0)
                StudyFlowTvReviewNotebook.availableActions(reviewState)
                    .forEach { action -> addView(tvReviewAction(action)) }
            },
        )
    }

    private fun renderProgressDestination() {
        val snapshot = StudyFlowTvProgressAnalytics.defaultSnapshot()
        contentCards.visibility = View.VISIBLE
        contentCards.removeAllViews()
        contentTitle.text = getString(R.string.tv_content_title_progress)
        contentBody.text = getString(
            R.string.tv_progress_status_format,
            snapshot.examName,
            snapshot.weekLabel,
            snapshot.weeklyProgressPercent,
            snapshot.averageReadinessPercent,
            snapshot.weakestSubject.title,
        )
        contentCards.addView(sectionLabel(R.string.tv_progress_section_title))
        snapshot.metrics.chunked(2).forEach { rowMetrics ->
            contentCards.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    rowMetrics.forEach { metric -> addView(tvProgressMetric(metric)) }
                },
            )
        }
        contentCards.addView(sectionLabel(R.string.tv_progress_weak_subjects_title))
        snapshot.subjects.chunked(2).forEach { rowSubjects ->
            contentCards.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    rowSubjects.forEach { subject -> addView(tvProgressSubject(subject)) }
                },
            )
        }
        contentCards.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(12), 0, 0)
                StudyFlowTvProgressAnalytics.actions
                    .forEach { action -> addView(tvProgressAction(action)) }
            },
        )
    }

    private fun renderScheduleDestination() {
        val snapshot = scheduleSnapshot
        contentCards.visibility = View.VISIBLE
        contentCards.removeAllViews()
        contentTitle.text = getString(R.string.tv_content_title_schedule)
        contentBody.text = getString(
            R.string.tv_schedule_status_format,
            snapshot.title,
            snapshot.weekLabel,
            snapshot.items.size,
            snapshot.activeItem.title,
        )
        contentCards.addView(sectionLabel(R.string.tv_schedule_section_title))
        snapshot.items.chunked(2).forEach { rowItems ->
            contentCards.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    rowItems.forEach { item -> addView(tvScheduleItem(item)) }
                },
            )
        }
        contentCards.addView(
            TextView(this).apply {
                text = getString(
                    R.string.tv_schedule_read_only_note_format,
                    snapshot.reviewWindowCount,
                    snapshot.mockExamCount,
                )
                setTextColor(Color.rgb(226, 232, 240))
                textSize = 18f
                gravity = Gravity.START
                setPadding(0, dp(8), 0, dp(8))
            },
        )
        contentCards.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(12), 0, 0)
                StudyFlowTvScheduleSurface.actions.forEach { action -> addView(tvScheduleAction(action)) }
            },
        )
    }

    private fun renderLibraryDestination() {
        val snapshot = librarySnapshot
        contentCards.visibility = View.VISIBLE
        contentCards.removeAllViews()
        contentTitle.text = getString(R.string.tv_content_title_library)
        contentBody.text = getString(
            R.string.tv_library_status_format,
            snapshot.title,
            snapshot.collectionLabel,
            snapshot.references.size,
            snapshot.activeReference.title,
        )
        contentCards.addView(sectionLabel(R.string.tv_library_section_title))
        snapshot.references.chunked(2).forEach { rowReferences ->
            contentCards.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    rowReferences.forEach { reference -> addView(tvLibraryReference(reference)) }
                },
            )
        }
        contentCards.addView(
            TextView(this).apply {
                text = getString(
                    R.string.tv_library_active_excerpt_format,
                    snapshot.activeReference.subject,
                    snapshot.activeReference.excerpt,
                )
                setTextColor(Color.rgb(226, 232, 240))
                textSize = 18f
                gravity = Gravity.START
                setPadding(0, dp(8), 0, dp(8))
            },
        )
        contentCards.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(12), 0, 0)
                StudyFlowTvLibrarySurface.actions.forEach { action -> addView(tvLibraryAction(action)) }
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


    private fun toggleMockExamPlayback() {
        mockExamState = StudyFlowTvMockExamStudio.applyAction(
            mockExamState,
            when (mockExamState.status) {
                StudyFlowTvMockExamStatus.RUNNING -> StudyFlowTvMockExamAction.PAUSE
                StudyFlowTvMockExamStatus.PAUSED -> StudyFlowTvMockExamAction.RESUME
                StudyFlowTvMockExamStatus.READY -> StudyFlowTvMockExamAction.START
                StudyFlowTvMockExamStatus.SUBMITTED -> StudyFlowTvMockExamAction.REVIEW_RESULTS
                StudyFlowTvMockExamStatus.RESULTS -> StudyFlowTvMockExamAction.RETURN_HOME
            },
        )
        renderMockExamDestination()
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

    private fun tvPracticeChoice(choice: StudyFlowTvPracticeChoice): Button = tvCardButton(
        title = getString(R.string.tv_practice_choice_format, choice.id.optionId),
        body = choice.label,
        onClick = {
            questionPracticeState = StudyFlowTvQuestionPractice.applyAction(
                questionPracticeState,
                when (choice.id.optionId) {
                    "A" -> StudyFlowTvPracticeAction.SELECT_A
                    "B" -> StudyFlowTvPracticeAction.SELECT_B
                    "C" -> StudyFlowTvPracticeAction.SELECT_C
                    else -> StudyFlowTvPracticeAction.SELECT_D
                },
            )
            renderQuestionPracticeDestination()
        },
    )

    private fun tvPracticeAction(action: StudyFlowTvPracticeAction): Button = tvActionButton(action.labelResId()).apply {
        setOnClickListener {
            questionPracticeState = StudyFlowTvQuestionPractice.applyAction(questionPracticeState, action)
            renderQuestionPracticeDestination()
        }
    }

    private fun tvMockExamSection(section: StudyFlowTvMockExamSection): Button = tvCardButton(
        title = section.title,
        body = getString(R.string.tv_mock_section_body_format, section.questionCount, section.minutes),
        onClick = {
            mockExamState = StudyFlowTvMockExamStudio.applyAction(mockExamState, StudyFlowTvMockExamAction.NEXT_SECTION)
            renderMockExamDestination()
        },
    )

    private fun tvMockExamAction(action: StudyFlowTvMockExamAction): Button = tvActionButton(action.labelResId()).apply {
        setOnClickListener {
            if (action == StudyFlowTvMockExamAction.RETURN_HOME) {
                selectDestination(StudyFlowTvDestination.HOME)
            } else {
                mockExamState = StudyFlowTvMockExamStudio.applyAction(mockExamState, action)
                renderMockExamDestination()
            }
        }
    }

    private fun tvReviewItem(item: StudyFlowTvReviewItem): Button = tvCardButton(
        title = getString(item.titleResId()),
        body = getString(
            R.string.tv_reviews_card_body_format,
            item.subject,
            item.confidencePercent,
            item.reason,
        ),
        onClick = {
            reviewState = reviewState.copy(
                status = StudyFlowTvReviewStatus.REVIEWING,
                activeReviewIndex = reviewState.items.indexOfFirst { it.id == item.id }.coerceAtLeast(0),
                explanationVisible = false,
            )
            renderReviewDestination()
        },
    )

    private fun tvReviewAction(action: StudyFlowTvReviewAction): Button = tvActionButton(action.labelResId()).apply {
        setOnClickListener {
            if (action == StudyFlowTvReviewAction.RETURN_TODAY) {
                selectDestination(StudyFlowTvDestination.TODAY)
            } else {
                reviewState = StudyFlowTvReviewNotebook.applyAction(reviewState, action)
                renderReviewDestination()
            }
        }
    }

    private fun tvProgressMetric(metric: StudyFlowTvProgressMetric): Button = tvCardButton(
        title = getString(metric.titleResId()),
        body = getString(
            R.string.tv_progress_metric_body_format,
            metric.valueLabel,
            metric.trendLabel,
            metric.percent,
        ),
        onClick = { selectDestination(StudyFlowTvDestination.PROGRESS) },
    )

    private fun tvProgressSubject(subject: StudyFlowTvProgressSubject): Button = tvCardButton(
        title = getString(subject.titleResId()),
        body = getString(
            R.string.tv_progress_subject_body_format,
            subject.readinessPercent,
            subject.weakArea,
        ),
        onClick = { selectDestination(subject.recommendedDestination) },
    )

    private fun tvProgressAction(action: StudyFlowTvProgressAction): Button = tvActionButton(action.labelResId()).apply {
        setOnClickListener {
            selectDestination(StudyFlowTvProgressAnalytics.destinationForAction(action))
        }
    }

    private fun tvScheduleItem(item: StudyFlowTvScheduleItem): Button = tvCardButton(
        title = item.title,
        body = getString(R.string.tv_schedule_item_body_format, item.timeLabel, item.kind.name.lowercase()),
        onClick = {
            scheduleSnapshot = scheduleSnapshot.copy(
                activeItemIndex = scheduleSnapshot.items.indexOfFirst { it.id == item.id }.coerceAtLeast(0),
            )
            selectDestination(item.destination)
        },
    )

    private fun tvScheduleAction(action: StudyFlowTvScheduleAction): Button = tvActionButton(action.labelResId()).apply {
        setOnClickListener {
            scheduleSnapshot = StudyFlowTvScheduleSurface.applyAction(scheduleSnapshot, action)
            val destination = StudyFlowTvScheduleSurface.destinationForAction(action, scheduleSnapshot)
            if (destination == StudyFlowTvDestination.SCHEDULE) {
                renderScheduleDestination()
            } else {
                selectDestination(destination)
            }
        }
    }

    private fun tvLibraryReference(reference: StudyFlowTvLibraryReference): Button = tvCardButton(
        title = reference.title,
        body = getString(R.string.tv_library_reference_body_format, reference.subject, reference.sourceLabel),
        onClick = {
            librarySnapshot = librarySnapshot.copy(
                activeReferenceIndex = librarySnapshot.references.indexOfFirst { it.id == reference.id }.coerceAtLeast(0),
            )
            renderLibraryDestination()
        },
    )

    private fun tvLibraryAction(action: StudyFlowTvLibraryAction): Button = tvActionButton(action.labelResId()).apply {
        setOnClickListener {
            librarySnapshot = StudyFlowTvLibrarySurface.applyAction(librarySnapshot, action)
            val destination = StudyFlowTvLibrarySurface.destinationForAction(action, librarySnapshot)
            if (destination == StudyFlowTvDestination.LIBRARY) {
                renderLibraryDestination()
            } else {
                selectDestination(destination)
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
        StudyFlowTvDestination.LIBRARY -> R.string.tv_nav_library
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
        StudyFlowTvDestination.LIBRARY -> R.string.tv_content_title_library
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
        StudyFlowTvDestination.LIBRARY -> R.string.tv_content_body_library
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


    private fun StudyFlowTvPracticeAction.labelResId(): Int = when (this) {
        StudyFlowTvPracticeAction.SELECT_A -> R.string.tv_practice_action_select_a
        StudyFlowTvPracticeAction.SELECT_B -> R.string.tv_practice_action_select_b
        StudyFlowTvPracticeAction.SELECT_C -> R.string.tv_practice_action_select_c
        StudyFlowTvPracticeAction.SELECT_D -> R.string.tv_practice_action_select_d
        StudyFlowTvPracticeAction.PREVIOUS_QUESTION -> R.string.tv_practice_action_previous
        StudyFlowTvPracticeAction.NEXT_QUESTION -> R.string.tv_practice_action_next
        StudyFlowTvPracticeAction.SHOW_EXPLANATION -> R.string.tv_practice_action_explanation
        StudyFlowTvPracticeAction.FINISH_SET -> R.string.tv_practice_action_finish
    }

    private fun StudyFlowTvMockExamAction.labelResId(): Int = when (this) {
        StudyFlowTvMockExamAction.START -> R.string.tv_mock_action_start
        StudyFlowTvMockExamAction.PAUSE -> R.string.tv_mock_action_pause
        StudyFlowTvMockExamAction.RESUME -> R.string.tv_mock_action_resume
        StudyFlowTvMockExamAction.NEXT_SECTION -> R.string.tv_mock_action_next_section
        StudyFlowTvMockExamAction.MARK_FOR_REVIEW -> R.string.tv_mock_action_mark_review
        StudyFlowTvMockExamAction.SUBMIT -> R.string.tv_mock_action_submit
        StudyFlowTvMockExamAction.REVIEW_RESULTS -> R.string.tv_mock_action_results
        StudyFlowTvMockExamAction.RETURN_HOME -> R.string.tv_mock_action_home
    }

    private fun StudyFlowTvReviewItem.titleResId(): Int = when (id) {
        StudyFlowTvReviewId.CONSTITUTION_FLASHCARDS -> R.string.tv_reviews_item_constitution_title
        StudyFlowTvReviewId.ADMINISTRATIVE_LAW_ERRORS -> R.string.tv_reviews_item_admin_title
        StudyFlowTvReviewId.PORTUGUESE_GRAMMAR -> R.string.tv_reviews_item_portuguese_title
        StudyFlowTvReviewId.INFORMATICS_SHORT_NOTES -> R.string.tv_reviews_item_informatics_title
    }

    private fun StudyFlowTvReviewAction.labelResId(): Int = when (this) {
        StudyFlowTvReviewAction.START_REVIEW -> R.string.tv_reviews_action_start
        StudyFlowTvReviewAction.NEXT_ITEM -> R.string.tv_reviews_action_next
        StudyFlowTvReviewAction.MARK_KNOWN -> R.string.tv_reviews_action_known
        StudyFlowTvReviewAction.ADD_ERROR_NOTE -> R.string.tv_reviews_action_error_note
        StudyFlowTvReviewAction.SHOW_EXPLANATION -> R.string.tv_reviews_action_explain
        StudyFlowTvReviewAction.COMPLETE_REVIEW -> R.string.tv_reviews_action_complete
        StudyFlowTvReviewAction.RETURN_TODAY -> R.string.tv_reviews_action_today
    }

    private fun StudyFlowTvProgressMetric.titleResId(): Int = when (id) {
        StudyFlowTvProgressMetricId.WEEKLY_PROGRESS -> R.string.tv_progress_metric_weekly_title
        StudyFlowTvProgressMetricId.ACCURACY -> R.string.tv_progress_metric_accuracy_title
        StudyFlowTvProgressMetricId.QUESTION_VOLUME -> R.string.tv_progress_metric_volume_title
        StudyFlowTvProgressMetricId.READINESS -> R.string.tv_progress_metric_readiness_title
    }

    private fun StudyFlowTvProgressSubject.titleResId(): Int = when (id) {
        StudyFlowTvProgressSubjectId.CONSTITUTIONAL_LAW -> R.string.tv_progress_subject_constitutional_title
        StudyFlowTvProgressSubjectId.ADMINISTRATIVE_LAW -> R.string.tv_progress_subject_admin_title
        StudyFlowTvProgressSubjectId.PORTUGUESE -> R.string.tv_progress_subject_portuguese_title
        StudyFlowTvProgressSubjectId.INFORMATICS -> R.string.tv_progress_subject_informatics_title
    }

    private fun StudyFlowTvProgressAction.labelResId(): Int = when (this) {
        StudyFlowTvProgressAction.OPEN_TODAY -> R.string.tv_progress_action_today
        StudyFlowTvProgressAction.OPEN_PRACTICE -> R.string.tv_progress_action_practice
        StudyFlowTvProgressAction.OPEN_REVIEWS -> R.string.tv_progress_action_reviews
        StudyFlowTvProgressAction.OPEN_MOCK_EXAM -> R.string.tv_progress_action_mock
    }

    private fun StudyFlowTvScheduleAction.labelResId(): Int = when (this) {
        StudyFlowTvScheduleAction.OPEN_ACTIVE_ITEM -> R.string.tv_schedule_action_open_active
        StudyFlowTvScheduleAction.PREVIOUS_ITEM -> R.string.tv_schedule_action_previous
        StudyFlowTvScheduleAction.NEXT_ITEM -> R.string.tv_schedule_action_next
        StudyFlowTvScheduleAction.OPEN_TODAY -> R.string.tv_schedule_action_today
        StudyFlowTvScheduleAction.OPEN_MOCK_EXAM -> R.string.tv_schedule_action_mock
    }

    private fun StudyFlowTvLibraryAction.labelResId(): Int = when (this) {
        StudyFlowTvLibraryAction.OPEN_REFERENCE -> R.string.tv_library_action_open
        StudyFlowTvLibraryAction.PREVIOUS_REFERENCE -> R.string.tv_library_action_previous
        StudyFlowTvLibraryAction.NEXT_REFERENCE -> R.string.tv_library_action_next
        StudyFlowTvLibraryAction.START_RELATED_PRACTICE -> R.string.tv_library_action_practice
        StudyFlowTvLibraryAction.RETURN_HOME -> R.string.tv_library_action_home
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
