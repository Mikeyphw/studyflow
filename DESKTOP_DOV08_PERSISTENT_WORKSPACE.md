# DOV08 — Persistent Desktop Workspace

DOV08 replaces the production Desktop shell's restart-volatile Plan/settings/preferences state with a JVM-native durable workspace.

## Implemented

- Atomic `workspace-v1.properties` state under an OS-appropriate data directory.
- `STUDYFLOW_DESKTOP_DATA_DIR` override for tests, portable installs, and operators.
- Study Plans and `StudyPlanSettings` survive process restart.
- Desktop language/theme/default-plan/recent-search preferences survive process restart.
- Production bootstrap opens the durable workspace before constructing services.
- The ordinary test/embed factory remains side-effect-free and in-memory.
- Corrupt or unsupported workspace schemas fail closed instead of silently resetting data.

DOV09 extends this same workspace with Plan/subject/topic lifecycle state; DOV10 and DOV11 extend it with planner and Study Session state.
