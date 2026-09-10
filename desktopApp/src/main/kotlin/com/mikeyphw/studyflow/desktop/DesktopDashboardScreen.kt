package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DesktopDashboardScreen(
    state: DesktopDashboardState,
    onOpenLibrary: () -> Unit,
) {
    if (state.status == DesktopScreenStatus.EMPTY) {
        DesktopStateMessage(
            title = desktopText("No Study Plan yet"),
            message = "The desktop bootstrap is ready, but no in-memory Study Plan was provided.",
        )
        return
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        DesktopSectionCard(title = state.activePlanName) {
            Text(state.activePlanDescription)
            DesktopInlineStatus(state.status)
            Button(onClick = onOpenLibrary) {
                Text(desktopText("Open library"))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = desktopText("Plan progress"),
                value = "${state.progressPercent}%",
                detail = "Overall Study Plan completion",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = desktopText("This week"),
                value = "${state.studiedHoursThisWeek}h",
                detail = "Goal: ${state.weeklyGoalHours}h",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = desktopText("Accuracy"),
                value = "${state.averageAccuracyPercent}%",
                detail = "${state.questionsAnswered} answered questions",
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = desktopText("Library"),
                value = "${state.libraryProgressPercent}%",
                detail = "${state.materialCount} materials, ${state.videoCount} videos",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = desktopText("Favorites"),
                value = state.favoriteCount.toString(),
                detail = "Pinned resources for focused desktop work",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = desktopText("Shell readiness"),
                value = "${state.readinessPercent}%",
                detail = "Domain readiness audit from core:domain",
                modifier = Modifier.weight(1f),
            )
        }

        DesktopSectionCard(title = desktopText("Desktop bootstrap")) {
            Text("Domain managers wired:", fontWeight = FontWeight.Bold)
            state.domainManagers.forEach { manager -> Text("• $manager") }
            Text("Allowed shared modules:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            state.boundaryModules.forEach { module -> Text("• $module") }
        }
    }
}
