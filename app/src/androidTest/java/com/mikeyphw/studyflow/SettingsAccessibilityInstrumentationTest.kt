package com.mikeyphw.studyflow

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.StudySyncState
import com.mikeyphw.studyflow.core.model.ThemeMode
import com.mikeyphw.studyflow.feature.settings.SettingsScreen
import com.mikeyphw.studyflow.feature.settings.R as SettingsR
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SettingsAccessibilityInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun radioAndSwitchRowsExposeOneInteractiveTargetPerControl() {
        setSettingsContent()

        val clickableRadio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            .and(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        val clickableSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
            .and(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))

        composeRule.onAllNodes(clickableRadio, useUnmergedTree = true)
            .assertCountEquals(AppLanguage.entries.size + ThemeMode.entries.size)
        composeRule.onAllNodes(clickableSwitch, useUnmergedTree = true)
            .assertCountEquals(1)

        val english = composeRule.activity.getString(SettingsR.string.language_english)
        composeRule.onNodeWithText(english)
            .assertHasClickAction()
            .assertIsSelected()
    }

    @Test
    fun settingsControlsRemainReachableAtTwoHundredPercentFontScaleOnCompactWidth() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f) then
                    DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 740.dp)),
            ) {
                MaterialTheme {
                    SettingsScreen(
                        preferences = preferences(),
                        hasDefaultPlan = true,
                        syncState = StudySyncState.disabled(),
                        onLanguageChanged = {},
                        onThemeModeChanged = {},
                        onDynamicColorChanged = {},
                        onOpenDefaultPlanBackup = {},
                        onOpenDefaultPlanReminders = {},
                        onOpenPrivacyCenter = {},
                        onOpenSyncCenter = {},
                        onOpenDebugWorkbench = {},
                    )
                }
            }
        }

        val english = composeRule.activity.getString(SettingsR.string.language_english)
        val dynamicColor = composeRule.activity.getString(SettingsR.string.dynamic_color)
        composeRule.onNodeWithText(english).assertIsDisplayed()
        composeRule.onNodeWithText(dynamicColor).assertIsDisplayed()
    }

    @Test
    fun failedSettingsWriteRetriesTheSameMutationAndCanRecover() {
        val attempts = AtomicInteger(0)
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    preferences = preferences(),
                    hasDefaultPlan = true,
                    syncState = StudySyncState.disabled(),
                    onLanguageChanged = {
                        if (attempts.incrementAndGet() == 1) error("injected settings failure")
                    },
                    onThemeModeChanged = {},
                    onDynamicColorChanged = {},
                    onOpenDefaultPlanBackup = {},
                    onOpenDefaultPlanReminders = {},
                    onOpenPrivacyCenter = {},
                    onOpenSyncCenter = {},
                    onOpenDebugWorkbench = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string
            .language_portuguese_brazil)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string
            .settings_operation_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.retry)).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) { attempts.get() == 2 }
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string
            .settings_operation_succeeded)).assertIsDisplayed()
    }

    private fun setSettingsContent() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    preferences = preferences(),
                    hasDefaultPlan = true,
                    syncState = StudySyncState.disabled(),
                    onLanguageChanged = {},
                    onThemeModeChanged = {},
                    onDynamicColorChanged = {},
                    onOpenDefaultPlanBackup = {},
                    onOpenDefaultPlanReminders = {},
                    onOpenPrivacyCenter = {},
                    onOpenSyncCenter = {},
                    onOpenDebugWorkbench = {},
                )
            }
        }
    }

    private fun preferences() = AppPreferences(
        language = AppLanguage.ENGLISH,
        dynamicColorEnabled = true,
    )
}
