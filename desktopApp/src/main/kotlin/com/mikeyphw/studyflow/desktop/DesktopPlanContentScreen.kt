package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DesktopPlanContentScreen(appState: DesktopAppState) {
    val state = appState.planStructureState()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DesktopSectionCard("Study Plans") {
            state.plans.forEach { plan ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("${plan.name} • ${plan.status.name}", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { appState.selectPlan(plan.id) }) { Text("Select") }
                }
            }
            OutlinedTextField(
                value = appState.planNameDraft,
                onValueChange = appState::updatePlanNameDraft,
                label = { Text("Plan name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = appState::createPlan) { Text("Create") }
                OutlinedButton(onClick = appState::renameSelectedPlan) { Text("Rename selected") }
                OutlinedButton(onClick = appState::archiveSelectedPlan) { Text("Archive") }
                OutlinedButton(onClick = appState::restoreSelectedPlan) { Text("Restore") }
            }
        }

        val selected = state.selectedPlan
        if (selected == null) {
            DesktopStateMessage("No Study Plan", "Create a Study Plan to start organizing Subjects and Topics.")
            return@Column
        }

        DesktopSectionCard("Subjects — ${selected.name}") {
            OutlinedTextField(
                value = appState.subjectNameDraft,
                onValueChange = appState::updateSubjectNameDraft,
                label = { Text("New subject") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = appState::createSubject) { Text("Add subject") }
            state.subjects.forEach { subject ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("${subject.name} • ${subject.status.name}", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { appState.archiveSubject(subject.id) }) { Text("Archive") }
                    OutlinedButton(onClick = { appState.deleteSubject(subject.id) }) { Text("Delete") }
                }
            }
        }

        DesktopSectionCard("Topics") {
            OutlinedTextField(
                value = appState.topicNameDraft,
                onValueChange = appState::updateTopicNameDraft,
                label = { Text("New root topic") },
                modifier = Modifier.fillMaxWidth(),
            )
            state.subjects.firstOrNull { !it.archived }?.let { subject ->
                Button(onClick = { appState.createTopic(subject.id) }) { Text("Add to ${subject.name}") }
            }
            state.topics.forEach { topic ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("${topic.name} • ${topic.status.name}", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { appState.archiveTopic(topic.id) }) { Text("Archive") }
                    OutlinedButton(onClick = { appState.deleteTopic(topic.id) }) { Text("Delete") }
                }
            }
        }
        if (appState.structureActionMessage.isNotBlank()) {
            DesktopStateMessage("Plan action", appState.structureActionMessage)
        }
    }
}
