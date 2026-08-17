package com.mikeyphw.studyflow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.model.StudyMaterialId

@Composable
fun DesktopLibraryScreen(
    state: DesktopLibraryScreenState,
    selectedMaterialId: StudyMaterialId?,
    onMaterialSelected: (StudyMaterialId) -> Unit,
) {
    when (state.status) {
        DesktopScreenStatus.LOADING -> DesktopStateMessage(
            title = "Loading library",
            message = "Desktop library state is being prepared.",
        )

        DesktopScreenStatus.ERROR -> DesktopStateMessage(
            title = "Library unavailable",
            message = "The desktop bootstrap could not build a library snapshot.",
        )

        DesktopScreenStatus.EMPTY -> DesktopStateMessage(
            title = "No resources yet",
            message = "Add materials after persistent desktop storage is wired.",
        )

        DesktopScreenStatus.CONTENT -> DesktopLibraryContent(
            state = state,
            selectedMaterialId = selectedMaterialId,
            onMaterialSelected = onMaterialSelected,
        )
    }
}

@Composable
private fun DesktopLibraryContent(
    state: DesktopLibraryScreenState,
    selectedMaterialId: StudyMaterialId?,
    onMaterialSelected: (StudyMaterialId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = "Materials",
                value = state.summary.activeMaterials.toString(),
                detail = "${state.summary.favoriteMaterials} favorites",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Videos",
                value = state.summary.videos.toString(),
                detail = "Desktop video launcher deferred",
                modifier = Modifier.weight(1f),
            )
            DesktopMetricCard(
                label = "Annexes",
                value = state.summary.annexes.toString(),
                detail = "Filesystem adapters deferred to DOV08",
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DesktopMaterialList(
                materials = state.materials,
                selectedMaterialId = selectedMaterialId,
                onMaterialSelected = onMaterialSelected,
                modifier = Modifier.weight(1f),
            )
            DesktopMaterialDetail(
                material = state.materials.firstOrNull { material -> material.id == selectedMaterialId }
                    ?: state.materials.firstOrNull(),
                modifier = Modifier.weight(1f),
            )
        }

        DesktopVideoList(videos = state.videos)
    }
}

@Composable
private fun DesktopMaterialList(
    materials: List<DesktopLibraryMaterialRow>,
    selectedMaterialId: StudyMaterialId?,
    onMaterialSelected: (StudyMaterialId) -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopSectionCard(title = "Resource list", modifier = modifier) {
        Text("Keyboard and mouse friendly scrolling list")
        LazyColumn(
            modifier = Modifier.height(320.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(materials, key = { material -> material.id.value }) { material ->
                DesktopMaterialListRow(
                    material = material,
                    selected = material.id == selectedMaterialId,
                    onMaterialSelected = onMaterialSelected,
                )
            }
        }
    }
}

@Composable
private fun DesktopMaterialListRow(
    material: DesktopLibraryMaterialRow,
    selected: Boolean,
    onMaterialSelected: (StudyMaterialId) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (selected) "Selected • ${material.title}" else material.title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${material.typeLabel} • ${material.progressPercent}% complete")
            Button(onClick = { onMaterialSelected(material.id) }) {
                Text("Show details")
            }
        }
    }
}

@Composable
private fun DesktopMaterialDetail(
    material: DesktopLibraryMaterialRow?,
    modifier: Modifier = Modifier,
) {
    DesktopSectionCard(title = "Resource detail", modifier = modifier) {
        if (material == null) {
            DesktopStateMessage(
                title = "Select a material",
                message = "The detail shell is ready for future editing and launch actions.",
            )
            return@DesktopSectionCard
        }
        Column(
            modifier = Modifier
                .height(320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(material.title, fontWeight = FontWeight.Bold)
            Text(material.description)
            Divider()
            Text("Type: ${material.typeLabel}")
            Text("Progress: ${material.progressPercent}%")
            Text("Favorite: ${if (material.favorite) "yes" else "no"}")
            if (material.tags.isNotEmpty()) {
                DesktopTagRow(material.tags)
            }
            DesktopStateMessage(
                title = "Action shell",
                message = "Open, edit, and file export actions wait for desktop filesystem ports.",
            )
        }
    }
}

@Composable
private fun DesktopVideoList(videos: List<DesktopLibraryVideoRow>) {
    DesktopSectionCard(title = "Videos") {
        if (videos.isEmpty()) {
            DesktopStateMessage(
                title = "No videos",
                message = "Video resources will appear here when loaded from shared storage.",
            )
            return@DesktopSectionCard
        }
        videos.forEach { video ->
            Text("• ${video.title} — ${video.progressLabel}")
        }
    }
}
