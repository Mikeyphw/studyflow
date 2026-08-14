# StudyFlow Remediation OV-11 + OV-12 Implementation

## Scope

This intermediate overlay implements the next two dependency-ordered remediation phases after OV-09/10:

- **OV-11 — `performance_security_hardening`**: RM-057 and RM-058.
- **OV-12 — `diagnostics_debug_truthfulness`**: RM-046 through RM-053.

The overlay is cumulative against the repository state after corrected OV-09/10 and is intended to be applied with Devtool `--no-validate`. Campaign validation remains deferred to the final release/regression overlay.

## OV-11 — Performance hardening (RM-057)

- Preserves one application-owned Room/data-layer graph instead of creating per-feature/per-plan database graphs.
- Reworks analytics around a bounded SQL aggregate DAO and a memoized/shared upstream flow; removes the unused questions observer from the analytics hot path.
- Adds an application-maintained Room FTS4 search index with bounded SQL candidate selection and current ownership/archive rechecks before ranking.
- Removes the global-search sequence of unbounded full-table scans.
- Streams portability annexes through bounded staging files instead of materializing the complete annex payload map in memory during production inspect/import/restore paths.
- Makes Review Center rendering lazy and pre-indexes review/error content to avoid eager composition and repeated O(errors × reviews) lookup work.
- Removes duplicate Progress Workspace readiness/adaptive observer graphs; preview calculations reuse already-loaded workspace snapshots while adaptive mutation still goes through the canonical manager.

## OV-11 — Security hardening (RM-058)

- Keeps application backup disabled and backup/data-extraction exclusions explicit.
- Keeps FileProvider non-exported and limits configured roots to purpose-specific shareable/cache subdirectories rather than broad roots.
- Routes reminder/widget launches through a private non-exported trampoline instead of trusting reminder extras on the exported launcher activity.
- Removes unnecessary package-visibility declarations.
- Adds behavioral Android policy coverage for installed/merged manifest component policy and FileProvider allowed/rejected path behavior.

## OV-12 — Recorder retention and routine evidence (RM-046)

- Rotates the JSONL recorder on diagnostic session boundaries.
- Retains bounded prior segments in chronological order and includes retained history in timeline/support-bundle output.
- Reads only complete JSONL records from active/retained segments.
- Adds sanitized mutation-boundary diagnostic events for routine settings writes, session creation/transitions, practice/mock submission, review mutations, schedule generation, and portability export/import/snapshot restore.
- Cancellation is rethrown and fatal errors are not converted into ordinary recoverable outcomes.

## OV-12 — Support-bundle privacy seal (RM-047)

- Re-sanitizes outbound diagnostic events before final serialization.
- Scans each text payload after construction and before the final support bundle is accepted.
- Generates the redaction report from actual scan/resanitization counters.
- Fails closed on schema/privacy violations and scans the checksum manifest as an outbound text payload too.
- Adds deliberately unsafe credential/identifier and malformed-timeline canaries.

## OV-12 — Behavioral probes (RM-048)

- Widget refresh probes await completion instead of treating dispatch as success.
- Process-exit evidence classifies crash, native crash, ANR, initialization failure, resource-limit, low-memory, signaled, and unknown histories instead of treating “history exists” as PASS.
- Backup/FileProvider probes inspect real runtime policy; FileProvider tests an allowed configured path and confirms an unconfigured private path is rejected.
- Backup dry-run exercises the production streamed-annex portability path.
- Search, recommendations, and sync probes assert domain outcomes/checkpoints rather than only absence of exceptions.

## OV-12 — Reminder lab truthfulness (RM-049)

- Separates reminder planning, scheduling bookkeeping, delivery-identity evidence, and projected trigger time.
- Explicitly states that a PendingIntent/delivery token is not proof that AlarmManager currently holds an alarm.
- Removes the old diagnostic direct-notification bypass.
- The production-pipeline canary now inserts a disposable diagnostic occurrence, invokes the production reminder receiver path, observes delivery-coordinator completion through the durable occurrence marker, and cleans all canary bookkeeping/notification state afterward.
- Scheduling evidence can only become fully passing when durable bookkeeping aligns and a recent production-pipeline canary succeeded.

