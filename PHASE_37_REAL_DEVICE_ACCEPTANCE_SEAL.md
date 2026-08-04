# Phase 37: Real-device Acceptance Seal Framework

Phase 37 adds the acceptance evidence structure required before StudyFlow can be called release-ready.
It does not claim device success from source validation and deliberately keeps the checked-in report pending because this container cannot install and test the
APK on the target device.

## Delivered

- `docs/release/FINAL_ACCEPTANCE_CHECKLIST.md` defines the target-device acceptance matrix.
- `FINAL_ACCEPTANCE_REPORT.md` is a pending report template for real device or emulator evidence.
- Release validation stays blocked for build, assemble, signing, and device smoke gates until target
  execution fills the report.
- Source validators fail if the checked-in report claims completed acceptance without target details.

## Required target evidence

- Fresh install and first-run flow.
- Upgrade install over the previous StudyFlow build.
- Dashboard session action, sessions, practice, reviews, reminders, widgets, backup export, and
  restore dry-run.
- Language, theme, notification permission, background/foreground, kill/reopen, date/time, and
  accessibility checks.
