# Phase 50-51 acceptance

Source validation must prove:

- weakness drills rank direct repeated errors before same-topic and same-subject questions;
- overdue question reviews can drive a drill even when the error notebook is empty;
- drills are not invented without questions or weak signals;
- CSV, TSV/Anki-style, and Markdown import formats are parsed;
- invalid rows and duplicates are skipped before saving;
- V2 Practice exposes weakness drills and question import;
- English and pt-BR workspace resources stay in parity;
- `Icons.AutoMirrored.Outlined.TrendingUp` replaces the deprecated directional icon.

Devtool target validation remains responsible for Kotlin/Android compilation, lint, tests, resource merge, APK assembly, and device checks.

## R2 audit gate

The verifier must additionally reject illegal Kotlin regex escapes, tab characters in patched Kotlin source, recurrence-only scoring of unrelated drill questions, and rejected-count math based on individual issue rows instead of rejected drafts. Domain tests cover TSV/Anki short-answer import, escaped quotes in CSV, and unrelated-question exclusion.

## R3 compile gate

The verifier must require stable local snapshots for `flashcardId`, `questionId`, `errorEntryId`, and `topicId` before null checks in `reviewTitle`. Direct smart-casts of the public cross-module `ReviewItem` properties are forbidden because they fail Kotlin compilation.

