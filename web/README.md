# StudyFlow Local Web

StudyFlow Local Web is the mobile-first, self-contained browser edition of StudyFlow.

## W00 contract

- The phone experience is canonical; tablet and desktop layouts enhance it.
- The core product requires no account, backend, cloud database, or network after installation.
- Core user data is locally owned and must remain exportable/importable.
- Primary product loop: **Today → Plan → Practice → Progress**.
- Browser limitations are reported truthfully; Android-only behavior is not simulated or overpromised.
- No remote sync, analytics, ads, or telemetry are introduced by this foundation.

## Architecture progression

The `studyflow_web` Devtool target is preconfigured in `.devtool.toml` as a Node target; W00 adopts it without rewriting repository target configuration. Devtool restore is therefore provided by the Node/npm runner, while build/test/lint come from this package.json. W00 then starts the dependency-free web project scaffold. W01 adds semantic HTML. Later overlays add CSS, vanilla JavaScript, local persistence, IndexedDB/OPFS, PWA support, TypeScript, and React in that order. Domain/planning logic stays independent from the UI framework.

## Devtool

From the repository root:

```sh
devtool --yes --copy -r "$HOME/Code/studyflow" --target studyflow_web validate
```

For local browsing after W01 exists:

```sh
cd web
npm run dev
```

## W04/W05 foundation

W04 introduces the versioned temporary browser-state repository and makes Quick Add durable across reloads. W05 adds the framework-independent Plan/Subject/Topic/Activity/StudySession/Goal/Material/Review/Question/Evidence model plus reference validation and pure selectors. Focus/session evidence arrived in W06. IndexedDB is now the primary browser persistence backend as of W12, while the legacy repository remains only for migration/fallback.


## WEB-W06/W07

The current browser build now has durable focus sessions and editable plan content. Focus uses absolute persisted timestamps, survives reload, records completed/cancelled `StudySession` history, and emits completion `Evidence` without inventing performance data. Plan Content supports subject/topic create, edit, safe delete, archive, reorder, weight, priority, status, notes, search, filters and evidence counts. Legacy localStorage state is migrated forward instead of discarded; W12 now advances the current domain state to schema v4 and moves normal browser persistence into IndexedDB.

## W08/W09

Schedule changes are now preview-first and revision-guarded. Practice questions, review completion and the Error Notebook are stored locally and generate evidence only from actual user actions. Ungraded questions remain truthfully ungraded.


## W10/W11

Progress now derives study time, graded accuracy, topic coverage, review health and readiness only from persisted evidence. Unknown measurements render as `No data`; a measured zero remains `0`. Goals use supported evidence metrics. Tools expose a read-only evidence timeline and content-integrity status.

Portable packages use the local `studyflow-local-package` JSON format. Import is limited to 5 MiB, validated, previewed as add/update/remove counts, revision-guarded, and only replaces local structured state after explicit Apply. Attachment bytes are deliberately not embedded in this JSON format; exports declare that boundary and strip device-local IndexedDB locators.


## W12/W13

Structured state now prefers IndexedDB, with verified migration from the old localStorage keys and a compatibility fallback when IndexedDB is unavailable. Local Library files are stored as Blob/File records in a separate IndexedDB attachment store while domain `Material` entities retain only metadata and attachment IDs.

StudyFlow Local is also an installable PWA. The manifest supplies 192/512/maskable icons and standalone launch metadata; the service worker caches the self-contained app shell, supports offline navigation fallback, cleans old cache versions, and exposes install/offline status in Tools. TypeScript and React are now implemented by W14/W15; the domain/store/persistence architecture remains outside the React component tree.


## W14/W15

The authoritative application source is now TypeScript under `src/`, compiled into the tracked CommonJS browser runtime under `runtime/`. `tsconfig.json` uses strict checking, and `scripts/compile_runtime.mjs` recompiles into a temporary directory and byte-compares every generated runtime file so committed browser output cannot silently drift from TypeScript source.

React owns the presentation layer through `src/react/App.tsx`, but it does not own StudyFlow state or business logic. `src/main.tsx` composes persistence, attachments, the StudyFlow store, and PWA services before mounting React; components subscribe with `useSyncExternalStore` and invoke typed store actions. The former vanilla `src/*.js` implementation files remain only as migration tombstones so this overlay does not depend on unproven file-delete inventory semantics.

The browser bootloader stays local and dependency-pinned. It loads the generated CommonJS runtime plus the exact React/ReactDOM/Scheduler files installed by `npm ci`; the W15 service-worker app shell caches that runtime graph for offline use after installation. There is still no backend, account, telemetry, or remote runtime CDN dependency.

## W16/W17

Concurso Público is now a first-class typed plan workflow: truthful target identity/date status, stages, edital preview/apply with revision provenance and archive-not-delete reconciliation, banca-aware question metadata, legislation, mock-exam blueprints/scoring, evidence-derived dashboard metrics and confirmed-date final-stretch guidance.

The remaining plan modes are also functional: programming exercises/projects use a browser-only execution boundary; language decks support six review directions with manual review for sentence creation; reading progress is explicit; and general-skill drills record named metrics. All mode configuration lives under the canonical plan and remains IndexedDB/local-first. PC03 advances the state to schema v6 for per-plan study cycles and nested topic parent links while preserving v5-and-older migration and portable-package compatibility.


## W18/W19 — hardening and Local 1.0

W18 completes the release hardening pass: ARIA/keyboard tab semantics, main-landmark focus management, accessible names for every form control, forced-colors support, explicit PWA listener cleanup, an app-shell-only cache policy, IndexedDB last-known-good recovery, and a storage-health audit with explicit orphan cleanup. Missing attachment references are reported but never silently rewritten.

W19 seals the browser edition as **StudyFlow Local 1.0.0**. `npm run release:check` verifies release identity and offline-shell completeness in addition to the cumulative validator, strict TypeScript compile/runtime parity, tests, and lint. The 1.0 contract remains local-first with no required backend/account/telemetry/CDN, current state schema v6, IndexedDB schema v1, and portable package format v1.

## PC00/PC01 correctness closure

Post-W19 correctness hardening fixes weighted mock scoring and makes the configured spaced-review intervals authoritative. See `../docs/web/PC00_PC01_CORRECTNESS_CLOSURE.md`.


## Post-W19 parity closure: PC02/PC03

Plan Content now has a local generic TXT/Markdown/CSV/TSV/JSON import pipeline with parse → normalize → validate → preview/diff → explicit apply. Merge and reconcile modes are distinct; reconcile archives missing canonical content instead of deleting history. Imports can target the active plan or create a new plan.

Plans are now first-class local workspaces: create, rename, switch, duplicate, archive/restore, and safe-delete are available. Topic hierarchy supports parent/child nesting with cycle prevention and sibling-local reordering. Study-cycle cursors persist per plan.


## Post-1.0 parity closure: PC04 / PC05

Adaptive scheduling now honors plan capacity, optional clock windows, fixed commitments and user locks while exposing conflicts/overload/unscheduled work before Apply. Portable v2 is a full local backup with attachment bytes and SHA-256 verification; portable v1 remains supported for compatibility.

## PC06/PC07 practice + Concurso parity

Question import, weakness drills, saved sets, full banca filters, hierarchical edital revisions, explainable priorities, legislation verification links and the Edital Matrix are local and preview/evidence driven.
