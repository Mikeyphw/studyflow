package com.mikeyphw.studyflow

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncState
import com.mikeyphw.studyflow.feature.settings.R as SettingsR
import com.mikeyphw.studyflow.feature.settings.SyncFoundationScreen
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test

class SyncRecoveryInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun failedSyncPrepareRetriesTheSameOperationInPlace() {
        val attempts = AtomicInteger(0)
        composeRule.setContent {
            MaterialTheme {
                SyncFoundationScreen(
                    state = StudySyncState.disabled(),
                    summary = StudySyncChangeSummary(
                        planCount = 1,
                        activePlanCount = 1,
                        latestUpdatedAtEpochMillis = 1L,
                        localChangeToken = "token",
                    ),
                    summaryFailed = false,
                    onRetrySummary = {},
                    onPrepare = {
                        if (attempts.incrementAndGet() == 1) error("injected sync failure")
                    },
                    onDisable = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.sync_prepare_checkpoint))
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.sync_operation_failed))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string.retry)).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) { attempts.get() == 2 }
        composeRule.onNodeWithText(composeRule.activity.getString(SettingsR.string
            .sync_operation_succeeded)).assertIsDisplayed()
    }
}
