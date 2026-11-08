# SFDT22 HF10 — await the real Plan Content tab and pane

**Pinned PR:** Mikeyphw/studyflow #15, commit `8d6fffc108c763c705f666c3a6054b3ebc125063`.

## Hosted evidence

Run `42487198002` (HF09) compiled and completed the real plan-creation journey, but `realUserAddsSubjectAndTopic` failed at `subject_open_content` with `compose_assertion`. This can occur when the bottom Plan root is selected before the PlanWorkspace screen has finished loading. No evidence suggests a failure in the HF09 topic editor path.

## Patch

The existing Compose instrumentation now waits for the production Plan Content tab (text + Role.Tab), scrolls the horizontal strip when necessary, taps the real tab using touch input, checks its selected semantics, and waits for the production Manage Content action in the pane. Failure markers separate missing tabs, failed touch, unsuccessful navigation, and an unavailable pane. No route or repository shortcuts, no seeded fixtures, no disabling tests. Five study journeys, seven screenshots, process restart and persistence remain compulsory.

## Qualification

Source-pinned portable installer, SHA-256 payload verification, clean-worktree and correct branch verification, atomically stages modified files only after all existing offline contract suites pass. GitHub Actions hosted Android API36 must still pass to qualify.
