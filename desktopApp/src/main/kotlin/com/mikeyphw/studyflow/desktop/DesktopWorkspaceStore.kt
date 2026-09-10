package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.ExamStageType
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.StudyStrategy
import com.mikeyphw.studyflow.core.model.StudyStrategyId
import com.mikeyphw.studyflow.core.model.StudyStrategyType
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.VideoResourceId
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.ThemeMode
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
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
    val studyTargets: List<StudyTarget> = emptyList(),
    val examStages: List<ExamStage> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val topics: List<Topic> = emptyList(),
    val strategies: List<StudyStrategy> = emptyList(),
    val scheduleRules: List<ScheduleRule> = emptyList(),
    val plannedActivities: List<PlannedActivity> = emptyList(),
    val sessions: List<StudySession> = emptyList(),
) {
    companion object {
        fun seed(sample: DesktopStudyFlowSampleData): DesktopWorkspaceSnapshot = DesktopWorkspaceSnapshot(
            plans = sample.plans,
            planSettings = sample.plans.associate { plan -> plan.id to StudyPlanSettings.defaults(plan.id) },
            preferences = AppPreferences(
                dynamicColorEnabled = false,
                defaultStudyPlanId = sample.plans.firstOrNull()?.id,
            ),
            subjects = sample.subjects,
            topics = sample.topics,
            strategies = sample.strategies,
            scheduleRules = sample.scheduleRules,
            plannedActivities = sample.plannedActivities,
            sessions = sample.sessions,
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
    fun updateContent(
        studyTargets: List<StudyTarget>,
        examStages: List<ExamStage>,
        subjects: List<Subject>,
        topics: List<Topic>,
    ) {
        snapshot = snapshot.copy(
            studyTargets = studyTargets.toList(),
            examStages = examStages.toList(),
            subjects = subjects.toList(),
            topics = topics.toList(),
        )
        persistLocked()
    }

    @Synchronized
    fun updateSchedule(
        strategies: List<StudyStrategy>,
        scheduleRules: List<ScheduleRule>,
        plannedActivities: List<PlannedActivity>,
    ) {
        snapshot = snapshot.copy(
            strategies = strategies.toList(),
            scheduleRules = scheduleRules.toList(),
            plannedActivities = plannedActivities.toList(),
        )
        persistLocked()
    }

    @Synchronized
    fun updateSessions(sessions: List<StudySession>) {
        snapshot = snapshot.copy(sessions = sessions.toList())
        persistLocked()
    }

    /** Rewrites any supported legacy workspace schema using the current DOV20 schema. */
    @Synchronized
    fun rewriteCurrentSchema() {
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
        const val CURRENT_SCHEMA_VERSION: Int = 4
        private const val WORKSPACE_FILE = "workspace-v1.properties"

        fun open(
            root: Path = defaultDesktopWorkspaceRoot(),
            seed: DesktopWorkspaceSnapshot,
        ): DesktopWorkspacePersistence {
            Files.createDirectories(root)
            val file = root.resolve(WORKSPACE_FILE)
            val initial = if (Files.exists(file)) {
                Files.newInputStream(file).use { input -> DesktopWorkspacePropertiesCodec.read(input, seed) }
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
    private const val SCHEMA_VERSION = DesktopWorkspacePersistence.CURRENT_SCHEMA_VERSION

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

        properties["target.count"] = snapshot.studyTargets.size.toString()
        snapshot.studyTargets.forEachIndexed { index, target -> writeTarget(properties, "target.$index", target) }
        properties["stage.count"] = snapshot.examStages.size.toString()
        snapshot.examStages.forEachIndexed { index, stage -> writeStage(properties, "stage.$index", stage) }
        properties["subject.count"] = snapshot.subjects.size.toString()
        snapshot.subjects.forEachIndexed { index, subject -> writeSubject(properties, "subject.$index", subject) }
        properties["topic.count"] = snapshot.topics.size.toString()
        snapshot.topics.forEachIndexed { index, topic -> writeTopic(properties, "topic.$index", topic) }
        properties["strategy.count"] = snapshot.strategies.size.toString()
        snapshot.strategies.forEachIndexed { index, strategy -> writeStrategy(properties, "strategy.$index", strategy) }
        properties["rule.count"] = snapshot.scheduleRules.size.toString()
        snapshot.scheduleRules.forEachIndexed { index, rule -> writeRule(properties, "rule.$index", rule) }
        properties["activity.count"] = snapshot.plannedActivities.size.toString()
        snapshot.plannedActivities.forEachIndexed { index, activity ->
            writeActivity(properties, "activity.$index", activity)
        }
        properties["session.count"] = snapshot.sessions.size.toString()
        snapshot.sessions.forEachIndexed { index, session -> writeSession(properties, "session.$index", session) }
        properties.store(output, "StudyFlow Desktop workspace schema v$SCHEMA_VERSION")
    }

    fun read(input: InputStream, seed: DesktopWorkspaceSnapshot): DesktopWorkspaceSnapshot {
        val properties = Properties().apply { load(input) }
        val version = properties.requiredInt("schema.version")
        require(version in 1..SCHEMA_VERSION) { "Unsupported StudyFlow Desktop workspace schema: $version" }

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
        val planIds = plans.mapTo(linkedSetOf(), StudyPlan::id)
        val targets = if (version >= 2) {
            List(properties.optionalInt("target.count") ?: 0) { index -> readTarget(properties, "target.$index") }
        } else seed.studyTargets.filter { it.studyPlanId in planIds }
        val stages = if (version >= 2) {
            List(properties.optionalInt("stage.count") ?: 0) { index -> readStage(properties, "stage.$index") }
        } else seed.examStages.filter { it.studyPlanId in planIds }
        val subjects = if (version >= 2) {
            List(properties.optionalInt("subject.count") ?: 0) { index -> readSubject(properties, "subject.$index") }
        } else seed.subjects.filter { it.studyPlanId in planIds }
        val topics = if (version >= 2) {
            List(properties.optionalInt("topic.count") ?: 0) { index -> readTopic(properties, "topic.$index") }
        } else seed.topics.filter { it.studyPlanId in planIds }
        val strategies = if (version >= 3) {
            List(properties.optionalInt("strategy.count") ?: 0) { index -> readStrategy(properties, "strategy.$index") }
        } else seed.strategies.filter { it.studyPlanId in planIds }
        val rules = if (version >= 3) {
            List(properties.optionalInt("rule.count") ?: 0) { index -> readRule(properties, "rule.$index") }
        } else seed.scheduleRules.filter { it.studyPlanId in planIds }
        val activities = if (version >= 3) {
            List(properties.optionalInt("activity.count") ?: 0) { index -> readActivity(properties, "activity.$index") }
        } else seed.plannedActivities.filter { it.studyPlanId in planIds }
        val sessions = if (version >= 4) {
            List(properties.optionalInt("session.count") ?: 0) { index -> readSession(properties, "session.$index") }
        } else seed.sessions.filter { it.studyPlanId in planIds }
        return DesktopWorkspaceSnapshot(
            plans, settings, preferences, targets, stages, subjects, topics, strategies, rules, activities, sessions,
        )
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

    private fun writeSession(properties: Properties, prefix: String, session: StudySession) {
        properties["$prefix.id"] = session.id.value
        properties["$prefix.planId"] = session.studyPlanId.value
        session.plannedActivityId?.let { properties["$prefix.activityId"] = it.value }
        session.subjectId?.let { properties["$prefix.subjectId"] = it.value }
        session.topicId?.let { properties["$prefix.topicId"] = it.value }
        session.materialId?.let { properties["$prefix.materialId"] = it.value }
        session.videoResourceId?.let { properties["$prefix.videoId"] = it.value }
        properties["$prefix.type"] = session.type.name
        properties["$prefix.status"] = session.status.name
        properties["$prefix.startedAt"] = session.startedAtEpochMillis.toString()
        session.endedAtEpochMillis?.let { properties["$prefix.endedAt"] = it.toString() }
        properties["$prefix.grossSeconds"] = session.grossDurationSeconds.toString()
        properties["$prefix.pauseSeconds"] = session.pauseDurationSeconds.toString()
        properties["$prefix.pages"] = session.pagesCompleted.toString()
        properties["$prefix.questions"] = session.questionsAnswered.toString()
        properties["$prefix.correct"] = session.correctAnswers.toString()
        session.concentrationRating?.let { properties["$prefix.concentration"] = it.toString() }
        session.difficultyRating?.let { properties["$prefix.difficulty"] = it.toString() }
        properties["$prefix.notes"] = session.notes
        session.targetDurationMinutes?.let { properties["$prefix.targetMinutes"] = it.toString() }
    }

    private fun readSession(properties: Properties, prefix: String): StudySession = StudySession(
        id = StudySessionId(properties.required("$prefix.id")),
        studyPlanId = StudyPlanId(properties.required("$prefix.planId")),
        plannedActivityId = properties.getProperty("$prefix.activityId")?.let(::PlannedActivityId),
        subjectId = properties.getProperty("$prefix.subjectId")?.let(::SubjectId),
        topicId = properties.getProperty("$prefix.topicId")?.let(::TopicId),
        materialId = properties.getProperty("$prefix.materialId")?.let(::StudyMaterialId),
        videoResourceId = properties.getProperty("$prefix.videoId")?.let(::VideoResourceId),
        type = StudyActivityType.valueOf(properties.required("$prefix.type")),
        status = StudySessionStatus.valueOf(properties.required("$prefix.status")),
        startedAtEpochMillis = properties.requiredLong("$prefix.startedAt"),
        endedAtEpochMillis = properties.optionalLong("$prefix.endedAt"),
        grossDurationSeconds = properties.requiredLong("$prefix.grossSeconds"),
        pauseDurationSeconds = properties.requiredLong("$prefix.pauseSeconds"),
        pagesCompleted = properties.requiredInt("$prefix.pages"),
        questionsAnswered = properties.requiredInt("$prefix.questions"),
        correctAnswers = properties.requiredInt("$prefix.correct"),
        concentrationRating = properties.optionalInt("$prefix.concentration"),
        difficultyRating = properties.optionalInt("$prefix.difficulty"),
        notes = properties.required("$prefix.notes"),
        targetDurationMinutes = properties.optionalInt("$prefix.targetMinutes"),
    )

    private fun writeStrategy(properties: Properties, prefix: String, strategy: StudyStrategy) {
        properties["$prefix.id"] = strategy.id.value
        properties["$prefix.planId"] = strategy.studyPlanId.value
        properties["$prefix.type"] = strategy.type.name
        properties["$prefix.min"] = strategy.minimumSessionMinutes.toString()
        properties["$prefix.max"] = strategy.maximumSessionMinutes.toString()
        properties["$prefix.theory"] = strategy.theoryRatioPercent.toString()
        properties["$prefix.questions"] = strategy.questionRatioPercent.toString()
        properties["$prefix.review"] = strategy.reviewRatioPercent.toString()
        properties["$prefix.adaptive"] = strategy.adaptivePlanningEnabled.toString()
        properties["$prefix.configurationJson"] = strategy.configurationJson
    }

    private fun readStrategy(properties: Properties, prefix: String): StudyStrategy = StudyStrategy(
        id = StudyStrategyId(properties.required("$prefix.id")),
        studyPlanId = StudyPlanId(properties.required("$prefix.planId")),
        type = StudyStrategyType.valueOf(properties.required("$prefix.type")),
        minimumSessionMinutes = properties.requiredInt("$prefix.min"),
        maximumSessionMinutes = properties.requiredInt("$prefix.max"),
        theoryRatioPercent = properties.requiredInt("$prefix.theory"),
        questionRatioPercent = properties.requiredInt("$prefix.questions"),
        reviewRatioPercent = properties.requiredInt("$prefix.review"),
        adaptivePlanningEnabled = properties.requiredBoolean("$prefix.adaptive"),
        configurationJson = properties.required("$prefix.configurationJson"),
    )

    private fun writeRule(properties: Properties, prefix: String, rule: ScheduleRule) {
        properties["$prefix.id"] = rule.id.value
        properties["$prefix.planId"] = rule.studyPlanId.value
        rule.subjectId?.let { properties["$prefix.subjectId"] = it.value }
        properties["$prefix.type"] = rule.type.name
        rule.dayOfWeek?.let { properties["$prefix.dayOfWeek"] = it.toString() }
        rule.startMinuteOfDay?.let { properties["$prefix.startMinuteOfDay"] = it.toString() }
        properties["$prefix.durationMinutes"] = rule.durationMinutes.toString()
        rule.recurrenceRule?.let { properties["$prefix.recurrenceRule"] = it }
        properties["$prefix.locked"] = rule.locked.toString()
        properties["$prefix.enabled"] = rule.enabled.toString()
        properties["$prefix.position"] = rule.position.toString()
    }

    private fun readRule(properties: Properties, prefix: String): ScheduleRule = ScheduleRule(
        id = ScheduleRuleId(properties.required("$prefix.id")),
        studyPlanId = StudyPlanId(properties.required("$prefix.planId")),
        subjectId = properties.getProperty("$prefix.subjectId")?.let(::SubjectId),
        type = ScheduleRuleType.valueOf(properties.required("$prefix.type")),
        dayOfWeek = properties.optionalInt("$prefix.dayOfWeek"),
        startMinuteOfDay = properties.optionalInt("$prefix.startMinuteOfDay"),
        durationMinutes = properties.requiredInt("$prefix.durationMinutes"),
        recurrenceRule = properties.getProperty("$prefix.recurrenceRule"),
        locked = properties.requiredBoolean("$prefix.locked"),
        enabled = properties.requiredBoolean("$prefix.enabled"),
        position = properties.requiredInt("$prefix.position"),
    )

    private fun writeActivity(properties: Properties, prefix: String, activity: PlannedActivity) {
        properties["$prefix.id"] = activity.id.value
        properties["$prefix.planId"] = activity.studyPlanId.value
        activity.subjectId?.let { properties["$prefix.subjectId"] = it.value }
        activity.topicId?.let { properties["$prefix.topicId"] = it.value }
        activity.materialId?.let { properties["$prefix.materialId"] = it.value }
        activity.videoResourceId?.let { properties["$prefix.videoId"] = it.value }
        properties["$prefix.type"] = activity.type.name
        properties["$prefix.title"] = activity.title
        activity.scheduledStartEpochMillis?.let { properties["$prefix.start"] = it.toString() }
        properties["$prefix.duration"] = activity.plannedDurationMinutes.toString()
        properties["$prefix.status"] = activity.status.name
        properties["$prefix.locked"] = activity.locked.toString()
        activity.completedAtEpochMillis?.let { properties["$prefix.completedAt"] = it.toString() }
    }

    private fun readActivity(properties: Properties, prefix: String): PlannedActivity = PlannedActivity(
        id = PlannedActivityId(properties.required("$prefix.id")),
        studyPlanId = StudyPlanId(properties.required("$prefix.planId")),
        subjectId = properties.getProperty("$prefix.subjectId")?.let(::SubjectId),
        topicId = properties.getProperty("$prefix.topicId")?.let(::TopicId),
        materialId = properties.getProperty("$prefix.materialId")?.let(::StudyMaterialId),
        videoResourceId = properties.getProperty("$prefix.videoId")?.let(::VideoResourceId),
        type = StudyActivityType.valueOf(properties.required("$prefix.type")),
        title = properties.required("$prefix.title"),
        scheduledStartEpochMillis = properties.optionalLong("$prefix.start"),
        plannedDurationMinutes = properties.requiredInt("$prefix.duration"),
        status = PlannedActivityStatus.valueOf(properties.required("$prefix.status")),
        locked = properties.requiredBoolean("$prefix.locked"),
        completedAtEpochMillis = properties.optionalLong("$prefix.completedAt"),
    )

    private fun writeTarget(properties: Properties, prefix: String, target: StudyTarget) {
        properties["$prefix.id"] = target.id.value
        properties["$prefix.planId"] = target.studyPlanId.value
        properties["$prefix.organization"] = target.organization
        properties["$prefix.role"] = target.role
        properties["$prefix.examBoard"] = target.examBoard
        target.noticeUrl?.let { properties["$prefix.noticeUrl"] = it }
        target.confirmedExamDateIso?.let { properties["$prefix.confirmedExamDateIso"] = it }
        target.expectedExamDateIso?.let { properties["$prefix.expectedExamDateIso"] = it }
        target.openings?.let { properties["$prefix.openings"] = it.toString() }
        target.estimatedCutoffPercent?.let { properties["$prefix.estimatedCutoffPercent"] = it.toString() }
        properties["$prefix.createdAt"] = target.createdAtEpochMillis.toString()
        properties["$prefix.updatedAt"] = target.updatedAtEpochMillis.toString()
    }

    private fun readTarget(properties: Properties, prefix: String): StudyTarget = StudyTarget(
        id = StudyTargetId(properties.required("$prefix.id")),
        studyPlanId = StudyPlanId(properties.required("$prefix.planId")),
        organization = properties.required("$prefix.organization"),
        role = properties.required("$prefix.role"),
        examBoard = properties.required("$prefix.examBoard"),
        noticeUrl = properties.getProperty("$prefix.noticeUrl"),
        confirmedExamDateIso = properties.getProperty("$prefix.confirmedExamDateIso"),
        expectedExamDateIso = properties.getProperty("$prefix.expectedExamDateIso"),
        openings = properties.optionalInt("$prefix.openings"),
        estimatedCutoffPercent = properties.optionalInt("$prefix.estimatedCutoffPercent"),
        createdAtEpochMillis = properties.requiredLong("$prefix.createdAt"),
        updatedAtEpochMillis = properties.requiredLong("$prefix.updatedAt"),
    )

    private fun writeStage(properties: Properties, prefix: String, stage: ExamStage) {
        properties["$prefix.id"] = stage.id.value
        properties["$prefix.planId"] = stage.studyPlanId.value
        properties["$prefix.targetId"] = stage.studyTargetId.value
        properties["$prefix.name"] = stage.name
        properties["$prefix.type"] = stage.type.name
        stage.scheduledDateIso?.let { properties["$prefix.scheduledDateIso"] = it }
        properties["$prefix.weight"] = stage.weight.toString()
        stage.minimumScorePercent?.let { properties["$prefix.minimumScorePercent"] = it.toString() }
        properties["$prefix.position"] = stage.position.toString()
    }

    private fun readStage(properties: Properties, prefix: String): ExamStage = ExamStage(
        id = ExamStageId(properties.required("$prefix.id")),
        studyPlanId = StudyPlanId(properties.required("$prefix.planId")),
        studyTargetId = StudyTargetId(properties.required("$prefix.targetId")),
        name = properties.required("$prefix.name"),
        type = ExamStageType.valueOf(properties.required("$prefix.type")),
        scheduledDateIso = properties.getProperty("$prefix.scheduledDateIso"),
        weight = properties.required("$prefix.weight").toDouble(),
        minimumScorePercent = properties.optionalInt("$prefix.minimumScorePercent"),
        position = properties.requiredInt("$prefix.position"),
    )

    private fun writeSubject(properties: Properties, prefix: String, subject: Subject) {
        properties["$prefix.id"] = subject.id.value
        properties["$prefix.planId"] = subject.studyPlanId.value
        properties["$prefix.name"] = subject.name
        properties["$prefix.description"] = subject.description
        subject.colorArgb?.let { properties["$prefix.colorArgb"] = it.toString() }
        properties["$prefix.priority"] = subject.priority.toString()
        properties["$prefix.examWeight"] = subject.examWeight.toString()
        properties["$prefix.estimatedMinutes"] = subject.estimatedMinutes.toString()
        properties["$prefix.progressPercent"] = subject.progressPercent.toString()
        properties["$prefix.accuracyPercent"] = subject.accuracyPercent.toString()
        properties["$prefix.status"] = subject.status.name
        properties["$prefix.position"] = subject.position.toString()
        properties["$prefix.archived"] = subject.archived.toString()
        subject.examStageId?.let { properties["$prefix.examStageId"] = it.value }
    }

    private fun readSubject(properties: Properties, prefix: String): Subject = Subject(
        id = SubjectId(properties.required("$prefix.id")),
        studyPlanId = StudyPlanId(properties.required("$prefix.planId")),
        name = properties.required("$prefix.name"),
        description = properties.required("$prefix.description"),
        colorArgb = properties.optionalLong("$prefix.colorArgb"),
        priority = properties.requiredInt("$prefix.priority"),
        examWeight = properties.required("$prefix.examWeight").toDouble(),
        estimatedMinutes = properties.requiredInt("$prefix.estimatedMinutes"),
        progressPercent = properties.requiredInt("$prefix.progressPercent"),
        accuracyPercent = properties.requiredInt("$prefix.accuracyPercent"),
        status = ContentProgressStatus.valueOf(properties.required("$prefix.status")),
        position = properties.requiredInt("$prefix.position"),
        archived = properties.requiredBoolean("$prefix.archived"),
        examStageId = properties.getProperty("$prefix.examStageId")?.let(::ExamStageId),
    )

    private fun writeTopic(properties: Properties, prefix: String, topic: Topic) {
        properties["$prefix.id"] = topic.id.value
        properties["$prefix.planId"] = topic.studyPlanId.value
        properties["$prefix.subjectId"] = topic.subjectId.value
        topic.parentTopicId?.let { properties["$prefix.parentTopicId"] = it.value }
        properties["$prefix.name"] = topic.name
        properties["$prefix.description"] = topic.description
        properties["$prefix.priority"] = topic.priority.toString()
        properties["$prefix.difficulty"] = topic.difficulty.toString()
        topic.examIncidencePercent?.let { properties["$prefix.examIncidencePercent"] = it.toString() }
        properties["$prefix.estimatedMinutes"] = topic.estimatedMinutes.toString()
        properties["$prefix.progressPercent"] = topic.progressPercent.toString()
        properties["$prefix.masteryPercent"] = topic.masteryPercent.toString()
        properties["$prefix.status"] = topic.status.name
        properties["$prefix.position"] = topic.position.toString()
        properties["$prefix.archived"] = topic.archived.toString()
        topic.examStageId?.let { properties["$prefix.examStageId"] = it.value }
    }

    private fun readTopic(properties: Properties, prefix: String): Topic = Topic(
        id = TopicId(properties.required("$prefix.id")),
        studyPlanId = StudyPlanId(properties.required("$prefix.planId")),
        subjectId = SubjectId(properties.required("$prefix.subjectId")),
        parentTopicId = properties.getProperty("$prefix.parentTopicId")?.let(::TopicId),
        name = properties.required("$prefix.name"),
        description = properties.required("$prefix.description"),
        priority = properties.requiredInt("$prefix.priority"),
        difficulty = properties.requiredInt("$prefix.difficulty"),
        examIncidencePercent = properties.optionalInt("$prefix.examIncidencePercent"),
        estimatedMinutes = properties.requiredInt("$prefix.estimatedMinutes"),
        progressPercent = properties.requiredInt("$prefix.progressPercent"),
        masteryPercent = properties.requiredInt("$prefix.masteryPercent"),
        status = ContentProgressStatus.valueOf(properties.required("$prefix.status")),
        position = properties.requiredInt("$prefix.position"),
        archived = properties.requiredBoolean("$prefix.archived"),
        examStageId = properties.getProperty("$prefix.examStageId")?.let(::ExamStageId),
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
