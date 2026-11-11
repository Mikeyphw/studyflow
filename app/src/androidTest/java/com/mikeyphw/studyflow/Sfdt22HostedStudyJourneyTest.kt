package com.mikeyphw.studyflow

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import android.database.sqlite.SQLiteDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.diagnostics.Sfdt27DiagnosticTrace
import com.mikeyphw.studyflow.core.database.entity.StudyPlanSettingsEntity
import java.security.MessageDigest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.mikeyphw.studyflow.feature.workspace.R as WorkspaceR
import com.mikeyphw.studyflow.feature.plans.R as PlansR
import com.mikeyphw.studyflow.feature.subjects.R as SubjectsR
import com.mikeyphw.studyflow.feature.sessions.R as SessionsR
import com.mikeyphw.studyflow.feature.practice.R as PracticeR
import com.mikeyphw.studyflow.feature.reviews.R as ReviewsR

/**
 * SFDT22: real, production MainActivity UI acceptance. Every mutation below is
 * performed through production Compose controls; no injected repositories,
 * deep-link shortcuts, mock navigation, setContent or seeded domain fixtures.
 *
 * Methods are intentionally NOT order-independent: the hosted orchestrator
 * executes each method independently in the declared sequence, then force-stops
 * the app and executes the persisted-state method in a new application process.
 * The emulator MUST start without any StudyFlow installation or account data.
 */
