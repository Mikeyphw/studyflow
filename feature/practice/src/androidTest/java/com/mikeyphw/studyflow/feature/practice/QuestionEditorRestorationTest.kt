package com.mikeyphw.studyflow.feature.practice

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class QuestionEditorRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun questionEditorFieldsRemainReachableAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f) then
                    DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 520.dp)),
            ) {
                MaterialTheme {
                    QuestionEditorDialog(onDismiss = {}, onSave = {})
                }
            }
        }

        val tags = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.tags_hint)
        composeRule.onNodeWithText(tags).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun questionDraftRestoresAfterSavedStateRecreation() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                QuestionEditorDialog(onDismiss = {}, onSave = {})
            }
        }

        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("Restored question draft")
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onAllNodes(hasSetTextAction())[0].assertTextContains("Restored question draft")
    }
}
