# SFDT22 HF23 — Preserve personalized plan settings across content progress updates

## Observed evidence (not a root-cause proof)

HF22 hosted API36 run `42567537872` captured: `after_create=present`, `after_subject=present`, `after_topic=missing`, test failed at `settings_after_topic`, and no session/restart journeys were reached. This establishes a missing settings row in the read-only SQLite inspection, but not the exact SQL statement or thread that removed it.

## Production protection

The topic-save transaction now snapshots its entire original settings row **before topic insertion**, rehydrates that exact row if topic insertion removed it, and verifies that it remains unchanged after progress recalculation. In addition, `RoomStudyContentRepository.recalculateProgress` is already executed inside `database.withTransaction` for subject/topic mutations. Snapshot the **entire existing** `StudyPlanSettingsEntity` before modifying subject rows or plan progress. After the last update, reinsert the original entity **only if its row has disappeared**, using the existing `@Insert(IGNORE)` DAO method and verify exact equality. If settings were missing initially or unexpectedly modified, throw and roll back the whole user operation. No new defaults, no destructive replacement, no session bypass, no deletion of user's existing preferences.

This is a targeted data-integrity repair, **not** a proven explanation for the missing row. A background operation after this transaction could still cause the same failure. The hosted UI checkpoint must prove whether this protection resolves the observed issue.

## Acceptance

Run the source gate, hosted Kotlin compile and five genuine SFDT22 journeys including seven screenshots and new-PID restart. The new source-only test simulates an adversarial SQLite cascade and checks preservation of a customized 83-minute session preference and JSON configuration. It is not a substitute for the Android runtime.

Devtool CI HTTP 504 / 10-second timeout while fetching the pinned Gradle distribution is an independent runner/network problem; retry only failed jobs or address distribution provisioning separately. No release qualification or merge before full hosted success.
