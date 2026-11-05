# SFDT10-R2 — Same-SHA Android artifact comparison (read-only, manual)

This **additive** candidate compares two completed GitHub Actions runs:

- `StudyFlow CI`, authoritative legacy push/dispatch, all five active jobs passed.
- `StudyFlow SFDT10 native Android debug parity shadow`, all four jobs passed.

Both must refer to the exact same StudyFlow commit on `main`. R2 is a separate
GitHub-only manual comparator; it does **not** trigger signing, run Gradle,
authorize release, or alter required checks or branch protection.

## Inputs

- `expected_sha`: the reviewed shared consumer commit, 40 lowercase hex.
- `legacy_run_id`: successful *same-SHA* legacy CI run ID.
- `shadow_run_id`: successful *same-SHA* SFDT10 shadow run ID.

After R2 is merged, dispatch its workflow on `main` using these inputs. Do not
mistake the R2 comparator's own commit SHA for the two **historical consumer**
run SHAs: the comparator can be added in a later commit and checks the actual
run head SHAs via authenticated GitHub Actions metadata.

## What the verifier checks

1. Authenticated GitHub metadata: repository, main branch, event, workflow,
   source SHA, run attempt, completed/success, all required active jobs.
2. Expected artifact names, counts, expiry and sizes; actual member downloads
   through `gh run download`. Symlink, path escape and size caps fail closed.
3. SFDT10 local captured manifest and independently recomputed member SHA-256;
   native source/run/attempt receipt identity and canonical typed receipt digest.
4. Android phone/TV debug APK ZIP integrity, manifest existence, `aapt` package
   name + versionCode, and `apksigner verify` per APK on the hosted runner.
5. Same-source lint issue ID/severity counts between legacy and shadow.
6. Same-source TV and phone Release JVM testcase identity/outcome multisets,
   parsed from actual JUnit XML member files (not stdout or reports only).

Independent hosted Android debug builds may use different ephemeral debug
certificates; each signature **must verify**. Different cert fingerprints or APK
SHA-256 alone are not a parity failure, and the tool makes no reproducible-build
claim. Certificate identity is explicitly reported.

## Trust boundaries and known limitations

- GitHub's archive `digest` metadata is recorded but the `gh run download`
  extraction does not independently verify the compressed ZIP SHA-256.
- Read-only `GITHUB_TOKEN` (`contents: read`, `actions: read`) is used only
  to retrieve run/job/artifact evidence; no other credentials are accepted.
- A matched **Android** member contract is **not** full CI equivalence. Web/npm,
  desktop, deep profiles, required-check behavior, device acceptance and
  protected signer authorization all remain separate gating work.
- The final report always sets `legacy_equivalent=false`, `cutover_authorized=false`,
  `release_qualified=false` and `device_qualified=false`.

## Source-only validation

The overlay's required validator checks the R2 source contract and unit tests
without network, Android, Gradle or signing. Hosted integration is still
**NOT_RUN** until its R2 workflow is executed against two completed runs.
