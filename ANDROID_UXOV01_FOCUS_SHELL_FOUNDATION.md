# Android UXOV-01 — StudyFlow Focus Shell Foundation

UXOV-01 replaces the stock Material adaptive navigation-suite shell with a StudyFlow-owned Android shell while preserving the canonical Today / Plan / Practice / Progress route model.

## Delivered

- `StudyFlowShell` becomes the root workspace shell for canonical workspace destinations.
- Compact widths use an inset floating `StudyFlowCompactDock` instead of `NavigationBar`/`NavigationSuiteScaffold`.
- Widths at or above 600dp use a StudyFlow-owned navigation rail instead of stock `NavigationRail`.
- The shell remains route-driven: nested/non-workspace destinations render without duplicate workspace chrome.
- Root navigation still delegates through `navigateWorkspaceRoot`, preserving the existing plan-aware navigation contract.
- StudyFlow light/dark palettes are strengthened so the application has a recognizable visual identity even before later workspace redesign overlays.
- Navigation items expose selected semantics and `Role.Tab` for accessibility.
- UXOV-02/03/04 can build on the shell without changing domain/data contracts.

## Deliberately deferred

Today/Plan card-density cleanup, Practice immersive focus/session mini-player, contextual quick actions, Progress visualization, and final motion/adaptive supporting panes remain for UXOV-02 through UXOV-04.

## Validation policy

This is an interim UI overlay. Apply with `--no-validate`. A focused source regression test is included; full Android validation is reserved for UXOV-04.
