package com.mikeyphw.studyflow

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.mikeyphw.studyflow.feature.workspace.R as WorkspaceR
import com.mikeyphw.studyflow.feature.plans.R as PlansR
import com.mikeyphw.studyflow.feature.subjects.R as SubjectsR
import com.mikeyphw.studyflow.feature.sessions.R as SessionsR
import com.mikeyphw.studyflow.feature.practice.R as PracticeR
import com.mikeyphw.studyflow.feature.reviews.R as ReviewsR

/**
 * SFDT22: real, production MainActivity UI acceptance. Every mutation below is
 * performed through production Compose controls; no injected repositories,
 * deep-link shortcuts, mock navigation, setContent or seeded domain fixtures.
 *
 * Methods are intentionally NOT order-independent: the hosted orchestrator
 * executes each method independently in the declared sequence, then force-stops
 * the app and executes the persisted-state method in a new application process.
 * The emulator MUST start without any StudyFlow installation or account data.
 */
@RunWith(AndroidJUnit4::class)
class Sfdt22HostedStudyJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val planName = "SFDT22 Study Journey"
    private val subjectName = "SFDT22 Ecology"
    private val topicName = "SFDT22 Ecosystems"
    private val sessionName = "SFDT22 Focus Session"
    private val questionStatement = "SFDT22: ecosystems include living organisms?"
    private val flashcardFront = "SFDT22 review: what is ecology?"
    private val flashcardBack = "Relationships between organisms and their environment"

    private fun text(resId: Int): String = rule.activity.getString(resId)

    private fun tab(name: String) = rule.onNode(
        hasText(text(when (name) {
            "today" -> WorkspaceR.string.today_title
            "plan" -> WorkspaceR.string.plan_title
            "practice" -> WorkspaceR.string.practice_title
            "progress" -> WorkspaceR.string.progress_title
            else -> error("Unknown SFDT22 root")
        })) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
    )

    private fun innerTab(resId: Int) = rule.onNode(
        hasText(text(resId)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
    )

    private fun shell() {
        rule.waitUntil(30_000L) {
            listOf("today", "plan", "practice", "progress").all { name ->
                runCatching { tab(name).fetchSemanticsNode() }.isSuccess
            }
        }
    }

    private fun openRoot(name: String) {
        shell()
        tab(name).performClick()
        rule.waitForIdle()
        tab(name).assertIsDisplayed().assertIsSelected()
    }

    private fun seek(matcher: SemanticsMatcher) {
        // LazyColumn items outside the viewport are not composed. Move the
        // actual production scroll container instead of accepting missing UI.
        if (rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            val scrollables = rule.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes()
            if (scrollables.isNotEmpty()) {
                runCatching { rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(matcher) }
            }
        }
        rule.waitUntil(20_000L) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    /**
     * The empty-workspace button renders a Material3 Button wrapping Text.
     * A composite (hasText AND hasClickAction) matcher can miss this actual
     * user-visible action when merged semantics vary by Compose version.
     * Locate the visible text node in the unmerged tree, then send a genuine
     * screen-coordinate touch through the real production Button.
     * No test tags, injected routes, or direct repository calls are used.
     */
    private fun tapCreatePlan() {
        val label = text(WorkspaceR.string.create_plan)
        val labels = rule.onAllNodes(hasText(label), useUnmergedTree = true)
        rule.waitUntil(25_000L) { labels.fetchSemanticsNodes().size == 1 }
        labels.onFirst().assertIsDisplayed().performTouchInput { click() }
        rule.waitForIdle()
    }

    private fun click(resId: Int) = clickLabel(text(resId))

    private fun clickLabel(label: String) {
        // The matching node must expose a real production click action.
        val matcher = hasText(label) and hasClickAction()
        seek(matcher)
        val node = rule.onAllNodes(matcher).onFirst()
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun clickDialog(resId: Int) {
        // Popups can leave a same-labelled button rendered behind the modal.
        // Select the final visible matching action from the active dialog.
        val matcher = hasText(text(resId)) and hasClickAction()
        seek(matcher)
        rule.onAllNodes(matcher).onLast().performClick()
        rule.waitForIdle()
    }

    private fun fill(resId: Int, value: String) {
        // Material3 labels may be separate semantics nodes from editable text.
        // In the first, real plan wizard step the plan name is the FIRST of two
        // fields (name, description). Target its actual SetText action, not the
        // implementation-dependent label merge. Do not alter production UI.
        if (resId == PlansR.string.plan_name) {
            val fields = rule.onAllNodes(hasSetTextAction())
            rule.waitUntil(20_000L) { fields.fetchSemanticsNodes().size == 2 }
            fields.onFirst().performTextReplacement(value)
        } else {
            val label = text(resId)
            val matcher = hasSetTextAction() and hasText(label, substring = true)
            seek(matcher)
            rule.onNode(matcher).performTextReplacement(value)
        }
        rule.waitForIdle()
    }

    private fun stage(name: String, action: () -> Unit) {
        // A stable allowlisted stage marker travels inside AndroidJUnitRunner's
        // failure stack. Only the marker and a generic reason code survive on CI.
        try {
            action()
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_STEP_" + name, failure)
        }
    }

    private fun visible(value: String) {
        val matcher = hasText(value, substring = true)
        seek(matcher)
        rule.onAllNodes(matcher).onFirst().assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        require(name in setOf("plan", "subject", "topic", "session", "question", "review", "restart"))
        rule.waitForIdle()
        val image = rule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(image.width >= 320 && image.height >= 480)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.openFileOutput("sfdt22-ui-$name.png", Context.MODE_PRIVATE).use {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun realUserCreatesAStudyPlan() {
        // Real UI, fresh emulator and production plan persistence. The stage
        // wrappers do not catch/suppress failures; they rethrow for a red CI.
        // Separate readiness, actual touch, and editor load in the receipt:
        // a time-out now identifies exactly which on-screen step failed.
        stage("plan_shell") { shell() }
        stage("plan_open") { tapCreatePlan() }
        stage("plan_editor_ready") {
            rule.waitUntil(25_000L) {
                // The production objective step has name and description.
                rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 2
            }
        }
        stage("plan_name") { fill(PlansR.string.plan_name, planName) }
        stage("plan_target") { click(PlansR.string.next) }
        stage("plan_preferences") { click(PlansR.string.next) } // Default 20h, 45min.
        stage("plan_save") { click(PlansR.string.save_plan) }
        stage("plan_confirm") { shell(); visible(planName) }
        stage("plan_capture") { screenshot("plan") }
    }

    @Test fun realUserAddsSubjectAndTopic() {
        openRoot("plan")
        innerTab(WorkspaceR.string.plan_content).performClick()
        click(WorkspaceR.string.manage_content)
        rule.onNodeWithContentDescription(text(SubjectsR.string.add_subject)).performClick()
        fill(SubjectsR.string.name, subjectName)
        click(SubjectsR.string.save)
        visible(subjectName)
        screenshot("subject")
        click(SubjectsR.string.add_topic)
        fill(SubjectsR.string.name, topicName)
        click(SubjectsR.string.save)
        // The syllabus subject starts collapsed; expand through the real UI.
        click(SubjectsR.string.expand)
        visible(topicName)
        screenshot("topic")
    }

    @Test fun realUserStartsPausesResumesAndCompletesASession() {
        openRoot("plan")
        innerTab(WorkspaceR.string.plan_overview).performClick()
        click(WorkspaceR.string.study_sessions)
        click(SessionsR.string.quick_session)
        fill(SessionsR.string.session_title, sessionName)
        clickDialog(SessionsR.string.start_session)
        // Actual active-session controls prove that the persisted session started.
        click(SessionsR.string.pause_session)
        click(SessionsR.string.resume_session)
        click(SessionsR.string.finish_session)
        // The history card displays notes, not the quick-session title: persist
        // our unique marker in the actual production completion dialog.
        fill(SessionsR.string.session_notes, sessionName)
        // All numeric fields are initialized to zero; completion is a real mutation.
        clickDialog(SessionsR.string.finish_session)
        visible(sessionName)
        screenshot("session")
    }

    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {
        openRoot("practice")
        innerTab(WorkspaceR.string.practice_questions).performClick()
        click(WorkspaceR.string.open_question_bank)
        rule.onNodeWithContentDescription(text(PracticeR.string.add_question)).performClick()
        clickLabel("true false") // Production QuestionType.TRUE_FALSE option.
        fill(PracticeR.string.statement, questionStatement)
        click(PracticeR.string.save_question)
        visible(questionStatement)
        click(PracticeR.string.create_practice_test)
        click(PracticeR.string.start_or_resume)
        clickLabel("True")
        // Wait for the real async answer write; merely tapping is not enough.
        visible(text(PracticeR.string.answer_saved))
        click(PracticeR.string.finish_attempt)
        visible(text(PracticeR.string.attempt_finished))
        screenshot("question")
        rule.onNodeWithContentDescription(text(PracticeR.string.go_back)).performClick()
        innerTab(WorkspaceR.string.practice_reviews).performClick()
        click(WorkspaceR.string.open_review_center)
        fill(ReviewsR.string.flashcard_front, flashcardFront)
        fill(ReviewsR.string.flashcard_back, flashcardBack)
        click(ReviewsR.string.create_flashcard)
        visible(flashcardFront)
        screenshot("review")
    }

    @Test fun realUserSeesPersistedProgressAfterColdRestart() {
        // This method is run ONLY after ADB force-stop and a new MainActivity PID.
        shell()
        visible(planName)
        openRoot("plan")
        innerTab(WorkspaceR.string.plan_content).performClick()
        click(WorkspaceR.string.manage_content)
        visible(subjectName)
        click(SubjectsR.string.expand)
        visible(topicName)
        rule.onNodeWithContentDescription(text(SubjectsR.string.go_back)).performClick()
        innerTab(WorkspaceR.string.plan_overview).performClick()
        click(WorkspaceR.string.study_sessions)
        visible(sessionName)
        rule.onNodeWithContentDescription(text(SessionsR.string.go_back)).performClick()
        openRoot("practice")
        innerTab(WorkspaceR.string.practice_questions).performClick()
        visible(questionStatement)
        innerTab(WorkspaceR.string.practice_reviews).performClick()
        click(WorkspaceR.string.open_review_center)
        visible(flashcardFront)
        screenshot("restart")
    }
}
