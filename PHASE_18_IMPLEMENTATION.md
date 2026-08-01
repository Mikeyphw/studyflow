# Phase 18: Material Viewer and Media Playback

Phase 18 closes the post-MVP gap where library resources existed only as metadata.

## Delivered

- Launch planning for materials, videos, and annexes.
- Safe Android `ACTION_VIEW` handoff for web pages, documents, videos, and app-private annex copies.
- App `FileProvider` for app-private annex storage.
- Manifest package visibility queries for viewer/playback intents.
- Library UI open actions.
- Material/video open progress hooks.

## Scope boundary

Phase 18 does not download remote videos, bypass DRM, or implement background playback. It makes
existing StudyFlow resources usable through safe local or external viewer handoff.
