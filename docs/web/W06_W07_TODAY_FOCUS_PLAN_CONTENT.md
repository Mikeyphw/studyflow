# WEB-W06/W07 — Today/Focus + Plan Content

## W06 Today + Focus

Focus is now durable browser state. Running truth is represented by absolute `startedAt`, `lastResumedAt` and `endsAt` timestamps; rendering derives remaining time from the current clock rather than trusting interval ticks. Pause/resume survives reload. Completion creates a `StudySession`, optionally completes the linked Activity, and creates `Evidence` with `provenance = session`; cancellation remains visible in session history without pretending it is completion evidence. Session notes are stored with history.

The Today recommendation is derived only from activities already present in the local queue, plus explicit topic priority/subject weight/origin metadata when those fields exist. Missing evidence never becomes a fabricated weakness claim. Overdue review count is derived from actual Review records.

W04 compatibility is retained through explicit migration from `studyflow.local.state.v1` to schema v2/current storage. Corrupt data still fails closed to a safe baseline. IndexedDB remains W12.

## W07 Plan Content

Plan Content now supports local Subject and Topic create/read/update/safe-delete, explicit archive, reorder, subject weight, topic priority/status, notes, search and filters. Topic rows show evidence counts only from persisted Evidence records. Deletion refuses records that still own child topics or are referenced by study history; archiving is the safe alternative.

The import pipeline is intentionally not implemented here. TXT/Markdown/CSV/TSV and later PDF imports remain preview/diff/apply work so parsing never mutates live plan content silently. Schedule intelligence remains W08.

## Boundaries

- Vanilla JavaScript and browser storage only; no React or TypeScript yet.
- No network/backend/account dependency.
- Domain code remains independent from DOM/storage/network.
- `.devtool.toml` remains untouched; validation continues through the existing Node target.
