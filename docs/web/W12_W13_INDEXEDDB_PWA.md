# WEB-W12/W13 — IndexedDB storage and offline PWA

## W12 — durable browser storage

StudyFlow now prefers IndexedDB for structured state and local attachment bytes. The previous `localStorage` repository remains only as a compatibility fallback and migration source. On first IndexedDB startup, a valid `studyflow.local.state.v3`/v2/v1 state is migrated, written and validated before legacy keys are cleared.

Structured StudyFlow data and attachment bytes remain separate: `Material` records keep `attachmentId`, MIME type and `sizeBytes`, while the Blob/File bytes live in the IndexedDB `attachments` object store. The Library can store, open and delete local files. A failed metadata commit rolls back a newly stored attachment. Resetting local state clears the attachment store as well.

The current per-file UI guard is **50 MiB**. If IndexedDB cannot initialize, StudyFlow falls back to the localStorage compatibility repository and reports that attachments are unavailable rather than pretending they were saved.

Portable JSON remains truthful: attachment metadata may be represented, but attachment bytes are not embedded in the W11 JSON package. Export strips IndexedDB attachment locators/IDs and declares `bytesIncluded: false`, preventing imported state from pointing at nonexistent local blobs.

## W13 — installable offline PWA

The web app now includes:

- `manifest.webmanifest` with standalone display, start URL, theme/background colors and 192/512/maskable icons;
- `service-worker.js` with a versioned app-shell cache;
- cache cleanup during activation;
- navigation network-first with cached `index.html` fallback;
- same-origin static asset cache fallback;
- install-prompt and `appinstalled` handling;
- online/offline status messaging.

There are no CDN or remote runtime dependencies in the app shell. Service-worker installation makes the current shell available after the first successful load. User state and attachment bytes remain in IndexedDB and are not copied into Cache Storage.

## Boundaries preserved

W12/W13 do not introduce TypeScript or React; those remain W14/W15. OPFS remains optional future optimization rather than a requirement. Android-only alarms, permissions and Termux execution are not simulated by the browser PWA.
