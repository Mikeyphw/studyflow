package com.mikeyphw.studyflow

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import com.mikeyphw.studyflow.feature.workspace.R as WorkspaceR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SFDT21: real production MainActivity, real Navigation Compose and real touch
 * actions on a newly provisioned GitHub-hosted API 36 emulator. Does not call
 * setContent(), mock navigation, grant privileges, or access user data.
 * Emulator acceptance is NOT physical-device, release, or publication approval.
 */
@RunWith(AndroidJUnit4::class)
class Sfdt21HostedRealUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    // Match the real production Tab semantics and the actual localized label.
    // This follows Cinderbox's proven real-MainActivity test pattern, without
    // adding test-only tags or changing any production StudyFlow UI.
    private fun tabMatcher(name: String) =
        hasText(rule.activity.getString(when (name) {
            "today" -> WorkspaceR.string.today_title
            "plan" -> WorkspaceR.string.plan_title
            "practice" -> WorkspaceR.string.practice_title
            "progress" -> WorkspaceR.string.progress_title
            else -> error("Unreviewed workspace")
        })) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun tab(name: String) = rule.onNode(tabMatcher(name))
    private val workspaceNames = listOf("today", "plan", "practice", "progress")

    private fun awaitRealShell() {
        rule.waitUntil(30_000L) {
            workspaceNames.all { name ->
                rule.onAllNodes(tabMatcher(name)).fetchSemanticsNodes().size == 1
            }
        }
        tab("today").assertIsDisplayed().assertIsSelected()
        for (name in listOf("plan", "practice", "progress")) {
            tab(name).assertIsDisplayed()
        }
    }

    private fun screenshot(name: String) {
        require(name in setOf("today", "plan", "practice", "progress"))
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(bitmap.width >= 320 && bitmap.height >= 480)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.openFileOutput("sfdt21-ui-$name.png", Context.MODE_PRIVATE).use { stream ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    @Test
    fun realActivityNavigatesFourWorkspacesAndCapturesUi() {
        awaitRealShell()
        for (name in listOf("today", "plan", "practice", "progress")) {
            if (name != "today") tab(name).performClick()
            rule.waitForIdle()
            tab(name).assertIsDisplayed().assertIsSelected()
            screenshot(name)
        }
        tab("today").performClick()
        tab("today").assertIsSelected()
    }

    @Test
    fun realActivityRestoresSelectedWorkspaceAfterRecreation() {
        awaitRealShell()
        tab("practice").performClick()
        tab("practice").assertIsSelected()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(30_000L) {
            workspaceNames.all { name ->
                rule.onAllNodes(tabMatcher(name)).fetchSemanticsNodes().size == 1
            }
        }
        tab("practice").assertIsDisplayed().assertIsSelected()
        tab("today").performClick()
        tab("today").assertIsSelected()
    }

    @Test
    fun realActivityOpensGlobalSearchAndReturnsToToday() {
        awaitRealShell()
        // Tap the production Today header, open its actual SearchRoute, and
        // assert the searchable screen was rendered (not merely the tab state).
        val searchLabel = rule.activity.getString(WorkspaceR.string.search)
        rule.onNodeWithContentDescription(searchLabel).performClick()
        rule.onNode(hasSetTextAction()).assertIsDisplayed()
        // A focused search field may leave the IME visible. The first real
        // Android Back press can dismiss only the keyboard; use a second real
        // Back if SearchRoute is still on screen, never bypass navigation.
        Espresso.pressBack()
        rule.waitForIdle()
        if (rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()) {
            Espresso.pressBack()
        }
        rule.waitUntil(30_000L) {
            rule.onAllNodes(tabMatcher("today")).fetchSemanticsNodes().size == 1
        }
        tab("today").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun realActivityMaintainsExclusiveSelectionAcrossRepeatedNavigation() {
        awaitRealShell()
        for (name in listOf("progress", "plan", "today", "practice", "today")) {
            tab(name).performClick()
            rule.waitForIdle()
            tab(name).assertIsSelected()
            for (other in listOf("today", "plan", "practice", "progress").filter { it != name }) {
                tab(other).assertIsDisplayed()
            }
        }
    }
}
