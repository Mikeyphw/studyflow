# WEB-W18/W19 — hardening and StudyFlow Local 1.0 release seal

## W18 — accessibility, testing, offline and storage hardening

The final hardening pass keeps the existing product architecture and closes release-risk gaps rather than adding a new feature family.

### Accessibility

- Main-screen navigation moves programmatic focus to the main landmark after hash navigation.
- Plan, Practice and Progress tabs implement the ARIA tab pattern with `tablist`, `tab`, `aria-selected`, `aria-controls`, roving `tabIndex`, and Left/Right/Home/End keyboard navigation.
- Every input, textarea and select in the React surface has an accessible name; radio choices retain visible labels as well.
- Toast/PWA/storage status surfaces are polite live regions and do not make the focus timer announce every second.
- The visual system retains visible focus, reduced-motion behavior, 44 px interaction targets and adds forced-colors support.

### Storage recovery

IndexedDB keeps `current` plus a last-known-good `recovery` checkpoint in the existing state object store. Each validated save captures the previous valid state before committing the next state. On startup, an invalid current state is never trusted: StudyFlow validates the recovery checkpoint, restores it as current, and reports the recovery explicitly. If neither copy validates, startup still fails closed instead of fabricating state.

Tools also provide a non-mutating storage-health audit. It distinguishes missing referenced blobs from orphaned blobs. The only cleanup operation is explicit orphan deletion; missing references and structured history are not silently rewritten.

### Offline hardening

The service worker now caches only the declared application shell. Navigation uses the installed `index.html` immediately when present. Non-shell same-origin requests are not silently promoted into persistent cache entries. The release check verifies that every tracked runtime module is in the offline shell and that the shell contains no remote URL.

## W19 — StudyFlow Local 1.0

The web package is sealed as **StudyFlow Local 1.0.0**.

Release invariants:

- no required backend, account, analytics, ads, telemetry or runtime CDN;
- React/ReactDOM/TypeScript versions remain pinned by the lockfile;
- TypeScript source and tracked browser runtime remain byte-for-byte reproducible;
- at the W19 seal, state schema was v5; PC03 later advances current state to v6 while IndexedDB schema remains v1 and portable package format remains v1;
- unknown evidence remains distinct from measured zero;
- portable import remains preview-first and revision guarded;
- attachment bytes remain local and are not falsely claimed by the JSON portable package;
- all five study modes remain supported by one canonical state model;
- the installed PWA opens from its local application shell;
- localStorage remains only a structured-state compatibility fallback when IndexedDB is unavailable.

`npm run release:check` validates the release identity and offline shell in addition to the cumulative build/test/lint gates.
