# WTV06-WTV07 — TV Back semantics and phone-controlled browser receiver

This batch completes the first TV-browser adapter roadmap. It builds on WTV00-WTV05 and continues to use the same StudyFlow React state/store, IndexedDB persistence, evidence semantics and domain rules.

## WTV06 — History API / remote Back

TV mode now owns an explicit, small browser-history state record instead of using hash changes as the only navigation signal.

- `today`, `plan`, `practice` and `progress` are History API screen entries in TV mode.
- Search and Settings are overlay history entries.
- Material Viewer is a controlled `material` overlay carrying only the material ID; Back closes it without mutating the Library.
- `popstate` restores the matching screen/overlay deterministically.
- Search-result navigation replaces the temporary Search overlay entry instead of leaving a stale modal in the Back stack.
- Normal mobile/desktop web navigation retains the existing direct hash and React handlers required by PC19.
- The TV adapter handles `BrowserBack`, `GoBack`, and common exposed TV back key codes (461 and 10009). Browsers that consume their remote Back key internally still work through native browser-history behavior.

## WTV07 — protocol-v1 browser receiver

The browser receiver mirrors the existing `core:tvcontrol` contract rather than creating a browser-only protocol:

- protocol version: `1`
- pairing code length: `6`
- unpaired code TTL: `120` seconds
- commands require explicit user presence
- surfaces/actions use the same names as `StudyFlowTvSurface` and `StudyFlowTvControlAction`
- cast payloads carry presentation state (`title`, optional subtitle/progress/active item), never Room/database rows or screen-mirror frames

### LAN relay

`npm run serve:tv` now hosts an in-memory relay alongside the static web artifact.

1. TV calls `POST /api/tv/receiver` and receives a receiver ID, session nonce and short-lived code.
2. A phone opens `/remote` and pairs via `POST /api/tv/pair`.
3. Pairing returns a high-entropy controller token scoped to that receiver.
4. The TV receives relay envelopes over SSE (`/api/tv/events`). If `EventSource` is unavailable or fails, the browser receiver falls back to `/api/tv/poll` long polling.
5. The controller may send validated protocol-v1 commands to `/api/tv/control` and presentation state to `/api/tv/cast`.
6. `/api/tv/revoke` invalidates the controller immediately.

Receiver sessions, codes, tokens, event queues and cast/control envelopes live only in server memory. Restarting the server clears all pairing state. The relay does not write a pairing database or copy StudyFlow state into Node.

### Browser actions wired in this batch

The phone can open Today, Study Session, Questions, Reviews, Practice/Mock, Progress, Schedule, Library and Settings. Remote-bindable DOM actions cover focus start/pause/resume/complete, due-review ratings, question previous/next/explanation, queued activity open and Library material open. `BACK` delegates to browser history and `STOP_CAST` clears the current presentation banner.

The `/remote` page is intentionally small and dependency-free; it is a LAN controller surface and not a replacement for the authoritative phone/tablet StudyFlow application.
