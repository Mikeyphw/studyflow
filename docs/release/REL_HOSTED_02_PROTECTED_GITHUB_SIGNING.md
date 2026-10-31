# REL-HOSTED-02 — Protected GitHub signing (phone and Android TV)

**Scope:** a manual, public-repository GitHub Actions workflow converts two already-verified unsigned `hostedCandidate` APK/AAB pairs into **signed-stage-only** artifacts. It does not grant REL-FINAL release authority, does not run Gradle outside GitHub, and never publishes signing material. The candidate build remains unmodified.

## Security model and trade-off

The public repository's GitHub encrypted *environment* secrets are not exposed to readers of the repository. However, authorizing cloud signing **does entrust GitHub runners with a temporary decrypted copy of the permanent private key**. Only use this model if that trade-off is acceptable. Signed APK/AAB artifacts stored on a public repository's workflow runs may be downloaded by anyone with read access and a GitHub login. They are not signing secrets, but treat them as distributable binaries.

The `studyflow-signing` environment **MUST** have a required reviewer and a **custom deployment branch policy permitting only `main`**. Without these, an environment reference does not itself require approval. The setup helper refuses to upload secrets until it confirms those rules using GitHub's API. For a single-user repository, the user may approve their own manually triggered deployment if *Prevent self-review* is off; independent approval by a second trusted reviewer is stronger. Never relax this environment for troubleshooting.

The workflow:

- Accepts only `workflow_dispatch` from `main`, with exact input source commit SHA and candidate run ID.
- Obtains the prior workflow run directly from GitHub's API and checks its repository, exact SHA, successful conclusion, `main` branch, and `studyflow-hosted-release.yml` provenance.
- Downloads both immutable candidate artifacts by **exact run ID**, then checks their manifests, package identity, non-debuggable flag, unsigned state and APK/AAB hashes **before** handling signing secrets.
- Waits for environment approval, redownloads and verifies the inputs, and only then reads the encrypted PKCS#12, password, alias, and **pinned certificate SHA-256** secrets. The signed stage fails if the uploaded certificate differs from the pinned fingerprint.
- Uses the existing `studyflow_hosted_candidate.py sign` signer, including `zipalign`, `apksigner`, `jarsigner`, signature verification and receipt validation. Passwords and key files are written only into a temporary directory with restrictive permissions and removed after signing.
- Uploads separate phone/TV signed APK/AAB + `SIGNED_STAGE_ONLY` receipts for 7 days. **No automatic installs, public release creation, Gradle invocation, or REL-FINAL verdicts.**

## One-time environment setup and protected secret upload (Termux Zsh)

Have an existing permanent StudyFlow signing properties file and `.p12` keystore on Termux; **do not generate a new identity if StudyFlow is already installed with another certificate**. Run only after the overlay has been applied and committed:

```zsh
cd "$HOME/Code/studyflow"
python3 scripts/studyflow_github_signing.py setup-environment
python3 scripts/studyflow_github_signing.py configure-secrets \
  --signing-properties "$HOME/.config/studyflow/android-signing.properties"
gh secret list -R Mikeyphw/studyflow --env studyflow-signing
```

`setup-environment` creates an environment only if it does not already exist, with the authenticated account as required reviewer and an `main`-only branch policy. If an environment exists with weaker protection, it **refuses to change it automatically**; inspect Settings → Environments → `studyflow-signing`. `configure-secrets` checks protections first, then sends exactly five secrets through `gh secret set --env` with stdin (not shell arguments). It prints the public certificate SHA-256 only, not passwords or key bytes. The SSH or local `gh` login requires repository administration access for environment setup and encrypted secret writes.

The secrets are:

- `STUDYFLOW_SIGNING_P12_BASE64` — base64 of the existing `.p12` file (encoding, not encryption)
- `STUDYFLOW_SIGNING_STORE_PASSWORD`
- `STUDYFLOW_SIGNING_KEY_PASSWORD`
- `STUDYFLOW_SIGNING_KEY_ALIAS`
- `STUDYFLOW_SIGNING_CERT_SHA256` — SHA-256 fingerprint read locally via `keytool`

## Sign existing accepted unsigned candidates

The accepted GitHub candidate run is `41223283343`, source commit `8de803fba750dfac21c3471c915574b812b98b25`. The signing workflow itself runs on the new source commit on the `main` branch; **the earlier candidate commit is selected independently**, by the exact input SHA. Do not confuse these two commits.

```zsh
gh workflow run studyflow-hosted-sign.yml -R Mikeyphw/studyflow \
  --ref main \
  -f candidate_run_id=41223283343 \
  -f candidate_sha=8de803fba750dfac21c3471c915574b812b98b25

gh run list -R Mikeyphw/studyflow --workflow studyflow-hosted-sign.yml --limit 5
```

Open the resulting GitHub workflow run and select **Review deployments → studyflow-signing → Approve and deploy** after verifying the requested run and SHA. GitHub environment secrets become available only to the approved signing job.

After it passes, the signing workflow contains two downloadable artifacts named:

- `studyflow-signed-app-from-41223283343-<SIGNING_RUN_ID>`
- `studyflow-signed-app-tv-from-41223283343-<SIGNING_RUN_ID>`

Each contains signed APK, signed AAB and non-release signing receipt. Verify both downloaded receipts, hashes and signature fingerprints before installing; **REL-FINAL device/restore/restart gates remain pending**.

If the original GitHub candidate run expires (7-day artifact retention), rerun the unsigned candidate workflow at a new reviewed SHA; do not bypass the provenance checks or substitute arbitrary APKs.

## Local tests (no Gradle)

```zsh
python3 scripts/test_studyflow_github_signing.py
python3 scripts/test_studyflow_rel_hosted_01_contracts.py
python3 scripts/verify_personal_signing_overlay.py
python3 scripts/verify_studyflow_actions.py --source-only
git diff --check
```
