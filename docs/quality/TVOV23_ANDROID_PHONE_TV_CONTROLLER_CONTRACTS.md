# TVOV23 Android phone TV controller contracts

The phone controller is considered installed only when all of the following remain true:

- `SettingsScreen` exposes a TV-controller callback while remaining free of `StudyFlowTvLocalTransportClient` and `StudyFlowTvReceiverDiscovery` dependencies.
- `TvControllerRoute` is a typed navigation route and the app navigation graph opens `StudyFlowTvPhoneControllerScreen`.
- the screen consumes the production `AppContainer.tvReceiverDiscovery` and `tvLinkClient` objects;
- NSD discovery is foreground/user-present, releases the screen listener on stop, and requests the TVOV22 nearby-device permission when required;
- receiver selection is explicit and six-digit pairing is user-entered;
- a persisted unexpired credential can resume after process restart and select its matching rediscovered TV;
- all ten TV surfaces are reachable through shared `StudyFlowTvControlCommand` `OPEN` intents;
- session, question, mock, review, Back, continuity refresh, revoke, and local-forget controls are reachable;
- every remote command retains `requiresUserPresence=true`;
- stale/missing/unauthorized session responses clear the saved phone credential;
- no Cast SDK, MediaRouter, alternate network client, raw DB row, or arbitrary UI-tree mirroring is introduced;
- live typed-presentation synchronization stays deferred to TVOV24;
- the TV package remains within the existing small-APK target and advances to version code 23 / `0.1.0-tv23`.
