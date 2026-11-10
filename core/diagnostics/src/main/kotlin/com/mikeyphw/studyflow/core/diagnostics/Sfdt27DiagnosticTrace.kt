package com.mikeyphw.studyflow.core.diagnostics

/**
 * HF27 DIAG-01: DEBUG-ONLY, application-private, allowlisted observation ledger.
 * No plan/subject/topic identifiers, names, values, hashes, SQL or throwable text
 * may enter this API. Caller-supplied values are checked against fixed enums.
 * The Android host installs an atomic byte sink only in a debuggable process.
 */
object Sfdt27DiagnosticTrace {
    private const val MAX_EVENTS = 64
    private const val MAX_BYTES = 16_384
    private val areas = setOf("ui", "android_test", "content_repository", "plan_repository", "seed", "database_sql", "database_lifecycle", "portability", "sync_restore", "sample_provision")
    private val checkpoints = setOf(
        "PLAN_CREATED", "PLAN_UPDATED", "SUBJECT_TRANSACTION_START", "SUBJECT_COMMIT",
        "TOPIC_UI_CLICK", "TOPIC_COROUTINE_START", "TOPIC_TRANSACTION_START",
        "TOPIC_PRECHECK", "TOPIC_UPSERT_DONE", "PROGRESS_PRECHECK",
        "PROGRESS_UPDATED", "TOPIC_TRANSACTION_COMMIT", "TOPIC_TRANSACTION_ROLLBACK",
        "TOPIC_REPOSITORY_RETURN", "TOPIC_UI_FEEDBACK_REQUESTED", "TOPIC_POSTCOMMIT_VERIFY",
        "TOPIC_POSTCOMMIT_READ_ERROR",
        "TOPIC_EXTERNAL_PROBE_ACTION", "TOPIC_EXTERNAL_PROBE_SAVED",
        "PLAN_DELETE", "SEED_PASS", "SETTINGS_WRITTEN", "SETTINGS_OVERWRITE", "TRACE_RESET", "DATABASE_OPEN",
        "SQL_PLAN_MUTATION", "SQL_SETTINGS_MUTATION", "DATABASE_HANDLE_OPEN",
        "PRODUCTION_AFTER_SUBJECT", "PRODUCTION_BEFORE_TOPIC", "PRODUCTION_AFTER_TOPIC_ACTION",
        "PRODUCTION_AFTER_TOPIC_SAVED", "PRODUCTION_BEFORE_FEEDBACK", "PRODUCTION_AFTER_FEEDBACK",
        "PORTABILITY_IMPORT_START", "PORTABILITY_SNAPSHOT_RESTORE_START",
        "SYNC_SNAPSHOT_RESTORE_START", "SYNC_SNAPSHOT_RESTORE_COMMIT", "SYNC_SNAPSHOT_RESTORE_ROLLBACK",
        "SYNC_RESTORE_REQUESTED", "SAMPLE_PLAN_INSERTED",
    )
    private val operations = setOf("none", "create_plan", "update_plan", "delete_plan", "upsert_subject",
        "upsert_topic", "update_progress", "insert_settings", "replace_settings", "seed", "read_settings", "sql_insert", "sql_update", "sql_replace", "sql_delete", "restore_graph", "restore_snapshot", "sync_restore", "provision_sample")
    private val transactions = setOf("not_started", "begun", "write_seen", "committed", "rolled_back", "unknown")
    private val observers = setOf("none", "ui", "production_room", "independent_pair", "sql_callback")
    private val states = setOf("present", "missing", "changed", "unknown", "unavailable")
    private val errors = setOf("none", "constraint", "busy", "locked", "io", "corruption",
        "unexpected", "probe_error", "cancelled", "writer_unresolved",
        "settings_before_topic", "settings_before_progress", "settings_changed_progress",
        "settings_changed_topic", "invalid_input")

    private var writer: ((ByteArray) -> Unit)? = null
    private val events = ArrayDeque<String>()
    private var sequence = 0
    private var captureFailed = false

    @Synchronized
    fun installDebugSink(sink: (ByteArray) -> Unit) {
        writer = sink
        events.clear()
        sequence = 0
        captureFailed = false
        persist()
    }

