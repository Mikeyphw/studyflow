package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

private const val DesktopContentPrefix = "content://studyflow-desktop/"
private const val MaxTextPreviewBytes = 64 * 1024

enum class DesktopMaterialViewerKind { EMPTY, INLINE, TEXT, IMAGE, PDF, BINARY, WEB, MISSING }

data class DesktopMaterialViewerState(
    val material: StudyMaterial?,
    val kind: DesktopMaterialViewerKind,
    val content: String,
    val detail: String = "",
) {
    companion object {
        fun empty(): DesktopMaterialViewerState = DesktopMaterialViewerState(
            material = null,
            kind = DesktopMaterialViewerKind.EMPTY,
            content = "Select a material.",
        )
    }
}

class DesktopLibraryWorkspaceController(
    private val services: DesktopStudyFlowServices,
    private val workspaceRoot: Path?,
) {
    suspend fun importLocalFile(rawPath: String): StudyMaterial {
        val planId = requireActivePlan()
        val root = requireNotNull(workspaceRoot) { "Persistent Desktop workspace is required for file import." }
        val source = Path.of(rawPath.trim()).toAbsolutePath().normalize()
        require(Files.isRegularFile(source)) { "Select an existing local file." }
        val id = StudyMaterialId(UUID.randomUUID().toString())
        val directory = root.resolve("library-files").resolve(id.value).normalize()
        require(directory.startsWith(root.toAbsolutePath().normalize())) { "Invalid library destination." }
        Files.createDirectories(directory)
        val target = directory.resolve(source.fileName.toString())
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        val type = inferMaterialType(target)
        return try {
            services.manageStudyLibrary.createMaterial(
                id = id,
                studyPlanId = planId,
                subjectId = null,
                topicId = null,
                type = type,
                title = source.fileName.toString(),
                description = "Desktop imported copy • SHA-256 ${sha256(target).take(12)}",
                sourceUri = toContentUri(root, target),
                author = null,
                totalUnits = null,
                tags = listOf("desktop-import", type.name.lowercase()),
            )
        } catch (error: Throwable) {
            removeDesktopImportedLibraryFiles(root, listOf(id))
            throw error
        }
    }

    suspend fun addWebResource(title: String, rawUrl: String): StudyMaterial {
        val planId = requireActivePlan()
        val url = rawUrl.trim()
        require(url.startsWith("https://") || url.startsWith("http://")) { "Enter an HTTP(S) URL." }
        return services.manageStudyLibrary.createMaterial(
            id = StudyMaterialId(UUID.randomUUID().toString()),
            studyPlanId = planId,
            subjectId = null,
            topicId = null,
            type = StudyMaterialType.WEBSITE,
            title = title.trim().ifBlank { url },
            description = "Desktop web reference",
            sourceUri = url,
            author = null,
            totalUnits = null,
            tags = listOf("desktop-web"),
        )
    }

    suspend fun toggleFavorite(materialId: StudyMaterialId): StudyMaterial = services.manageStudyLibrary.toggleFavorite(
        requireActivePlan(),
        materialId,
    )

    fun externalTarget(materialId: StudyMaterialId?): String? {
        val planId = services.defaultPlanId ?: return null
        val material = materialId?.let { id ->
            services.libraryRepository.currentMaterials(planId).firstOrNull { it.id == id }
        } ?: return null
        val uri = material.sourceUri.orEmpty()
        if (uri.startsWith(DesktopContentPrefix) && workspaceRoot != null) {
            return resolveContentUri(workspaceRoot, uri)?.toAbsolutePath()?.normalize()?.toString()
        }
        return uri.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    fun viewerState(materialId: StudyMaterialId?): DesktopMaterialViewerState {
        val planId = services.defaultPlanId ?: return DesktopMaterialViewerState.empty()
        val material = materialId?.let { id ->
            services.libraryRepository.currentMaterials(planId).firstOrNull { it.id == id }
        }
            ?: services.libraryRepository.currentMaterials(planId).firstOrNull { !it.archived }
            ?: return DesktopMaterialViewerState.empty()
        val uri = material.sourceUri.orEmpty()
        if (uri.startsWith(DesktopContentPrefix) && workspaceRoot != null) {
            val path = resolveContentUri(workspaceRoot, uri)
                ?: return DesktopMaterialViewerState(
                    material,
                    DesktopMaterialViewerKind.MISSING,
                    "Imported file URI is invalid.",
                )
            if (!Files.isRegularFile(path)) {
                return DesktopMaterialViewerState(
                    material,
                    DesktopMaterialViewerKind.MISSING,
                    "Imported file is missing.",
                )
            }
            return inspectLocal(material, path)
        }
        if (uri.startsWith("https://") || uri.startsWith("http://")) {
            return DesktopMaterialViewerState(
                material,
                DesktopMaterialViewerKind.WEB,
                uri,
                "Persistent web reference; native browser opening is deferred to DOV23.",
            )
        }
        return DesktopMaterialViewerState(
            material,
            DesktopMaterialViewerKind.INLINE,
            material.description.ifBlank { "No inline material content." },
        )
    }

    private fun inspectLocal(material: StudyMaterial, path: Path): DesktopMaterialViewerState {
        val extension = path.fileName.toString().substringAfterLast('.', "").lowercase()
        if (extension in textExtensions) {
            val size = Files.size(path)
            val bytes = Files.newInputStream(path).use { it.readNBytes(MaxTextPreviewBytes) }
            val text = String(bytes, StandardCharsets.UTF_8)
            val suffix = if (size > MaxTextPreviewBytes) "\n\n[Preview truncated at 64 KiB]" else ""
            return DesktopMaterialViewerState(material, DesktopMaterialViewerKind.TEXT, text + suffix, "$size bytes")
        }
        if (extension in imageExtensions) {
            val image: BufferedImage? = runCatching { ImageIO.read(path.toFile()) }.getOrNull()
            val detail = if (image != null) {
                "${image.width} × ${image.height} • ${Files.size(path)} bytes"
            } else {
                "Unreadable image"
            }
            return DesktopMaterialViewerState(
                material,
                DesktopMaterialViewerKind.IMAGE,
                path.fileName.toString(),
                detail,
            )
        }
        if (extension == "pdf") {
            val pages = countPdfPageTokens(path).coerceAtLeast(1)
            return DesktopMaterialViewerState(
                material,
                DesktopMaterialViewerKind.PDF,
                "PDF document • approximately $pages page(s)",
                "${Files.size(path)} bytes • in-app metadata inspection",
            )
        }
        return DesktopMaterialViewerState(
            material,
            DesktopMaterialViewerKind.BINARY,
            path.fileName.toString(),
            "${Files.size(path)} bytes • binary local material",
        )
    }

    private fun requireActivePlan() = requireNotNull(services.currentDefaultPlanForDesktop()) {
        "No Study Plan is selected."
    }.also { plan ->
        require(plan.status == StudyPlanStatus.ACTIVE) { "Only active Study Plans can change the Library." }
    }.id

    companion object {
        private val textExtensions = setOf(
            "txt", "md", "csv", "json", "xml", "html", "htm", "kt", "java", "py", "js", "ts",
        )
        private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

        fun toContentUri(root: Path, path: Path): String {
            val normalizedRoot = root.toAbsolutePath().normalize()
            val normalizedPath = path.toAbsolutePath().normalize()
            require(normalizedPath.startsWith(normalizedRoot)) {
                "Imported file must stay inside the Desktop workspace."
            }
            val relative = normalizedRoot.relativize(normalizedPath).toString()
            val token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(relative.toByteArray(StandardCharsets.UTF_8))
            return "${DesktopContentPrefix}file/$token"
        }

        fun resolveContentUri(root: Path, uri: String): Path? {
            if (!uri.startsWith("${DesktopContentPrefix}file/")) return null
            val token = uri.removePrefix("${DesktopContentPrefix}file/")
            val relative = runCatching {
                String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8)
            }.getOrNull() ?: return null
            val normalizedRoot = root.toAbsolutePath().normalize()
            return normalizedRoot.resolve(relative).normalize().takeIf { it.startsWith(normalizedRoot) }
        }

        private fun inferMaterialType(path: Path): StudyMaterialType = when (
            path.fileName.toString().substringAfterLast('.', "").lowercase()
        ) {
            "pdf" -> StudyMaterialType.PDF
            "mp4", "mkv", "webm", "avi", "mov" -> StudyMaterialType.VIDEO
            else -> StudyMaterialType.LOCAL_FILE
        }

        private fun sha256(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun countPdfPageTokens(path: Path): Int {
            val token = "/Type /Page".toByteArray(StandardCharsets.US_ASCII)
            var matched = 0
            var count = 0
            Files.newInputStream(path).use { input ->
                while (true) {
                    val value = input.read()
                    if (value < 0) break
                    if (value.toByte() == token[matched]) {
                        matched += 1
                        if (matched == token.size) {
                            count += 1
                            matched = 0
                        }
                    } else {
                        matched = if (value.toByte() == token[0]) 1 else 0
                    }
                }
            }
            return count
        }
    }
}
