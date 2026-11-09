# SFDT22 HF25 — Plan identity and saved workspace isolation

## Observed on GitHub

- HF24 run `42641686109` completed one UI journey and failed at `topic_save_feedback` with `topic_save_settings_before_topic`.
- `after_create` and `after_subject` saw a settings row for the **new plan looked up by name**. That did **not** prove the active content workspace owned the subject/topic.
- The repository's `StudyFlowV2Navigation.navigateWorkspaceRootRoute` uses `restoreState = restoreSavedStack` on root tab switches. `PlanPickerRoute` clears these saved stacks on plan selection, but legacy `DashboardRoute` handoffs used by plan creation, plan-list selection and imports did not.
- A stale saved Plan root can therefore restore a previous plan ID. This is a **source-supported explanation to test**, not proof that settings were deleted or that this was the only failure.

## Production fix

- `DashboardRoute` passes `clearSavedWorkspaceStacks = true` to `LegacyWorkspaceRedirect`.
- The redirect performs `clearWorkspaceSavedStacks()` **before** navigating within one `LaunchedEffect` (not an asynchronous second side effect).
- Other legacy redirects keep their previous behavior, preserving ordinary navigation. The existing plan-picker clear remains.
- The fix does not create settings rows, overwrite personal preferences or bypass session failure rules.

## Harder real-device proof

- The second of the five real journeys verifies the visible Plan workspace header is the newly created plan **before** any subject edit.
- After subject save it verifies, by read-only SQL join, that the persisted subject is owned by that same newly created plan. It does not export row IDs, names, SQL or exception text.
- The host maps new, fixed failure markers to redacted `workspace_plan_mismatch`, `subject_owner_mismatch`, `subject_owner_lookup_failed` codes. Unexpected errors still fail.
- The later topic/save boundary, five journeys, seven screenshots and genuine process restart are unchanged.

## Qualification

Source-only Python and simulated Git/Devtool tests do not demonstrate Android behavior. Full GitHub API36 emulator, build and original historical source suite must still pass. Do **not** merge if the SFDT22 gate is red.

## Safety

The overlay is source-pinned to commit `8f802ca36cf69f4a052cba860d2d78f5cfce34bf` and tree `e6df5f43ab7c61ae3cb0d6d96175d7677b9e9e52` on `feature/sfdt22-real-study-workflows-20261107`. Use `TMPDIR` outside the repository for all staging and disposable worktrees. No local Gradle, force-push or automatic merge.
