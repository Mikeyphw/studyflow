# WEB-W04/W05 — Local application state + core study domain

This combined overlay moves StudyFlow Local from an ephemeral DOM prototype to a real local application model while keeping the existing mobile-first UI and Node Devtool target intact.

## W04 — application state + temporary persistence

The browser now has one canonical versioned state object with an explicit `schemaVersion`. It is split across dedicated modules:

- `src/storage.js` — the only `localStorage` boundary;
- `src/state.js` — mutations, subscriptions, persistence status and reset behavior;
- `src/render.js` — projects state into the DOM;
- `src/events.js` — binds persistent UI events;
- `app.js` — UI orchestration plus the W03-only ephemeral navigation/timer behavior.

The storage key is versioned (`studyflow.local.state.v1`). Saved JSON is parsed and validated before use. Invalid or unavailable storage never crashes the app: StudyFlow continues from a safe state in memory only and reports that persistence is not durable. No backend, account, `fetch`, IndexedDB or cloud service is introduced.

Quick Add is now durable: a new Activity is created through the domain layer, committed by the store, saved locally, and rendered from state. Reset sample data is explicit and local. The focus timer is intentionally still in memory only because W06 owns durable focus/session recovery and evidence.

IndexedDB remains W12 scope. The temporary repository API is deliberately narrow so W12 can replace the storage implementation without moving domain logic into the database.

## W05 — core study domain model

`src/domain.js` is framework-independent and has no DOM, browser-storage or network dependency. It defines stable-ID constructors and validation for:

- Plan
- Subject
- Topic
- Activity
- StudySession
- Goal
- Material
- Review
- Question
- Evidence

The state validates global ID uniqueness and important references such as subject -> plan, topic -> subject/plan, session -> activity, and evidence -> activity/session/question. The five long-term StudyFlow modes are represented without coupling them to UI components: `EXAM_PLAN`, `PROGRAMMING_COURSE`, `LANGUAGE_DECK`, `READING_STUDY`, and `GENERAL_SKILL`.

Pure selectors expose the active plan, plan subjects, subject topics, queued activities, and a small plan inventory summary. Full scheduling, readiness, review policy, practice scoring, evidence analytics and content CRUD remain owned by later roadmap phases.

## Phase boundary

W04/W05 establishes:

`event -> store mutation -> domain-valid state -> repository save -> render`

It does **not** implement W06 Today/Focus completion, W07 Plan Content CRUD/imports, W08 scheduling intelligence, W09 practice policy, W10 evidence analytics, or W12 IndexedDB.
