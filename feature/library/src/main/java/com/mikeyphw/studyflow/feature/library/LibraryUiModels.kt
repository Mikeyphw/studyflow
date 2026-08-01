package com.mikeyphw.studyflow.feature.library

import com.mikeyphw.studyflow.core.domain.LibraryProgressCalculator
import com.mikeyphw.studyflow.core.model.StudyMaterial

enum class LibraryFilter {
    MATERIALS,
    VIDEOS,
    ANNEXES,
}

data class MaterialRowState(
    val title: String,
    val progressPercent: Int,
    val favorite: Boolean,
    val archived: Boolean,
)

fun StudyMaterial.asRowState(): MaterialRowState = MaterialRowState(
    title = title,
    progressPercent = LibraryProgressCalculator.materialProgressPercent(this),
    favorite = favorite,
    archived = archived,
)
