package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.ExamStageType
import com.mikeyphw.studyflow.core.model.LegislationDocument
import com.mikeyphw.studyflow.core.model.LegislationDocumentId
import com.mikeyphw.studyflow.core.model.LegislationProvision
import com.mikeyphw.studyflow.core.model.LegislationProvisionId
import com.mikeyphw.studyflow.core.model.LegislationProvisionKind
import com.mikeyphw.studyflow.core.model.ProgrammingCourseLevel
import com.mikeyphw.studyflow.core.model.ProgrammingCourseProfile
import com.mikeyphw.studyflow.core.model.ProgrammingCourseProfileId
import com.mikeyphw.studyflow.core.model.ProgrammingLanguage
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyCycle
import com.mikeyphw.studyflow.core.model.StudyCycleId
import com.mikeyphw.studyflow.core.model.StudyCycleItem
import com.mikeyphw.studyflow.core.model.StudyCycleItemId
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyStrategy
import com.mikeyphw.studyflow.core.model.StudyStrategyId
import com.mikeyphw.studyflow.core.model.StudyStrategyType
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.portability.PortableRow
import com.mikeyphw.studyflow.core.portability.PortableTable
import com.mikeyphw.studyflow.core.portability.PortableValue
import com.mikeyphw.studyflow.core.portability.PortableValueType
import com.mikeyphw.studyflow.core.portability.StudyPlanEncryptedPackageCodec
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageCodec
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageManifest
import com.mikeyphw.studyflow.core.portability.StudyPlanPackagePayload
import com.mikeyphw.studyflow.core.portability.StudyPlanPrivacyPolicy
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

