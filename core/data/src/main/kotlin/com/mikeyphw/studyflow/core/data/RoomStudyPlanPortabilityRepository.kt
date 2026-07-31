package com.mikeyphw.studyflow.core.data

import android.content.Context
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyPlanGraphPortabilityStore
import com.mikeyphw.studyflow.core.database.entity.PlanSnapshotEntity
import com.mikeyphw.studyflow.core.domain.StudyPlanPortabilityRepository
import com.mikeyphw.studyflow.core.model.PlanSnapshot
import com.mikeyphw.studyflow.core.model.PlanSnapshotId
import com.mikeyphw.studyflow.core.model.PlanSnapshotKind
import com.mikeyphw.studyflow.core.model.PlanSnapshotLabels
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportPreview
import com.mikeyphw.studyflow.core.portability.StudyPlanImportResult
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageCodec
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomStudyPlanPortabilityRepository(
    context: Context,
    private val database: StudyFlowDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val applicationVersion: String = "0.4.0-phase04",
    private val codec: StudyPlanPackageCodec = StudyPlanPackageCodec(),
) : StudyPlanPortabilityRepository {
    private val applicationFiles = context.applicationContext.filesDir
    private val graphStore = StudyPlanGraphPortabilityStore(database)
    private val supportDao = database.studySupportDao()

    override fun observeSnapshots(planId: StudyPlanId): Flow<List<PlanSnapshot>> =
        supportDao.observeSnapshots(planId.value).map { entities ->
            entities.map { it.asExternalModel() }
        }

    override suspend fun exportPlan(
        planId: StudyPlanId,
        options: StudyPlanExportOptions,
        output: OutputStream,
    ) {
        val payload = graphStore.exportPayload(
            planId = planId.value,
            options = options,
            applicationVersion = applicationVersion,
            exportedAtEpochMillis = clock(),
        )
        codec.write(payload, output)
    }

    override suspend fun previewImport(input: InputStream): StudyPlanImportPreview = codec.preview(input)

    override suspend fun importPlan(
        input: InputStream,
        strategy: StudyPlanImportStrategy,
        targetPlanId: StudyPlanId?,
    ): StudyPlanImportResult {
        val payload = codec.read(input)
        val destinationId = when (strategy) {
            StudyPlanImportStrategy.CREATE_AS_NEW -> StudyPlanId(UUID.randomUUID().toString())
            StudyPlanImportStrategy.REPLACE_EXISTING,
            StudyPlanImportStrategy.MERGE_COMPATIBLE,
            -> requireNotNull(targetPlanId) { "This import strategy requires a target Study Plan." }
        }

        val safetySnapshot = if (strategy == StudyPlanImportStrategy.CREATE_AS_NEW) {
            null
        } else {
            writeSnapshotFile(
                planId = destinationId,
                kind = PlanSnapshotKind.PRE_IMPORT,
                label = PlanSnapshotLabels.PRE_IMPORT,
            )
        }

        try {
            val importedRows = graphStore.importPayload(
                payload = payload,
                strategy = strategy,
                targetPlanId = destinationId.value,
                importedAtEpochMillis = clock(),
            )
            if (supportDao.findPlanSettings(destinationId.value) == null) {
                supportDao.upsertPlanSettings(StudyPlanSettings.defaults(destinationId).asEntity())
            }
            safetySnapshot?.let { supportDao.upsertSnapshots(listOf(it.asEntity())) }
            return StudyPlanImportResult(
                studyPlanId = destinationId.value,
                strategy = strategy,
                importedRows = importedRows,
                snapshotId = safetySnapshot?.id?.value,
            )
        } catch (error: Throwable) {
            safetySnapshot?.payloadReference?.let(::resolveSnapshotFile)?.delete()
            throw error
        }
    }

    override suspend fun createSnapshot(
        planId: StudyPlanId,
        kind: PlanSnapshotKind,
        label: String,
    ): PlanSnapshot = writeSnapshotFile(planId, kind, label).also { snapshot ->
        supportDao.upsertSnapshots(listOf(snapshot.asEntity()))
    }

    override suspend fun restoreSnapshot(snapshotId: PlanSnapshotId): StudyPlanImportResult {
        val stored = requireNotNull(supportDao.findSnapshot(snapshotId.value)) {
            "Snapshot ${snapshotId.value} does not exist."
        }
        val snapshot = stored.asExternalModel()
        val sourceFile = resolveSnapshotFile(snapshot.payloadReference)
        require(sourceFile.isFile) { "Snapshot package is missing from private storage." }
        require(sha256(sourceFile).equals(snapshot.checksumSha256, ignoreCase = true)) {
            "Snapshot checksum verification failed."
        }

        val safetySnapshot = writeSnapshotFile(
            planId = snapshot.studyPlanId,
            kind = PlanSnapshotKind.AUTOMATIC,
            label = PlanSnapshotLabels.PRE_RESTORE,
        )
        try {
            val payload = sourceFile.inputStream().use(codec::read)
            val importedRows = graphStore.importPayload(
                payload = payload,
                strategy = StudyPlanImportStrategy.REPLACE_EXISTING,
                targetPlanId = snapshot.studyPlanId.value,
                importedAtEpochMillis = clock(),
            )
            supportDao.upsertSnapshots(listOf(snapshot.asEntity(), safetySnapshot.asEntity()))
            return StudyPlanImportResult(
                studyPlanId = snapshot.studyPlanId.value,
                strategy = StudyPlanImportStrategy.REPLACE_EXISTING,
                importedRows = importedRows,
                snapshotId = safetySnapshot.id.value,
            )
        } catch (error: Throwable) {
            resolveSnapshotFile(safetySnapshot.payloadReference).delete()
            throw error
        }
    }

    override suspend fun deleteSnapshot(snapshotId: PlanSnapshotId) {
        val snapshot = supportDao.findSnapshot(snapshotId.value) ?: return
        resolveSnapshotFile(snapshot.payloadReference).delete()
        supportDao.deleteSnapshot(snapshotId.value)
    }

    private suspend fun writeSnapshotFile(
        planId: StudyPlanId,
        kind: PlanSnapshotKind,
        label: String,
    ): PlanSnapshot {
        val id = PlanSnapshotId(UUID.randomUUID().toString())
        val directory = File(applicationFiles, "study-plans/${planId.value}/snapshots").apply { mkdirs() }
        val destination = File(directory, "${id.value}.studyplan")
        val temporary = File(directory, ".${id.value}.tmp")
        val payload = graphStore.exportPayload(
            planId = planId.value,
            options = StudyPlanExportOptions(StudyPlanExportMode.FULL, includeAnnexMetadata = true),
            applicationVersion = applicationVersion,
            exportedAtEpochMillis = clock(),
        )
        try {
            temporary.outputStream().use { codec.write(payload, it) }
            check(temporary.renameTo(destination)) { "Could not commit the Study Plan snapshot file." }
        } catch (error: Throwable) {
            temporary.delete()
            destination.delete()
            throw error
        }
        return PlanSnapshot(
            id = id,
            studyPlanId = planId,
            kind = kind,
            label = label,
            schemaVersion = payload.manifest.schemaVersion,
            payloadReference = destination.relativeTo(applicationFiles).invariantSeparatorsPath,
            checksumSha256 = sha256(destination),
            createdAtEpochMillis = clock(),
        )
    }

    private fun resolveSnapshotFile(reference: String): File {
        val candidate = File(applicationFiles, reference).canonicalFile
        require(candidate.path.startsWith(applicationFiles.canonicalPath + File.separator)) {
            "Unsafe snapshot path."
        }
        return candidate
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun PlanSnapshotEntity.asExternalModel() = PlanSnapshot(
        id = PlanSnapshotId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        kind = PlanSnapshotKind.valueOf(kind),
        label = label,
        schemaVersion = schemaVersion,
        payloadReference = payloadReference,
        checksumSha256 = checksumSha256,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    private fun PlanSnapshot.asEntity() = PlanSnapshotEntity(
        id = id.value,
        studyPlanId = studyPlanId.value,
        kind = kind.name,
        label = label,
        schemaVersion = schemaVersion,
        payloadReference = payloadReference,
        checksumSha256 = checksumSha256,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