    /** Explicit test-journey start; absence of this marker is a runner failure. */
    @Synchronized
    fun resetForHostedJourney() {
        if (writer == null) return
        events.clear()
        sequence = 0
        captureFailed = false
        record("android_test", "TRACE_RESET")
    }

    @Synchronized
    fun record(
        area: String,
        checkpoint: String,
        operation: String = "none",
        transaction: String = "unknown",
        observer: String = "none",
        plan: String = "unknown",
        settings: String = "unknown",
        error: String = "none",
    ) {
        if (writer == null) return
        require(area in areas && checkpoint in checkpoints && operation in operations &&
            transaction in transactions && observer in observers && plan in states &&
            settings in states && error in errors) { "Unreviewed DIAG-01 event category" }
        sequence++
        events.addLast(listOf(sequence.toString(), area, checkpoint, operation,
            transaction, observer, plan, settings, error).joinToString("|"))
        while (events.size > MAX_EVENTS) events.removeFirst()
        persist()
    }

    /** Never throw into production operations on an instrumentation write failure. */
    private fun persist() {
        val sink = writer ?: return
        val encoded = ("V2\n" + events.joinToString("\n") + "\n").toByteArray(Charsets.US_ASCII)
        if (encoded.size > MAX_BYTES) {
            captureFailed = true
            return
        }
        try {
            sink(encoded)
        } catch (_: Exception) {
            captureFailed = true
        }
    }

    // A read-only callback bound to the SAME application Room instance, never a
    // second Room builder. IDs are in-memory query arguments and NEVER logged.
    private var productionRead: (suspend (String) -> Pair<Boolean, Boolean>)? = null

    @Synchronized
    fun installProductionRead(reader: suspend (String) -> Pair<Boolean, Boolean>) {
        if (writer != null) productionRead = reader
    }

    suspend fun probeProduction(planId: String, checkpoint: String) {
        require(checkpoint in setOf("PRODUCTION_AFTER_SUBJECT", "PRODUCTION_BEFORE_TOPIC",
            "PRODUCTION_AFTER_TOPIC_ACTION", "PRODUCTION_AFTER_TOPIC_SAVED",
            "PRODUCTION_BEFORE_FEEDBACK", "PRODUCTION_AFTER_FEEDBACK")) {
            "Unreviewed DIAG-02 production probe"
        }
        val reader = synchronized(this) { if (writer == null) null else productionRead } ?: return
        try {
            val (plan, settings) = reader(planId)
            record("database_lifecycle", checkpoint, operation = "read_settings",
                transaction = "unknown", observer = "production_room",
                plan = if (plan) "present" else "missing",
                settings = if (settings) "present" else "missing")
        } catch (cancel: java.util.concurrent.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            record("database_lifecycle", checkpoint, operation = "read_settings",
                observer = "production_room", error = "probe_error")
        }
    }

    // Room's debug query callback supplies SQL and bind values. Never export
    // either. Capture only the statement verb and one of two fixed table names.
    // Callback invocation is a statement ATTEMPT, not evidence of commit.
    private val sqlWritePrefix = Regex(
        """^\s*(INSERT(?:\s+OR\s+[A-Z]+)?|REPLACE|UPDATE(?:\s+OR\s+[A-Z]+)?|DELETE)\s+(?:INTO\s+|FROM\s+)?[`"\[]?([A-Z_]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun observeSql(sql: String) {
        if (synchronized(this) { writer == null }) return
        val match = sqlWritePrefix.find(sql) ?: return
        val table = match.groupValues[2].lowercase(java.util.Locale.ROOT)
        val checkpoint = when (table) {
            "study_plans" -> "SQL_PLAN_MUTATION"
            "study_plan_settings" -> "SQL_SETTINGS_MUTATION"
            else -> return
        }
        val verb = match.groupValues[1].uppercase(java.util.Locale.ROOT)
        val operation = when {
            verb == "REPLACE" || "REPLACE" in verb -> "sql_replace"
            verb.startsWith("INSERT") -> "sql_insert"
            verb.startsWith("UPDATE") -> "sql_update"
            else -> "sql_delete"
        }
        record("database_sql", checkpoint, operation = operation,
            transaction = "unknown", observer = "sql_callback")
    }

    @Synchronized
    fun hasCaptureFailure(): Boolean = captureFailed
}
