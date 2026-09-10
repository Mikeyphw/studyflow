package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DesktopLibraryScreen(appState: DesktopAppState) {
    val state = appState.libraryState()
    when (state.status) {
        DesktopScreenStatus.LOADING -> DesktopStateMessage(
            "Loading library",
            "Desktop library state is being prepared.",
        )
        DesktopScreenStatus.ERROR -> DesktopStateMessage(
            "Library unavailable",
            "The Desktop library could not be loaded.",
        )
        DesktopScreenStatus.EMPTY, DesktopScreenStatus.CONTENT -> DesktopLibraryContent(appState, state)
    }
}

@Composable
private fun DesktopLibraryContent(appState: DesktopAppState, state: DesktopLibraryScreenState) {
    val viewer = appState.libraryViewerState()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                "Materials",
                state.summary.activeMaterials.toString(),
                "${state.summary.favoriteMaterials} favorites",
                Modifier.weight(1f),
            )
            DesktopMetricCard("Videos", state.summary.videos.toString(), "Persistent catalog", Modifier.weight(1f))
            DesktopMetricCard("Annexes", state.summary.annexes.toString(), "Persistent metadata", Modifier.weight(1f))
        }

        DesktopSectionCard(title = desktopText("Add resource")) {
            Text(desktopText("Choose a local file or enter a path manually."))
            OutlinedTextField(
                value = appState.libraryPathDraft,
                onValueChange = appState::updateLibraryPath,
                label = { Text(desktopText("Local file path")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = appState::chooseLibraryImportFile) {
                    Text(desktopText("Choose file"))
                }
                Button(onClick = appState::importLibraryFile, enabled = appState.libraryPathDraft.isNotBlank()) {
                    Text(desktopText("Import file"))
                }
            }
            OutlinedTextField(
                value = appState.libraryWebTitleDraft,
                onValueChange = appState::updateLibraryWebTitle,
                label = { Text(desktopText("Web title")) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = appState.libraryWebUrlDraft,
                onValueChange = appState::updateLibraryWebUrl,
                label = { Text(desktopText("HTTP(S) URL")) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = appState::addLibraryWebResource) { Text(desktopText("Add web resource")) }
            if (appState.libraryActionMessage.isNotBlank()) Text(appState.libraryActionMessage)
        }

        if (state.status == DesktopScreenStatus.EMPTY) {
            DesktopStateMessage("No resources yet", "Import a local file or add a web reference.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopSectionCard(title = desktopText("Resource list"), modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.materials, key = { material -> material.id.value }) { material ->
                        val selected = material.id == appState.selectedMaterialId
                        Text(
                            if (selected) "Selected • ${material.title}" else material.title,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${material.typeLabel} • ${material.progressPercent}% complete")
                        OutlinedButton(onClick = { appState.selectMaterial(material.id) }) {
                            Text(desktopText("Show details"))
                        }
                    }
                }
            }
            DesktopSectionCard(
                title = desktopText("Resource detail · Material viewer"),
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier.height(360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(viewer.material?.title ?: "Select a material", fontWeight = FontWeight.Bold)
                    Text("Viewer: ${viewer.kind.name}")
                    Text(viewer.content)
                    if (viewer.detail.isNotBlank()) Text(viewer.detail)
                    if (viewer.material != null) {
                        OutlinedButton(onClick = appState::toggleSelectedMaterialFavorite) {
                            Text(desktopText("Toggle favorite"))
                        }
                    }
                    if (viewer.material != null && viewer.kind != DesktopMaterialViewerKind.INLINE) {
                        OutlinedButton(onClick = appState::openSelectedMaterialExternally) {
                            Text(
                                desktopText(
                                    if (viewer.kind == DesktopMaterialViewerKind.WEB) {
                                        "Open in browser"
                                    } else {
                                        "Open with system app"
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
        DesktopSectionCard(title = desktopText("Videos")) {
            if (state.videos.isEmpty()) Text(desktopText("No videos"))
            state.videos.forEach { video -> Text("• ${video.title} — ${video.progressLabel}") }
        }
    }
}
