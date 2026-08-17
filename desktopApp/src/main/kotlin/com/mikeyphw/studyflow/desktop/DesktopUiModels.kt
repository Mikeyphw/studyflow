package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.LibraryProgressCalculator
import com.mikeyphw.studyflow.core.domain.StudyLibrarySnapshot
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.VideoResource
import kotlin.math.roundToInt

data class DesktopDashboardState(
    val status: DesktopScreenStatus,
    val activePlanName: String,
    val activePlanDescription: String,
    val progressPercent: Int,
    val weeklyGoalHours: Int,
    val studiedMinutesThisWeek: Int,
    val questionsAnswered: Int,
    val averageAccuracyPercent: Int,
    val readinessPercent: Int,
    val libraryProgressPercent: Int,
    val materialCount: Int,
    val favoriteCount: Int,
    val videoCount: Int,
    val domainManagers: List<String>,
    val boundaryModules: List<String>,
) {
    val studiedHoursThisWeek: Double = (studiedMinutesThisWeek.toDouble() / 60.0)
        .times(10.0)
        .roundToInt()
        .toDouble() / 10.0
}

data class DesktopLibraryScreenState(
    val status: DesktopScreenStatus,
    val planName: String,
    val materials: List<DesktopLibraryMaterialRow>,
    val videos: List<DesktopLibraryVideoRow>,
    val summary: DesktopLibrarySummary,
)

data class DesktopLibrarySummary(
    val activeMaterials: Int,
    val favoriteMaterials: Int,
    val videos: Int,
    val annexes: Int,
    val progressPercent: Int,
) {
    companion object {
        fun fromSnapshot(snapshot: StudyLibrarySnapshot): DesktopLibrarySummary = DesktopLibrarySummary(
            activeMaterials = snapshot.activeMaterials.size,
            favoriteMaterials = snapshot.favoriteMaterials.size,
            videos = snapshot.videos.size,
            annexes = snapshot.annexes.size,
            progressPercent = snapshot.progressPercent,
        )
    }
}

data class DesktopLibraryMaterialRow(
    val id: StudyMaterialId,
    val title: String,
    val description: String,
    val typeLabel: String,
    val progressPercent: Int,
    val favorite: Boolean,
    val tags: List<String>,
) {
    companion object {
        fun fromMaterial(material: StudyMaterial): DesktopLibraryMaterialRow = DesktopLibraryMaterialRow(
            id = material.id,
            title = material.title,
            description = material.description,
            typeLabel = material.type.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
            progressPercent = LibraryProgressCalculator.materialProgressPercent(material),
            favorite = material.favorite,
            tags = material.tags,
        )
    }
}

data class DesktopLibraryVideoRow(
    val title: String,
    val completed: Boolean,
    val progressLabel: String,
) {
    companion object {
        fun fromVideo(video: VideoResource): DesktopLibraryVideoRow = DesktopLibraryVideoRow(
            title = video.title,
            completed = video.completed,
            progressLabel = if (video.completed) "Complete" else "In progress",
        )
    }
}
