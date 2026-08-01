package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.VideoResource

object LibraryProgressCalculator {
    fun materialProgressPercent(material: StudyMaterial): Int {
        val total = material.totalUnits ?: return if (material.completedUnits > 0) 100 else 0
        if (total <= 0) return 0
        return ((material.completedUnits.coerceIn(0, total).toDouble() / total.toDouble()) * 100).toInt()
    }

    fun overallProgressPercent(materials: List<StudyMaterial>, videos: List<VideoResource>): Int {
        val materialScores = materials.filterNot(StudyMaterial::archived).map(::materialProgressPercent)
        val videoScores = videos.map { video -> if (video.completed) 100 else 0 }
        val scores = materialScores + videoScores
        return if (scores.isEmpty()) 0 else scores.average().toInt()
    }
}

data class StudyLibrarySnapshot(
    val materials: List<StudyMaterial>,
    val videos: List<VideoResource>,
    val annexes: List<Annex>,
) {
    val activeMaterials: List<StudyMaterial> = materials.filterNot(StudyMaterial::archived)
    val favoriteMaterials: List<StudyMaterial> = activeMaterials.filter(StudyMaterial::favorite)
    val completedVideos: List<VideoResource> = videos.filter(VideoResource::completed)
    val progressPercent: Int = LibraryProgressCalculator.overallProgressPercent(activeMaterials, videos)
}