## OV-12 — Portability lab truthfulness (RM-050)

- Uses temp-file streams and the production portability repository/codec rather than byte-array surrogate packaging.
- Includes annex payload/ID privacy evidence; zero-annex packages report `NOT_APPLICABLE` rather than a false PASS.
- Tests authenticated inspection, wrong-password rejection, tamper rejection, and failed-import isolation.
- Performs a disposable real `CREATE_AS_NEW` restore, verifies restored rows/plan visibility, permanently deletes it, and verifies the disposable plan is absent after cleanup.
- Exercises snapshot create/delete retention behavior and logical/foreign-key integrity checks.

## OV-12 — Study Engine canary (RM-051)

- Separates planner output from persisted schedule evidence.
- Verifies due/upcoming review time semantics rather than only item counts.
- Removes unconditional analytics success and uses canonical sync checkpoint auditing.
- Exercises deterministic Practice behavior for immediate-feedback versus practice mode visibility, deadlines/timeouts, and answer mutation constraints.
- Exercises deterministic Mock Exam behavior for negative marking, scoring, pass/fail thresholds, elapsed time, terminal submission state, explanation visibility, and post-submit mutation rejection.
- Retains deterministic SRS/deck checks and uses `EMPTY`/`NOT_APPLICABLE` where evidence is genuinely absent.

## OV-12 — Runtime self-check seal (RM-052)

- Uses explicit terminal verdicts: `PASS`, `PASS_WITH_WARNINGS`, `BLOCKED`, `FAIL`, and `CANCELLED`.
- Persists a bounded, sanitized history of terminal self-check runs using stable probe/status/message/invariant codes only.
- Preserves partial evidence when cancellation occurs, records the cancelled terminal run, and then rethrows cancellation.
- Applies cross-probe invariants so a nominal probe result cannot hide missing behavioral evidence.

## OV-12 — Workbench lifecycle/concurrency truthfulness (RM-053)

- Moves diagnostic job/running/lab/report ownership into `StudyFlowDebugWorkbenchViewModel` and `viewModelScope`.
- Serializes suite/individual probe execution through a mutex and prevents competing report updates.
- Exposes refresh/copy failures instead of silently discarding them.
- Does not persist ephemeral “running” work as saveable UI state.
- Renames the misleading “Live status” presentation to **Status snapshot** unless data is actively refreshed.
- Runtime Smoke delegates to the same behavioral probe registry instead of maintaining a weaker “no exception = PASS” parallel path.

## Re-audit corrections completed before packaging

The promised-scope re-audit found and fixed additional gaps before this overlay was packaged:

- Removed duplicate Progress Workspace readiness/adaptive subscriptions missed in the first OV-11 pass.
- Added routine recorder instrumentation at the required mutation boundaries instead of limiting diagnostics to startup/debug operations.
- Replaced Runtime Smoke false-green checks with the behavioral probe registry.
- Classified recent low-memory process exits as warning evidence rather than PASS.
- Replaced the Reminder Lab direct NotificationManager canary with the production receiver → delivery coordinator path.
- Expanded the Study Engine canary to cover Practice/Mock modes, timeout/deadline behavior, negative marking, scoring/pass-fail, and terminal-state mutation rules.
- Fixed cancellation/fatal-error handling in new diagnostic boundaries.
- Updated existing platform-policy tests to the expanded truthful backup/FileProvider/process-exit evidence models.

## Static acceptance performed for this intermediate overlay

- Promised-scope source acceptance: **167 / 167 checks passed**.
- Changed Kotlin/KTS files passed delimiter/string/comment lexical-balance inspection.
- Changed XML parses successfully.
- Changed `values` resources contain no duplicate named entries.
- All newly introduced English Settings strings have pt-BR counterparts.
- Base-to-work diff contains no whitespace-error findings.

## Deliberately not run

Per the remediation handoff and current campaign policy, this intermediate overlay does **not** run Gradle or Android validation. In particular, the following remain deferred until the final campaign validation step:

- Gradle compilation.
- Unit tests.
- Android-test compilation.
- Lint.
- Instrumentation tests.
- Emulator/device tests.
- APK/release assembly.
- Full campaign regression/acceptance validation.