@RunWith(AndroidJUnit4::class)
class Sfdt22HostedStudyJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val planName = "SFDT22 Study Journey"
    private val subjectName = "SFDT22 Ecology"
    private val topicName = "SFDT22 Ecosystems"
    private val sessionName = "SFDT22 Focus Session"
    private val questionStatement = "SFDT22: ecosystems include living organisms?"
    private val flashcardFront = "SFDT22 review: what is ecology?"
    private val flashcardBack = "Relationships between organisms and their environment"

    /** Read-only SQLite inspection of the *actual* app database, never a test fixture. */
    private fun recordBoundary(point: String, state: String) {
        require(point in setOf("after_create", "after_subject", "after_topic", "session_route", "before_session_start"))
        require(state in setOf("present", "missing", "lookup_failed", "ambiguous", "mismatched", "matched"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.openFileOutput("sfdt22-boundaries.txt", Context.MODE_APPEND).use { output ->
            output.write("$point|$state\n".toByteArray(Charsets.US_ASCII))
        }
    }

    private fun persistedSettingsBoundary(point: String) {
        require(point in setOf("after_create", "after_subject", "after_topic", "before_session_start"))
        // The explicit READONLY open cannot create a database or mutate app state.
        // Name is an on-device SQL argument only. No IDs, names or rows are exported.
        val state = try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = context.getDatabasePath("studyflow.db")
            if (!file.isFile) "lookup_failed" else {
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery(
                        "SELECT COUNT(*) AS matchedPlans, COUNT(settings.studyPlanId) AS storedSettings " +
                            "FROM study_plans AS plans " +
                            "LEFT JOIN study_plan_settings AS settings ON settings.studyPlanId = plans.id " +
                            "WHERE plans.name = ?",
                        arrayOf(planName),
                    ).use { cursor ->
                        if (!cursor.moveToFirst()) "lookup_failed"
                        else when {
                            cursor.getLong(0) != 1L -> "ambiguous"
                            cursor.getLong(1) == 1L -> "present"
                            else -> "missing"
                        }
                    }
                }
            }
        } catch (_: Exception) {
            "lookup_failed"
        }
        recordBoundary(point, state)
        if (state != "present") {
            throw AssertionError("SFDT22_BOUNDARY_${point.uppercase()}_${state.uppercase()}")
        }
    }

    /** HF27 private, exact-ID, read-only SQL versus a SECOND real Room handle.
     * This is NOT the production repository's transaction connection. The
     * distinction matters: disagreement is diagnostic, not proof of deletion.
     * Only allowlisted categories are exported; no hashes, IDs, rows or SQL. */
    private fun recordSfdt27Probe(point: String, producer: String) {
        require(point in setOf("after_subject", "before_topic", "after_topic_action", "after_topic_saved"))
        require(producer in setOf("subject", "topic"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = runCatching {
            val recorded = context.openFileInput("sfdt27-$producer-plan.sha256").use { input ->
                val bytes = input.readBytes()
                String(bytes, Charsets.US_ASCII).takeIf { bytes.size == 64 &&
                    it.matches(Regex("[0-9a-f]{64}")) }
            } ?: return@runCatching "ui_marker_missing"
            val file = context.getDatabasePath(StudyFlowDatabase.DATABASE_NAME)
            if (!file.isFile) return@runCatching "lookup_failed"
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
                val ids = mutableListOf<String>()
                sql.rawQuery("SELECT id FROM study_plans WHERE name = ?", arrayOf(planName)).use { cursor ->
                    while (cursor.moveToNext() && ids.size < 2) ids.add(cursor.getString(0))
                }
                if (ids.size != 1) return@runCatching "plan_ambiguous"
                val exactId = ids.single()
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(exactId.toByteArray(Charsets.UTF_8))
                    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                if (digest != recorded) return@runCatching "different_plan_id"
                // Test-only helper queries the production Room HANDLE, not a
                // second builder, before the existing independent SQL/Room pair.
                Sfdt27ProductionSettingsProbe.record(exactId, point)
                fun count(query: String, args: Array<String>): Long =
                    sql.rawQuery(query, args).use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getLong(0)
                    }
                if (count("SELECT COUNT(*) FROM subjects WHERE studyPlanId = ? AND name = ?",
                        arrayOf(exactId, subjectName)) != 1L) return@runCatching "subject_not_owned"
                // Snapshot the exact same-settings row by its REAL plan identity.
                // Only in-memory typed values and a private one-way checksum exist.
                val sqlSettings = sql.rawQuery(
                    "SELECT studyPlanId, adaptivePlanningEnabled, reviewNotificationsEnabled, " +
                        "strictExamModeEnabled, preferredSessionMinutes, quietHoursStartMinute, " +
                        "quietHoursEndMinute, configurationJson " +
                        "FROM study_plan_settings WHERE studyPlanId = ?",
                    arrayOf(exactId),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null else StudyPlanSettingsEntity(
                        studyPlanId = cursor.getString(0),
                        adaptivePlanningEnabled = cursor.getInt(1) != 0,
                        reviewNotificationsEnabled = cursor.getInt(2) != 0,
                        strictExamModeEnabled = cursor.getInt(3) != 0,
                        preferredSessionMinutes = cursor.getInt(4),
                        quietHoursStartMinute = if (cursor.isNull(5)) null else cursor.getInt(5),
                        quietHoursEndMinute = if (cursor.isNull(6)) null else cursor.getInt(6),
                        configurationJson = cursor.getString(7),
                    )
                }
                // The independent Room read lives in an Android-test-only helper;
                // the five-journey UI suite does not block coroutines directly.
                val roomSettings = Sfdt27RoomSettingsProbe.read(context, exactId)
                when {
                    sqlSettings == null && roomSettings == null -> "row_missing"
                    sqlSettings == null -> "room_present_sql_missing"
                    roomSettings == null -> "room_missing_sql_present"
                    sqlSettings != roomSettings -> "read_only_snapshot_mismatch"
                    else -> {
                        val checksum = MessageDigest.getInstance("SHA-256")
                            .digest(sqlSettings.toString().toByteArray(Charsets.UTF_8))
                            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                        val checksumFile = "sfdt27-settings-baseline.sha256"
                        if (point == "after_subject") {
                            context.openFileOutput(checksumFile, Context.MODE_PRIVATE).use { output ->
                                output.write(checksum.toByteArray(Charsets.US_ASCII))
                            }
                            "matched"
                        } else {
                            val baseline = runCatching {
                                context.openFileInput(checksumFile).use { input ->
                                    val bytes = input.readBytes()
                                    String(bytes, Charsets.US_ASCII).takeIf {
                                        bytes.size == 64 && it.matches(Regex("[0-9a-f]{64}"))
                                    }
                                }
                            }.getOrNull()
                            when {
                                baseline == null -> "baseline_missing"
                                baseline != checksum -> "row_changed"
                                else -> "matched"
                            }
                        }
                    }
                }
            }
        }.getOrDefault("lookup_failed")
        require(state in setOf("matched", "ui_marker_missing", "lookup_failed", "plan_ambiguous",
            "different_plan_id", "subject_not_owned", "room_missing_sql_present",
            "room_present_sql_missing", "row_missing", "read_only_snapshot_mismatch",
            "row_changed", "baseline_missing"))
        context.openFileOutput("sfdt27-probes.txt", Context.MODE_APPEND).use { output ->
            output.write("$point|$state\n".toByteArray(Charsets.US_ASCII))
        }
        if (point == "after_topic_action" || point == "after_topic_saved") {
            Sfdt27DiagnosticTrace.record("android_test",
                if (point == "after_topic_action") "TOPIC_EXTERNAL_PROBE_ACTION" else "TOPIC_EXTERNAL_PROBE_SAVED",
                operation = "read_settings", transaction = "unknown", observer = "independent_pair",
                plan = if (state == "plan_ambiguous" || state == "lookup_failed") "unknown" else "present",
                settings = when (state) {
                    "matched" -> "present"
                    "row_missing" -> "missing"
                    "row_changed" -> "changed"
                    else -> "unknown"
                },
                error = if (state == "lookup_failed" || state == "read_only_snapshot_mismatch")
                    "probe_error" else "none")
        }
    }

    /** On-device, read-only SQLite proof independent of Compose/snackbar state.
     * Only fixed AssertionError categories are exported by the host parser. */
    private fun assertSessionClosedInDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = context.getDatabasePath("studyflow.db")
        if (!file.isFile) throw AssertionError("SFDT22_SESSION_STATUS_LOOKUP_FAILED")
        val counts = try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT COUNT(*), " +
                        "COALESCE(SUM(CASE WHEN s.status = 'COMPLETED' " +
                        "AND s.endedAtEpochMillis IS NOT NULL AND s.notes = ? THEN 1 ELSE 0 END), 0), " +
                        "COALESCE(SUM(CASE WHEN s.status IN ('RUNNING','PAUSED') THEN 1 ELSE 0 END), 0) " +
                        "FROM study_sessions AS s " +
                        "JOIN study_plans AS p ON p.id = s.studyPlanId WHERE p.name = ?",
                    arrayOf(sessionName, planName),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null else Triple(
                        cursor.getLong(0), cursor.getLong(1), cursor.getLong(2),
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
        if (counts == null) throw AssertionError("SFDT22_SESSION_STATUS_LOOKUP_FAILED")
        if (counts != Triple(1L, 1L, 0L)) {
            throw AssertionError("SFDT22_SESSION_STATUS_NOT_COMPLETED")
        }
    }

    private fun sessionRouteBoundary() {
        val expected = rule.activity.getString(SessionsR.string.sessions_subtitle, planName)
        val matched = runCatching {
            rule.waitUntil(20_000L) {
                rule.onAllNodes(hasText(expected, substring = false), useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1
            }
            rule.onAllNodes(hasText(expected, substring = false), useUnmergedTree = true)
                .onFirst().assertIsDisplayed()
            true
        }.getOrDefault(false)
        recordBoundary("session_route", if (matched) "matched" else "mismatched")
        if (!matched) throw AssertionError("SFDT22_BOUNDARY_SESSION_ROUTE_MISMATCHED")
    }

    private fun text(resId: Int): String = rule.activity.getString(resId)

    private fun tab(name: String) = rule.onNode(
        hasTestTag("studyflow.shell.root.$name") and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasClickAction(),
        useUnmergedTree = true,
    )

    private fun innerTab(resId: Int) = rule.onAllNodes(
        hasText(text(resId)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
    ).onFirst()

    /** Wait for a real Plan workspace tab after the bottom navigation switches. */
    private fun openPlanContentTab() {
        val tabMatcher = hasText(text(WorkspaceR.string.plan_content)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val tabs = rule.onAllNodes(tabMatcher)
        // Bottom navigation may become selected while the workspace still says Loading.
        try {
            rule.waitUntil(45_000L) { tabs.fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_PLAN_CONTENT_TAB_MISSING", failure)
        }
        val tab = tabs.onFirst()
        // The workspace tab strip scrolls horizontally; Content may start clipped.
        val scroll = runCatching { tab.performScrollTo() }
        if (scroll.isFailure && runCatching { tab.assertIsDisplayed() }.isFailure) {
            throw AssertionError("SFDT22_PLAN_CONTENT_TAB_TAP_FAILED", scroll.exceptionOrNull())
        }
        try {
            tab.assertIsDisplayed()
            tab.performTouchInput { click() }
            rule.waitForIdle()
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_PLAN_CONTENT_TAB_TAP_FAILED", failure)
        }
        try {
            rule.waitUntil(25_000L) {
                runCatching { tabs.onFirst().assertIsSelected() }.isSuccess
            }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_PLAN_CONTENT_TAB_NOT_SELECTED", failure)
        }
        val manageContent = hasText(text(WorkspaceR.string.manage_content)) and hasClickAction()
        try {
            rule.waitUntil(25_000L) {
                rule.onAllNodes(manageContent).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_PLAN_CONTENT_PANE_NOT_READY", failure)
        }
    }

    private fun shell() {
        val roots = listOf("today", "plan", "practice", "progress")
        try {
            rule.waitUntil(30_000L) {
                roots.all { name ->
                    rule.onAllNodes(
                        hasTestTag("studyflow.shell.root.$name") and
                            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
                            hasClickAction(),
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes().size == 1
                }
            }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_ROOT_NAVIGATION_MISSING", failure)
        }
    }

    /** Prove the visible Plan root belongs to the user-created plan, not a restored tab. */
    private fun assertCreatedPlanWorkspaceVisible() {
        val names = rule.onAllNodes(hasText(planName, substring = false), useUnmergedTree = true)
        val correct = runCatching {
            rule.waitUntil(15_000L) { names.fetchSemanticsNodes().isNotEmpty() }
            names.onFirst().assertIsDisplayed()
            true
        }.getOrDefault(false)
        if (!correct) throw AssertionError("SFDT22_WORKSPACE_PLAN_MISMATCH")
    }

    /** Read-only identity join: never export plan IDs, names or raw database rows. */
    private fun assertCreatedPlanOwnsSavedSubject() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = context.getDatabasePath("studyflow.db")
        if (!file.isFile) throw AssertionError("SFDT22_SUBJECT_OWNER_LOOKUP_FAILED")
        val count = try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT COUNT(*) FROM subjects AS content " +
                        "JOIN study_plans AS owner ON owner.id = content.studyPlanId " +
                        "WHERE owner.name = ? AND content.name = ?",
                    arrayOf(planName, subjectName),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null else cursor.getLong(0)
                }
            }
        } catch (_: Exception) {
            null
        }
        if (count == null) throw AssertionError("SFDT22_SUBJECT_OWNER_LOOKUP_FAILED")
        if (count != 1L) throw AssertionError("SFDT22_SUBJECT_OWNER_MISMATCH")
    }

    private fun openRoot(name: String) {
        shell()
        val root = tab(name)
        root.assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
        rule.waitUntil(25_000L) {
            runCatching { tab(name).assertIsDisplayed().assertIsSelected() }.isSuccess
        }
    }

    /** Await a genuine workspace tab, its selection, and actionable pane content. */
    private fun openInnerTabReady(resId: Int, expectedActionResId: Int, alternativeActionResId: Int? = null) {
        val match = hasText(text(resId)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val tabs = rule.onAllNodes(match)
        try {
            rule.waitUntil(45_000L) { tabs.fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_INNER_TAB_MISSING", failure)
        }
        try {
            val selected = tabs.onFirst()
            runCatching { selected.performScrollTo() }
            selected.assertIsDisplayed().performTouchInput { click() }
            rule.waitForIdle()
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_INNER_TAB_TOUCH_FAILED", failure)
        }
        try {
            rule.waitUntil(25_000L) {
                runCatching { tabs.onFirst().assertIsSelected() }.isSuccess
            }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_INNER_TAB_NOT_SELECTED", failure)
        }
        val contentAction = if (alternativeActionResId == null) {
            hasText(text(expectedActionResId)) and hasClickAction()
        } else {
            (hasText(text(expectedActionResId)) or hasText(text(alternativeActionResId))) and hasClickAction()
        }
        try {
            // The expected control can be a LazyColumn item not yet composed.
            seek(contentAction)
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_INNER_PANE_NOT_READY", failure)
        }
    }

    /** The real review pane varies its action label with due-review count. */
    private fun clickReviewCenterEntry() {
        val emptyQueueAction = hasText(text(WorkspaceR.string.open_review_center)) and hasClickAction()
        if (rule.onAllNodes(emptyQueueAction).fetchSemanticsNodes().isNotEmpty()) {
            click(WorkspaceR.string.open_review_center)
        } else {
            click(WorkspaceR.string.review_now)
        }
    }

    private fun seek(matcher: SemanticsMatcher) {
        // LazyColumn items outside the viewport are not composed. Move the
        // actual production scroll container instead of accepting missing UI.
        if (rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            val scrollables = rule.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes()
            if (scrollables.isNotEmpty()) {
                runCatching { rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(matcher) }
            }
        }
        rule.waitUntil(20_000L) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Real user's route works whether Today shows no plan or an active plan. */
    private fun openSystemHub() {
        val more = rule.onNodeWithContentDescription(text(WorkspaceR.string.more))
        rule.waitUntil(30_000L) { runCatching { more.fetchSemanticsNode() }.isSuccess }
        more.assertIsDisplayed().performTouchInput { click() }
        rule.waitForIdle()
    }

    /** Click the real System Hub -> All plans row. Never invoke a route directly. */
    private fun openAllPlans() {
        val row = rule.onNodeWithTag("studyflow.system.all_plans", useUnmergedTree = true)
        rule.waitUntil(30_000L) { runCatching { row.fetchSemanticsNode() }.isSuccess }
        row.assertIsDisplayed().performTouchInput { click() }
        rule.waitForIdle()
    }

    /** Click the production Plan List floating action button. */
    private fun tapCreatePlan() {
        val button = rule.onNodeWithTag("studyflow.plan_list.create_plan", useUnmergedTree = true)
        rule.waitUntil(30_000L) { runCatching { button.fetchSemanticsNode() }.isSuccess }
        button.assertIsDisplayed().performTouchInput { click() }
        rule.waitForIdle()
    }

    /** A persisted subject CARD, not typed text inside an unsaved editor. */
    private fun awaitSubjectSaved() {
        val subjectCardTag = hasTestTag("studyflow.subject_card.name.$subjectName")
        val cards = rule.onAllNodes(subjectCardTag, useUnmergedTree = true)
        if (cards.fetchSemanticsNodes().isEmpty()) {
            val lists = rule.onAllNodes(hasScrollToIndexAction())
            if (lists.fetchSemanticsNodes().isNotEmpty()) {
                runCatching { lists.onFirst().performScrollToNode(subjectCardTag) }
            }
        }
        try {
            rule.waitUntil(35_000L) { cards.fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_SUBJECT_CARD_MISSING", failure)
        }
        val nameEditor = hasTestTag("studyflow.subject_editor.name")
        if (rule.onAllNodes(nameEditor).fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodes(nameEditor, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            throw AssertionError("SFDT22_SUBJECT_EDITOR_STILL_OPEN")
        }
        runCatching { cards.onFirst().performScrollTo() }
        cards.onFirst().assertIsDisplayed()
    }

    /** Snackbar is additional real UI evidence, not a substitute for a persisted row.
     * A Snackbar may expire before this observer runs; that alone never qualifies
     * or disqualifies a database write. The persisted row remains mandatory. */
    private var topicSaveSuccessSeen = false

    private fun observeTopicSaveFeedback() {
        val saved = rule.onAllNodes(hasText(text(SubjectsR.string.topic_saved)))
        val failed = rule.onAllNodes(hasText(text(SubjectsR.string.operation_failed)))
        var savedSeen = false
        var failedSeen = false
        // The real save is asynchronous. Observe actual production feedback,
        // without requiring a transient Snackbar to outlive its UI lifetime.
        runCatching {
            rule.waitUntil(12_000L) {
                failedSeen = failedSeen || failed.fetchSemanticsNodes().isNotEmpty()
                savedSeen = savedSeen || saved.fetchSemanticsNodes().isNotEmpty()
                failedSeen || savedSeen
            }
        }
        if (failedSeen) {
            // The production app writes only a strict code in its private debug
            // data. The actual exception, row values, names and IDs never leave it.
            val diagnosticCode = runCatching {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                context.openFileInput("sfdt22-topic-failure.txt").use { input ->
                    val buffer = ByteArray(64)
                    val length = input.read(buffer)
                    if (length <= 0 || input.read() != -1) null
                    else String(buffer, 0, length, Charsets.US_ASCII).takeIf {
                        it in setOf(
                            "settings_before_topic", "settings_before_progress",
                            "settings_changed_progress", "settings_changed_topic",
                            "sqlite_constraint", "sqlite_locked", "sqlite_other",
                            "invalid_input", "unexpected",
                        )
                    }
                }
            }.getOrNull()
            if (diagnosticCode != null) {
                throw AssertionError("SFDT22_TOPIC_CAUSE_" + diagnosticCode.uppercase())
            }
        }
        if (failedSeen) throw AssertionError("SFDT22_TOPIC_SAVE_FAILED")
        topicSaveSuccessSeen = savedSeen
    }

    /** Operate exactly the saved subject using its real production Compose action.
     * Never assume a touch succeeded: prove the collapse-tag transition. */
    private fun ensureSavedSubjectExpanded() {
        awaitSubjectSaved()
        val subjectCardTag = hasTestTag("studyflow.subject_card.name.$subjectName")
        val collapsedControl = hasClickAction() and
            hasTestTag("studyflow.subject_card.expand") and hasAnyAncestor(subjectCardTag)
        val expandedControl = hasClickAction() and
            hasTestTag("studyflow.subject_card.collapse") and hasAnyAncestor(subjectCardTag)
        val collapsed = rule.onAllNodes(collapsedControl, useUnmergedTree = true)
        val expanded = rule.onAllNodes(expandedControl, useUnmergedTree = true)
        if (expanded.fetchSemanticsNodes().size == 1) return
        try {
            rule.waitUntil(12_000L) {
                collapsed.fetchSemanticsNodes().size == 1 ||
                    expanded.fetchSemanticsNodes().size == 1
            }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_SUBJECT_EXPAND_ACTION_MISSING", failure)
        }
        if (expanded.fetchSemanticsNodes().size == 1) return
        if (collapsed.fetchSemanticsNodes().size != 1) {
            throw AssertionError("SFDT22_SUBJECT_EXPAND_ACTION_MISSING")
        }
        // Prefer the genuine TextButton's Semantics click action: a screen
        // Snackbar may cover the visual coordinate but cannot intercept it.
        // Retain failure status rather than silently treating a thrown click
        // as a successful action.
        val semanticsClick = runCatching {
            val action = collapsed.onFirst()
            runCatching { action.performScrollTo() }
            action.performClick()
            rule.waitForIdle()
        }
        if (expanded.fetchSemanticsNodes().size == 1) return
        val expandedAfterSemantics = runCatching {
            rule.waitUntil(5_000L) { expanded.fetchSemanticsNodes().size == 1 }
        }.isSuccess
        if (expandedAfterSemantics || expanded.fetchSemanticsNodes().size == 1) return
        // Ensure the original saved subject is still uniquely collapsed before
        // a fallback: tapping an already expanded card would collapse it again.
        if (collapsed.fetchSemanticsNodes().size != 1) {
            throw AssertionError("SFDT22_SUBJECT_EXPAND_STATE_AMBIGUOUS")
        }
        val subjectSnackbar = rule.onAllNodes(hasText(text(SubjectsR.string.subject_saved)))
        val topicSnackbar = rule.onAllNodes(hasText(text(SubjectsR.string.topic_saved)))
        val failureSnackbar = rule.onAllNodes(hasText(text(SubjectsR.string.operation_failed)))
        if (failureSnackbar.fetchSemanticsNodes().isNotEmpty()) {
            throw AssertionError("SFDT22_TOPIC_SAVE_FAILED")
        }
        try {
            rule.waitUntil(15_000L) {
                subjectSnackbar.fetchSemanticsNodes().isEmpty() &&
                    topicSnackbar.fetchSemanticsNodes().isEmpty() &&
                    failureSnackbar.fetchSemanticsNodes().isEmpty()
            }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_SUBJECT_EXPAND_SNACKBAR_OBSTRUCTION", failure)
        }
        if (expanded.fetchSemanticsNodes().size == 1) return
        if (collapsed.fetchSemanticsNodes().size != 1) {
            throw AssertionError("SFDT22_SUBJECT_EXPAND_STATE_AMBIGUOUS")
        }
        // Exactly one real pointer retry, only after the semantic action has
        // failed to establish expanded state. Never insert directly in Room.
        try {
            val action = collapsed.onFirst()
            runCatching { action.performScrollTo() }
            action.assertIsDisplayed().performTouchInput { click() }
            rule.waitForIdle()
        } catch (failure: Throwable) {
            if (semanticsClick.isFailure) {
                throw AssertionError("SFDT22_SUBJECT_EXPAND_SEMANTIC_CLICK_FAILED", failure)
            }
            throw AssertionError("SFDT22_SUBJECT_EXPAND_TOUCH_FAILED", failure)
        }
        try {
            rule.waitUntil(12_000L) { expanded.fetchSemanticsNodes().size == 1 }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_SUBJECT_NOT_EXPANDED", failure)
        }
    }

    /** A user can find a saved topic through syllabus search even when the
     * lazily rendered list did not initially compose its row. Only the actual
     * topic Text node can qualify this fallback; a Snackbar cannot. */
    private fun findSavedTopicThroughSearch(): Boolean {
        val searchTag = hasTestTag("studyflow.syllabus.search")
        val searchMatcher = hasSetTextAction() and (searchTag or hasAnyAncestor(searchTag))
        val searchEditors = rule.onAllNodes(searchMatcher, useUnmergedTree = true)
        if (searchEditors.fetchSemanticsNodes().isEmpty()) {
            // Locate the real search field in the production LazyColumn, without
            // creating or writing any domain object.
            val lists = rule.onAllNodes(hasScrollToIndexAction())
            if (lists.fetchSemanticsNodes().isNotEmpty()) {
                runCatching { lists.onFirst().performScrollToNode(searchTag) }
            }
        }
        try {
            rule.waitUntil(12_000L) { searchEditors.fetchSemanticsNodes().size == 1 }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_TOPIC_SEARCH_EDITOR_MISSING", failure)
        }
        try {
            val searchEditor = searchEditors.onFirst()
            runCatching { searchEditor.performScrollTo() }
            searchEditor.assertIsDisplayed().performTextReplacement(topicName)
            searchEditor.assertTextContains(topicName)
            rule.waitForIdle()
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_TOPIC_SEARCH_WRITE_FAILED", failure)
        }
        // Production query.isNotBlank() expands matching subjects. Require
        // exactly one real, saved-topic Text node to become visible.
        val tag = hasTestTag("studyflow.topic_row.name.$topicName")
        val matchingRows = rule.onAllNodes(tag, useUnmergedTree = true)
        val errorFeedback = rule.onAllNodes(hasText(text(SubjectsR.string.operation_failed)))
        runCatching {
            rule.waitUntil(25_000L) {
                matchingRows.fetchSemanticsNodes().size == 1 ||
                    errorFeedback.fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (errorFeedback.fetchSemanticsNodes().isNotEmpty()) {
            throw AssertionError("SFDT22_TOPIC_SAVE_FAILED")
        }
        if (matchingRows.fetchSemanticsNodes().size != 1) return false
        runCatching { matchingRows.onFirst().performScrollTo() }
        // Never accept a matching name in a hidden or uncomposed row.
        return runCatching { matchingRows.onFirst().assertIsDisplayed() }.isSuccess
    }

    /** Verify a persisted topic row in the actually expanded saved subject. */
    private fun awaitTopicSaved() {
        ensureSavedSubjectExpanded()
        val editorTag = hasTestTag("studyflow.topic_editor.name")
        if (rule.onAllNodes(editorTag).fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodes(editorTag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            throw AssertionError("SFDT22_TOPIC_EDITOR_STILL_OPEN")
        }
        val topicTag = hasTestTag("studyflow.topic_row.name.$topicName")
        val rows = rule.onAllNodes(topicTag, useUnmergedTree = true)
        // A lazy row may not yet be composed. Ask the real outer list to
        // scroll to this content before treating its absence as a timeout.
        val topicLists = rule.onAllNodes(hasScrollToIndexAction())
        if (rows.fetchSemanticsNodes().isEmpty() &&
            topicLists.fetchSemanticsNodes().isNotEmpty()) {
            runCatching { topicLists.onFirst().performScrollToNode(topicTag) }
        }
        val failureToast = rule.onAllNodes(hasText(text(SubjectsR.string.operation_failed)))
        var saveFailed = false
        try {
            rule.waitUntil(35_000L) {
                saveFailed = saveFailed || failureToast.fetchSemanticsNodes().isNotEmpty()
                saveFailed || rows.fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            if (saveFailed) throw AssertionError("SFDT22_TOPIC_SAVE_FAILED", failure)
            val subjectCardTag = hasTestTag("studyflow.subject_card.name.$subjectName")
            val expandedControl = hasTestTag("studyflow.subject_card.collapse") and
                hasAnyAncestor(subjectCardTag)
            if (rule.onAllNodes(expandedControl, useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty()) {
                throw AssertionError("SFDT22_SUBJECT_NOT_EXPANDED", failure)
            }
            // Save feedback alone never qualifies a successful journey. On a
            // lazy-row timeout, use actual production search to reveal the
            // exact topic name, then require its visible Text node.
            if (findSavedTopicThroughSearch()) return
            if (topicSaveSuccessSeen) {
                // Even after a real UI search, no saved topic row was visible.
                throw AssertionError("SFDT22_TOPIC_ROW_NOT_COMPOSED", failure)
            }
            throw AssertionError("SFDT22_TOPIC_ROW_MISSING", failure)
        }
        if (saveFailed) throw AssertionError("SFDT22_TOPIC_SAVE_FAILED")
        if (rows.fetchSemanticsNodes().isEmpty()) {
            throw AssertionError("SFDT22_TOPIC_ROW_MISSING")
        }
        runCatching { rows.onFirst().performScrollTo() }
        rows.onFirst().assertIsDisplayed()
    }

    /** Scope a real click action to the SUBJECT WE JUST SAVED. */
    private fun openTopicEditorFromSubject() {
        awaitSubjectSaved()
        val subjectCardTag = hasTestTag("studyflow.subject_card.name.$subjectName")
        val addTag = hasTestTag("studyflow.subject_card.add_topic")
        val addActions = rule.onAllNodes(
            hasClickAction() and hasAnyAncestor(subjectCardTag) and
                (addTag or hasAnyAncestor(addTag)),
            useUnmergedTree = true,
        )
        try {
            rule.waitUntil(25_000L) { addActions.fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_TOPIC_ADD_BUTTON_MISSING", failure)
        }
        if (addActions.fetchSemanticsNodes().size != 1) {
            throw AssertionError("SFDT22_TOPIC_ADD_BUTTON_AMBIGUOUS")
        }
        val beforeFields = rule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size
        val action = addActions.onFirst()
        runCatching { action.performScrollTo() }
        action.assertIsDisplayed()
        action.performClick()
        rule.waitForIdle()
        // A dialog is positively evidenced by new real editable controls
        // and not only by a testTag which can disappear between semantics trees.
        val nameTag = hasTestTag("studyflow.topic_editor.name")
        val taggedMerged = rule.onAllNodes(nameTag)
        val taggedUnmerged = rule.onAllNodes(nameTag, useUnmergedTree = true)
        val dialogTag = hasTestTag("studyflow.topic_editor.dialog")
        val modalMerged = rule.onAllNodes(dialogTag)
        val modalUnmerged = rule.onAllNodes(dialogTag, useUnmergedTree = true)
        var afterFields = beforeFields
        fun editorVisible(): Boolean {
            afterFields = rule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
                .fetchSemanticsNodes().size
            return modalMerged.fetchSemanticsNodes().isNotEmpty() ||
                modalUnmerged.fetchSemanticsNodes().isNotEmpty() ||
                taggedMerged.fetchSemanticsNodes().isNotEmpty() ||
                taggedUnmerged.fetchSemanticsNodes().isNotEmpty() ||
                afterFields >= beforeFields + 3
        }
        // A semantics click is attempted once. If it never opens the editor,
        // recover with one real pointer tap, after the save Snackbar has cleared.
        // Add Topic only opens a form: unlike Save, this retry cannot insert data.
        if (runCatching { rule.waitUntil(4_000L) { editorVisible() } }.isFailure &&
            !editorVisible()) {
            val saveSnackbar = rule.onAllNodes(hasText(text(SubjectsR.string.subject_saved)))
            try {
                rule.waitUntil(12_000L) { saveSnackbar.fetchSemanticsNodes().isEmpty() }
            } catch (failure: Throwable) {
                throw AssertionError("SFDT22_TOPIC_SNACKBAR_OBSTRUCTION", failure)
            }
            if (!editorVisible()) {
                if (addActions.fetchSemanticsNodes().size != 1) {
                    throw AssertionError("SFDT22_TOPIC_ADD_BUTTON_AMBIGUOUS")
                }
                try {
                    val retry = addActions.onFirst()
                    runCatching { retry.performScrollTo() }
                    retry.assertIsDisplayed()
                    retry.performTouchInput { click() }
                    rule.waitForIdle()
                } catch (failure: Throwable) {
                    throw AssertionError("SFDT22_TOPIC_FALLBACK_TOUCH_FAILED", failure)
                }
            }
        }
        try {
            rule.waitUntil(25_000L) { editorVisible() }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_TOPIC_DIALOG_MISSING", failure)
        }
        if (taggedMerged.fetchSemanticsNodes().isEmpty() &&
            taggedUnmerged.fetchSemanticsNodes().isEmpty()) {
            throw AssertionError("SFDT22_TOPIC_EDITOR_TAG_MISSING")
        }
    }

    /** Edit only the active production field, in either Compose semantics tree. */
    private fun replaceTopicNameInDialog(value: String) {
        val nameTag = hasTestTag("studyflow.topic_editor.name")
        val tagged = rule.onAllNodes(nameTag, useUnmergedTree = true)
        val taggedMerged = rule.onAllNodes(nameTag)
        try {
            rule.waitUntil(20_000L) {
                tagged.fetchSemanticsNodes().isNotEmpty() ||
                    taggedMerged.fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_TOPIC_DIALOG_MISSING", failure)
        }
        val editableMatcher = hasSetTextAction() and (nameTag or hasAnyAncestor(nameTag))
        val unmergedFields = rule.onAllNodes(editableMatcher, useUnmergedTree = true)
        val mergedFields = rule.onAllNodes(hasSetTextAction() and nameTag)
        try {
            rule.waitUntil(20_000L) {
                mergedFields.fetchSemanticsNodes().isNotEmpty() ||
                    unmergedFields.fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_TOPIC_EDITABLE_MISSING", failure)
        }
        val fields = if (mergedFields.fetchSemanticsNodes().isNotEmpty()) mergedFields else unmergedFields
        // Both matchers are scoped to the unique actual production topic-name testTag.
        val editor = fields.onFirst()
        editor.assertIsDisplayed().performTextReplacement(value)
        rule.waitForIdle()
        val confirmed = rule.onAllNodes(editableMatcher and hasText(value), useUnmergedTree = true)
        if (confirmed.fetchSemanticsNodes().isEmpty() &&
            rule.onAllNodes(hasSetTextAction() and nameTag and hasText(value)).fetchSemanticsNodes().isEmpty()) {
            try {
                editor.assertTextContains(value)
            } catch (failure: Throwable) {
                throw AssertionError("SFDT22_TOPIC_VALUE_NOT_WRITTEN", failure)
            }
        }
    }

    private fun click(resId: Int) = clickLabel(text(resId))

    private fun clickLabel(label: String) {
        // The matching node must expose a real production click action.
        val matcher = hasText(label) and hasClickAction()
        seek(matcher)
        val node = rule.onAllNodes(matcher).onFirst()
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun clickDialog(resId: Int) {
        // Popups can leave a same-labelled button rendered behind the modal.
        // Select the final visible matching action from the active dialog.
        val matcher = hasText(text(resId)) and hasClickAction()
        seek(matcher)
        rule.onAllNodes(matcher).onLast().performClick()
        rule.waitForIdle()
    }

    private fun fill(resId: Int, value: String) {
        // Material3 labels may be separate semantics nodes from editable text.
        // In the first, real plan wizard step the plan name is the FIRST of two
        // fields (name, description). Target its actual SetText action, not the
        // implementation-dependent label merge. Do not alter production UI.
        if (resId == PlansR.string.plan_name) {
            val fields = rule.onAllNodes(hasSetTextAction())
            rule.waitUntil(20_000L) { fields.fetchSemanticsNodes().size == 2 }
            fields.onFirst().performTextReplacement(value)
        } else {
            val label = text(resId)
            val matcher = hasSetTextAction() and hasText(label, substring = true)
            seek(matcher)
            rule.onNode(matcher).performTextReplacement(value)
        }
        rule.waitForIdle()
    }

    private fun stage(name: String, action: () -> Unit) {
        // A stable allowlisted stage marker travels inside AndroidJUnitRunner's
        // failure stack. Only the marker and a generic reason code survive on CI.
        try {
            action()
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_STEP_" + name, failure)
        }
    }

    private fun visible(value: String) {
        val matcher = hasText(value, substring = true)
        seek(matcher)
        rule.onAllNodes(matcher).onFirst().assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        require(name in setOf("plan", "subject", "topic", "session", "question", "review", "restart"))
        rule.waitForIdle()
        val image = rule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(image.width >= 320 && image.height >= 480)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.openFileOutput("sfdt22-ui-$name.png", Context.MODE_PRIVATE).use {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun realUserCreatesAStudyPlan() {
        Sfdt27DiagnosticTrace.resetForHostedJourney()
        // Real UI, fresh emulator and production plan persistence. The stage
        // wrappers do not catch/suppress failures; they rethrow for a red CI.
        // Separate readiness, actual touch, and editor load in the receipt:
        // a time-out now identifies exactly which on-screen step failed.
        stage("plan_shell") { shell() }
        stage("plan_system_hub") { openSystemHub() }
        stage("plan_all_plans") { openAllPlans() }
        stage("plan_open") { tapCreatePlan() }
        stage("plan_editor_ready") {
            rule.waitUntil(25_000L) {
                // The production objective step has name and description.
                rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 2
            }
        }
        stage("plan_name") { fill(PlansR.string.plan_name, planName) }
        stage("plan_target") { click(PlansR.string.next) }
        stage("plan_preferences") { click(PlansR.string.next) } // Default 20h, 45min.
        stage("plan_save") { click(PlansR.string.save_plan) }
        stage("plan_confirm") { shell(); visible(planName) }
        stage("settings_after_create") { persistedSettingsBoundary("after_create") }
        stage("plan_capture") { screenshot("plan") }
    }

    @Test fun realUserAddsSubjectAndTopic() {
        Sfdt27DiagnosticTrace.resetForHostedJourney()
        // Every action operates on the production UI, not injected domain data.
        // Dialog name fields have distinct semantics tags to prevent multiple
        // "Name" nodes from being accidentally selected by label substring.
        stage("subject_open_root") { openRoot("plan") }
        stage("subject_plan_identity") { assertCreatedPlanWorkspaceVisible() }
        stage("subject_open_content") { openPlanContentTab() }
        stage("subject_open_manager") { click(WorkspaceR.string.manage_content) }
        // A content description can describe more than one Compose semantics node.
        // Select the *production* FAB by its unique tag and actionable semantics.
        stage("subject_fab_ready") {
            val fab = rule.onAllNodes(
                hasTestTag("studyflow.syllabus.add_subject_fab") and hasClickAction(),
                useUnmergedTree = true,
            )
            rule.waitUntil(25_000L) {
                fab.fetchSemanticsNodes().size == 1 &&
                    runCatching { fab.onFirst().assertIsDisplayed().assertIsEnabled() }.isSuccess
            }
        }
        stage("subject_open_dialog") {
            rule.onAllNodes(
                hasTestTag("studyflow.syllabus.add_subject_fab") and hasClickAction(),
                useUnmergedTree = true,
            ).onFirst().performTouchInput { click() }
            rule.waitForIdle()
        }
        stage("subject_editor_ready") {
            val editor = rule.onAllNodes(
                hasTestTag("studyflow.subject_editor.name") and hasSetTextAction(),
                useUnmergedTree = true,
            )
            rule.waitUntil(25_000L) {
                editor.fetchSemanticsNodes().size == 1 &&
                    runCatching { editor.onFirst().assertIsDisplayed() }.isSuccess
            }
        }
        stage("subject_fill_name") {
            rule.onNodeWithTag("studyflow.subject_editor.name").performTextReplacement(subjectName)
            rule.waitForIdle()
        }
        stage("subject_save") { clickDialog(SubjectsR.string.save) }
        stage("subject_confirm") { awaitSubjectSaved() }
        stage("subject_owner_confirm") { assertCreatedPlanOwnsSavedSubject() }
        stage("settings_after_subject") { persistedSettingsBoundary("after_subject") }
        stage("sfdt27_after_subject") { recordSfdt27Probe("after_subject", "subject") }
        stage("subject_capture") { screenshot("subject") }
        stage("sfdt27_before_topic") { recordSfdt27Probe("before_topic", "subject") }
        stage("topic_open_dialog") { openTopicEditorFromSubject() }
        stage("topic_fill_name") { replaceTopicNameInDialog(topicName) }
        stage("topic_save") { clickDialog(SubjectsR.string.save) }
        stage("sfdt27_after_topic_action") { recordSfdt27Probe("after_topic_action", "topic") }
        stage("topic_save_feedback") { observeTopicSaveFeedback() }
        stage("topic_expand") { ensureSavedSubjectExpanded() }
        stage("topic_confirm") { awaitTopicSaved() }
        stage("sfdt27_after_topic_saved") { recordSfdt27Probe("after_topic_saved", "topic") }
        stage("settings_after_topic") { persistedSettingsBoundary("after_topic") }
        stage("topic_capture") { screenshot("topic") }
    }

    /** Exact production session control; require it to be visible and enabled. */
    private fun awaitSessionAction(tag: String) {
        val actions = rule.onAllNodes(hasTestTag(tag) and hasClickAction(), useUnmergedTree = true)
        try {
            rule.waitUntil(35_000L) {
                actions.fetchSemanticsNodes().size == 1 &&
                    runCatching { actions.onFirst().assertIsDisplayed().assertIsEnabled() }.isSuccess
            }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_SESSION_CONTROL_NOT_READY", failure)
        }
    }

    private fun clickSessionAction(tag: String) {
        awaitSessionAction(tag)
        rule.onAllNodes(hasTestTag(tag) and hasClickAction(), useUnmergedTree = true)
            .onFirst().performClick()
        rule.waitForIdle()
    }

    /** Material3 may not merge an OutlinedTextField's label with its editor. */
    private fun awaitSessionEditor(tag: String) {
        val tagged = hasTestTag(tag)
        val inputs = rule.onAllNodes(
            hasSetTextAction() and (tagged or hasAnyAncestor(tagged)),
            useUnmergedTree = true,
        )
        try {
            rule.waitUntil(30_000L) {
                if (inputs.fetchSemanticsNodes().size != 1) false else {
                    runCatching { inputs.onFirst().performScrollTo() }
                    runCatching { inputs.onFirst().assertIsDisplayed() }.isSuccess
                }
            }
        } catch (failure: Throwable) {
            throw AssertionError("SFDT22_SESSION_EDITOR_NOT_READY", failure)
        }
    }

    private fun writeSessionEditor(tag: String, value: String) {
        awaitSessionEditor(tag)
        val tagged = hasTestTag(tag)
        val input = rule.onAllNodes(
            hasSetTextAction() and (tagged or hasAnyAncestor(tagged)),
            useUnmergedTree = true,
        ).onFirst()
        input.performTextReplacement(value)
        input.assertTextContains(value)
        rule.waitForIdle()
    }

    /** Distinguish production start errors from real UI state/semantics failures.
     * No mocks, injected data, bypassed repository writes, or diagnostic screenshots. */
    private fun awaitSessionStartedAndRunning() {
        val tag = hasTestTag("studyflow.session.active.pause")
        val tagged = rule.onAllNodes(tag, useUnmergedTree = true)
        val actions = rule.onAllNodes(tag and hasClickAction(), useUnmergedTree = true)
        val failures = rule.onAllNodes(hasText(text(SessionsR.string.operation_failed)))
        val successes = rule.onAllNodes(hasText(text(SessionsR.string.session_started)))
        var failureSeen = false
        var successSeen = false
        try {
            rule.waitUntil(35_000L) {
                failureSeen = failureSeen || failures.fetchSemanticsNodes().isNotEmpty()
                successSeen = successSeen || successes.fetchSemanticsNodes().isNotEmpty()
                if (failureSeen) true else {
                    if (actions.fetchSemanticsNodes().size != 1) false else {
                        val button = actions.onFirst()
                        runCatching { button.performScrollTo() }
                        runCatching { button.assertIsDisplayed().assertIsEnabled() }.isSuccess
                    }
                }
            }
        } catch (_: Exception) {
            // A timeout is classified below using fresh production semantics.
            // Never convert a real UI failure into a successful test.
        }
        // A real production error always wins over a possible stale UI node.
        if (failureSeen || failures.fetchSemanticsNodes().isNotEmpty()) {
            val categories = listOf(
                "studyflow.session.start.failure.validation" to
                    "SFDT22_SESSION_START_VALIDATION_FAILED",
                "studyflow.session.start.failure.plan_settings" to
                    "SFDT22_SESSION_START_PLAN_SETTINGS_FAILED",
                "studyflow.session.start.failure.plan_settings_missing" to
                    "SFDT22_SESSION_START_PLAN_SETTINGS_MISSING",
                "studyflow.session.start.failure.plan_settings_lookup_failed" to
                    "SFDT22_SESSION_START_PLAN_SETTINGS_LOOKUP_FAILED",
                "studyflow.session.start.failure.active_session" to
                    "SFDT22_SESSION_START_ALREADY_ACTIVE",
                "studyflow.session.start.failure.active_lookup" to
                    "SFDT22_SESSION_START_ACTIVE_LOOKUP_FAILED",
                "studyflow.session.start.failure.persistence" to
                    "SFDT22_SESSION_START_PERSISTENCE_FAILED",
            )
            val matches = categories.filter { (tag, _) ->
                rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            if (matches.size > 1) {
                throw AssertionError("SFDT22_SESSION_START_DIAGNOSTIC_AMBIGUOUS")
            }
            if (matches.size == 1) {
                throw AssertionError(matches.single().second)
            }
            throw AssertionError("SFDT22_SESSION_START_FAILED")
        }
        val taggedCount = tagged.fetchSemanticsNodes().size
        val actionCount = actions.fetchSemanticsNodes().size
        if (taggedCount > 1 || actionCount > 1) {
            throw AssertionError("SFDT22_SESSION_RUNNING_CONTROL_AMBIGUOUS")
        }
        if (taggedCount == 0) {
            if (successSeen) throw AssertionError("SFDT22_SESSION_START_SUCCEEDED_NO_RUNNING_UI")
            throw AssertionError("SFDT22_SESSION_RUNNING_CONTROL_MISSING")
        }
        if (actionCount == 0) {
            throw AssertionError("SFDT22_SESSION_RUNNING_ACTION_MISSING")
        }
        val button = actions.onFirst()
        runCatching { button.performScrollTo() }
        if (runCatching { button.assertIsEnabled() }.isFailure) {
            throw AssertionError("SFDT22_SESSION_RUNNING_CONTROL_DISABLED")
        }
        if (runCatching { button.assertIsDisplayed() }.isFailure) {
            throw AssertionError("SFDT22_SESSION_RUNNING_CONTROL_OFFSCREEN")
        }
        // Success means the real RUNNING button is unique, enabled, and visible.
        button.assertIsDisplayed().assertIsEnabled()
    }

    @Test fun realUserStartsPausesResumesAndCompletesASession() {
        stage("session_open_root") { openRoot("plan") }
        stage("session_open_overview") {
            openInnerTabReady(WorkspaceR.string.plan_overview, WorkspaceR.string.study_sessions)
        }
        stage("session_open_sessions") { click(WorkspaceR.string.study_sessions) }
        stage("session_route_identity") { sessionRouteBoundary() }
        stage("session_quick_dialog") {
            val action = rule.onNodeWithContentDescription(text(SessionsR.string.quick_session))
            rule.waitUntil(30_000L) {
                runCatching { action.assertIsDisplayed().assertIsEnabled() }.isSuccess
            }
            action.performClick()
            awaitSessionEditor("studyflow.session.quick.title")
        }
        stage("session_title") { writeSessionEditor("studyflow.session.quick.title", sessionName) }
        stage("settings_before_session_start") { persistedSettingsBoundary("before_session_start") }
        stage("session_start") { clickSessionAction("studyflow.session.quick.start") }
        stage("session_running") { awaitSessionStartedAndRunning() }
        stage("session_pause") { clickSessionAction("studyflow.session.active.pause") }
        stage("session_paused") { awaitSessionAction("studyflow.session.active.resume") }
        stage("session_resume") { clickSessionAction("studyflow.session.active.resume") }
        stage("session_resumed") { awaitSessionAction("studyflow.session.active.pause") }
        stage("session_finish_open") { clickSessionAction("studyflow.session.active.finish") }
        stage("session_finish_dialog") { awaitSessionEditor("studyflow.session.finish.notes") }
        stage("session_finish_notes") {
            writeSessionEditor("studyflow.session.finish.notes", sessionName)
        }
        stage("session_finish_confirm") { clickSessionAction("studyflow.session.finish.confirm") }
        stage("session_completion_ui") {
            // A title alone is not completion: require the production no-active state.
            val noActive = hasText(text(SessionsR.string.no_active_session))
            rule.waitUntil(35_000L) {
                rule.onAllNodes(noActive).fetchSemanticsNodes().isNotEmpty() &&
                    listOf("studyflow.session.active.pause", "studyflow.session.active.resume",
                           "studyflow.session.active.finish").all { tag ->
                        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                            .fetchSemanticsNodes().isEmpty()
                    }
            }
        }
        stage("session_completion_persisted") { assertSessionClosedInDatabase() }
        stage("session_finished") { visible(sessionName) }
        stage("session_capture") { screenshot("session") }
        stage("session_return_to_workspace") {
            // Real in-app Back control, never injected NavController/deep-link routing.
            rule.onNodeWithContentDescription(text(SessionsR.string.go_back))
                .assertIsDisplayed().performClick()
            rule.waitForIdle()
        }
        stage("session_workspace_ready") { shell() }
    }

    @Test fun realUserAnswersQuestionAndOpensReviewQueue() {
        // Independent test invocation: recheck actual persisted session state.
        stage("practice_session_closed") { assertSessionClosedInDatabase() }
        stage("practice_shell_ready") { shell() }
        stage("practice_open_root") { openRoot("practice") }
        stage("practice_tab_selected") { tab("practice").assertIsDisplayed().assertIsSelected() }
        stage("practice_open_questions") {
            openInnerTabReady(WorkspaceR.string.practice_questions, WorkspaceR.string.open_question_bank)
        }
        stage("practice_enter_question_bank") { click(WorkspaceR.string.open_question_bank) }
        stage("practice_open_question_editor") {
            rule.onNodeWithContentDescription(text(PracticeR.string.add_question)).performClick()
        }
        stage("practice_choose_true_false") {
            clickLabel("true false") // Real production QuestionType.TRUE_FALSE selector
        }
        stage("practice_fill_statement") { fill(PracticeR.string.statement, questionStatement) }
        stage("practice_save_question") { click(PracticeR.string.save_question) }
        stage("practice_question_visible") { visible(questionStatement) }
        stage("practice_create_test") { click(PracticeR.string.create_practice_test) }
        // The new StudyTest is saved asynchronously. Require its actual, actionable
        // production button BEFORE selecting it; no sleeps, seeded rows or shortcuts.
        stage("practice_test_ready") {
            visible(text(PracticeR.string.start_or_resume))
        }
        stage("practice_start_test") { click(PracticeR.string.start_or_resume) }
        stage("practice_runner_question") { visible(questionStatement) }
        stage("practice_answer_true") { clickLabel("True") }
        // This snackbar is shown only after the real answer operation completes.
        stage("practice_answer_acknowledged") { visible(text(PracticeR.string.answer_saved)) }
        stage("practice_finish_test") { click(PracticeR.string.finish_attempt) }
        stage("practice_test_finished") { visible(text(PracticeR.string.attempt_finished)) }
        stage("practice_capture_question") { screenshot("question") }
        stage("practice_back_to_workspace") {
            rule.onNodeWithContentDescription(text(PracticeR.string.go_back)).performClick()
        }
        stage("practice_open_reviews") {
            openInnerTabReady(
                WorkspaceR.string.practice_reviews,
                WorkspaceR.string.open_review_center,
                WorkspaceR.string.review_now,
            )
        }
        stage("practice_review_center") { clickReviewCenterEntry() }
        stage("practice_card_front") { fill(ReviewsR.string.flashcard_front, flashcardFront) }
        stage("practice_card_back") { fill(ReviewsR.string.flashcard_back, flashcardBack) }
        stage("practice_save_card") { click(ReviewsR.string.create_flashcard) }
        stage("practice_card_visible") { visible(flashcardFront) }
        stage("practice_capture_review") { screenshot("review") }
    }

    @Test fun realUserSeesPersistedProgressAfterColdRestart() {
        // This method is run ONLY after ADB force-stop and a new MainActivity PID.
        shell()
        visible(planName)
        openRoot("plan")
        stage("restart_open_content") { openPlanContentTab() }
        click(WorkspaceR.string.manage_content)
        awaitSubjectSaved()
        ensureSavedSubjectExpanded()
        awaitTopicSaved()
        rule.onNodeWithContentDescription(text(SubjectsR.string.go_back)).performClick()
        stage("restart_open_overview") {
            openInnerTabReady(WorkspaceR.string.plan_overview, WorkspaceR.string.study_sessions)
        }
        click(WorkspaceR.string.study_sessions)
        visible(sessionName)
        rule.onNodeWithContentDescription(text(SessionsR.string.go_back)).performClick()
        openRoot("practice")
        stage("restart_open_questions") {
            openInnerTabReady(WorkspaceR.string.practice_questions, WorkspaceR.string.open_question_bank)
        }
        visible(questionStatement)
        stage("restart_open_reviews") {
            openInnerTabReady(
                WorkspaceR.string.practice_reviews,
                WorkspaceR.string.open_review_center,
                WorkspaceR.string.review_now,
            )
        }
        clickReviewCenterEntry()
        visible(flashcardFront)
        screenshot("restart")
    }
}
