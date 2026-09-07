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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowStatusPill
import com.mikeyphw.studyflow.core.domain.runRecoverableOperation
import com.mikeyphw.studyflow.core.domain.ManageAnnexFileStorage
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.StudyReferenceFreshnessPolicy
import com.mikeyphw.studyflow.core.domain.StudyReferenceFreshnessStatus
import com.mikeyphw.studyflow.core.domain.StudyReferenceOrigin
import com.mikeyphw.studyflow.core.domain.StudyReferenceProvenanceCodec
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
import java.time.LocalDate
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
    val resources = LocalResources.current
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
    val annexPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                runRecoverableOperation {
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
    val annexLinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                runRecoverableOperation {
                    annexFiles.linkDocument(
                        studyPlanId = plan.id,
                        displayName = context.contentResolver.displayName(uri),
                        mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
                        storageReference = uri.toString(),
                    )
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
                    text = pluralStringResource(
                        R.plurals.library_summary,
                        snapshot.materials.size,
                        snapshot.materials.size,
                        snapshot.videos.size,
                        snapshot.annexes.size,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StudyFlowStatusPill(text = stringResource(R.string.library_progress, snapshot.progressPercent))
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
                    enabled = materialTitle.isNotBlank() &&
                        library.isValidMaterialSource(StudyMaterialType.PDF, materialSource),
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
                    enabled = videoTitle.isNotBlank() &&
                        library.isValidVideoSource(VideoSourceType.WEB_PAGE, videoUri),
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
                OutlinedButton(onClick = { annexLinkPicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.link_annex_file))
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.library_title)) {
                LibraryFilterRow(selected = filter, onSelected = { filter = it })
                when (filter) {
                    LibraryFilter.MATERIALS -> MaterialList(
                        materials = snapshot.materials,
                        library = library,
                        resourceViewer = resourceViewer,
                        plan = plan,
                        onLaunchResult = { result ->
                            message = resources.getString(result.messageRes())
                        },
                    )
                    LibraryFilter.VIDEOS -> VideoList(
                        videos = snapshot.videos,
                        library = library,
                        resourceViewer = resourceViewer,
                        plan = plan,
                        onLaunchResult = { result ->
                            message = resources.getString(result.messageRes())
                        },
                    )
                    LibraryFilter.ANNEXES -> AnnexList(
                        annexes = snapshot.annexes,
                        annexFiles = annexFiles,
                        resourceViewer = resourceViewer,
                        plan = plan,
                        onDeleted = { message = annexDeletedMessage },
                        onLaunchResult = { result ->
                            message = resources.getString(result.messageRes())
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
    onLaunchResult: (StudyResourceLaunchResult) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    if (materials.isEmpty()) {
        Text(stringResource(R.string.no_materials), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        materials.forEach { material ->
            val row = material.asRowState()
            StudyFlowSectionCard(title = row.title) {
                Text(stringResource(R.string.progress_percent, row.progressPercent))
                MaterialReferenceControls(material = material, library = library, plan = plan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val launchPlan = resourceViewer.planForMaterial(plan.id, material.id)
                                val result = context.launchStudyResource(launchPlan)
                                if (result == StudyResourceLaunchResult.Opened) {
                                    resourceViewer.markMaterialOpened(plan.id, material.id)
                                }
                                onLaunchResult(result)
                            }
                        },
                    ) { Text(stringResource(R.string.open_resource)) }
                    OutlinedButton(
                        onClick = { coroutineScope.launch { library.toggleFavorite(plan.id, material.id) } },
                    ) {
                        Icon(Icons.Outlined.Star, contentDescription = null)
                        Text(stringResource(R.string.favorite))
                    }
                    if (material.archived) {
                        OutlinedButton(
                            onClick = { coroutineScope.launch { library.restoreMaterial(plan.id, material.id) } },
                        ) { Text(stringResource(R.string.restore)) }
                    } else {
                        OutlinedButton(
                            onClick = { coroutineScope.launch { library.archiveMaterial(plan.id, material.id) } },
                        ) { Text(stringResource(R.string.archive)) }
                    }
                    ConfirmingDeleteButton {
                        coroutineScope.launch { library.deleteMaterial(plan.id, material.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialReferenceControls(
    material: StudyMaterial,
    library: ManageStudyLibrary,
    plan: StudyPlan,
) {
    val scope = rememberCoroutineScope()
    val provenance = remember(material.tags) { StudyReferenceProvenanceCodec.decode(material) }
    val freshness = remember(material.tags) {
        StudyReferenceFreshnessPolicy.evaluate(material, LocalDate.now())
    }
    var replacing by rememberSaveable(material.id.value) { mutableStateOf(false) }
    var replacementSource by rememberSaveable(material.id.value) { mutableStateOf("") }
    var verifiedOn by rememberSaveable(material.id.value) { mutableStateOf(LocalDate.now().toString()) }

    if (provenance.origin == null) {
        OutlinedButton(onClick = {
            scope.launch {
                library.markReferenceOrigin(
                    plan.id,
                    material.id,
                    StudyReferenceOrigin.EDITAL_2022,
                    LocalDate.now().toString(),
                )
            }
        }) { Text(stringResource(R.string.reference_mark_edital_2022)) }
        return
    }

    Text(stringResource(R.string.reference_origin_edital_2022))
    Text(
        provenance.verifiedOn?.let { stringResource(R.string.reference_verified_on, it.toString()) }
            ?: stringResource(R.string.reference_never_verified),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StudyFlowStatusPill(
        text = stringResource(
            when (freshness.status) {
                StudyReferenceFreshnessStatus.POSSIBLY_STALE -> R.string.reference_possibly_stale
                StudyReferenceFreshnessStatus.NEEDS_VERIFICATION -> R.string.reference_needs_verification
                StudyReferenceFreshnessStatus.CURRENT -> R.string.reference_current
                StudyReferenceFreshnessStatus.UNTRACKED -> R.string.reference_untracked
            },
        ),
    )
    if (freshness.status == StudyReferenceFreshnessStatus.POSSIBLY_STALE) {
        Text(
            pluralStringResource(
                R.plurals.reference_stale_policy,
                StudyReferenceFreshnessPolicy.PossiblyStaleAfterDays.toPluralQuantity(),
                StudyReferenceFreshnessPolicy.PossiblyStaleAfterDays,
            ),
            color = MaterialTheme.colorScheme.error,
        )
    }
    provenance.supersedesMaterialId?.let {
        Text(
            stringResource(R.string.reference_supersedes, it.value),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            scope.launch { library.verifyReference(plan.id, material.id, LocalDate.now().toString()) }
        }) { Text(stringResource(R.string.reference_verify_today)) }
        OutlinedButton(onClick = { replacing = true }) {
            Text(stringResource(R.string.reference_replace))
        }
    }

    if (replacing) {
        AlertDialog(
            onDismissRequest = { replacing = false },
            title = { Text(stringResource(R.string.reference_replace)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.reference_replace_history_notice))
                    OutlinedTextField(
                        value = replacementSource,
                        onValueChange = { replacementSource = it },
                        label = { Text(stringResource(R.string.reference_new_source)) },
                    )
                    OutlinedTextField(
                        value = verifiedOn,
                        onValueChange = { verifiedOn = it },
                        label = { Text(stringResource(R.string.reference_verified_date)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = replacementSource.isNotBlank() && runCatching { LocalDate.parse(verifiedOn) }.isSuccess,
                    onClick = {
                        replacing = false
                        scope.launch {
                            library.replaceReference(
                                plan.id,
                                material.id,
                                StudyMaterialId(UUID.randomUUID().toString()),
                                replacementSource,
                                verifiedOn,
                            )
                        }
                    },
                ) { Text(stringResource(R.string.reference_replace)) }
            },
            dismissButton = {
                TextButton(onClick = { replacing = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun VideoList(
    videos: List<VideoResource>,
    library: ManageStudyLibrary,
    resourceViewer: ManageStudyResourceViewer,
    plan: StudyPlan,
    onLaunchResult: (StudyResourceLaunchResult) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
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
                                val result = context.launchStudyResource(launchPlan)
                                if (result == StudyResourceLaunchResult.Opened) {
                                    resourceViewer.markVideoOpened(plan.id, video.id)
                                }
                                onLaunchResult(result)
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
                    ConfirmingDeleteButton {
                        coroutineScope.launch { library.deleteVideo(plan.id, video.id) }
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
    onLaunchResult: (StudyResourceLaunchResult) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    if (annexes.isEmpty()) {
        Text(stringResource(R.string.no_annexes), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        annexes.forEach { annex ->
            StudyFlowSectionCard(title = annex.displayName) {
                Text(
                    pluralStringResource(
                        R.plurals.annex_size,
                        annex.byteSize.toPluralQuantity(),
                        annex.byteSize,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val result = context.launchStudyResource(
                                    resourceViewer.planForAnnex(annex),
                                )
                                onLaunchResult(result)
                            }
                        },
                    ) { Text(stringResource(R.string.open_annex)) }
                    ConfirmingDeleteButton {
                        coroutineScope.launch {
                            annexFiles.deleteStoredAnnex(plan.id, annex.id)
                            onDeleted()
                        }
                    }
                }
            }
        }
    }
}

private fun StudyResourceLaunchResult.messageRes(): Int = when (this) {
    StudyResourceLaunchResult.Opened -> R.string.resource_opened
    StudyResourceLaunchResult.MissingSource -> R.string.resource_missing_source
    StudyResourceLaunchResult.InvalidSource -> R.string.resource_invalid_source
    StudyResourceLaunchResult.MissingPrivateFile -> R.string.resource_missing_private_file
    StudyResourceLaunchResult.UnsafePrivatePath -> R.string.resource_unsafe_private_path
    StudyResourceLaunchResult.IntegrityFailure -> R.string.resource_integrity_failure
    StudyResourceLaunchResult.NoHandler -> R.string.resource_no_handler
    StudyResourceLaunchResult.LaunchFailed -> R.string.resource_launch_failed
}

private fun LibraryFilter.labelRes(): Int = when (this) {
    LibraryFilter.MATERIALS -> R.string.materials
    LibraryFilter.VIDEOS -> R.string.videos
    LibraryFilter.ANNEXES -> R.string.annexes
}

private fun Long.toPluralQuantity(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

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


@Composable
private fun ConfirmingDeleteButton(onConfirmed: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { confirming = true }) {
        Icon(Icons.Outlined.Delete, contentDescription = null)
        Text(stringResource(R.string.delete))
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onConfirmed() }) {
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
