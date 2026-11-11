# StudyFlow SFDT22 HF30 HF06 — quick-session terminal-state repair

## Root cause

At PR #16 commit `7c133c9229564085b7f6d71294fae4d4c2697968`, hosted API36
run `42952948819` failed within the third real journey at
`session_completion_persisted` with `session_finish_operation_failed`.
Quick sessions intentionally have `plannedActivityId=null`. The domain
manager unconditionally supplied linked planned-activity transition
arguments to the Room repository, which correctly rejects a missing link.
This production/domain mismatch—not the worktree launcher—is the verified defect.

## Production correction

Completion and cancellation supply linked planned-activity arguments only
when the current session actually has a linked activity. Quick-session
completion persists status, end timestamp, notes, and metrics; cancellation
persists terminal CANCELLED state. Linked sessions retain compare-and-set
activity transitions, including restoration of original MISSED state on cancel.

## Real regression gate

- Fake unit repository now rejects phantom planned-activity transitions.
- New unit regression: quick cancellation without a linked activity.
- Real Room: quick completion with persisted metrics/notes and a new session.
- Real Room: quick cancellation with persisted terminal status.
- Real Room: linked completion updates a real scheduled activity.
- Earlier real Room linked MISSED-state cancellation test remains unchanged.
- The full Android 36 hosted emulator job runs the entire relevant
  `StudySessionAtomicStateTest` class through `:core:data:connectedDebugAndroidTest`
  before beginning the five production UI journeys. The Gradle command is
  fail-closed and cannot be replaced with source-only test discovery.

## Safety gates

The existing five real UI journeys, seven unique screenshots, on-device
read-only SQLite confirmation, new-PID force-stop persistence, inherited Room
and SFLP checks, and release gate are unchanged. Updated SHA-256 pins cover
all changed/new sources and are checked before emulator work.
No PR #15, staging, main, release signing or publication action is allowed.
A diagnostic or source pass is not full Android qualification.
