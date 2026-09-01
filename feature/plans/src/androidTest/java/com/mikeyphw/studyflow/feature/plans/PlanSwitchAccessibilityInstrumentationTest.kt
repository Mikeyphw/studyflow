package com.mikeyphw.studyflow.feature.plans

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class PlanSwitchAccessibilityInstrumentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun preferenceSwitchExposesExactlyOneInteractiveSwitchTarget() {
        composeRule.setContent {
            MaterialTheme {
                PreferenceSwitch(
                    title = "Adaptive planning",
                    description = "Adaptive planning description",
                    checked = true,
                    onCheckedChange = {},
                )
            }
        }

        val clickableSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
            .and(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        composeRule.onAllNodes(clickableSwitch, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithText("Adaptive planning").assertHasClickAction()
    }
}
