# SFDT22 HF21 — repair existing plans with missing persisted settings

## Evidence and root-cause boundary

HF20 hosted run 42550473489 passed plan creation and subject/topic UI journeys,
then failed the real Quick Session at `session_running`:
`session_start_plan_settings_missing`. HF19 proved the settings query returned
`null`, not an exception. StudyPlanDao and StudySupportDao prove normal creation
and first-time sample seeding write both parent and settings transactionally.
However, `seedFoundationSamplesOnce()` was gated by a one-time metadata marker,
so any pre-existing plan with a missing settings row was never repaired at
subsequent startup. The origin of that missing row is not proven by the receipt.

## Production correction

During existing application initialization, within the existing Room
`database.withTransaction` startup transaction, select only owning plan IDs
without corresponding study_plan_settings rows using a SQL LEFT JOIN. Insert
`StudyPlanSettings.defaults(StudyPlanId(id))` for those IDs **only** via
`@Insert(onConflict = OnConflictStrategy.IGNORE)` (never REPLACE or @Upsert).
This operation is safe, repeatable and does not overwrite existing customized
preferences. It runs on each initialize, regardless of the historical one-time
foundation seed marker. No session-start defaults, ownership bypass, schema
migration, direct test database write, or changes to the existing five UI
journeys are introduced.

## Qualification and limitations

The HF21 source contract executes the real Room SQL selection against a small
SQLite database, checks missing-only and idempotence, verifies the production
startup call and the original strict session guard, and enforces every exact
SHA pin and old SFDT22 gate. Kotlin compilation, actual Android database repair,
all five hosted UI journeys, seven screenshots and cold-restart persistence
still require a fresh hosted API 36 GitHub Actions run. The underlying cause
that initially removed the settings row needs a separate audit if it recurs.
PR #15 remains open until that hosted acceptance passes.
