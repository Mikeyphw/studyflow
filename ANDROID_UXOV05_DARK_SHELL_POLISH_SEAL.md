# StudyFlow Android UXOV-05-R2 — Dark Shell Polish Seal

This overlay is a focused polish pass on top of UXOV-04-R4. It fixes issues found in the first screenshot audit without changing domain, persistence, repositories, or navigation routes.

## Delivered

- Dark-mode text contrast is corrected by explicitly setting `onBackground`, `onSurface`, and `onSurfaceVariant` in the StudyFlow palette and by giving the root shell a dark-readable content color.
- Workspace content is protected from the status bar through shell-level `WindowInsets.statusBars` handling.
- Non-shell utility/focus surfaces continue using safe drawing insets.
- The compact phone dock is reduced to a 64dp content height.
- Today and Practice use `StudyFlowActionOrb` for contextual floating actions.
- Plan overview spacing is tightened and keeps the compact metric/summary structure.
- Practice gets a visible Ready queue identity header below its custom tabs.
- The separate Goals and notes route from the screenshots is also polished: old `Scaffold`, `TopAppBar`, `FilterChip`, and `StudyFlowSectionCard` usage is replaced by StudyFlow surfaces, metrics, tabs, rows, and action orbs.
- Explicit JVM source-contract tests now cover dark shell polish and the Goals/notes route.

## Validation intent

Apply after UXOV-04-R4. Do not use `--no-validate`; the overlay validates Kotlin compilation, source contracts, workspace/progress unit tests, and lint.
