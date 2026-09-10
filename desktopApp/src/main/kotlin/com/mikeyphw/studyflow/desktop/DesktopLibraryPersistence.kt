package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import com.mikeyphw.studyflow.core.model.VideoSourceType
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Comparator
import java.util.Properties

private const val LibrarySchemaVersion = 1
private const val LibraryFileName = "library-v1.properties"
private const val LibraryNull = "~"

data class DesktopLibrarySnapshot(
    val materials: List<StudyMaterial>,
    val videos: List<VideoResource>,
    val annexes: List<Annex>,
)

class DesktopLibraryPersistence private constructor(
    val root: Path,
    private val file: Path,
    initial: DesktopLibrarySnapshot,
) {
    private var snapshot = initial
    fun current(): DesktopLibrarySnapshot = snapshot

    @Synchronized
    fun update(next: DesktopLibrarySnapshot) {
        writeAtomically(next)
        snapshot = next
    }

    private fun writeAtomically(value: DesktopLibrarySnapshot) {
        Files.createDirectories(root)
        val temporary = Files.createTempFile(root, ".studyflow-library-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { LibraryPropertiesCodec.write(it, value) }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        fun open(root: Path, seed: DesktopLibrarySnapshot): DesktopLibraryPersistence {
            val normalized = root.toAbsolutePath().normalize()
            Files.createDirectories(normalized)
            val file = normalized.resolve(LibraryFileName)
            val initial = if (Files.isRegularFile(file)) {
                Files.newInputStream(file).use(LibraryPropertiesCodec::read)
            } else seed
            return DesktopLibraryPersistence(normalized, file, initial).also {
                if (!Files.isRegularFile(file)) it.writeAtomically(initial)
            }
        }
    }
}

