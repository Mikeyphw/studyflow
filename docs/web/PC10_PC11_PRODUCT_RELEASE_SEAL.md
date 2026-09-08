# PC10 / PC11 — Product shell and parity-closure release seal

PC10 closes the remaining product-shell gaps. Global search is local-only and indexes plans, subjects, nested topics, questions, materials and error records without a backend. Search can optionally include archived plans. Settings persist application locale, search scope and existing schedule/review preferences. Privacy and storage surfaces state the local-only boundary, telemetry/account absence and storage health. At the original PC10/PC11 seal, encrypted portable backups were not yet part of StudyFlow Local 1.0. PC18 supersedes that portability-security limitation with an Android-compatible authenticated encrypted envelope; the local-only/no-account boundary remains unchanged.

Persistent application preferences advance structured state to schema **v7**. Existing v6 users migrate with onboarding already completed, while genuinely fresh state shows the local onboarding wizard. The wizard can retain sample content or create a blank plan. English and pt-BR are persisted locally, update the document language and localize the rendered product shell without changing domain data.

PC11 replaces the source-tree deployment assumption with a production `dist/` artifact. `scripts/build_dist.mjs` copies the compiled StudyFlow runtime and the exact pinned production React/ReactDOM/Scheduler files into `dist/vendor`; the production bootloader/service worker is rewritten to those local vendor paths and does not reference `node_modules` at runtime. The release verifier rejects remote runtime URLs, missing app-shell files, manifest/hash mismatches and any production `node_modules` reference.

`npm run release:package` creates a deterministic ZIP named `studyflow-local-1.0.0.zip` with normalized 1980 ZIP timestamps plus a SHA-256 sidecar. The release manifest hashes every dist payload. The source development tree may still use npm-installed dependencies during build, but the shipped artifact is self-contained.

Acceptance wording: global search is local-only. The historical no-encrypted-backup statement is superseded by PC18; PC10/PC11 remains the source for the local-only product-shell contract.

historically, encrypted portable backups were not part of StudyFlow Local 1.0; PC18 supersedes this limitation.

## Runtime URL validation hardening

The production release checker rejects actual remote runtime dependencies and network-capable references (remote script/link/form assets, CSS `url()`/`@import`, `fetch`, XHR, workers, WebSocket/EventSource, `sendBeacon`, and remote module imports). It intentionally does not reject inert URL strings embedded inside pinned vendored libraries, such as React diagnostic links or W3 namespace identifiers, because those strings do not create network dependencies.
