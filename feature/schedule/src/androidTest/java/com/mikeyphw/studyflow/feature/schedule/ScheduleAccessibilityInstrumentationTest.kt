package com.mikeyphw.studyflow.feature.schedule

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class ScheduleAccessibilityInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun scheduleRuleBooleanRowsExposeOneInteractiveCheckboxTargetEach() {
        composeRule.setContent {
            MaterialTheme {
                ScheduleRuleDialog(onDismiss = {}, onSave = {})
            }
        }

        val clickableCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
            .and(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        composeRule.onAllNodes(clickableCheckbox, useUnmergedTree = true).assertCountEquals(2)
        val enabled = composeRule.activity.getString(R.string.enabled)
        composeRule.onNodeWithText(enabled).assertHasClickAction()
    }

    @Test
    fun scheduleRuleFormRemainsReachableAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f) then
                    DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 520.dp)),
            ) {
                MaterialTheme {
                    ScheduleRuleDialog(onDismiss = {}, onSave = {})
                }
            }
        }

        val enabled = composeRule.activity.getString(R.string.enabled)
        composeRule.onNodeWithText(enabled).performScrollTo().assertIsDisplayed()
    }
}
