# BH03 — Installation-unique sync device identity

## Closed contracts

1. Fresh StudyFlow sync foundations no longer derive `device_id` from the display name. A missing persisted id receives an opaque UUID-backed `device-install-*` identity.
2. Every repository fallback that may need to establish the local device identity uses the same resolver. Two unrelated installations named `This device` therefore do not intentionally collide.
3. Existing persisted ids are authoritative and are returned unchanged, including deterministic `device-*` ids written by older StudyFlow versions.
4. Disabling/clearing the local sync foundation clears sync configuration but preserves the persisted `device_id`, so re-enabling sync on the same installation does not register a new device identity.
5. The legacy `stableDeviceId(seed)` function remains unchanged for backward compatibility; it is no longer used to create a new local installation identity.

## Focused validation

```sh
python3 scripts/verify_bh03_installation_device_identity.py
./gradlew :core:model:test :core:data:test --no-daemon
```