fun removeDesktopImportedLibraryFiles(root: Path, materialIds: List<StudyMaterialId>) {
    val filesRoot = root.resolve("library-files").toAbsolutePath().normalize()
    materialIds.forEach { materialId ->
        val directory = filesRoot.resolve(materialId.value).normalize()
        if (!directory.startsWith(filesRoot) || !Files.exists(directory)) return@forEach
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}

private object LibraryPropertiesCodec {
    fun write(output: OutputStream, snapshot: DesktopLibrarySnapshot) {
        val props = Properties()
        props["schema.version"] = LibrarySchemaVersion.toString()
        props["material.count"] = snapshot.materials.size.toString()
        snapshot.materials.forEachIndexed { i, v -> props["material.$i"] = encodeMaterial(v) }
        props["video.count"] = snapshot.videos.size.toString()
        snapshot.videos.forEachIndexed { i, v -> props["video.$i"] = encodeVideo(v) }
        props["annex.count"] = snapshot.annexes.size.toString()
        snapshot.annexes.forEachIndexed { i, v -> props["annex.$i"] = encodeAnnex(v) }
        props.store(output, "StudyFlow Desktop library schema v$LibrarySchemaVersion")
    }

    fun read(input: InputStream): DesktopLibrarySnapshot {
        val props = Properties().apply { load(input) }
        require(props.required("schema.version").toInt() == LibrarySchemaVersion) {
            "Unsupported Desktop library schema."
        }
        return DesktopLibrarySnapshot(
            materials = List(props.required("material.count").toInt()) {
                decodeMaterial(props.required("material.$it"))
            },
            videos = List(props.required("video.count").toInt()) { decodeVideo(props.required("video.$it")) },
            annexes = List(props.required("annex.count").toInt()) { decodeAnnex(props.required("annex.$it")) },
        )
    }

    private fun encodeMaterial(v: StudyMaterial) = fields(
        v.id.value, v.studyPlanId.value, v.subjectId?.value, v.topicId?.value, v.type.name, v.title, v.description,
        v.sourceUri, v.author, v.totalUnits?.toString(), v.completedUnits.toString(), v.primary.toString(),
        v.favorite.toString(), v.archived.toString(), v.tags.joinToString("\u001f"),
        v.createdAtEpochMillis.toString(), v.updatedAtEpochMillis.toString(),
    )
    private fun decodeMaterial(value: String): StudyMaterial {
        val f=split(value,17)
        return StudyMaterial(
            id=StudyMaterialId(f[0]!!), studyPlanId=StudyPlanId(f[1]!!), subjectId=f[2]?.let(::SubjectId),
            topicId=f[3]?.let(::TopicId), type=StudyMaterialType.valueOf(f[4]!!), title=f[5]!!,
            description=f[6]!!, sourceUri=f[7], author=f[8], totalUnits=f[9]?.toInt(),
            completedUnits=f[10]!!.toInt(), primary=f[11]!!.toBooleanStrict(), favorite=f[12]!!.toBooleanStrict(),
            archived=f[13]!!.toBooleanStrict(), tags=splitList(f[14]), createdAtEpochMillis=f[15]!!.toLong(),
            updatedAtEpochMillis=f[16]!!.toLong(),
        )
    }

    private fun encodeVideo(v: VideoResource) = fields(
        v.id.value, v.studyPlanId.value, v.subjectId?.value, v.topicId?.value, v.title, v.sourceType.name, v.sourceUri,
        v.canonicalSourceId, v.thumbnailUri, v.durationMillis?.toString(), v.playbackPositionMillis.toString(),
        v.completed.toString(), v.createdAtEpochMillis.toString(), v.updatedAtEpochMillis.toString(),
    )
    private fun decodeVideo(value: String): VideoResource {
        val f=split(value,14)
        return VideoResource(
            id=VideoResourceId(f[0]!!), studyPlanId=StudyPlanId(f[1]!!), subjectId=f[2]?.let(::SubjectId),
            topicId=f[3]?.let(::TopicId), title=f[4]!!, sourceType=VideoSourceType.valueOf(f[5]!!), sourceUri=f[6]!!,
            canonicalSourceId=f[7], thumbnailUri=f[8], durationMillis=f[9]?.toLong(),
            playbackPositionMillis=f[10]!!.toLong(), completed=f[11]!!.toBooleanStrict(),
            createdAtEpochMillis=f[12]!!.toLong(), updatedAtEpochMillis=f[13]!!.toLong(),
        )
    }

    private fun encodeAnnex(v: Annex) = fields(
        v.id.value, v.studyPlanId.value, v.subjectId?.value, v.topicId?.value, v.materialId?.value, v.displayName,
        v.mimeType, v.byteSize.toString(), v.checksumSha256, v.storageMode.name, v.storageReference,
        v.persistedPermission.toString(), v.createdAtEpochMillis.toString(), v.updatedAtEpochMillis.toString(),
    )
    private fun decodeAnnex(value: String): Annex {
        val f=split(value,14)
        return Annex(
            id=AnnexId(f[0]!!), studyPlanId=StudyPlanId(f[1]!!), subjectId=f[2]?.let(::SubjectId),
            topicId=f[3]?.let(::TopicId), materialId=f[4]?.let(::StudyMaterialId), displayName=f[5]!!,
            mimeType=f[6]!!, byteSize=f[7]!!.toLong(), checksumSha256=f[8],
            storageMode=AnnexStorageMode.valueOf(f[9]!!), storageReference=f[10]!!,
            persistedPermission=f[11]!!.toBooleanStrict(), createdAtEpochMillis=f[12]!!.toLong(),
            updatedAtEpochMillis=f[13]!!.toLong(),
        )
    }

    private fun fields(vararg values: String?): String = values.joinToString(".") { it?.let(::encode) ?: LibraryNull }
    private fun split(value: String, expected: Int): List<String?> = value.split('.').also {
        require(it.size == expected) { "Corrupt Desktop library record." }
    }.map { if (it == LibraryNull) null else decode(it) }
    private fun splitList(value: String?): List<String> = value.orEmpty().split('\u001f').filter(String::isNotEmpty)
    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    private fun Properties.required(key: String): String = requireNotNull(getProperty(key)) { "Missing $key" }
}
