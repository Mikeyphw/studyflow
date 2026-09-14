package com.mikeyphw.studyflow.feature.subjects

internal const val Ov15MaxManualImportChars: Int = 512 * 1024
internal const val Ov15MaxPdfImportBytes: Long = 16L * 1024L * 1024L

internal data class BoundedImportDraft(
    val text: String,
    val accepted: Boolean,
) {
    val truncated: Boolean = !accepted
}

internal fun boundManualImportDraft(candidate: String): BoundedImportDraft =
    if (candidate.length <= Ov15MaxManualImportChars) {
        BoundedImportDraft(candidate, accepted = true)
    } else {
        BoundedImportDraft(candidate.take(Ov15MaxManualImportChars), accepted = false)
    }
