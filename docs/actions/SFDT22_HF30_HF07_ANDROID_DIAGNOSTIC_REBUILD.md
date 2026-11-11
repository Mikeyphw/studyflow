# StudyFlow SFDT22 HF30 HF07 — rebuilt hosted Android diagnostic execution

## Scope

This replaces the opaque Android 36 execution/diagnostic layer in two previously
failing jobs: the **SFDT22 full hosted 5-journey** qualification and **SFDT26
nonqualifying 4-journey** hotfix lane. It does **not** replace real production
screens with mocks, remove old strict security tests, or mark failed checks green.
It does not touch PR #15, HF29 staging, `main`, release signing or publication.

## Why this is different

The earlier HF06 run ended with a much smaller artifact than usual, so it
couldn't be determined whether the separate connected Room test APK failed to
compile or execute, or whether the UI journey had begun. A single exit code is
not enough to distinguish these cases. This diagnostic rebuild splits the
full pipeline into independently reported stages and also records meaningful
information from a source-bound UI test failure receipt.

## New executable diagnostic runner

`python3 -B scripts/sfdt22_hf30_hf07_diagnostics.py room-run --mode full ...`
proves the GitHub source and hosted API36 emulator identity. It runs real
`:core:data:assembleDebugAndroidTest`, then real
`:core:data:connectedDebugAndroidTest`, never claiming source-only tests
as Android proof. Failures are written atomically to `HF07_ROOM.json`, with
compile/instrumentation/timeout stage classification and strictly whitelisted
JUnit method names parsed from XML reports. Gradle's own diagnostic console
remains available in the Actions job; no raw Gradle logs, database contents,
user identifiers, screenshots or arbitrary exceptions enter the artifact.

Both full and focused workflows run the new always-reporting summarizer,
writing `HF07_DIAGNOSTICS.json` and a safe GitHub job summary after the
emulator job, even on early failures. The report records actual failure phase,
test, stage, allowlisted reason, number of completed journeys, Room result,
and qualifying/nonqualifying lane identity. It cannot turn a failed emulator
step into a green check. Its redacted reports remain useful even when the
original SFDT22 evidence is missing or incomplete.

## Mandatory original checks kept

- Five real production UI journeys, seven distinct screenshot SHA-256 hashes;
  real force-stop/new-PID restart and persisted progress check.
- Independently verified debug APK/source identity, signing, all prior
  SFDT27 Room + SFLP test groups, and on-device read-only SQLite evidence.
- Real Room session atomic-state Android class (five methods) must pass.
- The focused push/label/manual lane remains diagnostic-only, runs four
  real journeys, and is never the full qualification or release gate.
- Exact SHA-256 source pins for every changed/new file are checked before
  Android work; no bypass/`continue-on-error`/`if:false`.

## How to inspect failures

In a failed workflow, check the `HF07_STAGE_...` lines and its
`HF07_DIAG=...` job-summary line. Open the uploaded diagnostic artifact and
read `HF07_DIAGNOSTICS.json`, `HF07_ROOM.json` (full lane) and the original
`SFDT22_EVIDENCE.json` if emitted. Examples:

- `room_stage=build_test_apk` — Android test APK compile/package failure.
- `room_stage=execute_room_tests` — device instrumentation/test failure;
  `room_failing_methods` identifies the exact test(s) when JUnit XML exists.
- `ui_phase=session`, `ui_stage=session_completion_persisted` — actual
  application/session interaction failure after Room qualification.
- `ui_status=NOT_RUN` — stopped before the production UI journey.

Do **not** mark a PR qualified until all six PR checks, mandatory full
Android emulator receipt validation, and the dedicated release gate pass.
