package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StudyFlowDesktopApp(
    shellState: DesktopShellState = remember { createDesktopShellState() },
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                DesktopHero(shellState)
                DesktopBoundaryCard(shellState)
            }
        }
    }
}

@Composable
private fun DesktopHero(shellState: DesktopShellState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = shellState.appName,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = shellState.status,
            fontSize = 20.sp,
        )
        Text(
            text = "Compose Multiplatform Desktop is wired without copying Android screen logic.",
        )
    }
}

@Composable
private fun DesktopBoundaryCard(shellState: DesktopShellState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Shared boundary",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = shellState.boundarySummary)
            Spacer(modifier = Modifier.height(4.dp))
            shellState.allowedSharedModules.forEach { modulePath ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "•")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = modulePath)
                }
            }
            Button(onClick = {}) {
                Text("Next: wire real StudyFlow state")
            }
        }
    }
}
