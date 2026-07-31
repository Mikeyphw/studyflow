# Phase 3 Acceptance Contract

Phase 3 is accepted only when all of the following remain true.

## Lifecycle

- Plans can be created, edited, duplicated, activated, paused, archived, restored, moved to trash,
  restored from trash, and deleted permanently.
- Direct status updates cannot place a plan in `TRASHED`; the dedicated trash operation must record
  its timestamp.
- Restoring from trash results in `PAUSED` status.
- Permanent deletion remains transactional and cascade-owned by Room.

## Duplication

- The duplicate has a new root ID, a user-visible copy name, `DRAFT` status, and no trash timestamp.
- All twenty-one plan-owned Phase 2 tables are copied.
- Primary and foreign identifiers are remapped into the new plan namespace.
- Plan-specific settings are copied or safely defaulted.
- Duplicate creation is atomic.

## User experience

- Creation and editing use a three-step objective, target, and preferences wizard.
- The plan list separates current, archived, and trashed plans.
- Destructive operations require confirmation.
- A default plan can be selected or cleared and is opened once on a fresh app launch.
- The dashboard can switch between available plans and reach edit and plan-specific settings.
- Cards expose status, progress, weekly goal, exam countdown, default status, and next-action state.

## Localization and quality

- English and Brazilian Portuguese resources have exact key parity.
- No localized status text is stored in the database.
- Backup XML contains no invalid `cache` domain.
- Plan count and duration strings use plurals.
- Devtool test coverage includes `:feature:plans:testDebugUnitTest`.
- `scripts/verify_phase_0.py` through `scripts/verify_phase_3.py` pass.
