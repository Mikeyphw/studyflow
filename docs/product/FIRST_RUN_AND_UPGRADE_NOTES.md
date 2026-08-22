# First-run and Upgrade Notes

## Local-first by default

StudyFlow starts as a local-first study workspace. Study Plans, reminders, widgets, backups, search,
recommendations, and sync-preparation state stay local unless the user explicitly exports a package or later chooses optional cloud sync.

## Recommended first run

1. Create or import one Study Plan.
2. Set it as the default plan.
3. Review reminders and quiet hours.
4. Run the runtime smoke workbench from Settings.
5. Export an encrypted `.studyplan` package before large imports or destructive cleanup.

## Upgrade notes

- Existing local Study Plans remain local.
- The local checkpoint foundation remains available without cloud setup.
- Optional cloud sync settings default to off, provider none, automatic sync off, Wi-Fi-only on, charging-only off, and remote-backup encryption on.
- Reminder delivery uses local inexact scheduling and honors quiet hours.
- Restore dry-run remains the safe pre-import step.
- Metadata-private exports are recommended before sharing backups for support.
