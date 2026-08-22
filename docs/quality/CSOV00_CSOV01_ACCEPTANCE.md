# CSOV00-CSOV01 Acceptance

CSOV00 migrates StudyFlow from no-cloud language to local-first optional cloud language.

CSOV01 adds the off-by-default sync settings model:

- `syncEnabled = false`
- `providerId = null`
- `automaticSyncEnabled = false`
- `wifiOnly = true`
- `chargingOnly = false`
- `encryptRemoteBackups = true`

Acceptance boundaries:

- no fresh install requires cloud setup;
- cloud sync is presented as optional;
- local checkpoint controls remain usable without a provider;
- automatic sync cannot be enabled while sync is off;
- this overlay does not add network permission, OAuth, provider upload/download, or merge behavior.
