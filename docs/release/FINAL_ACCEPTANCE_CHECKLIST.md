# Final Acceptance Checklist

Status: **PENDING TARGET EXECUTION**

Run this checklist on a real device or emulator using the `1.0.0-rc1` APK/AAB produced on the target
Android build host.

## Build and package evidence

- [ ] Gradle configuration resolves on the target host.
- [ ] Changed and historical source sets compile.
- [ ] Unit tests pass.
- [ ] Room schema and migration validation pass.
- [ ] Android resources merge.
- [ ] Lint completes with zero unexpected diagnostics.
- [ ] Release APK **and** AAB are assembled for `versionName = 1.0.0-rc1`.
- [ ] Release APK and AAB are signed with external release credentials; no signing secrets are committed.

## Fresh install

- [ ] App installs cleanly.
- [ ] First launch reaches the plan list/dashboard without a crash.
- [ ] Create a Study Plan.
- [ ] Add subjects and content.
- [ ] Create or inspect schedule data.
- [ ] Dashboard primary session action opens Study Sessions.

## Study workflow

- [ ] Start, pause, resume, and finish a study session.
- [ ] Answer practice questions.
- [ ] Create or review wrong-answer/remediation data.
- [ ] Open library materials and annex metadata.
- [ ] Inspect analytics/progress surfaces.

## Reminder, widget, and backup

- [ ] Notification permission denied path is understandable.
- [ ] Notification permission granted path schedules reminders.
- [ ] Reminder notification opens the intended destination.
- [ ] Today widget renders and refreshes.
- [ ] Export backup succeeds.
- [ ] Restore dry-run previews data without mutation.

## Upgrade install

- [ ] Install over the previous accepted StudyFlow build.
- [ ] Existing Room data survives.
- [ ] DataStore preferences survive.
- [ ] Reminder reconciliation does not duplicate stale alarms.
- [ ] Backup/export still works after upgrade.

## Runtime robustness

- [ ] Rotate or resize where supported.
- [ ] Background and foreground during an active session.
- [ ] Kill and reopen the app.
- [ ] Verify date/time and timezone-sensitive reminder behavior.
- [ ] Switch English and Portuguese (Brazil).
- [ ] Switch light/dark/system theme.
- [ ] Run a TalkBack/focus/touch-target sanity pass.

## Result

The release is not accepted until every required item above has evidence in `FINAL_ACCEPTANCE_REPORT.md`.

## OV-13 evidence seal additions

- [ ] `verifyFinalReleaseAutomatedGate` passes on the Android build host.
- [ ] Host evidence binds signed Release APK and AAB paths and SHA-256 values in one validation session.
- [ ] APK identity/version/non-debuggable state and signer certificate pass `apkanalyzer` + `apksigner`.
- [ ] Release AAB signature integrity passes strict `jarsigner` verification.
- [ ] Connected core-data, Room migration, Plans, Practice, Schedule, Workspace, and Release app suites pass.
- [ ] The exact signed APK completes the full production-container journey with real
  `REPLACE_EXISTING` and `CREATE_AS_NEW` restores.
- [ ] A second instrumentation invocation after forced process restart re-reads the retained restored data.
- [ ] For every required device check below, invoke `record_final_device_acceptance.py` with both
  `--pass-check CHECK` and `--evidence 'CHECK=what was observed'`. Bare PASS flags are rejected.
- [ ] `verifyFinalReleaseVerdict` reports `releaseReady=true`; WARNING/BLOCKED/FAIL/missing evidence
  never counts as release-ready.

Example attestation shape (repeat the two arguments for **every** required check):

```bash
python3 scripts/record_final_device_acceptance.py \
  --pass-check accessibility_talkback_focus_touch_target \
  --evidence 'accessibility_talkback_focus_touch_target=TalkBack traversal, visible focus, and touch targets verified'
./gradlew verifyFinalReleaseVerdict
```