/** DOV20 Desktop portability: cross-client structure packages plus full encrypted workspace backups. */
class DesktopPortabilityController(
    private val services: DesktopStudyFlowServices,
    private val root: Path?,
    private val packageCodec: StudyPlanPackageCodec = StudyPlanPackageCodec(),
    private val encryptedCodec: StudyPlanEncryptedPackageCodec = StudyPlanEncryptedPackageCodec(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun exportPortableStructure(destination: Path): Path {
        val planId = requirePlanId()
        val payload = buildPortableStructure(planId)
        destination.toAbsolutePath().parent?.let(Files::createDirectories)
        Files.newOutputStream(destination).use { packageCodec.write(payload, it) }
        return destination
    }

    fun previewPortableStructure(source: Path): DesktopPortableStructurePreview {
        Files.newInputStream(source).use { input ->
            val payload = packageCodec.read(input)
            require(payload.manifest.exportMode == StudyPlanExportMode.STRUCTURE_ONLY) {
                "Desktop DOV20 imports structure-only .studyplan packages through this workflow."
            }
            return DesktopPortableStructurePreview(
                planName = payload.manifest.displayName,
                sourcePlanId = payload.manifest.sourceStudyPlanId,
                tableCount = payload.tables.size,
                rowCount = payload.tables.sumOf { it.rows.size },
            )
        }
    }

    fun importPortableStructure(source: Path): StudyPlanId = runBlocking {
        val payload = Files.newInputStream(source).use(packageCodec::read)
        require(payload.manifest.exportMode == StudyPlanExportMode.STRUCTURE_ONLY) {
            "Desktop DOV20 imports structure-only .studyplan packages through this workflow."
        }
        importStructurePayload(payload)
    }

    fun createEncryptedWorkspaceBackup(destination: Path, password: CharArray): Path =
        services.atomicDesktopMutation("point-in-time encrypted Desktop backup") {
            val workspace = requireRoot().toRealPath(LinkOption.NOFOLLOW_LINKS)
            requireWorkspaceTreeHasNoSymlinks(workspace)
            val normalizedDestination = destination.toAbsolutePath().normalize()
            val destinationParent = requireNotNull(normalizedDestination.parent) {
                "Encrypted Desktop backup destination must have a parent directory."
            }
            Files.createDirectories(destinationParent)
            requireExternalPath(workspace, destinationParent, normalizedDestination, "backup destination")
            val snapshotRoot = Files.createTempDirectory(destinationParent, ".studyflow-backup-snapshot-")
            try {
                val snapshotFiles = createPointInTimeWorkspaceSnapshot(workspace, snapshotRoot, workspaceBackupFiles(workspace))
                try {
                    Files.newOutputStream(normalizedDestination).use { rawOutput ->
                        val boundedOutput = BoundedBackupOutputStream(rawOutput, MAX_BACKUP_BYTES)
                        encryptedCodec.encrypt(boundedOutput, password) { encryptedOutput ->
                            ZipOutputStream(encryptedOutput.buffered()).use { zip ->
                                writeBackupManifest(zip)
                                snapshotFiles.forEach { file -> writeWorkspaceFile(zip, snapshotRoot, file) }
                            }
                        }
                    }
                } catch (error: Throwable) {
                    Files.deleteIfExists(normalizedDestination)
                    throw error
                }
                require(Files.size(normalizedDestination) <= MAX_BACKUP_BYTES) {
                    "Encrypted Desktop backup exceeds the restore size limit."
                }
                normalizedDestination
            } finally {
                deleteTree(snapshotRoot)
            }
        }

    fun restoreEncryptedWorkspaceBackup(source: Path, password: CharArray): DesktopWorkspaceRestoreResult {
        val workspace = requireRoot().toRealPath(LinkOption.NOFOLLOW_LINKS)
        requireWorkspaceTreeHasNoSymlinks(workspace)
        val sourceFile = source.toAbsolutePath().normalize()
        require(Files.exists(sourceFile, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(sourceFile)) {
            "Restore source must be a real regular file, not a symbolic link."
        }
        require(Files.isRegularFile(sourceFile, LinkOption.NOFOLLOW_LINKS)) {
            "Restore source must be a regular backup file."
        }
        requireExternalPath(workspace, requireNotNull(sourceFile.parent), sourceFile, "restore source")
        require(Files.size(sourceFile) <= MAX_BACKUP_BYTES) {
            "Desktop backup exceeds the supported size."
        }
        val parent = workspace.parent ?: error("Desktop workspace has no parent directory.")
        val staging = Files.createTempDirectory(parent, ".studyflow-restore-")
        val safety = Files.createTempDirectory(parent, ".studyflow-safety-")
        val plain = Files.createTempFile(parent, ".studyflow-decrypted-", ".zip")
        try {
            Files.newInputStream(sourceFile).use { input ->
                Files.newOutputStream(plain).use { output ->
                    encryptedCodec.decrypt(input, output, password, MAX_BACKUP_BYTES)
                }
            }
            extractWorkspaceBackup(plain, staging)
            validateStagedWorkspace(staging)
            validateAuxiliaryStagedStores(staging)
            val restoredFileCount = regularFileCount(staging)
            copyTree(workspace, safety)
            try {
                replaceTree(workspace, staging)
                services.reloadFromDiskAfterRestore()
            } catch (error: Throwable) {
                replaceTree(workspace, safety)
                services.reloadFromDiskAfterRestore()
                throw error
            }
            return DesktopWorkspaceRestoreResult(
                restoredFiles = restoredFileCount,
                restartRequired = false,
                safetySnapshotCreated = true,
            )
        } finally {
            deleteTree(staging)
            deleteTree(safety)
            Files.deleteIfExists(plain)
        }
    }

    fun migrateWorkspaceSchema(): Int {
        val persistence = requireNotNull(services.workspacePersistence) {
            "Workspace migration requires the persistent Desktop composition root."
        }
        persistence.rewriteCurrentSchema()
        return DesktopWorkspacePersistence.CURRENT_SCHEMA_VERSION
    }

    private fun buildPortableStructure(planId: StudyPlanId): StudyPlanPackagePayload {
        val plan = requireNotNull(services.planRepository.currentPlans().firstOrNull { it.id == planId })
        val content = services.contentRepository.currentState()
        val publicExam = services.publicExamRepository.current()
        val programming = services.contentModeRepository.programmingProfile(planId)
        val settings = services.planRepository.currentSettings()[planId] ?: StudyPlanSettings.defaults(planId)
        val options = StudyPlanExportOptions(
            mode = StudyPlanExportMode.STRUCTURE_ONLY,
            includeAnnexMetadata = false,
            includeAnnexPayloads = false,
        )
        val rootRow = StudyPlanPrivacyPolicy.sanitizeRoot(plan.toPortableRow(), options)
        val tables = listOf(
            PortableTable("study_plans", listOf(rootRow)),
            PortableTable("study_targets", content.targets.filterPlan(planId).map { it.toPortableRow() }),
            PortableTable("exam_stages", content.examStages.filterPlan(planId).map { it.toPortableRow() }),
            PortableTable("subjects", content.subjects.filterPlan(planId).map { it.toPortableRow() }),
            PortableTable("topics", content.topics.filterPlan(planId).map { it.toPortableRow() }),
            PortableTable(
                "legislation_documents",
                publicExam.legislationDocuments.filterPlan(planId).map { it.toPortableRow() },
            ),
            PortableTable(
                "legislation_provisions",
                publicExam.legislationProvisions.filterPlan(planId).map { it.toPortableRow() },
            ),
            PortableTable("study_cycles", publicExam.studyCycles.filterPlan(planId).map { it.toPortableRow() }),
            PortableTable(
                "study_cycle_items",
                publicExam.studyCycleItems.filterPlan(planId).map { it.toPortableRow() },
            ),
            PortableTable(
                "study_strategies",
                services.scheduleRepository.currentStrategies().filterPlan(planId).map { it.toPortableRow() },
            ),
            PortableTable(
                "schedule_rules",
                services.scheduleRepository.currentRules(planId).map { it.toPortableRow() },
            ),
            PortableTable("study_plan_settings", listOf(settings.toPortableRow())),
            PortableTable(
                "programming_course_profiles",
                programming?.let { listOf(it.toPortableRow()) }.orEmpty(),
            ),
            PortableTable("termux_runtime_preferences", emptyList()),
        ).map { table ->
            if (table.name == "study_plans") table else table.copy(
                rows = table.rows.map { row -> StudyPlanPrivacyPolicy.sanitizeRow(table.name, row, options) },
            )
        }
        return StudyPlanPackagePayload(
            manifest = StudyPlanPackageManifest(
                applicationVersion = "desktop-dov20",
                exportedAtEpochMillis = clock(),
                sourceStudyPlanId = plan.id.value,
                displayName = plan.name,
                exportMode = StudyPlanExportMode.STRUCTURE_ONLY,
                includesPrivateHistory = false,
                includesAnnexMetadata = false,
                includesAnnexPayloads = false,
                tableRowCounts = tables.associate { it.name to it.rows.size },
                redactedSections = StudyPlanPrivacyPolicy.redactedSections(options),
            ),
            tables = tables,
        )
    }

    private suspend fun importStructurePayload(payload: StudyPlanPackagePayload): StudyPlanId {
        val tables = payload.tables.associateBy(PortableTable::name)
        val rootRow = requireNotNull(tables["study_plans"]?.rows?.singleOrNull()) {
            "Portable Study Plan package has no root Study Plan."
        }
        val plan = rootRow.toStudyPlan()
        require(services.planRepository.currentPlans().none { it.id == plan.id }) {
            "Study Plan ${plan.id.value} already exists. Import into a workspace without that identifier."
        }
        val settings = tables["study_plan_settings"]?.rows?.singleOrNull()?.toStudyPlanSettings(plan.id)
            ?: StudyPlanSettings.defaults(plan.id)
        services.planRepository.create(plan, settings)
        try {
            tables.rows("study_targets").map { it.toStudyTarget() }
                .forEach { services.contentRepository.upsertStudyTarget(it) }
            tables.rows("exam_stages").map { it.toExamStage() }
                .forEach { services.contentRepository.upsertExamStage(it) }
            tables.rows("subjects").map { it.toSubject() }
                .sortedBy(Subject::position)
                .forEach { services.contentRepository.upsertSubject(it) }
            tables.rows("topics").map { it.toTopic() }
                .sortedBy(Topic::position)
                .forEach { services.contentRepository.upsertTopic(it) }
            tables.rows("study_strategies").map { it.toStudyStrategy() }
                .forEach { services.scheduleRepository.upsertStrategy(it) }
            tables.rows("schedule_rules").map { it.toScheduleRule() }
                .sortedBy(ScheduleRule::position)
                .forEach { services.scheduleRepository.upsertScheduleRule(it) }
            val documents = tables.rows("legislation_documents").map { it.toLegislationDocument() }
            val provisions = tables.rows("legislation_provisions").map { it.toLegislationProvision() }
            val cycles = tables.rows("study_cycles").map { it.toStudyCycle() }
            val cycleItems = tables.rows("study_cycle_items").map { it.toStudyCycleItem() }
            services.publicExamRepository.importPortableStructure(plan.id, documents, provisions, cycles, cycleItems)
            tables.rows("programming_course_profiles").singleOrNull()?.let { row ->
                services.contentModeRepository.upsertProgramming(row.toProgrammingProfile())
            }
            return plan.id
        } catch (error: Throwable) {
            services.scheduleRepository.purgePlan(plan.id)
            services.contentRepository.purgePlan(plan.id)
            services.publicExamRepository.purgePlan(plan.id)
            services.contentModeRepository.purgePlan(plan.id)
            services.planRepository.deletePermanently(plan.id)
            throw error
        }
    }

    private fun writeBackupManifest(zip: ZipOutputStream) {
        val properties = Properties().apply {
            setProperty("format", BACKUP_FORMAT)
            setProperty("schema.version", BACKUP_SCHEMA_VERSION.toString())
            setProperty("created.at", clock().toString())
        }
        val bytes = ByteArrayOutputStream()
            .also { properties.store(it, "StudyFlow Desktop encrypted backup") }
            .toByteArray()
        zip.putNextEntry(ZipEntry(BACKUP_MANIFEST).apply { time = 0L })
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeWorkspaceFile(zip: ZipOutputStream, workspace: Path, file: Path) {
        require(!Files.isSymbolicLink(file) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "Workspace backups refuse symbolic links and non-regular files: $file"
        }
        val relative = workspace.relativize(file).normalize()
        require(!relative.startsWith("..")) { "Workspace backup path escaped its root." }
        val size = Files.size(file)
        require(size <= MAX_ENTRY_BYTES) { "Workspace file is too large to back up: $relative" }
        zip.putNextEntry(ZipEntry("workspace/${relative.toString().replace('\\', '/')}").apply { time = 0L })
        Files.newInputStream(file).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun workspaceBackupFiles(workspace: Path): List<Path> {
        var total = 0L
        return Files.walk(workspace).use { paths ->
            paths.sorted().map { path ->
                require(!Files.isSymbolicLink(path)) {
                    "Desktop backup refuses symbolic links inside the workspace: ${workspace.relativize(path)}"
                }
                path
            }.filter { path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) }
                .peek { file ->
                    val size = Files.size(file)
                    require(size <= MAX_ENTRY_BYTES) {
                        "Workspace file is too large to back up: ${workspace.relativize(file)}"
                    }
                    require(total <= MAX_BACKUP_PAYLOAD_BYTES - size) {
                        "Desktop workspace is too large to create a restorable encrypted backup."
                    }
                    total += size
                }.toList()
        }
    }

    private fun requireWorkspaceTreeHasNoSymlinks(workspace: Path) {
        Files.walk(workspace).use { paths ->
            paths.forEach { path ->
                require(!Files.isSymbolicLink(path)) {
                    "Desktop workspace contains a symbolic link and cannot be backed up safely: " +
                        workspace.relativize(path)
                }
            }
        }
    }

    private fun requireExternalPath(workspace: Path, parent: Path, candidate: Path, label: String) {
        val realWorkspace = workspace.toRealPath(LinkOption.NOFOLLOW_LINKS)
        val realParent = parent.toRealPath(LinkOption.NOFOLLOW_LINKS)
        require(!realParent.startsWith(realWorkspace)) {
            "Desktop $label must live outside the active StudyFlow workspace."
        }
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(candidate)) { "Desktop $label cannot be a symbolic link." }
            val realCandidate = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS)
            require(!realCandidate.startsWith(realWorkspace)) {
                "Desktop $label must live outside the active StudyFlow workspace."
            }
        }
    }

    private fun extractWorkspaceBackup(archive: Path, staging: Path) {
        var sawManifest = false
        var total = 0L
        ZipInputStream(Files.newInputStream(archive).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(!name.startsWith('/') && ".." !in name.split('/', '\\')) {
                    "Unsafe Desktop backup entry: $name"
                }
                if (name == BACKUP_MANIFEST) {
                    val bytes = zip.readBytesBounded(MAX_MANIFEST_BYTES)
                    val props = Properties().apply { bytes.inputStream().use(::load) }
                    require(props.getProperty("format") == BACKUP_FORMAT) { "Not a StudyFlow Desktop backup." }
                    require(props.getProperty("schema.version")?.toIntOrNull() == BACKUP_SCHEMA_VERSION) {
                        "Unsupported Desktop backup schema."
                    }
                    sawManifest = true
                } else if (name.startsWith("workspace/") && !entry.isDirectory) {
                    val relative = name.removePrefix("workspace/")
                    val target = staging.resolve(relative).normalize()
                    require(target.startsWith(staging)) { "Desktop backup entry escaped the staging directory." }
                    target.parent?.let(Files::createDirectories)
                    Files.newOutputStream(target).use { output ->
                        total += zip.copyBounded(output, MAX_ENTRY_BYTES)
                        require(total <= MAX_BACKUP_BYTES) { "Desktop backup exceeds the supported size." }
                    }
                }
                zip.closeEntry()
            }
        }
        require(sawManifest) { "Desktop backup manifest is missing." }
    }

    private fun validateStagedWorkspace(staging: Path) {
        require(Files.isRegularFile(staging.resolve("workspace-v1.properties"))) {
            "Desktop backup does not contain workspace state."
        }
        DesktopWorkspacePersistence.open(
            root = staging,
            seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create()),
        ).use { it.current() }
        Files.deleteIfExists(staging.resolve(".studyflow-workspace.lock"))
    }

    private fun validateAuxiliaryStagedStores(staging: Path) {
        val sample = DesktopStudyFlowSampleData.create()
        if (Files.isRegularFile(staging.resolve("library-v1.properties"), LinkOption.NOFOLLOW_LINKS)) {
            DesktopLibraryPersistence.open(
                staging,
                DesktopLibrarySnapshot(sample.materials, sample.videos, sample.annexes),
            ).current()
        }
        if (Files.isRegularFile(staging.resolve("practice-v1.properties"), LinkOption.NOFOLLOW_LINKS)) {
            DesktopPracticePersistence.open(
                staging,
                DesktopPracticeSnapshot(sample.questions, sample.tests, sample.mockExams),
            ).current()
        }
        if (Files.isRegularFile(staging.resolve("reviews-v1.properties"), LinkOption.NOFOLLOW_LINKS)) {
            DesktopReviewPersistence.open(staging).current()
        }
        if (Files.isRegularFile(staging.resolve("content-modes-v1.properties"), LinkOption.NOFOLLOW_LINKS)) {
            DesktopContentModePersistence.open(staging).current()
        }
        if (Files.isRegularFile(staging.resolve("public-exam-v1.properties"), LinkOption.NOFOLLOW_LINKS)) {
            DesktopPublicExamPersistence.open(staging).current()
        }
        if (Files.isRegularFile(staging.resolve("progress-v1.properties"), LinkOption.NOFOLLOW_LINKS)) {
            DesktopStudyProgressRepository(root = staging).reloadFromDisk()
        }
    }

    private fun createPointInTimeWorkspaceSnapshot(
        workspace: Path,
        snapshotRoot: Path,
        files: List<Path>,
    ): List<Path> {
        Files.createDirectories(snapshotRoot)
        files.sorted().forEach { file ->
            val relative = workspace.relativize(file).normalize()
            require(!relative.startsWith("..")) { "Workspace snapshot path escaped its root." }
            val target = snapshotRoot.resolve(relative.toString()).normalize()
            require(target.startsWith(snapshotRoot)) { "Workspace snapshot target escaped its root." }
            target.parent?.let(Files::createDirectories)
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
        }
        requireWorkspaceTreeHasNoSymlinks(snapshotRoot)
        return workspaceBackupFiles(snapshotRoot)
    }

    private fun replaceTree(destination: Path, source: Path) {
        deleteTree(destination)
        Files.createDirectories(destination)
        copyTree(source, destination)
    }

    private fun copyTree(source: Path, destination: Path) {
        if (!Files.exists(source)) return
        Files.walk(source).use { paths ->
            paths.sorted().forEach { path ->
                val relative = source.relativize(path)
                val target = destination.resolve(relative.toString())
                require(!Files.isSymbolicLink(path)) { "Refusing to copy symbolic link in Desktop workspace tree." }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target)
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    target.parent?.let(Files::createDirectories)
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                }
            }
        }
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun regularFileCount(path: Path): Int = Files.walk(path).use { paths ->
        paths.filter { candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) }.count().toInt()
    }

    private class BoundedBackupOutputStream(
        private val delegate: java.io.OutputStream,
        private val limit: Long,
    ) : java.io.OutputStream() {
        private var written = 0L

        override fun write(value: Int) {
            reserve(1)
            delegate.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            reserve(length.toLong())
            delegate.write(bytes, offset, length)
        }

        override fun flush() = delegate.flush()

        private fun reserve(bytes: Long) {
            require(bytes >= 0L && written <= limit - bytes) {
                "Encrypted Desktop backup exceeds the restore size limit."
            }
            written += bytes
        }
    }

    private fun requirePlanId(): StudyPlanId = requireNotNull(services.defaultPlanId) { "No active Study Plan." }
    private fun requireRoot(): Path = requireNotNull(root) { "Desktop workspace persistence is unavailable." }

    private fun ZipInputStream.readBytesBounded(limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        copyBounded(output, limit)
        return output.toByteArray()
    }

    private fun ZipInputStream.copyBounded(output: java.io.OutputStream, limit: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Desktop backup entry exceeds the supported size." }
            output.write(buffer, 0, count)
        }
        return total
    }

    private fun StudyPlan.toPortableRow() = row(
        "id" to text(id.value), "name" to text(name), "description" to text(description),
        "type" to text(type.name), "contentMode" to text(contentMode.name), "status" to text(status.name),
        "examDateIso" to nullableText(examDateIso), "weeklyGoalHours" to integer(weeklyGoalHours),
        "progressPercent" to integer(progressPercent), "studiedMinutesThisWeek" to integer(studiedMinutesThisWeek),
        "questionsAnswered" to integer(questionsAnswered), "averageAccuracyPercent" to integer(averageAccuracyPercent),
        "createdAtEpochMillis" to integer(createdAtEpochMillis),
        "updatedAtEpochMillis" to integer(updatedAtEpochMillis),
        "trashedAtEpochMillis" to nullableInteger(trashedAtEpochMillis),
    )

    private fun StudyTarget.toPortableRow() = row(
        "id" to text(id.value), "studyPlanId" to text(studyPlanId.value), "organization" to text(organization),
        "role" to text(role), "examBoard" to text(examBoard), "noticeUrl" to nullableText(noticeUrl),
        "confirmedExamDateIso" to nullableText(confirmedExamDateIso),
        "expectedExamDateIso" to nullableText(expectedExamDateIso), "openings" to nullableInteger(openings?.toLong()),
        "estimatedCutoffPercent" to nullableInteger(estimatedCutoffPercent?.toLong()),
        "createdAtEpochMillis" to integer(createdAtEpochMillis),
        "updatedAtEpochMillis" to integer(updatedAtEpochMillis),
    )

    private fun ExamStage.toPortableRow() = row(
        "id" to text(id.value), "studyPlanId" to text(studyPlanId.value), "studyTargetId" to text(studyTargetId.value),
        "name" to text(name), "type" to text(type.name), "scheduledDateIso" to nullableText(scheduledDateIso),
        "weight" to real(weight), "minimumScorePercent" to nullableInteger(minimumScorePercent?.toLong()),
        "position" to integer(position),
    )

    private fun Subject.toPortableRow() = row(
        "id" to text(id.value), "studyPlanId" to text(studyPlanId.value), "name" to text(name),
        "description" to text(description), "colorArgb" to nullableInteger(colorArgb), "priority" to integer(priority),
        "examWeight" to real(examWeight), "estimatedMinutes" to integer(estimatedMinutes),
        "progressPercent" to integer(progressPercent), "accuracyPercent" to integer(accuracyPercent),
        "status" to text(status.name), "position" to integer(position), "archived" to bool(archived),
        "examStageId" to nullableText(examStageId?.value),
    )

    private fun Topic.toPortableRow() = row(
        "id" to text(id.value), "studyPlanId" to text(studyPlanId.value), "subjectId" to text(subjectId.value),
        "parentTopicId" to nullableText(parentTopicId?.value), "name" to text(name), "description" to text(description),
        "priority" to integer(priority), "difficulty" to integer(difficulty),
        "examIncidencePercent" to nullableInteger(examIncidencePercent?.toLong()),
        "estimatedMinutes" to integer(estimatedMinutes), "progressPercent" to integer(progressPercent),
        "masteryPercent" to integer(masteryPercent), "status" to text(status.name), "position" to integer(position),
        "archived" to bool(archived), "examStageId" to nullableText(examStageId?.value),
    )

    private fun LegislationDocument.toPortableRow() = row(
        "id" to text(id.value), "studyPlanId" to text(studyPlanId.value), "title" to text(title),
        "sourceReference" to nullableText(sourceReference), "createdAtEpochMillis" to integer(createdAtEpochMillis),
        "updatedAtEpochMillis" to integer(updatedAtEpochMillis),
    )

    private fun LegislationProvision.toPortableRow() = row(
        "id" to text(id.value), "legislationDocumentId" to text(legislationDocumentId.value),
        "studyPlanId" to text(studyPlanId.value), "reference" to text(reference),
        "parentReference" to nullableText(parentReference), "kind" to text(kind.name), "text" to text(text),
        "position" to integer(position), "read" to bool(read), "important" to bool(important),
        "createdAtEpochMillis" to integer(createdAtEpochMillis),
        "updatedAtEpochMillis" to integer(updatedAtEpochMillis),
    )

    private fun StudyCycle.toPortableRow() = row(
        "id" to text(id.value), "studyPlanId" to text(studyPlanId.value), "name" to text(name),
        "enabled" to bool(enabled), "currentItemId" to nullableText(currentItemId?.value),
        "createdAtEpochMillis" to integer(createdAtEpochMillis),
        "updatedAtEpochMillis" to integer(updatedAtEpochMillis),
    )

    private fun StudyCycleItem.toPortableRow() = row(
        "id" to text(id.value), "studyCycleId" to text(studyCycleId.value), "studyPlanId" to text(studyPlanId.value),
        "subjectId" to nullableText(subjectId?.value), "topicId" to nullableText(topicId?.value),
        "position" to integer(position), "targetDurationMinutes" to integer(targetDurationMinutes),
        "enabled" to bool(enabled), "activityType" to text(activityType.name),
        "completionRule" to nullableText(completionRule),
    )

    private fun StudyStrategy.toPortableRow() = row(
        "id" to text(id.value), "studyPlanId" to text(studyPlanId.value), "type" to text(type.name),
        "minimumSessionMinutes" to integer(minimumSessionMinutes),
        "maximumSessionMinutes" to integer(maximumSessionMinutes),
        "theoryRatioPercent" to integer(theoryRatioPercent), "questionRatioPercent" to integer(questionRatioPercent),
        "reviewRatioPercent" to integer(reviewRatioPercent), "adaptivePlanningEnabled" to bool(adaptivePlanningEnabled),
        "configurationJson" to text(configurationJson),
    )

    private fun ScheduleRule.toPortableRow() = row(
        "id" to text(id.value), "studyPlanId" to text(studyPlanId.value), "subjectId" to nullableText(subjectId?.value),
        "type" to text(type.name), "dayOfWeek" to nullableInteger(dayOfWeek?.toLong()),
        "startMinuteOfDay" to nullableInteger(startMinuteOfDay?.toLong()),
        "durationMinutes" to integer(durationMinutes),
        "recurrenceRule" to nullableText(recurrenceRule), "locked" to bool(locked), "enabled" to bool(enabled),
        "position" to integer(position),
    )

    private fun StudyPlanSettings.toPortableRow() = row(
        "studyPlanId" to text(studyPlanId.value), "adaptivePlanningEnabled" to bool(adaptivePlanningEnabled),
        "reviewNotificationsEnabled" to bool(reviewNotificationsEnabled),
        "strictExamModeEnabled" to bool(strictExamModeEnabled),
        "preferredSessionMinutes" to integer(preferredSessionMinutes),
        "quietHoursStartMinute" to nullableInteger(quietHoursStartMinute?.toLong()),
        "quietHoursEndMinute" to nullableInteger(quietHoursEndMinute?.toLong()),
        "configurationJson" to text(configurationJson),
    )

    private fun ProgrammingCourseProfile.toPortableRow() = row(
        "id" to text(id.value),
        "studyPlanId" to text(studyPlanId.value),
        "primaryLanguage" to text(primaryLanguage.name),
        "level" to text(level.name), "repositoryUri" to nullableText(repositoryUri),
        "workspaceRelativePath" to text(workspaceRelativePath), "buildSystem" to nullableText(buildSystem),
        "packageManager" to nullableText(packageManager), "tagsJson" to text(tags.toJsonArray()),
        "createdAtEpochMillis" to integer(createdAtEpochMillis),
        "updatedAtEpochMillis" to integer(updatedAtEpochMillis),
    )

    private fun PortableRow.toStudyPlan() = StudyPlan(
        id = StudyPlanId(requiredText("id")), name = requiredText("name"), description = requiredText("description"),
        type = StudyType.valueOf(requiredText("type")),
        contentMode = StudyContentMode.valueOf(requiredText("contentMode")),
        status = StudyPlanStatus.valueOf(requiredText("status")), examDateIso = optionalText("examDateIso"),
        weeklyGoalHours = requiredInt("weeklyGoalHours"), progressPercent = requiredInt("progressPercent"),
        studiedMinutesThisWeek = requiredInt("studiedMinutesThisWeek"),
        questionsAnswered = requiredInt("questionsAnswered"),
        averageAccuracyPercent = requiredInt("averageAccuracyPercent"),
        createdAtEpochMillis = requiredLong("createdAtEpochMillis"),
        updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
        trashedAtEpochMillis = optionalLong("trashedAtEpochMillis"),
    )

    private fun PortableRow.toStudyTarget() = StudyTarget(
        id = StudyTargetId(requiredText("id")), studyPlanId = StudyPlanId(requiredText("studyPlanId")),
        organization = requiredText("organization"), role = requiredText("role"), examBoard = requiredText("examBoard"),
        noticeUrl = optionalText("noticeUrl"), confirmedExamDateIso = optionalText("confirmedExamDateIso"),
        expectedExamDateIso = optionalText("expectedExamDateIso"), openings = optionalInt("openings"),
        estimatedCutoffPercent = optionalInt("estimatedCutoffPercent"),
        createdAtEpochMillis = requiredLong("createdAtEpochMillis"),
        updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
    )

    private fun PortableRow.toExamStage() = ExamStage(
        id = ExamStageId(requiredText("id")), studyPlanId = StudyPlanId(requiredText("studyPlanId")),
        studyTargetId = StudyTargetId(requiredText("studyTargetId")), name = requiredText("name"),
        type = ExamStageType.valueOf(requiredText("type")), scheduledDateIso = optionalText("scheduledDateIso"),
        weight = requiredDouble("weight"), minimumScorePercent = optionalInt("minimumScorePercent"),
        position = requiredInt("position"),
    )

    private fun PortableRow.toSubject() = Subject(
        id = SubjectId(requiredText("id")), studyPlanId = StudyPlanId(requiredText("studyPlanId")),
        name = requiredText("name"), description = requiredText("description"), colorArgb = optionalLong("colorArgb"),
        priority = requiredInt("priority"), examWeight = requiredDouble("examWeight"),
        estimatedMinutes = requiredInt("estimatedMinutes"), progressPercent = requiredInt("progressPercent"),
        accuracyPercent = requiredInt("accuracyPercent"),
        status = ContentProgressStatus.valueOf(requiredText("status")),
        position = requiredInt("position"), archived = requiredBoolean("archived"),
        examStageId = optionalText("examStageId")?.let(::ExamStageId),
    )

    private fun PortableRow.toTopic() = Topic(
        id = TopicId(requiredText("id")), studyPlanId = StudyPlanId(requiredText("studyPlanId")),
        subjectId = SubjectId(requiredText("subjectId")), parentTopicId = optionalText("parentTopicId")?.let(::TopicId),
        name = requiredText("name"), description = requiredText("description"), priority = requiredInt("priority"),
        difficulty = requiredInt("difficulty"), examIncidencePercent = optionalInt("examIncidencePercent"),
        estimatedMinutes = requiredInt("estimatedMinutes"), progressPercent = requiredInt("progressPercent"),
        masteryPercent = requiredInt("masteryPercent"), status = ContentProgressStatus.valueOf(requiredText("status")),
        position = requiredInt("position"), archived = requiredBoolean("archived"),
        examStageId = optionalText("examStageId")?.let(::ExamStageId),
    )

    private fun PortableRow.toStudyStrategy() = StudyStrategy(
        id = StudyStrategyId(requiredText("id")), studyPlanId = StudyPlanId(requiredText("studyPlanId")),
        type = StudyStrategyType.valueOf(requiredText("type")),
        minimumSessionMinutes = requiredInt("minimumSessionMinutes"),
        maximumSessionMinutes = requiredInt("maximumSessionMinutes"),
        theoryRatioPercent = requiredInt("theoryRatioPercent"),
        questionRatioPercent = requiredInt("questionRatioPercent"),
        reviewRatioPercent = requiredInt("reviewRatioPercent"),
        adaptivePlanningEnabled = requiredBoolean("adaptivePlanningEnabled"),
        configurationJson = requiredText("configurationJson"),
    )

    private fun PortableRow.toScheduleRule() = ScheduleRule(
        id = ScheduleRuleId(requiredText("id")), studyPlanId = StudyPlanId(requiredText("studyPlanId")),
        subjectId = optionalText("subjectId")?.let(::SubjectId), type = ScheduleRuleType.valueOf(requiredText("type")),
        dayOfWeek = optionalInt("dayOfWeek"), startMinuteOfDay = optionalInt("startMinuteOfDay"),
        durationMinutes = requiredInt("durationMinutes"), recurrenceRule = optionalText("recurrenceRule"),
        locked = requiredBoolean("locked"), enabled = requiredBoolean("enabled"), position = requiredInt("position"),
    )

    private fun PortableRow.toStudyPlanSettings(planId: StudyPlanId) = StudyPlanSettings(
        studyPlanId = planId, adaptivePlanningEnabled = requiredBoolean("adaptivePlanningEnabled"),
        reviewNotificationsEnabled = requiredBoolean("reviewNotificationsEnabled"),
        strictExamModeEnabled = requiredBoolean("strictExamModeEnabled"),
        preferredSessionMinutes = requiredInt("preferredSessionMinutes"),
        quietHoursStartMinute = optionalInt("quietHoursStartMinute"),
        quietHoursEndMinute = optionalInt("quietHoursEndMinute"),
        configurationJson = requiredText("configurationJson"),
    )

    private fun PortableRow.toLegislationDocument() = LegislationDocument(
        id = LegislationDocumentId(requiredText("id")), studyPlanId = StudyPlanId(requiredText("studyPlanId")),
        title = requiredText("title"), sourceReference = optionalText("sourceReference"),
        createdAtEpochMillis = requiredLong("createdAtEpochMillis"),
        updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
    )

    private fun PortableRow.toLegislationProvision() = LegislationProvision(
        id = LegislationProvisionId(requiredText("id")),
        legislationDocumentId = LegislationDocumentId(requiredText("legislationDocumentId")),
        studyPlanId = StudyPlanId(requiredText("studyPlanId")), reference = requiredText("reference"),
        parentReference = optionalText("parentReference"),
        kind = LegislationProvisionKind.valueOf(requiredText("kind")),
        text = requiredText("text"), position = requiredInt("position"), read = requiredBoolean("read"),
        important = requiredBoolean("important"), createdAtEpochMillis = requiredLong("createdAtEpochMillis"),
        updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
    )

    private fun PortableRow.toStudyCycle() = StudyCycle(
        id = StudyCycleId(requiredText("id")), studyPlanId = StudyPlanId(requiredText("studyPlanId")),
        name = requiredText("name"), enabled = requiredBoolean("enabled"),
        currentItemId = optionalText("currentItemId")?.let(::StudyCycleItemId),
        createdAtEpochMillis = requiredLong("createdAtEpochMillis"),
        updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
    )

    private fun PortableRow.toStudyCycleItem() = StudyCycleItem(
        id = StudyCycleItemId(requiredText("id")), studyCycleId = StudyCycleId(requiredText("studyCycleId")),
        studyPlanId = StudyPlanId(requiredText("studyPlanId")), subjectId = optionalText("subjectId")?.let(::SubjectId),
        topicId = optionalText("topicId")?.let(::TopicId), position = requiredInt("position"),
        targetDurationMinutes = requiredInt("targetDurationMinutes"), enabled = requiredBoolean("enabled"),
        activityType = com.mikeyphw.studyflow.core.model.StudyActivityType.valueOf(requiredText("activityType")),
        completionRule = optionalText("completionRule"),
    )

    private fun PortableRow.toProgrammingProfile() = ProgrammingCourseProfile(
        id = ProgrammingCourseProfileId(requiredText("id")), studyPlanId = StudyPlanId(requiredText("studyPlanId")),
        primaryLanguage = ProgrammingLanguage.valueOf(requiredText("primaryLanguage")),
        level = ProgrammingCourseLevel.valueOf(requiredText("level")), repositoryUri = optionalText("repositoryUri"),
        workspaceRelativePath = requiredText("workspaceRelativePath"), buildSystem = optionalText("buildSystem"),
        packageManager = optionalText("packageManager"), tags = requiredText("tagsJson").parseJsonArray(),
        createdAtEpochMillis = requiredLong("createdAtEpochMillis"),
        updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
    )

    private fun Map<String, PortableTable>.rows(name: String): List<PortableRow> = this[name]?.rows.orEmpty()
    private fun <T> List<T>.filterPlan(planId: StudyPlanId): List<T> = filter { value ->
        when (value) {
            is StudyTarget -> value.studyPlanId == planId
            is ExamStage -> value.studyPlanId == planId
            is Subject -> value.studyPlanId == planId
            is Topic -> value.studyPlanId == planId
            is LegislationDocument -> value.studyPlanId == planId
            is LegislationProvision -> value.studyPlanId == planId
            is StudyCycle -> value.studyPlanId == planId
            is StudyCycleItem -> value.studyPlanId == planId
            is StudyStrategy -> value.studyPlanId == planId
            else -> false
        }
    }

    private fun row(vararg values: Pair<String, PortableValue>) = PortableRow(linkedMapOf(*values))
    private fun text(value: String) = PortableValue(PortableValueType.TEXT, value)
    private fun integer(value: Number) = PortableValue(PortableValueType.INTEGER, value.toLong().toString())
    private fun real(value: Number) = PortableValue(PortableValueType.REAL, value.toDouble().toString())
    private fun bool(value: Boolean) = integer(if (value) 1 else 0)
    private fun nullableText(value: String?) = value?.let(::text) ?: PortableValue(PortableValueType.NULL)
    private fun nullableInteger(value: Long?) = value?.let(::integer) ?: PortableValue(PortableValueType.NULL)

    private fun PortableRow.requiredValue(name: String): String = values[name]?.value
        ?: error("Portable Study Plan row is missing $name.")
    private fun PortableRow.requiredText(name: String): String = requiredValue(name)
    private fun PortableRow.requiredInt(name: String): Int = requiredValue(name).toInt()
    private fun PortableRow.requiredLong(name: String): Long = requiredValue(name).toLong()
    private fun PortableRow.requiredDouble(name: String): Double = requiredValue(name).toDouble()
    private fun PortableRow.requiredBoolean(name: String): Boolean = requiredValue(name) in setOf("1", "true")
    private fun PortableRow.optionalText(name: String): String? = values[name]?.takeUnless {
        it.type == PortableValueType.NULL
    }?.value
    private fun PortableRow.optionalInt(name: String): Int? = optionalText(name)?.toInt()
    private fun PortableRow.optionalLong(name: String): Long? = optionalText(name)?.toLong()

    private fun List<String>.toJsonArray(): String = joinToString(prefix = "[", postfix = "]") { value ->
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    private fun String.parseJsonArray(): List<String> = trim().removePrefix("[").removeSuffix("]")
        .takeIf(String::isNotBlank)?.split(',')?.map { token -> token.trim().removeSurrounding("\"") }.orEmpty()

    companion object {
        private const val BACKUP_FORMAT = "studyflow.desktop.workspace.backup"
        private const val BACKUP_SCHEMA_VERSION = 1
        private const val BACKUP_MANIFEST = "desktop-backup.properties"
        private const val MAX_MANIFEST_BYTES = 64L * 1024L
        private const val MAX_ENTRY_BYTES = 256L * 1024L * 1024L
        private const val MAX_BACKUP_BYTES = 2L * 1024L * 1024L * 1024L
        private const val MAX_BACKUP_PAYLOAD_BYTES = MAX_BACKUP_BYTES - (64L * 1024L * 1024L)
    }
}

data class DesktopPortableStructurePreview(
    val planName: String,
    val sourcePlanId: String,
    val tableCount: Int,
    val rowCount: Int,
)

data class DesktopWorkspaceRestoreResult(
    val restoredFiles: Int,
    val restartRequired: Boolean,
    val safetySnapshotCreated: Boolean,
)
