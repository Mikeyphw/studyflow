package com.mikeyphw.studyflow.feature.library

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.domain.ManageAnnexFileStorage
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.ManageStudyResourceViewer
import com.mikeyphw.studyflow.core.domain.StudyLibrarySnapshot
import com.mikeyphw.studyflow.core.model.Annex
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
    annexFiles: ManageAnnexFileStorage,
    resourceViewer: ManageStudyResourceViewer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by library.observeSnapshot(plan.id).collectAsStateWithLifecycle(
        initialValue = StudyLibrarySnapshot(materials = emptyList(), videos = emptyList(), annexes = emptyList()),
    )
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.MATERIALS) }
    var materialTitle by rememberSaveable { mutableStateOf("") }
    var materialDescription by rememberSaveable { mutableStateOf("") }
    var materialSource by rememberSaveable { mutableStateOf("") }
    var materialUnits by rememberSaveable { mutableStateOf("") }
    var videoTitle by rememberSaveable { mutableStateOf("") }
    var videoUri by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val annexImportedMessage = stringResource(R.string.annex_imported)
    val annexDeletedMessage = stringResource(R.string.annex_deleted)
    val annexFailedMessage = stringResource(R.string.annex_failed)
    val resourceOpenedMessage = stringResource(R.string.resource_opened)
    val resourceOpenFailedMessage = stringResource(R.string.resource_open_failed)
    val annexPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    val displayName = context.contentResolver.displayName(uri)
                    val byteSize = context.contentResolver.byteSize(uri)
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        annexFiles.importCopy(
                            studyPlanId = plan.id,
                            displayName = displayName,
                            mimeType = mimeType,
                            declaredByteSize = byteSize,
                            input = input,
                        )
                    }
                }.onSuccess { message = annexImportedMessage }
                    .onFailure { message = annexFailedMessage }
            }
        }
    }

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
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
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
            StudyFlowSectionCard(title = stringResource(R.string.attach_annex)) {
                Text(
                    stringResource(R.string.attach_annex_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { annexPicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.choose_annex_file))
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.library_title)) {
                LibraryFilterRow(selected = filter, onSelected = { filter = it })
                when (filter) {
                    LibraryFilter.MATERIALS -> MaterialList(
                        materials = snapshot.activeMaterials,
                        library = library,
                        resourceViewer = resourceViewer,
                        plan = plan,
                        onLaunchResult = { opened ->
                            message = if (opened) resourceOpenedMessage else resourceOpenFailedMessage
                        },
                    )
                    LibraryFilter.VIDEOS -> VideoList(
                        videos = snapshot.videos,
                        library = library,
                        resourceViewer = resourceViewer,
                        plan = plan,
                        onLaunchResult = { opened ->
                            message = if (opened) resourceOpenedMessage else resourceOpenFailedMessage
                        },
                    )
                    LibraryFilter.ANNEXES -> AnnexList(
                        annexes = snapshot.annexes,
                        annexFiles = annexFiles,
                        resourceViewer = resourceViewer,
                        plan = plan,
                        onDeleted = { message = annexDeletedMessage },
                        onLaunchResult = { opened ->
                            message = if (opened) resourceOpenedMessage else resourceOpenFailedMessage
                        },
                    )
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
private fun MaterialList(
    materials: List<StudyMaterial>,
    library: ManageStudyLibrary,
    resourceViewer: ManageStudyResourceViewer,
    plan: StudyPlan,
    onLaunchResult: (Boolean) -> Unit,
) {
    val context = LocalContext.current
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
                        onClick = {
                            coroutineScope.launch {
                                val launchPlan = resourceViewer.planForMaterial(plan.id, material.id)
                                val opened = context.launchStudyResource(launchPlan)
                                if (opened) resourceViewer.markMaterialOpened(plan.id, material.id)
                                onLaunchResult(opened)
                            }
                        },
                    ) { Text(stringResource(R.string.open_resource)) }
                    OutlinedButton(
                        onClick = { coroutineScope.launch { library.toggleFavorite(plan.id, material.id) } },
                    ) {
                        Icon(Icons.Outlined.Star, contentDescription = null)
                        Text(stringResource(R.string.favorite))
                    }
                    OutlinedButton(
                        onClick = { coroutineScope.launch { library.archiveMaterial(plan.id, material.id) } },
                    ) { Text(stringResource(R.string.archive)) }
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
private fun VideoList(
    videos: List<VideoResource>,
    library: ManageStudyLibrary,
    resourceViewer: ManageStudyResourceViewer,
    plan: StudyPlan,
    onLaunchResult: (Boolean) -> Unit,
) {
    val context = LocalContext.current
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
                                val launchPlan = resourceViewer.planForVideo(plan.id, video.id)
                                val opened = context.launchStudyResource(launchPlan)
                                if (opened) resourceViewer.markVideoOpened(plan.id, video.id)
                                onLaunchResult(opened)
                            }
                        },
                    ) { Text(stringResource(R.string.open_video)) }
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

@Composable
private fun AnnexList(
    annexes: List<Annex>,
    annexFiles: ManageAnnexFileStorage,
    resourceViewer: ManageStudyResourceViewer,
    plan: StudyPlan,
    onDeleted: () -> Unit,
    onLaunchResult: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    if (annexes.isEmpty()) {
        Text(stringResource(R.string.no_annexes), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        annexes.forEach { annex ->
            StudyFlowSectionCard(title = annex.displayName) {
                Text(
                    stringResource(R.string.annex_size, annex.byteSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val opened = context.launchStudyResource(
                                    resourceViewer.planForAnnex(annex),
                                )
                                onLaunchResult(opened)
                            }
                        },
                    ) { Text(stringResource(R.string.open_annex)) }
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                annexFiles.deleteStoredAnnex(plan.id, annex.id)
                                onDeleted()
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
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

private fun android.content.ContentResolver.displayName(uri: Uri): String = query(
    uri,
    arrayOf(OpenableColumns.DISPLAY_NAME),
    null,
    null,
    null,
)?.use { cursor ->
    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
}.orEmpty().ifBlank { uri.lastPathSegment ?: "annex.bin" }

private fun android.content.ContentResolver.byteSize(uri: Uri): Long? = query(
    uri,
    arrayOf(OpenableColumns.SIZE),
    null,
    null,
    null,
)?.use { cursor ->
    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
}
