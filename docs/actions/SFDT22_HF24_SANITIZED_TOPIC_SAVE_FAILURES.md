# SFDT22 HF24 — Privacy-safe topic-save failures

## Scope and status

HF23's hosted API36 run 42630621390 completed 1/5 real journeys and failed at `topic_save_feedback`, `topic_save_failed`. The app's generic `operation_failed` snackbar discarded the specific failure type. HF24 **does not assert a root cause that the evidence cannot establish**. It adds a fail-closed classification bridge, preserving HF23's transactional settings checks and exact five-journey gate.

## Production diagnostic contract

`StudyContentScreen` still shows the same localized operation failure. On a failed topic save, it inspects only four **literal, exact** HF23 settings invariant messages and approved exception types; it never exports user-controlled text, IDs, plan/topic names, database rows, full stack traces, or SQL. It writes **one of nine static ASCII codes** to the app-private `sfdt22-topic-failure.txt` only when `ApplicationInfo.FLAG_DEBUGGABLE` is set. It clears stale diagnostics before every attempt, overwrites the file on failure, and clears it again after success; a write failure must not alter the real operation's failure result. Non-debuggable builds never write the file.

The real UI test reads at most 64 bytes from that file only after production feedback reports a failed save. Invalid, long, missing, or unknown data is ignored; the existing generic `topic_save_failed` remains fatal. The hosted Python verifier only accepts a code when the real subject/topic journey failed at the `topic_save_feedback` stage, then emits `topic_save_<allowlisted_code>` without raw logs. All five UI tests, seven screenshots, emulator freshness, and force-stop/restart remain mandatory for a full pass.

## Safe codebook

- `settings_before_topic`: absent row before topic write
- `settings_before_progress`: absent row at content progress calculation
- `settings_changed_progress`: settings changed across progress calculation
- `settings_changed_topic`: settings changed during topic save
- `sqlite_constraint`: SQLite constraint exception
- `sqlite_locked`: SQLite locking exception
- `sqlite_other`: another Android SQLite exception
- `invalid_input`: Kotlin argument-validation exception
- `unexpected`: all other failures, including unknown wrappers

These classify **symptoms**, not verified root causes. Existing settings are not overwritten or replaced by default values. The topic-save transaction remains rollback-safe. No raw log, stacktrace, exception message, database content, or mutable release qualification may appear in the hosted receipt.

## Qualification

This source-only kit must first pass the complete old source contracts and HF24 regressions in a disposable Git worktree, then native Devtool applies it to the exact pinned PR branch. Gradle, Kotlin compilation and Android API36 instrumentation occur only on GitHub. The source tests cannot prove runtime success; **do not merge PR #15** until the five-journey gate is green. If it fails, the exact `failure_reason_code` in `SFDT22_EVIDENCE.json` identifies the next production fix without a new speculative diagnostic design.
