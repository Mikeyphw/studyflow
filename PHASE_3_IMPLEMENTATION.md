# Phase 3 Implementation

Phase 3 turns the persisted Study Plan root into a complete lifecycle-managed workspace.

## Delivered

- Three-step bilingual create/edit wizard covering objective, target, workload, and initial settings.
- Current, archived, and trash collections in the plan list.
- Edit, duplicate, default, activate, pause, archive, restore, trash, and permanent-delete actions.
- Confirmation gates for trash and permanent deletion.
- Default-plan persistence through DataStore and one-time automatic opening on app launch.
- Dashboard plan switcher plus edit and plan-settings entry points.
- Per-plan adaptive planning, review notifications, strict exam mode, preferred session duration,
  and optional quiet hours.
- Transactional `ManageStudyPlan` domain operations.
- Transactional Room graph duplication across all twenty-one plan-owned child tables.
- Deterministic identifier and foreign-key remapping for duplicated plans.
- Default settings creation during plan creation and foundation seeding.
- Lint cleanup for backup rules, plurals, locale configuration, unused strings, and version-only
  advisory checks.
- Phase 3 tests, architecture decision, acceptance contract, validator, and Devtool test coverage.

## Lifecycle semantics

- Duplicates start as `DRAFT` to prevent accidental notifications or schedule execution.
- Restored archived and trashed plans return as `PAUSED`.
- Archived and trashed plans are not offered by the dashboard switcher.
- Deleting a plan permanently relies on Room cascade ownership.

## Deferred by design

- Import/export and snapshot payload files remain Phase 4.
- Subject and topic management remains Phase 5.
- Physical private-annex byte duplication remains part of the annex-storage phase.

## r3 compiler compatibility seal

The AGP 9 built-in Kotlin compiler resolves scoped layout modifiers through their receiver scopes.
Phase 3 r3 removes explicit imports of `align` and `weight` and validates the whole Kotlin source tree
against explicit imports of other scope-only Compose layout modifiers.
