package com.mikeyphw.studyflow.feature.plans

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.domain.ManageStudyPlanPortability
import com.mikeyphw.studyflow.core.model.PlanSnapshot
import com.mikeyphw.studyflow.core.model.PlanSnapshotKind
import com.mikeyphw.studyflow.core.model.PlanSnapshotLabels
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportPreview
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageMimeType
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanPortabilityScreen(
    plan: StudyPlan?,
    portability: ManageStudyPlanPortability,
    onBack: () -> Unit,
    onImported: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exportCompletedMessage = stringResource(R.string.export_completed)
    val importCompletedMessage = stringResource(R.string.import_completed)
    val portabilityFailedMessage = stringResource(R.string.portability_failed)
    val manualSnapshotLabel = stringResource(R.string.manual_snapshot_label)
    val snapshotCreatedMessage = stringResource(R.string.snapshot_created)
    val snapshotRestoredMessage = stringResource(R.string.snapshot_restored)
    val snapshotDeletedMessage = stringResource(R.string.snapshot_deleted)
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<StudyPlanExportOptions?>(null) }
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }
    var importPreview by remember { mutableStateOf<StudyPlanImportPreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val snapshotsFlow = remember(plan?.id) {
        plan?.let { portability.observeSnapshots(it.id) } ?: flowOf(emptyList())
    }
    val snapshots by snapshotsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(StudyPlanPackageMimeType),
    ) { uri: Uri? ->
        val options = pendingExport
        pendingExport = null
        if (uri != null && options != null && plan != null) {
            scope.launch {
                busy = true
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { output ->
                        portability.exportPlan(plan.id, options, output)
                    }
                }.onSuccess { message = exportCompletedMessage }
                    .onFailure { message = portabilityFailedMessage }
                busy = false
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            selectedImportUri = uri
            scope.launch {
                busy = true
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        portability.previewImport(input)
                    }
                }.onSuccess { importPreview = it }
                    .onFailure { message = portabilityFailedMessage }
                busy = false
            }
        }
    }

    fun export(options: StudyPlanExportOptions) {
        val current = plan ?: return
        pendingExport = options
        exportLauncher.launch("${current.name.toSafeFileName()}.studyplan")
    }

    fun import(strategy: StudyPlanImportStrategy) {
        val uri = selectedImportUri ?: return
        scope.launch {
            busy = true
            runCatching {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    portability.importPlan(input, strategy, plan?.id)
                }
            }.onSuccess { result ->
                importPreview = null
                selectedImportUri = null
                message = importCompletedMessage
                onImported(result.studyPlanId)
            }.onFailure { message = portabilityFailedMessage }
            busy = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.portability_title)) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.import_plan), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.import_plan_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                importLauncher.launch(
                                    arrayOf(
                                        StudyPlanPackageMimeType,
                                        "application/zip",
                                        "application/octet-stream",
                                    ),
                                )
                            },
                            enabled = !busy,
                        ) { Text(stringResource(R.string.choose_studyplan_file)) }
                    }
                }
            }
            if (plan != null) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(stringResource(R.string.export_plan), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.export_plan_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { export(StudyPlanExportOptions(StudyPlanExportMode.FULL, true)) },
                                enabled = !busy,
                            ) { Text(stringResource(R.string.export_full_plan)) }
                            OutlinedButton(
                                onClick = {
                                    export(
                                        StudyPlanExportOptions(
                                            StudyPlanExportMode.PRIVATE_STRIPPED,
                                            includeAnnexMetadata = true,
                                        ),
                                    )
                                },
                                enabled = !busy,
                            ) { Text(stringResource(R.string.export_without_private_history)) }
                            OutlinedButton(
                                onClick = { export(StudyPlanExportOptions(StudyPlanExportMode.STRUCTURE_ONLY, false)) },
                                enabled = !busy,
                            ) { Text(stringResource(R.string.export_structure_only)) }
                            OutlinedButton(
                                onClick = { export(StudyPlanExportOptions(StudyPlanExportMode.TEMPLATE, false)) },
                                enabled = !busy,
                            ) { Text(stringResource(R.string.export_template)) }
                        }
                    }
                }
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(stringResource(R.string.snapshots_title), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.snapshots_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        runCatching {
                                            portability.createSnapshot(
                                                plan.id,
                                                PlanSnapshotKind.MANUAL,
                                                manualSnapshotLabel,
                                            )
                                        }.onSuccess { message = snapshotCreatedMessage }
                                            .onFailure { message = portabilityFailedMessage }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                            ) { Text(stringResource(R.string.create_snapshot)) }
                        }
                    }
                }
                items(snapshots, key = { it.id.value }) { snapshot ->
                    SnapshotCard(
                        snapshot = snapshot,
                        enabled = !busy,
                        onRestore = {
                            scope.launch {
                                busy = true
                                runCatching { portability.restoreSnapshot(snapshot.id) }
                                    .onSuccess {
                                        message = snapshotRestoredMessage
                                        onImported(it.studyPlanId)
                                    }
                                    .onFailure { message = portabilityFailedMessage }
                                busy = false
                            }
                        },
                        onDelete = {
                            scope.launch {
                                portability.deleteSnapshot(snapshot.id)
                                message = snapshotDeletedMessage
                            }
                        },
                    )
                }
            }
            message?.let { text ->
                item { Text(text, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text(stringResource(R.string.import_preview_title, preview.manifest.displayName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        pluralStringResource(
                            R.plurals.import_preview_rows,
                            preview.totalRows,
                            preview.totalRows,
                        ),
                    )
                    Text(
                        stringResource(
                            R.string.import_preview_mode,
                            stringResource(exportModeLabel(preview.manifest.exportMode)),
                        ),
                    )
                    if (preview.manifest.includesAnnexMetadata && !preview.manifest.includesAnnexPayloads) {
                        Text(
                            stringResource(R.string.annex_metadata_only_warning),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (preview.manifest.exportMode == StudyPlanExportMode.TEMPLATE) {
                        Text(
                            stringResource(R.string.template_import_warning),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { import(StudyPlanImportStrategy.CREATE_AS_NEW) }) {
                    Text(stringResource(R.string.import_as_new))
                }
            },
            dismissButton = {
                if (plan != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { import(StudyPlanImportStrategy.MERGE_COMPATIBLE) }) {
                            Text(stringResource(R.string.merge_into_plan))
                        }
                        OutlinedButton(onClick = { import(StudyPlanImportStrategy.REPLACE_EXISTING) }) {
                            Text(stringResource(R.string.replace_plan))
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun SnapshotCard(
    snapshot: PlanSnapshot,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(snapshotDisplayLabel(snapshot), style = MaterialTheme.typography.titleMedium)
                Text(
                    DateFormat.getDateTimeInstance().format(Date(snapshot.createdAtEpochMillis)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestore, enabled = enabled) {
                Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.restore_snapshot))
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.delete_snapshot))
            }
        }
    }
}

private fun String.toSafeFileName(): String = lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9._-]+"), "-")
    .trim('-')
    .ifBlank { "study-plan" }


@androidx.annotation.StringRes
private fun exportModeLabel(mode: StudyPlanExportMode): Int = when (mode) {
    StudyPlanExportMode.FULL -> R.string.export_mode_full
    StudyPlanExportMode.PRIVATE_STRIPPED -> R.string.export_mode_private_stripped
    StudyPlanExportMode.STRUCTURE_ONLY -> R.string.export_mode_structure
    StudyPlanExportMode.TEMPLATE -> R.string.export_mode_template
}


@Composable
private fun snapshotDisplayLabel(snapshot: PlanSnapshot): String = when (snapshot.label) {
    PlanSnapshotLabels.PRE_IMPORT -> stringResource(R.string.pre_import_snapshot_label)
    PlanSnapshotLabels.PRE_RESTORE -> stringResource(R.string.pre_restore_snapshot_label)
    else -> snapshot.label
}
