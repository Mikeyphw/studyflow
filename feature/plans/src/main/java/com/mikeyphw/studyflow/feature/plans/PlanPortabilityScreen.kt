package com.mikeyphw.studyflow.feature.plans

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mikeyphw.studyflow.core.portability.StudyPlanBackupIntegrityReport
import com.mikeyphw.studyflow.core.portability.StudyPlanEncryptedPackageCodec
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportPreview
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageAuthenticationException
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageMimeType
import com.mikeyphw.studyflow.core.portability.StudyPlanPackagePasswordRequiredException
import com.mikeyphw.studyflow.core.portability.StudyPlanPortabilityWarning
import com.mikeyphw.studyflow.core.portability.StudyPlanPortabilityWarningType
import com.mikeyphw.studyflow.core.portability.StudyPlanPrivacyPolicy
import com.mikeyphw.studyflow.core.portability.StudyPlanRestoreDryRun
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class PendingExport(
    val options: StudyPlanExportOptions,
    val password: CharArray?,
)

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
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }
    var importPreview by remember { mutableStateOf<StudyPlanImportPreview?>(null) }
    var integrityReport by remember { mutableStateOf<StudyPlanBackupIntegrityReport?>(null) }
    var dryRun by remember { mutableStateOf<StudyPlanRestoreDryRun?>(null) }
    var exportPassword by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var encryptExport by remember { mutableStateOf(false) }
    var metadataPrivacy by remember { mutableStateOf(false) }
    var redactNotes by remember { mutableStateOf(false) }
    var redactHistory by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val snapshotsFlow = remember(plan?.id) {
        plan?.let { portability.observeSnapshots(it.id) } ?: flowOf(emptyList())
    }
    val snapshots by snapshotsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val failedMessage = stringResource(R.string.portability_failed)
    val exportCompleted = stringResource(R.string.export_completed)
    val importCompleted = stringResource(R.string.import_completed)
    val snapshotCreated = stringResource(R.string.snapshot_created)
    val snapshotRestored = stringResource(R.string.snapshot_restored)
    val snapshotDeleted = stringResource(R.string.snapshot_deleted)
    val manualSnapshotLabel = stringResource(R.string.manual_snapshot_label)
    val passwordTooShort = stringResource(R.string.password_too_short)
    val passwordRequired = stringResource(R.string.password_required_for_encrypted)
    val passwordInvalid = stringResource(R.string.password_or_package_invalid)
    val yesLabel = stringResource(R.string.yes)
    val noLabel = stringResource(R.string.no)

    suspend fun <T> usePassword(value: String, block: suspend (CharArray?) -> T): T {
        val password = value.takeIf(String::isNotBlank)?.toCharArray()
        return try {
            block(password)
        } finally {
            password?.fill('\u0000')
        }
    }

    fun operationFailure(error: Throwable): String = when (error) {
        is StudyPlanPackagePasswordRequiredException -> passwordRequired
        is StudyPlanPackageAuthenticationException -> passwordInvalid
        else -> failedMessage
    }

    suspend fun <T> runPortabilityOperation(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(StudyPlanPackageMimeType),
    ) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending != null && plan != null) {
            scope.launch {
                busy = true
                try {
                    runPortabilityOperation {
                        context.contentResolver.openOutputStream(uri, "wt")!!.use { output ->
                            portability.exportPlan(plan.id, pending.options, output, pending.password)
                        }
                    }.onSuccess {
                        message = exportCompleted
                        exportPassword = ""
                    }.onFailure { error ->
                        cleanupFailedExportDestination(context, uri)
                        message = operationFailure(error)
                    }
                } finally {
                    pending.password?.fill('\u0000')
                    busy = false
                }
            }
        } else {
            pending?.password?.fill('\u0000')
        }
    }

    fun inspect(uri: Uri) {
        scope.launch {
            busy = true
            try {
                runPortabilityOperation {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        usePassword(importPassword) { password ->
                            portability.inspectBackup(input, password)
                        }
                    }
                }.onSuccess { integrityReport = it; message = null }
                    .onFailure { message = operationFailure(it) }
            } finally {
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedImportUri = uri
            importPreview = null
            integrityReport = null
            dryRun = null
            inspect(uri)
        }
    }

    fun export(mode: StudyPlanExportMode, includeAnnexMetadata: Boolean, includeAnnexPayloads: Boolean) {
        val current = plan ?: return
        if (encryptExport && exportPassword.length < StudyPlanEncryptedPackageCodec.MinimumPasswordLength) {
            message = passwordTooShort
            return
        }
        val options = StudyPlanExportOptions(
            mode = mode,
            includeAnnexMetadata = includeAnnexMetadata,
            includeAnnexPayloads = includeAnnexPayloads,
            metadataPrivacyMode = metadataPrivacy,
            redactNotes = redactNotes,
            redactHistory = redactHistory,
        )
        pendingExport = PendingExport(options, if (encryptExport) exportPassword.toCharArray() else null)
        val fileName = if (metadataPrivacy) {
            StudyPlanPrivacyPolicy.PrivateExportFileName
        } else {
            "${current.name.toSafeFileName()}.studyplan"
        }
        exportLauncher.launch(fileName)
    }

    fun previewImport() {
        val uri = selectedImportUri ?: return
        scope.launch {
            busy = true
            try {
                runPortabilityOperation {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        usePassword(importPassword) { password ->
                            portability.previewImport(input, password)
                        }
                    }
                }.onSuccess { importPreview = it; message = null }
                    .onFailure { message = operationFailure(it) }
            } finally {
                busy = false
            }
        }
    }

    fun dryRunImport(strategy: StudyPlanImportStrategy) {
        val uri = selectedImportUri ?: return
        scope.launch {
            busy = true
            try {
                runPortabilityOperation {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        usePassword(importPassword) { password ->
                            portability.dryRunImport(input, strategy, plan?.id, password)
                        }
                    }
                }.onSuccess { dryRun = it; message = null }
                    .onFailure { message = operationFailure(it) }
            } finally {
                busy = false
            }
        }
    }

    fun import(strategy: StudyPlanImportStrategy) {
        val uri = selectedImportUri ?: return
        scope.launch {
            busy = true
            try {
                runPortabilityOperation {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        usePassword(importPassword) { password ->
                            portability.importPlan(input, strategy, plan?.id, password)
                        }
                    }
                }.onSuccess { result ->
                    importPreview = null
                    selectedImportUri = null
                    importPassword = ""
                    message = importCompleted
                    onImported(result.studyPlanId)
                }.onFailure { message = operationFailure(it) }
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.portability_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.go_back))
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
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.import_plan), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.import_plan_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = importPassword,
                            onValueChange = { importPassword = it },
                            label = { Text(stringResource(R.string.import_password_optional)) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
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
                        if (selectedImportUri != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { inspect(selectedImportUri!!) }, enabled = !busy) {
                                    Text(stringResource(R.string.inspect_integrity))
                                }
                                OutlinedButton(onClick = ::previewImport, enabled = !busy) {
                                    Text(stringResource(R.string.preview_import))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { dryRunImport(StudyPlanImportStrategy.CREATE_AS_NEW) },
                                    enabled = !busy,
                                ) { Text(stringResource(R.string.dry_run_new)) }
                                if (plan != null) {
                                    OutlinedButton(
                                        onClick = { dryRunImport(StudyPlanImportStrategy.REPLACE_EXISTING) },
                                        enabled = !busy,
                                    ) { Text(stringResource(R.string.dry_run_replace)) }
                                }
                            }
                        }
                        integrityReport?.let { report ->
                            Text(
                                pluralStringResource(
                                    R.plurals.integrity_report,
                                    report.totalRows,
                                    report.totalRows,
                                    report.annexPayloadCount,
                                    if (report.encrypted) yesLabel else noLabel,
                                ),
                            )
                            report.warnings.forEach { warning ->
                                Text(
                                    portabilityWarning(warning),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        dryRun?.let { report ->
                            Text(
                                pluralStringResource(
                                    R.plurals.dry_run_report,
                                    report.totalRows,
                                    report.totalRows,
                                    report.databaseRowsChanged,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            report.warnings.forEach { warning ->
                                Text(
                                    portabilityWarning(warning),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (plan != null) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.export_plan), style = MaterialTheme.typography.titleLarge)
                            PrivacyToggle(
                                stringResource(R.string.encrypt_export),
                                encryptExport,
                                { encryptExport = it },
                            )
                            if (encryptExport) {
                                OutlinedTextField(
                                    value = exportPassword,
                                    onValueChange = { exportPassword = it },
                                    label = { Text(stringResource(R.string.export_password)) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                            }
                            PrivacyToggle(
                                stringResource(R.string.metadata_privacy),
                                metadataPrivacy,
                                { metadataPrivacy = it },
                            )
                            PrivacyToggle(
                                stringResource(R.string.redact_notes),
                                redactNotes,
                                { redactNotes = it },
                            )
                            PrivacyToggle(
                                stringResource(R.string.redact_history),
                                redactHistory,
                                { redactHistory = it },
                            )
                            Button(
                                onClick = { export(StudyPlanExportMode.FULL, true, true) },
                                enabled = !busy,
                            ) { Text(stringResource(R.string.export_full_plan_with_annexes)) }
                            OutlinedButton(
                                onClick = { export(StudyPlanExportMode.FULL, true, false) },
                                enabled = !busy,
                            ) { Text(stringResource(R.string.export_full_plan_metadata_only)) }
                            OutlinedButton(
                                onClick = { export(StudyPlanExportMode.PRIVATE_STRIPPED, true, false) },
                                enabled = !busy,
                            ) { Text(stringResource(R.string.export_without_private_history)) }
                            OutlinedButton(
                                onClick = { export(StudyPlanExportMode.STRUCTURE_ONLY, false, false) },
                                enabled = !busy,
                            ) { Text(stringResource(R.string.export_structure_only)) }
                            OutlinedButton(
                                onClick = { export(StudyPlanExportMode.TEMPLATE, false, false) },
                                enabled = !busy,
                            ) { Text(stringResource(R.string.export_template)) }
                        }
                    }
                }
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.recovery_explanation_title))
                            Text(
                                stringResource(R.string.recovery_explanation_message),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.snapshots_title), style = MaterialTheme.typography.titleLarge)
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            runPortabilityOperation {
                                                portability.createSnapshot(
                                                    plan.id,
                                                    PlanSnapshotKind.MANUAL,
                                                    manualSnapshotLabel,
                                                )
                                            }.onSuccess { message = snapshotCreated }
                                                .onFailure { message = operationFailure(it) }
                                        } finally {
                                            busy = false
                                        }
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
                                try {
                                    runPortabilityOperation { portability.restoreSnapshot(snapshot.id) }
                                        .onSuccess { result ->
                                            message = snapshotRestored
                                            onImported(result.studyPlanId)
                                        }.onFailure { message = operationFailure(it) }
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                portability.deleteSnapshot(snapshot.id)
                                message = snapshotDeleted
                            }
                        },
                    )
                }
            }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        }
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text(stringResource(R.string.import_preview_title, preview.manifest.displayName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pluralStringResource(R.plurals.import_preview_rows, preview.totalRows, preview.totalRows))
                    Text(
                        stringResource(
                            R.string.import_preview_mode,
                            stringResource(exportModeLabel(preview.manifest.exportMode)),
                        ),
                    )
                    if (preview.encrypted) Text(stringResource(R.string.encrypted_package_verified))
                    preview.warnings.forEach { warning ->
                        Text(portabilityWarning(warning))
                    }
                    if (preview.manifest.redactedSections.isNotEmpty() &&
                        preview.warnings.none { it.type == StudyPlanPortabilityWarningType.PRIVACY_REDACTIONS }
                    ) {
                        Text(stringResource(R.string.privacy_redactions_present))
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
private fun portabilityWarning(warning: StudyPlanPortabilityWarning): String = when (warning.type) {
    StudyPlanPortabilityWarningType.ANNEX_METADATA_ONLY -> stringResource(R.string.annex_metadata_only_warning)
    StudyPlanPortabilityWarningType.ANNEX_PAYLOADS_INCLUDED -> stringResource(R.string.annex_payloads_included)
    StudyPlanPortabilityWarningType.TEMPLATE_WITHOUT_HISTORY -> stringResource(R.string.template_import_warning)
    StudyPlanPortabilityWarningType.PRIVACY_REDACTIONS -> stringResource(R.string.privacy_redactions_present)
    StudyPlanPortabilityWarningType.REPLACE_CREATES_SAFETY_SNAPSHOT -> stringResource(
        R.string.replace_creates_safety_snapshot,
    )
}

@Composable
private fun PrivacyToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(top = 12.dp))
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
                Icon(Icons.Outlined.Restore, stringResource(R.string.restore_snapshot))
            }
            ConfirmingSnapshotDeleteButton(enabled = enabled, onConfirmed = onDelete)
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


@Composable
private fun ConfirmingSnapshotDeleteButton(enabled: Boolean, onConfirmed: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    IconButton(onClick = { confirming = true }, enabled = enabled) {
        Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete_snapshot))
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


private fun cleanupFailedExportDestination(context: Context, uri: Uri) {
    val resolver = context.contentResolver
    val deleted = runCatching {
        DocumentsContract.isDocumentUri(context, uri) && DocumentsContract.deleteDocument(resolver, uri)
    }.getOrDefault(false)
    if (!deleted) {
        runCatching { resolver.openOutputStream(uri, "wt")?.use { } }
    }
}
