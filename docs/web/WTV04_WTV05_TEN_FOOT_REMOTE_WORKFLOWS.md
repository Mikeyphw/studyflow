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

## WTV14 audit corrections

The final promise-by-promise roadmap audit found four cumulative WTV05 leaks and closes all of them:

- WTV12 correctly replaced the TV Progress tabs with a navigation-only analytics surface, but that made the promised existing-goal completion and pause/resume controls unreachable. WTV14 restores those low-input goal controls on Today. Goal creation still remains on phone/computer, and WTV12 Progress remains read-only.
- TV Settings reused the normal Storage Health component and could expose its orphan-file cleanup mutation after a health check. WTV14 keeps the health check but suppresses cleanup on TV; cleanup remains available in normal web mode.
- A Writing activity opened from the Today queue could still start/resume the Writing session even though the Writing Workspace itself was delegated off-TV. WTV14 turns that route into an explicit phone/computer handoff and removes the TV Resume Writing control.
- The full plan-creation onboarding wizard could still appear when onboarding was incomplete. WTV14 replaces it in TV mode with a non-authoring handoff notice; normal web onboarding is unchanged.

The final seal also stops Search from auto-focusing its text field in TV mode, so opening Search does not immediately summon an on-screen keyboard; the field remains reachable explicitly by D-pad.
