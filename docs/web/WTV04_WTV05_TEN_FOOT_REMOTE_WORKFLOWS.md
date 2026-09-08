# WTV04-WTV05 — 10-foot presentation and remote-first workflows

This batch builds on WTV00-WTV03. It does not fork StudyFlow state, persistence, domain rules, or portable formats. The TV browser remains the same React application with explicit presentation branches gated by `data-studyflow-tv=true`.

## WTV04 — 10-foot visual polish

- Strengthens the persistent left rail and selected-destination indicator.
- Expands hero, metric, card, tab, list and modal sizing for 720p/1080p viewing distance.
- Keeps readable text lines bounded while allowing the overall TV canvas to use much more horizontal space.
- Adds focused-control scale cues on top of the existing 5 px focus ring. Text inputs do not scale while editing.
- Adds TV-specific layouts for subject/topic reference trees, schedules, due reviews, question browsing and answer reveal.
- Preserves the WTV02 compatibility rule: no `clamp()`/`min()` dependency is introduced into critical TV sizing.

## WTV05 — remote-first workflow simplification

TV mode now distinguishes low-input remote actions from authoring workflows.

### Kept on TV

- Today focus start/pause/complete/cancel and queued-activity launch.
- Active-plan switching.
- Read-only upcoming schedule, subject/topic hierarchy and Library Material Viewer.
- Due-review `Again` / `Good` / `Easy`.
- Question browsing plus explicit answer reveal for review.
- Weakness-drill preview.
- Error reclassification and resolution.
- Progress/readiness/performance/coach analytics.
- Existing goal completion and pause/resume.
- Storage-health checks and language/search-scope settings.

### Moved off TV

- Quick Add and session-note text entry.
- Writing Workspace drafting/rubric/checklist editing.
- Plan creation/rename/archive/duplicate/mode configuration.
- Schedule mutation/bulk editing and Plan Content imports.
- Material upload/delete/reference-provenance editing.
- Flashcard authoring, question authoring/import, typed graded answer entry, saved-set naming and mock answer entry.
- Goal creation.
- Portable package export/import, encrypted-password entry, destructive local-state reset, intelligence mutations/report downloads and PWA installation controls.

Each removed TV editor is replaced by a visible `Continue this edit on a phone or computer` affordance instead of silently hiding capability. Normal web mode is unchanged.

## Deferred

WTV06 remains responsible for browser-history / remote Back semantics. WTV07 remains responsible for phone-to-browser pairing/control/cast relay.
