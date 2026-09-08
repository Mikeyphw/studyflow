# WTV00-WTV03 — TV-browser foundation

This batch adapts the existing StudyFlow Local web product for television browsers without creating a second state model or frontend.

## WTV00 — compatibility baseline

- `?tv=1` forces TV mode.
- `?tv=0` forces the normal web shell even on a detected TV user agent.
- Otherwise a conservative Smart-TV user-agent matcher enables the adapter for Tizen, webOS/NetCast, HbbTV, BRAVIA/GoogleTV/Android TV and Fire TV identifiers.
- Runtime data attributes expose whether IndexedDB, secure context, SubtleCrypto and Service Worker support are available.
- The normal browser/mobile product remains the default and unchanged when TV mode is off.

## WTV01 — LAN server

`npm run serve:tv` builds the web artifact and starts a dependency-free Node HTTP server on `0.0.0.0:8000` by default. It prints detected LAN IPv4 URLs and redirects `/tv` to `/?tv=1`.

`npm run dev` intentionally remains bound to `127.0.0.1` for ordinary local development.

The TV server supports plain LAN HTTP because many televisions are easiest to reach that way. StudyFlow does not pretend that HTTP is a secure context: service-worker and Web Crypto availability are separately reported.

## WTV02 — older Smart-TV runtime

The TypeScript runtime target is ES2019 so optional chaining/nullish-coalescing and similar syntax are downleveled instead of being shipped directly to older TV Chromium engines. Critical TV layout uses `calc()`/`max-width` rather than newer CSS `min()`/`clamp()` functions, and the adapter probes flex-gap support at runtime with margin fallbacks for older Chromium TV engines.

IndexedDB state cloning uses native `structuredClone` when present and a JSON-safe state fallback otherwise. Attachment bytes are not cloned through this helper, so this fallback remains scoped to the persisted `StudyState` value.

## WTV03 — D-pad spatial focus

When TV mode is active, standard Arrow keys perform geometry-based spatial navigation across visible focusable controls. Selection is deterministic and strongly prefers candidates aligned with the current element.

Focus is scoped to the active modal while an `aria-modal=true` dialog is visible. Text-editing controls and selects retain native Arrow behavior, and Left/Right inside existing tablists remains owned by the tab component. Successful spatial moves prevent page scrolling and center the newly focused control.

Changing top-level screens focuses the first meaningful control in the new screen rather than the main container. Focus visibility is enlarged for 10-foot viewing.

## Deliberately deferred

WTV04-WTV07 remain separate work: full visual/density refinement, TV workflow simplification, browser-history Back semantics, and phone-to-browser control/cast relay.
