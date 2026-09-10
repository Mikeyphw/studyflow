package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DesktopPublicExamScreen(appState: DesktopAppState) {
    val state = appState.publicExamState()
    Column(
        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(desktopText("Concurso Público"))
        Text(desktopText("Shared edital, legislation, cycle and dashboard semantics with durable Desktop state."))
        OutlinedTextField(
            value = appState.publicExamOrganizationDraft,
            onValueChange = appState::updatePublicExamOrganization,
            label = { Text(desktopText("Organization")) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = appState.publicExamRoleDraft,
            onValueChange = appState::updatePublicExamRole,
            label = { Text(desktopText("Role / cargo")) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = appState.publicExamBoardDraft,
            onValueChange = appState::updatePublicExamBoard,
            label = { Text(desktopText("Exam board / banca")) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = appState.publicExamDateDraft,
            onValueChange = appState::updatePublicExamDate,
            label = { Text(desktopText("Confirmed exam date (YYYY-MM-DD)")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = appState::savePublicExamTarget) { Text(desktopText("Save public-exam target")) }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(desktopText("Edital revisions"))
                Text("Coverage: ${state.editalCoveragePercent}% · Revisions: ${state.revisions.size}")
                OutlinedTextField(
                    value = appState.publicExamEditalDraft,
                    onValueChange = appState::updatePublicExamEdital,
                    label = { Text(desktopText("Paste edital syllabus")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                )
                Button(onClick = appState::applyPublicExamEdital) {
                    Text(desktopText("Preview + apply edital revision"))
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(desktopText("Legislation"))
                Text("Documents: ${state.legislation?.documents?.size ?: 0} · " +
                    "Provisions: ${state.legislation?.provisions?.size ?: 0}")
                OutlinedTextField(
                    value = appState.publicExamLegislationDraft,
                    onValueChange = appState::updatePublicExamLegislation,
                    label = { Text(desktopText("Paste Lei Seca / legislation")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = appState::importPublicExamLegislation) { Text(desktopText("Import legislation")) }
                    Button(onClick = appState::togglePublicExamProvisionRead) { Text(desktopText("Toggle first read")) }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(desktopText("Study cycle"))
                Text("Items: ${state.cycle?.items?.size ?: 0} · " +
                    "Current: ${state.cycle?.cycle?.currentItemId?.value ?: "none"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = appState::buildPublicExamCycle) { Text(desktopText("Build from syllabus")) }
                    Button(onClick = appState::startPublicExamCycleItem) { Text(desktopText("Start current")) }
                    Button(onClick = appState::skipPublicExamCycleItem) { Text(desktopText("Skip")) }
                }
            }
        }

        state.dashboard?.let { dashboard ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(desktopText("Concurso dashboard"))
                    Text("Questions: ${dashboard.metrics.totalQuestions}")
                    Text("Overdue reviews: ${dashboard.metrics.overdueReviews}")
                    Text("Recurring errors: ${dashboard.metrics.recurringErrors}")
                    Text("Days until exam: ${dashboard.metrics.daysUntilExam ?: "unknown"}")
                    Text("Final stretch: ${state.finalStretch?.band?.name ?: "not scheduled"}")
                }
            }
        }
        if (appState.publicExamActionMessage.isNotBlank()) Text(appState.publicExamActionMessage)
    }
}
