# WTV14 — TV Library + roadmap promise audit / final seal

WTV14 closes the browser-TV roadmap with a dedicated Library/reference surface and a cumulative promise audit across WTV00-WTV13. The audit uses the current source, runtime modules, relay/server behavior, offline shell and regression suite as evidence; documentation labels alone are not treated as proof.

## Dedicated TV Library

- `LIBRARY` is now a first-class `#library` History API screen instead of only mapping to Plan → Library.
- The TV rail includes Library and protocol-v1 `OPEN/LIBRARY` routes to the same dedicated surface.
- All / Local files / Web references filters are D-pad buttons and paired-phone shortcuts.
- Previous/Next changes the selected material deterministically; START opens the selected material.
- Search → Material replaces the Search overlay with `#library` and then opens the material as a controlled `material` history overlay, so Back closes the viewer before leaving Library.
- The Library remains read-only: upload, delete and provenance/metadata editing continue on phone/computer.

## TV Material Viewer

The existing MIME-driven viewer remains authoritative. WTV14 adds TV-only controls without adding a second material model:

- local text is deterministically paged into bounded reading chunks with Previous/Next;
- images expose remote zoom-out/zoom-in controls;
- local PDF embeds use `#page=N&zoom=page-width` navigation plus an explicit Open PDF fallback for TV browser PDF viewers that ignore fragments;
- audio/video continue to use native controls and also respond to the existing TV Play/Pause media-key bridge;
- HTTP(S) references remain explicit external-resource opens;
- unsupported/download-only files retain an explicit local open/download action.

Remote command dispatch is scoped to the active `aria-modal=true` dialog. This keeps generic Previous/Next commands inside Material Viewer while it is open instead of accidentally operating the Library underneath it.

## Roadmap promise audit

| Phase | Audited result |
| --- | --- |
| WTV00 | PASS — query overrides, conservative TV UA detection, capability flags and normal-web default remain implemented. |
| WTV01 | PASS — dependency-free LAN server, `0.0.0.0`, `/tv`, printed LAN URLs and loopback-only ordinary dev remain implemented. |
| WTV02 | PASS — ES2019 target, state clone fallback, flex-gap probe/fallback and critical TV CSS compatibility contract remain implemented. |
| WTV03 | PASS — deterministic spatial focus, active-modal scoping, native editing/select arrows and tablist ownership remain implemented. |
| WTV04 | PASS — 10-foot rail, focus cues, readable sizing/density, TV modal/material/question layouts and no critical `clamp()`/CSS `min()` dependency remain implemented. |
| WTV05 | PASS AFTER CORRECTION — remote-first authoring handoffs remain implemented. The audit corrected four cumulative leaks: goal completion/pause/resume became unreachable after WTV12; TV Storage Health could expose cleanup; Today could still start/resume a Writing session; and incomplete onboarding could expose the full plan-creation wizard. WTV14 restores goal controls on Today, keeps TV storage health check-only, hands Writing launch/resume off to phone/computer, and replaces TV onboarding authoring with a non-authoring handoff. Search also no longer auto-opens the TV keyboard. Library has an explicit edit-on-phone/computer handoff. |
| WTV06 | PASS — History API screen/overlay restoration, Search/Settings/Material overlays, popstate and TV Back keys remain implemented; Library is now an additional first-class history screen. |
| WTV07 | PASS — protocol v1, six-digit/120-second pairing, explicit user presence, in-memory revocation, SSE/poll fallback and presentation-only relay remain implemented. Library remote routing is now first-class. |
| WTV08 | PASS — dedicated Study Session, canonical focus mutations, deterministic segments, phone bindings and media key bridge remain implemented. |
| WTV09 | PASS — dedicated Question Set, stable choices, canonical `answerQuestion` grading/evidence, explanation and typed-answer handoff remain implemented. |
| WTV10 | PASS — dedicated Mock Exam, frozen saved blueprint, live app ticker-backed countdown, pause/resume, flags, guarded Back and canonical `recordMockExam` persistence remain implemented. |
| WTV11 | PASS — dedicated Reviews/Error Notebook, canonical review progression, category reclassification/resolution and protocol-v1 option IDs remain implemented. |
| WTV12 | PASS — evidence-backed truthful Progress, no-data semantics and navigation-only analytics remain implemented. Goal mutations restored by WTV14 live on Today, not Progress, so WTV12 Progress remains read-only. |
| WTV13 | PASS — dedicated read-only Schedule, Today/week views, canonical ordering/routing, writing handoff, TV rail and phone shortcuts remain implemented. |
| WTV14 | PASS — dedicated Library/reference browsing, TV Material Viewer controls, first-class Library history/remote routing, cumulative compatibility/history/offline audits and roadmap seal. |

No unresolved browser-TV roadmap promise remains after WTV14.

## Corrective findings from the final audit

The audit did not accept an older phase as complete merely because a dedicated test once passed. It rechecked the cumulative WTV14 tree, because later overlays can make an earlier promise unreachable or accidentally re-expose a forbidden editor. Four WTV05 issues were found and corrected in the final tree:

1. **Existing goal actions became unreachable.** WTV12 made Progress intentionally read-only, replacing the older Goals tab. WTV14 moves only completion and pause/resume to Today. Goal creation/editing remains off-TV.
2. **Storage cleanup leaked through a shared component.** TV Settings still rendered `StorageHealthCard`, whose normal-web version can purge orphan attachments. WTV14 adds a check-only TV configuration; the destructive cleanup button remains available only outside TV mode.
3. **Writing could still start/resume from TV.** The editor was hidden, but Today activity routing could invoke `startWritingSession` and expose Resume Writing. WTV14 intercepts Writing launch in TV mode and delegates it to phone/computer; normal web behavior is unchanged.
4. **Initial onboarding could expose plan authoring.** When `onboardingCompleted=false`, the full creation/mode/template wizard was still mounted on TV. WTV14 substitutes a TV handoff notice that can be dismissed for the current session to browse existing local data without mutating onboarding state.

Search remains supported on TV, but its text input no longer uses automatic focus in TV mode. This preserves explicit D-pad access without immediately opening a soft keyboard.

## Final compatibility / release constraints

- No new backend or network dependency.
- No separate TV database, portable format or evidence model.
- TypeScript output remains ES2019.
- The service-worker source caches every `runtime/tv*.js` module, including `tv_library.js`.
- Normal mobile/desktop routes remain limited to Today/Plan/Practice/Progress and retain PC19 direct handlers.
- `web/dist/service-worker.js`, `web/dist/release-manifest.json` and `web/dist/THIRD_PARTY_NOTICES.txt` remain generated build outputs and are not immutable Devtool payload entries.
