package com.mikeyphw.studyflow.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.StudyLibrarySnapshot
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import com.mikeyphw.studyflow.core.model.VideoSourceType
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyLibraryScreen(
    plan: StudyPlan,
    library: ManageStudyLibrary,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by library.observeSnapshot(plan.id).collectAsStateWithLifecycle(
        initialValue = StudyLibrarySnapshot(materials = emptyList(), videos = emptyList(), annexes = emptyList()),
    )
    val coroutineScope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.MATERIALS) }
    var materialTitle by rememberSaveable { mutableStateOf("") }
    var materialDescription by rememberSaveable { mutableStateOf("") }
    var materialSource by rememberSaveable { mutableStateOf("") }
    var materialUnits by rememberSaveable { mutableStateOf("") }
    var videoTitle by rememberSaveable { mutableStateOf("") }
    var videoUri by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StudyFlowSectionCard(title = plan.name) {
                Text(
                    text = stringResource(
                        R.string.library_summary,
                        snapshot.materials.size,
                        snapshot.videos.size,
                        snapshot.annexes.size,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.library_progress, snapshot.progressPercent)) },
                )
            }
            StudyFlowSectionCard(title = stringResource(R.string.add_material)) {
                OutlinedTextField(
                    value = materialTitle,
                    onValueChange = { materialTitle = it },
                    label = { Text(stringResource(R.string.material_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = materialDescription,
                    onValueChange = { materialDescription = it },
                    label = { Text(stringResource(R.string.material_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = materialSource,
                    onValueChange = { materialSource = it },
                    label = { Text(stringResource(R.string.material_source)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = materialUnits,
                    onValueChange = { materialUnits = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.material_units)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            library.createMaterial(
                                id = StudyMaterialId(UUID.randomUUID().toString()),
                                studyPlanId = plan.id,
                                subjectId = null,
                                topicId = null,
                                type = StudyMaterialType.PDF,
                                title = materialTitle,
                                description = materialDescription,
                                sourceUri = materialSource,
                                author = null,
                                totalUnits = materialUnits.toIntOrNull(),
                                tags = emptyList(),
                            )
                            materialTitle = ""
                            materialDescription = ""
                            materialSource = ""
                            materialUnits = ""
                        }
                    },
                    enabled = materialTitle.isNotBlank(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.create_material))
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.add_video)) {
                OutlinedTextField(
                    value = videoTitle,
                    onValueChange = { videoTitle = it },
                    label = { Text(stringResource(R.string.video_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = videoUri,
                    onValueChange = { videoUri = it },
                    label = { Text(stringResource(R.string.video_uri)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            library.createVideo(
                                id = VideoResourceId(UUID.randomUUID().toString()),
                                studyPlanId = plan.id,
                                subjectId = null,
                                topicId = null,
                                title = videoTitle,
                                sourceType = VideoSourceType.WEB_PAGE,
                                sourceUri = videoUri,
                                durationMillis = null,
                            )
                            videoTitle = ""
                            videoUri = ""
                        }
                    },
                    enabled = videoTitle.isNotBlank() && videoUri.isNotBlank(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.create_video))
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.library_title)) {
                LibraryFilterRow(selected = filter, onSelected = { filter = it })
                when (filter) {
                    LibraryFilter.MATERIALS -> MaterialList(snapshot.activeMaterials, library, plan)
                    LibraryFilter.VIDEOS -> VideoList(snapshot.videos, library, plan)
                    LibraryFilter.ANNEXES -> if (snapshot.annexes.isEmpty()) {
                        Text(stringResource(R.string.no_annexes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        snapshot.annexes.forEach { annex -> Text(annex.displayName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFilterRow(selected: LibraryFilter, onSelected: (LibraryFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LibraryFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
    }
}

@Composable
private fun MaterialList(materials: List<StudyMaterial>, library: ManageStudyLibrary, plan: StudyPlan) {
    val coroutineScope = rememberCoroutineScope()
    if (materials.isEmpty()) {
        Text(stringResource(R.string.no_materials), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        materials.forEach { material ->
            val row = material.asRowState()
            StudyFlowSectionCard(title = row.title) {
                Text(stringResource(R.string.progress_percent, row.progressPercent))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { coroutineScope.launch { library.toggleFavorite(plan.id, material.id) } },
                    ) {
                        Icon(Icons.Outlined.Star, contentDescription = null)
                        Text(stringResource(R.string.favorite))
                    }
                    OutlinedButton(
                        onClick = { coroutineScope.launch { library.archiveMaterial(plan.id, material.id) } },
                    ) {
                        Text(stringResource(R.string.archive))
                    }
                    OutlinedButton(
                        onClick = { coroutineScope.launch { library.deleteMaterial(plan.id, material.id) } },
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoList(videos: List<VideoResource>, library: ManageStudyLibrary, plan: StudyPlan) {
    val coroutineScope = rememberCoroutineScope()
    if (videos.isEmpty()) {
        Text(stringResource(R.string.no_videos), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        videos.forEach { video ->
            StudyFlowSectionCard(title = video.title) {
                Text(video.sourceUri, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                library.updateVideoProgress(plan.id, video.id, 0L, true)
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Text(stringResource(R.string.mark_complete))
                    }
                    OutlinedButton(onClick = { coroutineScope.launch { library.deleteVideo(plan.id, video.id) } }) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

private fun LibraryFilter.labelRes(): Int = when (this) {
    LibraryFilter.MATERIALS -> R.string.materials
    LibraryFilter.VIDEOS -> R.string.videos
    LibraryFilter.ANNEXES -> R.string.annexes
}
