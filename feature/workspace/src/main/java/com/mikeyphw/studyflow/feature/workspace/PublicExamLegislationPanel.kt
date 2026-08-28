package com.mikeyphw.studyflow.feature.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionTitle
import com.mikeyphw.studyflow.core.domain.LegislationImportFormat
import com.mikeyphw.studyflow.core.domain.LegislationImportPreview
import com.mikeyphw.studyflow.core.domain.ManagePublicExam
import com.mikeyphw.studyflow.core.model.LegislationProvision
import com.mikeyphw.studyflow.core.model.LegislationStudySnapshot
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LegislationUiFilter { ALL, UNREAD, IMPORTANT, ERROR_LINKED }

@Composable
internal fun PublicExamLegislationPanel(
    planId: StudyPlanId,
    snapshot: LegislationStudySnapshot,
    manager: ManagePublicExam,
) {
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(LegislationUiFilter.ALL) }
    var importing by remember { mutableStateOf(false) }
    val errorLinked = snapshot.evidenceLinks.filter { it.errorEntryId != null }.map { it.provisionId }.toSet()
    val visible = snapshot.provisions.filter { provision ->
        when (filter) {
            LegislationUiFilter.ALL -> true
            LegislationUiFilter.UNREAD -> !provision.read
            LegislationUiFilter.IMPORTANT -> provision.important
            LegislationUiFilter.ERROR_LINKED -> provision.id in errorLinked
        }
    }
    StudyFlowSectionTitle(title = stringResource(R.string.legislation_title))
    Text(
        text = stringResource(R.string.legislation_local_only_notice),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LegislationUiFilter.entries.forEach { candidate ->
            FilterChip(
                selected = candidate == filter,
                onClick = { filter = candidate },
                label = { Text(stringResource(candidate.labelResource())) },
            )
        }
    }
    OutlinedButton(onClick = { importing = true }) { Text(stringResource(R.string.legislation_import)) }
    if (snapshot.documents.isEmpty()) {
        Text(stringResource(R.string.legislation_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        snapshot.documents.forEach { document ->
            Text(document.title, style = MaterialTheme.typography.titleMedium)
            visible.filter { it.legislationDocumentId == document.id }.forEach { provision ->
                LegislationProvisionRow(
                    provision = provision,
                    questionLinks = snapshot.linksFor(provision.id).count { it.questionId != null },
                    errorLinks = snapshot.linksFor(provision.id).count { it.errorEntryId != null },
                    reviewLinks = snapshot.linksFor(provision.id).count { it.reviewItemId != null },
                    onRead = { read ->
                        scope.launch { manager.setLegislationProvisionRead(planId, provision.id, read) }
                    },
                    onImportant = { important ->
                        scope.launch { manager.setLegislationProvisionImportant(planId, provision.id, important) }
                    },
                )
            }
        }
    }
    if (importing) {
        LegislationImportDialog(
            manager = manager,
            onDismiss = { importing = false },
            onApply = { preview ->
                scope.launch {
                    manager.applyLegislationImport(planId, preview)
                    importing = false
                }
            },
        )
    }
}

@Composable
private fun LegislationProvisionRow(
    provision: LegislationProvision,
    questionLinks: Int,
    errorLinks: Int,
    reviewLinks: Int,
    onRead: (Boolean) -> Unit,
    onImportant: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${provision.reference} — ${provision.text}")
        Text(
            stringResource(R.string.legislation_link_counts, questionLinks, errorLinks, reviewLinks),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onRead(!provision.read) }) {
                Text(stringResource(if (provision.read) R.string.mark_unread else R.string.mark_read))
            }
            TextButton(onClick = { onImportant(!provision.important) }) {
                Text(stringResource(if (provision.important) R.string.remove_important else R.string.mark_important))
            }
        }
    }
}

@Composable
private fun LegislationImportDialog(
    manager: ManagePublicExam,
    onDismiss: () -> Unit,
    onApply: (LegislationImportPreview) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(LegislationImportFormat.STRUCTURED_TEXT) }
    var preview by remember { mutableStateOf<LegislationImportPreview?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Could not open selected document.")
                }
                raw = text
                val name = uri.lastPathSegment.orEmpty()
                if (name.endsWith(".csv", ignoreCase = true)) format = LegislationImportFormat.CSV
                if (name.endsWith(".md", ignoreCase = true)) format = LegislationImportFormat.MARKDOWN
                preview = null
                failure = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error.message
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.legislation_import)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it; preview = null },
                    label = { Text(stringResource(R.string.legislation_document_title)) })
                OutlinedTextField(value = source, onValueChange = { source = it; preview = null },
                    label = { Text(stringResource(R.string.legislation_source_reference)) })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LegislationImportFormat.entries.forEach { candidate ->
                        FilterChip(selected = candidate == format, onClick = { format = candidate; preview = null },
                            label = { Text(candidate.name) })
                    }
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("text/plain", "text/markdown", "text/csv")) }) {
                    Text(stringResource(R.string.legislation_choose_file))
                }
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it; preview = null },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    label = { Text(stringResource(R.string.legislation_paste_text)) },
                )
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                preview?.let { ready ->
                    Text(stringResource(R.string.legislation_preview_count, ready.provisions.size))
                    ready.provisions.take(12).forEach { Text("${it.reference} — ${it.text}") }
                    ready.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                }
            }
        },
        confirmButton = {
            if (preview == null) {
                Button(onClick = {
                    runCatching { manager.previewLegislationImport(title, source.ifBlank { null }, raw, format) }
                        .onSuccess { preview = it; failure = null }
                        .onFailure { failure = it.message }
                }) { Text(stringResource(R.string.preview)) }
            } else {
                Button(onClick = { onApply(requireNotNull(preview)) }) { Text(stringResource(R.string.apply_changes)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun LegislationUiFilter.labelResource(): Int = when (this) {
    LegislationUiFilter.ALL -> R.string.legislation_filter_all
    LegislationUiFilter.UNREAD -> R.string.legislation_filter_unread
    LegislationUiFilter.IMPORTANT -> R.string.legislation_filter_important
    LegislationUiFilter.ERROR_LINKED -> R.string.legislation_filter_error_linked
}
