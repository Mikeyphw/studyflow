# Remediation OV24 — Web persistence, bootstrap, portable import & secret hygiene

Overlay 24 of 30 starts the normal Web/PWA hardening wave after OV23 sealed the Desktop runtime and StudyFlow Link UX.

## Baseline

- Roadmap position: Overlay 24 of 30.
- Previous overlay: OV23 — Desktop runtime, UX, tray & SFLP correctness.
- Target surface: normal Web/PWA and shared Web runtime.
- User-applied state: cumulative StudyFlow remediation through OV23 plus the Web/PWA source tree in the supplied snapshot.

## Findings closed

- `S10-01` — IndexedDB save acknowledgements were optimistic and could report success before the durable transaction committed.
- `S10-02` — Concurrent tabs could overwrite each other without a committed revision check.
- `S10-03` — IndexedDB initialization failure silently downgraded to localStorage and hid the durability loss.
- `S10-04` — Service-worker registration failures could poison the memoized registration promise and prevent later retries.
- `S10-09` — Portable and plan-content imports could materialize local files before enforcing import-size limits.
- `S10-10` — Exported portable packages carried live focus runtime timestamps/session state.
- `S10-11` — Web bootstrap could crash when the browser denied `localStorage` access.
- `S10-12` — BFCache restores stopped attention runtime without restarting it when the page resumed.
- `S10-13` — Removing a material row did not consistently clean its local attachment blob.
- `S10-14` — Web UI success copy could fire before asynchronous storage/import/export operations finished.
- `DS2-04` — Encrypted portable passwords were not cleared across all success and failure paths.
- `DS2-36` — Web sessions without IndexedDB attachment storage still exposed file-upload affordances.
- `DS2-46` — Import/preview password state could remain in memory after failed encrypted preview/import.
- `DS2-48` — Local browser fallback states were not explicit enough for users to distinguish compatibility mode from disabled durability.

## Implementation

### IndexedDB durable commits and multi-tab conflicts

`IndexedDbStudyRepository` now treats save and clear operations as asynchronous durable transactions. Calls return `pending` until `flush()` observes the transaction completion. The repository tracks the last committed revision loaded from IndexedDB and compares it inside the write transaction before replacing the current record. A stale tab aborts the transaction and rolls back its optimistic cache so the older tab cannot overwrite a newer committed state.

### Visible degraded persistence instead of silent downgrade

`createBrowserPersistence` keeps localStorage compatibility only when the IndexedDB API itself is absent. If IndexedDB exists but fails to open or initialize, Web/PWA returns an explicit unavailable repository and unavailable attachment store with fallback copy explaining that persistence is disabled instead of silently writing canonical study data to localStorage.

### Portable import/export hygiene

Portable import readers now enforce file-size ceilings before reading text or bytes. Plan-content imports are capped by the same 5 MiB local limit, encrypted packages are capped before `arrayBuffer()`, and portable runtime state is sanitized before package emission or import validation so live focus timers and timestamps do not leak across devices.

Encrypted export, import, and preview paths copy the submitted passphrase to a local variable and clear React password state in `finally`, covering success and failure paths. File-upload buttons are hidden when IndexedDB attachment storage is unavailable, and material deletion removes both the domain row and the local attachment blob.

### Bootstrap, attention, and async UX honesty

Service-worker registration clears its memoized promise after a failed registration so a later browser retry can attempt registration again. The Web attention runtime and SFLP peer bootstrap use safe localStorage probes, avoiding crashes when a browser policy throws on `localStorage`. BFCache page restores restart the attention runtime without disposing bridge state, while real terminal page hides still dispose bridges.

The shared React `run()` helper now waits for promise fulfillment before showing success toasts and surfaces asynchronous failures as failure copy instead of claiming success early.

## Validation

The overlay adds `verifyOv24WebPersistenceBootstrapSecretHygiene`. The artifact validation hook runs the focused OV24 verifier without stopping Gradle daemons.

The verifier checks source contracts for durable pending saves, stale-tab conflict rejection, explicit IndexedDB-open failure, import-size prechecks, focus sanitization, password clearing, attachment honesty, asynchronous success copy, service-worker retry, BFCache attention resume, localStorage-denial bootstrap safety, Gradle wiring, documentation, changelog and closure-ledger updates. It also runs TypeScript runtime parity plus the focused Python/Node OV24 behavior test suite.

Manual validation command:

```bash
./gradlew --no-daemon --offline verifyOv24WebPersistenceBootstrapSecretHygiene
```

Focused Web fallback when Gradle is unavailable:

```bash
cd web
node scripts/compile_runtime.mjs
python3 -m unittest tests/test_ov24_web_persistence_bootstrap_secret_hygiene.py -v
```
