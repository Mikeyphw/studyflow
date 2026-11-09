# SFDT22 HF17 — Session-start CI failure audit and exact diagnostics

Source pinned to `Mikeyphw/studyflow` PR #15 at commit `4af2f7ed539ce541ebfe80bb23e29f68273c0be8`.

## Confirmed GitHub Actions result

Run `42524996026` compiled and installed both debug APKs, completed plan and subject/topic journeys, and failed at `session_running` with reason `session_control_not_ready`. Session, practice, and restart qualification remain incomplete. Other latest workflows passed.

## Production behavior audited

The quick-session dialog invokes `ManageStudySession.startQuickSession`, which attempts to create a RUNNING session through the Room repository. The UI also exposes `session_started` and `operation_failed` Snackbars. The current CI evidence cannot distinguish a failed mutation, delayed Flow/recomposition, disabled/unreachable controls, or semantics matcher mismatch. There is no proof of a Room defect.

## HF17 corrections

The real `session_running` checkpoint observes actual production feedback and the exact Pause control concurrently. It succeeds only after one unique visible enabled clickable Pause control appears. It reports one of: `session_start_failed`, `session_start_success_without_running_ui`, `session_running_control_missing`, `session_running_action_missing`, `session_running_control_ambiguous`, `session_running_control_disabled`, `session_running_control_offscreen`. On failure the existing sanitizer emits only the allowlisted reason and stage—never raw instrumentation output. A transient success Snackbar is not a substitute for rendered app state. The production database, UI business logic, session data, and all later journeys remain unchanged.

## Qualification boundaries

Preflight runs the full source-contract chain in a disposable detached worktree, then applies through native Devtool and repeats the checks before auto-committing and normal pushing. No local Gradle. Five real MainActivity journeys, seven screenshots and a forced-new-PID restart are still required on a clean hosted API 36 emulator. No release, physical-device or publication claim is made before the real GitHub Actions run is green.
