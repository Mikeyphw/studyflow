# TVOV22 local pairing/discovery/transport quality contracts

TVOV22 closes the audited wire-transport gap without turning the TV APK into a second StudyFlow data owner.

- Pairing is explicit and user-present. The TV creates a random six-digit code plus a short-lived nonce; discovery never publishes the code itself.
- Pairing offers expire after 120 seconds and are limited to ten attempts per offer.
- Before pairing, discovery/health responses expose no StudyFlow continuity, destination, active item, or presentation state.
- Successful pairing returns a random expiring session id/secret. The session can be revoked from either side and the credential is persisted only in app-private preferences; both app manifests disable backup.
- Android NSD discovery is started explicitly by a caller and never scans in the background on its own.
- The transport is local framed TCP using a strict, bounded JSON envelope. It carries StudyFlow intents, typed presentation snapshots, and continuity only—no raw database rows or arbitrary UI trees.
- Remote commands enter the same TV reducer as D-pad commands. Typed presentation snapshots enter the same TV presentation inbox added in TVOV21.
- The shared Android link module has no Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, Room, repositories, or phone feature-module dependencies.
- The TV host is active only while the TV activity is started, preserving user presence and avoiding a background receiver service.
- TVOV22 deliberately does not add the phone controller UI. TVOV23 must expose discovery, pairing, control and revoke actions to the Android mobile user.
