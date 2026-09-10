# REL03/REL04 artifact identity, install smoke and rollout manifest

REL03 and REL04 continue the post-SFLP release-operation track after REL02. They do not add product features and do not extend SFLP beyond SFLP24.

## REL03 signed artifact identity

REL03 records exact APK/AAB evidence in `release-artifact-signature-identity.json`:

- file presence, size and SHA-256;
- ZIP-structure sanity for Release APK and Release AAB;
- APK signature and certificate verification through `apksigner` when present;
- APK package identity through `apkanalyzer` when present;
- AAB signature verification through `jarsigner` when present; and
- fail-closed `PENDING_TARGET_EXECUTION` when target-host SDK tools are unavailable.

Run:

```bash
python3 scripts/collect_release_artifact_identity.py
```

## REL03 install smoke evidence

REL03 also records install-smoke evidence in `install-smoke-evidence.json` and can produce `install-smoke-command-template.sh` for the target device host.

```bash
python3 scripts/collect_install_smoke_evidence.py --mode template
python3 scripts/collect_install_smoke_evidence.py --mode adb --fail-when-blocked
```

Manual evidence is accepted only when every required check has non-empty observation text.

## REL04 rollout manifest

REL04 consumes REL01, REL02 and REL03 reports and writes:

- `release-channel-manifest.json`
- `rollout-readiness.json`

```bash
python3 scripts/build_release_channel_manifest.py --channel internal
python3 scripts/build_release_channel_manifest.py --channel beta
python3 scripts/build_release_channel_manifest.py --channel production --fail-when-blocked
```

Production remains blocked unless all required reports are present and passing. `PENDING_TARGET_EXECUTION` and `BLOCKED` never become a synthetic release-ready verdict.
