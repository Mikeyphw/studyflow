# StudyFlow SFDT22 HF27 HF01 — exact-plan identity and Room evidence (nonqualifying)

**Based on:** staging `sfdt22-hotfix/hf25-staging-20261109`, source SHA `33f4e145f0351697ee80e26ea379cd9a57f7da23`, tree `65ee2711b31504b880b64798f7b376f871f64aee`. PR #15 remains at `8f802ca36cf69f4a052cba860d2d78f5cfce34bf` and must not be touched by this hotfix.

HF26's sanitized `topic_save_settings_before_topic` means the production Room settings lookup returned null. It is **not** proof that settings were deleted. HF27 intentionally makes **no production persistence repair** before a same-plan, same-database diagnosis identifies the cause. HF23's exact personalized-settings rollback guards stay untouched.

HF01 source-contract repair: HF22 requires the five production-UI journeys to contain no `runBlocking {` operation. The independent read-only Room lookup now runs in `Sfdt27RoomSettingsProbe.kt` (Android test source set) rather than inline in the UI journey test. This preserves the previously guarded acceptance semantics, without weakening HF22 or substituting a mock.

## What HF27 adds

1. At four stages in the real five-journey UI test, inspect the same exact plan ID (resolved on-device by a single-name match and checked against the production Compose UI's private SHA-256 plan binding). Check one specific saved subject's ownership. Query the actual `studyflow.db` with `SQLiteDatabase.OPEN_READONLY` and compare with a **second real Room handle** configured without the production open-callback that installs triggers, not the production repository's transaction handle. The distinction is explicit: a mismatch is a clue, not a proven write cascade. The probes are `after_subject`, `before_topic`, `after_topic_action`, `after_topic_saved`.
2. At each probe output **only** fixed allowlisted states: `matched`, `ui_marker_missing`, `lookup_failed`, `plan_ambiguous`, `different_plan_id`, `subject_not_owned`, `room_missing_sql_present`, `room_present_sql_missing`, `row_missing`, `read_only_snapshot_mismatch`, `row_changed`, `baseline_missing`. No plan IDs, names, hashes, SQL contents or exception text enter artifacts. A private SHA-256 checksum of the complete personalized settings row captures changes after the subject checkpoint; a differing value emits only `row_changed`. SQLite and independent Room row values are compared in memory, emitting `read_only_snapshot_mismatch` on disagreement. The SHA-256 identifier exists only in an app-private file on the disposable debuggable emulator; no such file is uploaded. Release builds never write this marker.
3. Run `Sfdt27RoomSettingsRegressionTest` **on the real GitHub-hosted Android 36 emulator**, using real Room against a separate, disposable file-backed database and an actual cold DB close/reopen. Verify custom nondefault settings, subject/topic ownership, progress updates, and injected transaction failure rollback. The test runs before the pre-existing five real UI journeys, and only an exact 2-test AndroidJUnitRunner PASS qualifies its status. This test does not claim that the production repository UI issue is resolved.
4. Extend strict `sfdt22_emulator_verify.py` sanitizer with fixed-schema `sfdt27_probes` and `hf27_room_regression` in `SFDT22_EVIDENCE.json`. No raw Android stdout, logs, hashes, plan identifiers, SQL or exceptions are uploaded. Partial failure retains only bounded probe states. Existing five UI journeys, seven screenshots, force-stop/new PID, source and emulator gates are unchanged.
5. The application Gradle change adds database and Room only as `androidTestImplementation`, with no production dependency changes. Existing HF26 fast non-PR staging workflow automatically executes the hosted Room test and exact-plan probes via `--run --diagnostic-through session`; it does not run the unrelated full PR suites between hotfixes.

## Qualification / next decision

The focused staging run at the **new exact SHA** must show `hf27_room_regression: passed` and all four `sfdt27_probes` as `matched`, in addition to three successful real UI journeys. If it fails, inspect `sfdt27_probes` and the existing allowlisted `failure_reason_code` before designing a *production* fix. `row_missing` is a missing row in both observations for the ID in question, not automatically a cascade diagnosis. `room_missing_sql_present` compares two different handles and cannot by itself prove a DAO bug. `different_plan_id` is a concrete UI/SQL identity disagreement and warrants tracing navigation before any restore.

Only after focused diagnostics/real Android journeys pass should a separately reviewed qualified promotion run all existing Devtool, SFDT21, five SFDT22 journeys, screenshot hash, source contracts and CI checks. Until then **no merge, release, physical device qualification or production-settings default regeneration** is authorized.

## Implementation logistics

Native Devtool source-pinned overlay manifest covers every changed and new path, `source_sha256=null` for added paths, exact base tree guard, plus offline source and recovery tests. The apply kit needs a clean on-base checkout or a rigorously recognized retry state; normal push only to existing staging, no force-push/reset/stash or automatic promotion. All disposable extraction, worktrees and ZIP generation use explicitly exported absolute writable `$TMPDIR`. Gradle runs only in GitHub Actions, never in Termux.

## HF27 HF03 — hosted Room failure triage and single-kit watcher (nonqualifying)

Hosted staging SHA `f8eabe8462478939607554f34d88f80af87b5f37` compiled both APKs,
passed source gates and APK provenance, but stopped at `room_regression` before any
production UI journeys. The combined `am instrument` runner and generic
`OK (2 tests)` check concealed the individual result; the cause of the
independent Room-test failure has not yet been established. Do not infer that
production settings were removed or modify production storage on this evidence.

HF03 preserves the same two real Room tests, actual database triggers, full UI
acceptance and all original CI gates. It executes each regression as a separate
`am instrument -w -r` test, requiring an explicit start, finish, and successful
runner exit. The two Android tests include fixed error stage codes for plan setup,
subject/topic writes, cold reopen, rollback setup, write and verification. A
failed Room regression is recorded but does NOT stop the separate real UI
diagnostic from running; final acceptance is still strictly failed unless BOTH
Room regressions and all required real UI journeys pass. Only
preapproved failure codes and an exact static test name may enter bounded evidence;
raw Android output, exception messages, plan IDs, user data, and logs are never
persisted or uploaded. Any unexpected or incomplete result remains a hard failure.

The consolidated HF03 installer starts from the **already-applied** HF02 staging
commit, keeps all nine earlier HF27 owned changes intact, and includes the repaired
self-contained source-pinned CI watcher; no separate watcher download is necessary.
Native Devtool/Gradle policies, clean staging, no force-push, no PR #15 mutation and
no automatic merge remain in effect. All temporary files stay under `$TMPDIR`.

## HF27 HF04 — exact-plan settings protection across existing-plan updates (nonqualifying)

Run `42682181271`, stage SHA `4b08d059c992a4b24bbd89d3e5f4cd2b0c873866`,
compiled both APKs, passed both independent Android/Room regressions and completed the first
real UI journey. The second journey persisted a topic but failed at
`settings_after_topic` (`boundary_after_topic_missing`). All four exact-plan probes
agreed on the plan identity; the first three reported `matched`, and the final
reported `row_missing`. The settings row was observed before and immediately after
the save action, then missing after topic UI confirmation. The specific writer
responsible is not yet proven; no claim about an actual cascade operation is made.

HF04 hardens **existing-plan writes** against any future parent-replacement FK
cascade. The four `RoomStudyPlanRepository` editing/status/trash/restore code
paths now call the Room `@Update`-only DAO method instead of `@Upsert`.
Within the pre-existing Room transaction, the exact personalized settings entity
is checked before and after the mutation, with a strict row-count assertion.
Any missing or changed settings fails and rolls back; no default preferences,
auto-heal or replacement is allowed. A new hosted Android/Room assertion
performs a parent UPDATE with the real child settings and subject/topic FKs,
then cold-reopens and verifies all records are intact. Both original Room tests,
all existing full-gate checks, five real UI journeys, seven screenshots and
strict hosted signing/provenance requirements remain enforced.

This is a **source-bound preventative fix and unproven root-cause hypothesis**,
not a claim that HF04 has fixed the live topic-save problem. Continue only after
exact-SHA hosted Android-36 regression and three real UI journeys confirm it.
If the focused run still loses settings, the evidence points to another writer;
do not restore defaults or merge PR #15. The full five-journey qualification
remains required. No local Gradle, no release signing, no auto merge.
