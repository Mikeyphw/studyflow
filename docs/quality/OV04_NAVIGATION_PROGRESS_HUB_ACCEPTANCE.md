# OV04 — Navigation and Progress hub acceptance

OV04 turns the four canonical workspace roots into compact, stable navigation labels and reorganizes Progress around task-oriented segments instead of one long undifferentiated feed.

## Product contract

- Canonical root labels remain **Today / Plan / Practice / Progress** (Hoje / Plano / Prática / Progresso).
- The plan selector keeps the active plan name and switch affordance, but no longer occupies the full workspace width.
- Compact dock labels stay single-line and retain a 48dp minimum interactive height.
- Progress exposes four functional segments: **Summary / Performance / Goals / Tools** (Resumo / Desempenho / Metas / Ferramentas).
- Summary and Performance retain the 7/30/90-day period selector.
- Goals and Tools deliberately omit period controls because those surfaces are not period-scoped.
- Existing readiness, analytics, goal/note, reschedule, sprint, runbook, report, cleanup, and evidence actions remain reachable in the segment that owns them.

## Validation

The overlay validator runs the OV04 source contract together with the OV03, OV02, OV01, BH01–BH08, and focus-shell source seals. Gradle validation is supplied explicitly to Devtool with `--validate --task ...` so the Android target does not expand into its full validation matrix.
