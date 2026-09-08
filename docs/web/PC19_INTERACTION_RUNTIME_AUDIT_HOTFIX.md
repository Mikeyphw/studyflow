# PC19 — Interaction runtime audit + hotfix

PC19 is a corrective runtime audit triggered by a real-device report that StudyFlow rendered but behaved as if it were static. The earlier parity suites proved domain/state contracts and the presence of React handlers, but they did not prove that a browser main thread remained responsive after first paint. PC19 closes that validation gap.

## Critical finding: localization observer could starve the browser main thread

`installDocumentLocalization()` observed `characterData` and translated attributes while also writing those same values back into the observed DOM. The old implementation wrote even when the translated value was already identical. A `MutationObserver` therefore observed its own writes and could repeatedly schedule more writes. On a real browser this can produce **main-thread starvation after the initial React paint**: the page is visible, but clicks, timers, navigation and other queued work appear frozen.

The fix makes every localization write idempotent. Text and translated attributes are changed only when the desired rendered value differs from the current DOM value.

## Critical finding: localization could revert dynamic React text

The old observer remembered only the first string associated with a DOM text node. React frequently reuses a text node when state changes. For example, a heading could legitimately change from `Today` to `Plan`, but the observer could treat the node's first `Today` value as permanent and overwrite the newer value.

PC19 tracks both the canonical source and the last rendered translation. A value that differs from the last rendered value is treated as a genuine application update and becomes the new canonical source. This preserves dynamic React transitions in both English and Portuguese while still preventing observer feedback loops.

## High finding: interaction validation was structural, not behavioral

The previous W03/PC validators checked that `onClick`, `onSubmit`, `useSyncExternalStore` and other interaction tokens existed. That cannot detect a blocked event loop or a localization observer that undoes React updates.

PC19 adds two layers:

- a deterministic regression for localization idempotency and dynamic same-node updates;
- an **instrumented browser interaction smoke** that, when Playwright + Chromium are available, mounts the real tracked CommonJS runtime with the vendored React runtime and verifies onboarding, navigation, Search, Focus start/pause, Quick Add and live locale switching by actually clicking controls.

The browser smoke is optional in environments such as Termux where Chromium/Playwright may not be installed, but it is mandatory for the PC19 audit evidence produced during development.

## High finding: offline cache identity was not content-addressed

The service worker previously used a stable cache name even when runtime files changed. If the service-worker source itself did not change, a browser could keep serving an older cached shell/runtime.

The production builder now fingerprints the self-contained runtime payload and injects a **content-addressed** `CACHE_VERSION`. The release manifest records the same cache version and `check_release.mjs` verifies that the service worker and manifest agree. Any change to the built runtime payload changes the cache identity and therefore causes the browser to install a fresh app-shell cache.

## Medium findings

- The PWA manifest still advertised a light background/theme despite the dark-only product contract. PC19 changes both manifest colors to the dark shell palette.
- Product copy still said encrypted portable backups were unavailable even though PC18 added them. PC19 replaces that stale copy with truthful password-backup guidance.
- A delayed `data-studyflow-interactive="ready"` marker is emitted only after the React effect queue reaches a macrotask. Browser smoke uses this as an event-loop liveness probe.

## Visible UI findings from the real-device captures

The screenshots also exposed two presentation defects that were independent of the frozen event loop:

- **`Skip to content` was permanently visible.** The semantic link existed, but its keyboard-only visual treatment was missing. PC19 hides it off-canvas until keyboard focus, preserving accessibility without adding an always-visible browser-default link above the shell.
- **Editable controls fell back to Android/browser-native styling.** `textarea` was not included in the app font/focus contract and general text/select controls had no dark-shell rule outside older `.field` forms. PC19 applies a consistent dark surface, border, text, placeholder, caret and focus behavior while leaving checkbox/radio/file controls to their appropriate specialized rendering.

These are now static regression requirements so the dark-only shell cannot silently regress to native light/gray form surfaces.

## Parity statement correction

PC18's functional parity statement described feature coverage, but the real-device interaction failure showed that feature presence alone was insufficient to claim an operational browser application. PC19 supersedes the runtime-validity portion of that seal. The parity statement is considered operational only with PC19 applied and its interaction/runtime audit green.

Platform boundaries remain unchanged: Android distribution, Keystore/FileProvider, native Termux arbitrary execution and provider/cloud integrations are still not claimed as browser parity.

## Dark-only policy

PC19 preserves and strengthens the **dark-only** policy. There is no light theme, no light-theme toggle, and the install manifest now matches the dark application shell.
