package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.ThemeMode
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Durable Desktop workspace state that is intentionally independent of Android persistence APIs. */
data class DesktopWorkspaceSnapshot(
    val plans: List<StudyPlan>,
    val planSettings: Map<StudyPlanId, StudyPlanSettings>,
    val preferences: AppPreferences,
) {
    companion object {
        fun seed(sample: DesktopStudyFlowSampleData): DesktopWorkspaceSnapshot = DesktopWorkspaceSnapshot(
            plans = sample.plans,
            planSettings = sample.plans.associate { plan -> plan.id to StudyPlanSettings.defaults(plan.id) },
            preferences = AppPreferences(
                dynamicColorEnabled = false,
                defaultStudyPlanId = sample.plans.firstOrNull()?.id,
            ),
        )
    }
}

class DesktopWorkspacePersistence private constructor(
    val root: Path,
    private var snapshot: DesktopWorkspaceSnapshot,
) {
    private val workspaceFile: Path = root.resolve(WORKSPACE_FILE)

    @Synchronized
    fun current(): DesktopWorkspaceSnapshot = snapshot

    @Synchronized
    fun updatePlans(
        plans: List<StudyPlan>,
        settings: Map<StudyPlanId, StudyPlanSettings>,
    ) {
        snapshot = snapshot.copy(plans = plans.toList(), planSettings = settings.toMap())
        persistLocked()
    }

    @Synchronized
    fun updatePreferences(preferences: AppPreferences) {
        snapshot = snapshot.copy(preferences = preferences)
        persistLocked()
    }

    @Synchronized
    private fun persistLocked() {
        Files.createDirectories(root)
        val temporary = root.resolve("$WORKSPACE_FILE.tmp")
        Files.newOutputStream(temporary).use { output ->
            DesktopWorkspacePropertiesCodec.write(snapshot, output)
        }
        try {
            Files.move(
                temporary,
                workspaceFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, workspaceFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val WORKSPACE_FILE = "workspace-v1.properties"

        fun open(
            root: Path = defaultDesktopWorkspaceRoot(),
            seed: DesktopWorkspaceSnapshot,
        ): DesktopWorkspacePersistence {
            Files.createDirectories(root)
            val file = root.resolve(WORKSPACE_FILE)
            val initial = if (Files.exists(file)) {
                Files.newInputStream(file).use(DesktopWorkspacePropertiesCodec::read)
            } else {
                seed
            }
            return DesktopWorkspacePersistence(root, initial).also { persistence ->
                if (!Files.exists(file)) persistence.persistLocked()
            }
        }
    }
}

fun defaultDesktopWorkspaceRoot(): Path {
    System.getenv("STUDYFLOW_DESKTOP_DATA_DIR")?.takeIf(String::isNotBlank)?.let { configured ->
        return Path.of(configured).toAbsolutePath().normalize()
    }
    val home = Path.of(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> Path.of(System.getenv("APPDATA") ?: home.toString()).resolve("StudyFlow")
        os.contains("mac") -> home.resolve("Library/Application Support/StudyFlow")
        else -> Path.of(System.getenv("XDG_DATA_HOME") ?: home.resolve(".local/share").toString())
            .resolve("studyflow/desktop")
    }.toAbsolutePath().normalize()
}

private object DesktopWorkspacePropertiesCodec {
    private const val SCHEMA_VERSION = 1

    fun write(snapshot: DesktopWorkspaceSnapshot, output: OutputStream) {
        val properties = Properties()
        properties["schema.version"] = SCHEMA_VERSION.toString()
        properties["plan.count"] = snapshot.plans.size.toString()
        snapshot.plans.forEachIndexed { index, plan -> writePlan(properties, "plan.$index", plan) }

        val settings = snapshot.planSettings.entries.sortedBy { it.key.value }
        properties["settings.count"] = settings.size.toString()
        settings.forEachIndexed { index, (_, value) -> writeSettings(properties, "settings.$index", value) }

        val preferences = snapshot.preferences
        properties["preferences.language"] = preferences.language.name
        properties["preferences.theme"] = preferences.themeMode.name
        properties["preferences.dynamicColor"] = preferences.dynamicColorEnabled.toString()
        preferences.defaultStudyPlanId?.let { properties["preferences.defaultPlanId"] = it.value }
        properties["preferences.recentSearch.count"] = preferences.recentSearches.size.toString()
        preferences.recentSearches.forEachIndexed { index, query ->
            properties["preferences.recentSearch.$index"] = query
        }
        properties.store(output, "StudyFlow Desktop workspace schema v$SCHEMA_VERSION")
    }

    fun read(input: InputStream): DesktopWorkspaceSnapshot {
        val properties = Properties().apply { load(input) }
        val version = properties.requiredInt("schema.version")
        require(version == SCHEMA_VERSION) { "Unsupported StudyFlow Desktop workspace schema: $version" }

        val plans = List(properties.requiredInt("plan.count")) { index ->
            readPlan(properties, "plan.$index")
        }
        val settings = List(properties.requiredInt("settings.count")) { index ->
            readSettings(properties, "settings.$index")
        }.associateBy(StudyPlanSettings::studyPlanId)

        val recentSearches = List(properties.optionalInt("preferences.recentSearch.count") ?: 0) { index ->
            properties.required("preferences.recentSearch.$index")
        }
        val defaultPlan = properties.getProperty("preferences.defaultPlanId")
            ?.takeIf(String::isNotBlank)
            ?.let(::StudyPlanId)
        val preferences = AppPreferences(
            language = enumValueOf(properties.required("preferences.language")),
            themeMode = enumValueOf(properties.required("preferences.theme")),
            dynamicColorEnabled = properties.requiredBoolean("preferences.dynamicColor"),
            defaultStudyPlanId = defaultPlan,
            recentSearches = recentSearches,
        )
        return DesktopWorkspaceSnapshot(plans, settings, preferences)
    }

    private fun writePlan(properties: Properties, prefix: String, plan: StudyPlan) {
        properties["$prefix.id"] = plan.id.value
        properties["$prefix.name"] = plan.name
        properties["$prefix.description"] = plan.description
        properties["$prefix.type"] = plan.type.name
        properties["$prefix.status"] = plan.status.name
        plan.examDateIso?.let { properties["$prefix.examDateIso"] = it }
        properties["$prefix.weeklyGoalHours"] = plan.weeklyGoalHours.toString()
        properties["$prefix.progressPercent"] = plan.progressPercent.toString()
        properties["$prefix.studiedMinutesThisWeek"] = plan.studiedMinutesThisWeek.toString()
        properties["$prefix.questionsAnswered"] = plan.questionsAnswered.toString()
        properties["$prefix.averageAccuracyPercent"] = plan.averageAccuracyPercent.toString()
        properties["$prefix.createdAtEpochMillis"] = plan.createdAtEpochMillis.toString()
        properties["$prefix.updatedAtEpochMillis"] = plan.updatedAtEpochMillis.toString()
        plan.trashedAtEpochMillis?.let { properties["$prefix.trashedAtEpochMillis"] = it.toString() }
        properties["$prefix.contentMode"] = plan.contentMode.name
    }

    private fun readPlan(properties: Properties, prefix: String): StudyPlan = StudyPlan(
        id = StudyPlanId(properties.required("$prefix.id")),
        name = properties.required("$prefix.name"),
        description = properties.required("$prefix.description"),
        type = StudyType.valueOf(properties.required("$prefix.type")),
        status = StudyPlanStatus.valueOf(properties.required("$prefix.status")),
        examDateIso = properties.getProperty("$prefix.examDateIso"),
        weeklyGoalHours = properties.requiredInt("$prefix.weeklyGoalHours"),
        progressPercent = properties.requiredInt("$prefix.progressPercent"),
        studiedMinutesThisWeek = properties.requiredInt("$prefix.studiedMinutesThisWeek"),
        questionsAnswered = properties.requiredInt("$prefix.questionsAnswered"),
        averageAccuracyPercent = properties.requiredInt("$prefix.averageAccuracyPercent"),
        createdAtEpochMillis = properties.requiredLong("$prefix.createdAtEpochMillis"),
        updatedAtEpochMillis = properties.requiredLong("$prefix.updatedAtEpochMillis"),
        trashedAtEpochMillis = properties.optionalLong("$prefix.trashedAtEpochMillis"),
        contentMode = StudyContentMode.valueOf(properties.required("$prefix.contentMode")),
    )

    private fun writeSettings(properties: Properties, prefix: String, settings: StudyPlanSettings) {
        properties["$prefix.planId"] = settings.studyPlanId.value
        properties["$prefix.adaptivePlanningEnabled"] = settings.adaptivePlanningEnabled.toString()
        properties["$prefix.reviewNotificationsEnabled"] = settings.reviewNotificationsEnabled.toString()
        properties["$prefix.strictExamModeEnabled"] = settings.strictExamModeEnabled.toString()
        properties["$prefix.preferredSessionMinutes"] = settings.preferredSessionMinutes.toString()
        settings.quietHoursStartMinute?.let { properties["$prefix.quietHoursStartMinute"] = it.toString() }
        settings.quietHoursEndMinute?.let { properties["$prefix.quietHoursEndMinute"] = it.toString() }
        properties["$prefix.configurationJson"] = settings.configurationJson
    }

    private fun readSettings(properties: Properties, prefix: String): StudyPlanSettings = StudyPlanSettings(
        studyPlanId = StudyPlanId(properties.required("$prefix.planId")),
        adaptivePlanningEnabled = properties.requiredBoolean("$prefix.adaptivePlanningEnabled"),
        reviewNotificationsEnabled = properties.requiredBoolean("$prefix.reviewNotificationsEnabled"),
        strictExamModeEnabled = properties.requiredBoolean("$prefix.strictExamModeEnabled"),
        preferredSessionMinutes = properties.requiredInt("$prefix.preferredSessionMinutes"),
        quietHoursStartMinute = properties.optionalInt("$prefix.quietHoursStartMinute"),
        quietHoursEndMinute = properties.optionalInt("$prefix.quietHoursEndMinute"),
        configurationJson = properties.required("$prefix.configurationJson"),
    )

    private fun Properties.required(key: String): String = requireNotNull(getProperty(key)) {
        "StudyFlow Desktop workspace is missing '$key'."
    }

    private fun Properties.requiredInt(key: String): Int = required(key).toInt()
    private fun Properties.requiredLong(key: String): Long = required(key).toLong()
    private fun Properties.requiredBoolean(key: String): Boolean = required(key).toBooleanStrict()
    private fun Properties.optionalInt(key: String): Int? = getProperty(key)?.toInt()
    private fun Properties.optionalLong(key: String): Long? = getProperty(key)?.toLong()
}
