# SFDT-W1 — StudyFlow × Devtool baseline and source-only integration

Status: **implementation candidate; acceptance pending**. This report documents
verified public GitHub state at the implementation base on November 3, 2026.
It is not a hosted build, signing, release, or device-acceptance result.

## Exact source pins

| Repository | Reviewed `main` source | Candidate work |
|---|---|---|
| Mikeyphw/devtool | `e2fea8a784711496e026030ec5418514aec4bebf` | SFDT-02 explicit ownership and EXO target-local job input attribution |
| Mikeyphw/studyflow | `ab16db83331cddb63ca4d7093c46c3a116de34b2` | SFDT-01 source-only EXO audit extension |

The Devtool pin is newer than the initial audit (`3ea6b54...`) by two
completed DN02/DN03 .NET changes, verified not to touch the W1 patch files.
The previous source-pinned W1 V1 bundle is superseded by this corrected R2
bundle; do not stack versions on an already modified checkout. Both native overlay manifests are generated from the exact
local Git base tree and source hashes. A different HEAD or dirty checkout is a
**refusal**, not an implicit rebase/reset.

## Accepted historical GitHub runs (not acceptance of this patch)

- Five-profile StudyFlow deep CI: run `41223280495`, SHA `8de803fb...`, five passed.
- Unsigned phone + TV candidate: run `41223283343`, same source, two passed.
- Current-base standard CI: run `41229102506`, SHA `ab16db83...`, all active jobs passed.
- Protected signing: run `41229110695`, signing workflow `ab16db83...`, candidate artifacts from `41223283343`; stage-only, not release qualified.

These pre-existing runs cannot attest a new source commit. Existing binary
artifacts from October 31 were scheduled for GitHub expiration November 7, 2026.
They may be downloaded independently from exact run IDs if retention is needed.

## Design and safety

- **No Gradle locally.** The new EXO node only executes a Python source
  contract. It is inserted into both existing wrapper entrypoints while keeping
  the same default, runner and protected GitHub workflow semantics.
- **Existing CI preserved.** The old source checks remain, and the new source
  contract plus nine negative tests are added to the current `source` job;
  the existing signing and Gradle-profile negative scripts are also run there.
- **No signed artifact changes.** Protected signing, keystore, release
  credentials, and candidate workflows are not modified.
- **No affected-job skipping.** Even after upstream ownership lands, selective
  CI remains observational until the complete cross-target matrix and hosted
  acceptance gates pass.
- **No GitHub writes by the assistant.** The connected integration rejected a
  Git blob write with HTTP 403. The local bundle therefore generates properly
  source-pinned, commit-enabled native Devtool overlay ZIPs instead.

## Acceptance still required

1. Generate both overlay ZIPs from exact clean checkouts using the supplied
   `build_sfdt_w1.py` without executing Gradle.
2. `devtool overlay check` each archive, confirm exact source and checksum
   contracts, and apply each transaction with `--yes` and its artifact manifest
   owning the Git commit.
3. Verify Devtool `test_sfdt02_affected_ownership.py` passes, along with the
   wider existing Devtool regression suite (Python only).
4. Verify StudyFlow `./devtoolw ci-source`, the new negative test suite and
   existing source validators pass.
5. Publish accepted Devtool upstream source commit before introducing the
   `affected_ownership="inputs-only"` settings into StudyFlow; that is SFDT-03.
6. Run new StudyFlow hosted source job from the exact resulting commit; the
   historical green status cannot be reused as acceptance.

This first transaction deliberately does not alter Android/TV Gradle tasks,
artifact/signing receipt shapes, or protected release workflows.
