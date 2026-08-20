# StudyFlow Android UXOV-04-R4 — Compose Compile Final Seal

UXOV-04-R4 replaces UXOV-04-R3 after Devtool correctly exposed three Compose compile errors in `StudyFlowShell.kt`.

## Fixes

- Imports `androidx.compose.runtime.getValue` so `State<Dp>` returned by `animateDpAsState` can be delegated with `by`.
- Moves expanded-layout active-session `AnimatedVisibility` into `StudyFlowExpandedSessionBar`, a receiver-free composable, avoiding the `RowScope.AnimatedVisibility` implicit-receiver collision.
- Extends the final source seal and JVM contract to require both compile-safety conditions.
- Keeps the complete R2/R3 UI redesign and `Files.readString` compatibility fixes intact.
- Orders explicit validation so Kotlin compilation runs before the heavier repository/unit-test gates.

## Baseline

This replacement overlay targets the exact repository state after UXOV-01, UXOV-02, and UXOV-03. UXOV-04-R3 rolled back and must not be applied first.
