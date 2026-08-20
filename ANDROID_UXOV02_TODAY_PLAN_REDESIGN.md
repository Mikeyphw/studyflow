# Android UXOV-02 — Today + Plan redesign

UXOV-02 builds on the UXOV-01 Focus Shell and migrates the two planning-oriented workspaces away from nested Material scaffold/card composition.

## Today

- Removes the nested workspace `Scaffold` and stock `FloatingActionButton`.
- Keeps one deliberate priority action surface for active session, due reviews, planned work, recommendation, or free study.
- Converts the daily path to dense timeline rows.
- Converts weekly progress to inline metrics instead of metric cards.
- Keeps coach guidance as typography-first content rather than another card.
- Anchors the quick action and snackbar inside the workspace area already reserved by the root shell.
- Preserves all existing Today callbacks and session/review/schedule/discovery behavior.

## Plan

- Removes the nested workspace `Scaffold`.
- Replaces `StudyFlowWorkspaceTabs`/Material tab-row presentation with `StudyFlowTabStrip` while retaining all four `PlanWorkspaceTab` values.
- Replaces overview metric cards, the plan summary card, and full-width Material action buttons with inline metrics, a key/value summary, and dense action rows.
- Schedule, topic and library entries use the new row language.
- Existing target-id ordering, cleanup reporting, accordions and all management callbacks remain intact.

## Shared design-system additions

`StudyFlowFocusWorkspacePrimitives.kt` introduces reusable action surface, section title, timeline item, metric strip/value, plan summary, action row, custom tab strip, and floating action primitives. They intentionally avoid Material Card, stock TabRow and stock FAB visual composition while continuing to use Compose semantics and theme tokens.

## Validation policy

This is an interim UI overlay. Apply with `--no-validate`; full Android validation remains reserved for UXOV-04. UXOV-02 adds focused source-contract tests to prevent nested Scaffold/FAB/tab-row regressions and to guard the existing Today/Plan action surfaces.
