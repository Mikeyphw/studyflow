# CSOV10 — Real provider lane: WebDAV / Nextcloud

CSOV10 adds the first account-capable provider lane without making cloud sync obligatory.

## Contract

- Fresh installs remain local-first and sync-off by default.
- WebDAV / Nextcloud is an optional provider with a user-supplied HTTPS endpoint.
- Provider uploads accept only encrypted `.sfbackup` bundles.
- The provider writes a small `studyflow-sync-index.tsv` sidecar so remote checkpoints can be listed without decrypting every bundle.
- Downloads and deletes use stable checkpoint IDs from the index.
- Google Drive is represented as an optional provider shell only; it is not configured by default and must not imply a StudyFlow account.

## Validation

Run:

```bash
./gradlew verifyCsov10RealProviders :core:domain:test
```
