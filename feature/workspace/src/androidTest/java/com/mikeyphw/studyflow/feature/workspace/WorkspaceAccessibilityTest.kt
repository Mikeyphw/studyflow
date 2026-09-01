package com.mikeyphw.studyflow.feature.workspace

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.LayoutDirection
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowTabStrip
import org.junit.Rule
import org.junit.Test

class WorkspaceAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun noPlanWorkspaceExposesPaneAndReachableHeaderActions() {
        val title = "Today"
        composeRule.setContent {
            MaterialTheme {
                WorkspaceNoPlanScreen(
                    title = title,
                    onCreatePlan = {},
                    onSwitchPlan = {},
                    onSearch = {},
                    onSystem = {},
                )
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title),
        ).assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true),
            useUnmergedTree = true,
        ).assertIsDisplayed()

        val search = composeRule.activity.getString(R.string.search)
        val more = composeRule.activity.getString(R.string.more)
        listOf(search, more).forEach { description ->
            composeRule.onNodeWithContentDescription(description)
                .assertHasClickAction()
                .assert(isFocusable())
                .assertHeightIsAtLeast(48.dp)
        }
    }


    @Test
    fun workspaceTabsExposeSelectedStateSemantics() {
        composeRule.setContent {
            MaterialTheme {
                StudyFlowTabStrip(
                    tabs = listOf("Today", "Plan"),
                    selected = "Plan",
                    label = { tab -> tab },
                    onSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Plan").assertIsSelected()
    }

    @Test
    fun compactPortraitRemainsOperableAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f) then
                    DeviceConfigurationOverride.ForcedSize(DpSize(390.dp, 844.dp)),
            ) {
                MaterialTheme {
                    WorkspaceNoPlanScreen(
                        title = "Today",
                        onCreatePlan = {},
                        onSwitchPlan = {},
                        onSearch = {},
                        onSystem = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.create_plan))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.search))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.more))
            .assertIsDisplayed()
    }

    @Test
    fun planPickerSearchQueryRestoresAfterSavedStateRecreation() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                PlanPickerScreen(
                    plans = emptyList(),
                    selectedPlanId = "",
                    activeSession = null,
                    onPlanSelected = {},
                    onCreatePlan = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("constitutional")
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNode(hasSetTextAction()).assertTextContains("constitutional")
    }

    @Test
    fun headerActionsRemainReachableInRightToLeftLayout() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.LayoutDirection(LayoutDirection.Rtl),
            ) {
                MaterialTheme {
                    WorkspaceNoPlanScreen(
                        title = "Today",
                        onCreatePlan = {},
                        onSwitchPlan = {},
                        onSearch = {},
                        onSystem = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.search))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.more))
            .assertIsDisplayed()
    }
}
