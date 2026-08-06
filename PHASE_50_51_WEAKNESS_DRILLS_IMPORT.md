# Phase 50-51: Weakness drills and question import

Phase 50 adds a deterministic weakness-drill generator. It converts repeated wrong-answer entries, overdue question reviews, matching topics, and matching subjects into a ranked question set. The generator refuses to invent a drill when the plan has no questions, no weak signals, or too few matching questions.

Phase 51 adds a local question import pipeline for CSV, TSV/Anki-style rows, and Markdown question blocks. It previews validated drafts, detects duplicates against existing questions and repeated import rows, and saves only accepted questions through the existing practice repository path.

The V2 Practice workspace now exposes both capabilities without creating a separate feature island. The Errors tab can create a weakness drill as an immediate-feedback practice test, and the Questions tab can paste/import question text.

The Progress workspace warning fix replaces deprecated directional `Icons.Outlined.TrendingUp` usage with `Icons.AutoMirrored.Outlined.TrendingUp`.

## R2 audit corrections

An independent delivery audit found two source-level gaps before Devtool target validation: the importer used illegal Kotlin regex escapes and literal tab characters, and the weakness-drill scorer could let recurrence weight pull unrelated questions into a drill. R2 fixes both, defaults optionless TSV/Anki rows to `SHORT_ANSWER`, counts rejected import drafts instead of issue rows, and extends tests plus `scripts/verify_phase_50_51.py` so these regressions fail before packaging.
