# CSOV11 — Final optional cloud sync acceptance seal

CSOV11 seals the local-first, cloud-optional sync roadmap.

## Acceptance contract

- StudyFlow works without cloud, accounts, providers, or background sync.
- Local folder, WebDAV / Nextcloud, and Google Drive lanes are optional catalog entries.
- Remote checkpoint uploads must use encrypted `.sfbackup` bundles.
- Manual conflict review remains the default for conflicting local/remote edits.
- Background sync is never scheduled from a fresh install.
- Automatic modes require a configured provider and encrypted remote backups.

## Validation

Run:

```bash
./gradlew verifyCsov11FinalSyncSeal :core:domain:test :core:data:compileDebugKotlin :feature:settings:compileDebugKotlin :app:compileDebugKotlin
```
