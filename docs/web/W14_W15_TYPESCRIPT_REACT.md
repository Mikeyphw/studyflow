# WEB-W14/W15 — TypeScript + React migration

## W14 — TypeScript migration

W14 moves the framework-independent browser application source from handwritten JavaScript to strict TypeScript while retaining the existing local-first architecture.

Authoritative TypeScript modules:

- `web/src/types.ts` — public state/repository/store/schedule/package contracts.
- `web/src/domain.ts` — pure study entities, normalization, validation and evidence-derived selectors.
- `web/src/indexeddb.ts` — IndexedDB state/attachment persistence.
- `web/src/storage.ts` — browser persistence composition and compatibility fallback.
- `web/src/state.ts` — StudyFlow store/actions.
- `web/src/portable.ts` — portable package validation/preview/apply helpers.
- `web/src/pwa.ts` — install/service-worker/offline integration.
- `web/src/main.tsx` — application composition root.

`tsconfig.json` enables strict checking. `scripts/compile_runtime.mjs` compiles source to a temporary CommonJS tree and byte-compares it with the tracked `web/runtime/` output. Validation therefore rejects stale generated runtime files.

The pre-W14 `web/src/*.js` implementation paths are migration tombstones only. They contain no domain, storage or UI logic and are not loaded by the application.

## W15 — React UI migration

React owns rendering and event binding in `web/src/react/App.tsx`. It does **not** own the store, scheduling algorithm, grading, evidence, portable-package validation, IndexedDB or attachment storage. `main.tsx` builds those services first and then mounts React.

The UI preserves the existing primary product loop and nested destinations:

- Today / Focus
- Plan: Overview / Schedule / Content / Library
- Practice: Reviews / Questions / Tests / Errors
- Progress: Summary / Performance / Goals / Tools

Components subscribe to the typed external store with `useSyncExternalStore` and call typed store actions. This keeps W05–W13 domain/application work reusable instead of rewriting it inside React.

## Runtime and offline boundary

The repository pins React and ReactDOM 19.2.8 and TypeScript 5.8.3 in `package-lock.json`. The small local `app.js` bootloader resolves the generated CommonJS graph and local package files under `node_modules`; it has no CDN URL. The W15 service worker includes the generated runtime plus React/ReactDOM/Scheduler production modules in the versioned app shell, preserving the W13 offline contract after a successful local install/first load.

No backend, authentication service, cloud database, telemetry, analytics or remote runtime dependency is introduced.
