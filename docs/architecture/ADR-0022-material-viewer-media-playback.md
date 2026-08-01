# ADR-0022: Material Viewer and Media Playback

## Decision

StudyFlow opens study resources through explicit launch plans. Domain code classifies each resource
as a web page, external document, external video, app-private annex, or missing source. Android UI
code turns those plans into safe `ACTION_VIEW` intents.

## Rationale

The domain layer stays free of Android `Context`, `Intent`, and `Uri` types. App-private annex copies
are exposed through the application `FileProvider`, which gives temporary read access without leaking
raw private file paths.

## Non-goals

- No video downloading.
- No DRM handling.
- No background playback.
- No custom browser engine in this phase.
