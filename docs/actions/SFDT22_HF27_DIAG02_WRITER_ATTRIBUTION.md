# StudyFlow SFDT22 HF27 — DIAG-02 writer attribution (nonqualifying)

## Exact base and evidence

- Staging branch: `sfdt22-hotfix/hf25-staging-20261109` at `13da640f71a4d80990d3f360856c3d19a054caf3`.
- DIAG-01 focused hosted API36 run `42749671418`: `boundary_after_topic_missing` with `hf27_room_regression=passed`.
- Trace seq 15 `TOPIC_TRANSACTION_COMMIT`, seq 16 `TOPIC_POSTCOMMIT_VERIFY` settings present, seq 19 `TOPIC_EXTERNAL_PROBE_SAVED` settings missing.
- **Causality not established.** A later write, a concurrent Room connection, and visibility differences remain possible. The trace's `writer_unresolved` is the correct classification until supported by direct evidence.

## DIAG-02 additions

1. The **production** Room builder installs a debug-only SQL *statement-attempt* callback, which classifies only `INSERT`, `REPLACE`, `UPDATE`, or `DELETE` aimed at `study_plans` or `study_plan_settings`. SQL source strings, bind arguments, identities, values and stack traces are never written or uploaded. A callback does **not** prove execution, effect or commit. Foreign-key cascades may occur without a direct settings-table SQL callback.
2. The `StudyFlowDataLayer` installs a debuggable, read-only production Room probe on its exact database instance. UI probes run before and after `showSnackbar` and the Android journey additionally queries that same handle before its existing independent SQLite/secondary Room pair. Results export only plan/settings present/missing/unavailable categories.
3. Database `onOpen` (after the existing integrity install), provisioning settings writes, portability graph imports/snapshot restores, manual folder restore, and logical snapshot restore begin/commit/rollback are traced. The latter markers identify entrypoint scope, not proof that a target plan was affected.
4. All existing HF04 transactional settings-preservation checks remain. No defaults, new mutations, test substitutes, extra debug writes to the Room DB, local Gradle, unattended CI watcher, PR push or PR merge are introduced.
5. The 64-event/16,384-byte atomic app-private trace and strict ASCII/enum host parser stay in place. Raw instrumentation output, SQL, bind arguments, IDs, names, hashes or private database files never become CI artifacts. The original full five-real-UI + cold-start + seven-screenshot gate remains authoritative.

## Decision criteria after hosted Android 36

- **`SQL_PLAN_MUTATION/sql_replace` between commit and missing:** evidence of a potentially destructive parent replace, **not yet deletion proof**. Inspect its caller, SQLite foreign-key behavior, and exact rollback/commit history.
- **`SQL_SETTINGS_MUTATION/sql_delete`**: evidence that a delete statement was attempted; verify the owning transaction and why it ran.
- **All production and independent readers missing:** focus on an uninstrumented writer/trigger or graph restore; examine event chronology and optionally add a scoped SQLite trigger audit in a further diagnostic phase.
- **Production present while independent readers missing:** investigate handle lifecycle, WAL snapshots, and observer timing rather than patching persistence.
- **Unexpected read or missing trace:** fail closed; retain `writer_unresolved`, no automatic settings repair.

Only an *actually executed* GitHub-hosted build can prove Android compilation and runtime behavior. This diagnostic staging branch is not PR #15 and cannot qualify a release.
