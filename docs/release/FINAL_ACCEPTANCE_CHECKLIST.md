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
- [ ] APK or AAB is assembled for `versionName = 1.0.0-rc1`.
- [ ] Signing or debug/internal distribution policy is recorded without committing secrets.

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
