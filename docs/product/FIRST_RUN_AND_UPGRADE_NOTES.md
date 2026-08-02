# First-run and Upgrade Notes

## Local-first by default

StudyFlow starts as an offline-first study workspace. Study Plans, reminders, widgets, backups, search,
recommendations, and sync-preparation state are local unless the user explicitly exports a package or later chooses a provider.

## Recommended first run

1. Create or import one Study Plan.
2. Set it as the default plan.
3. Review reminders and quiet hours.
4. Run the runtime smoke workbench from Settings.
5. Export an encrypted `.studyplan` package before large imports or destructive cleanup.

## Upgrade notes

- Existing local Study Plans remain local.
- The cloud-sync foundation is provider-neutral and does not upload data.
- Reminder delivery uses local inexact scheduling and honors quiet hours.
- Restore dry-run remains the safe pre-import step.
- Metadata-private exports are recommended before sharing backups for support.
