# SFDT12 — StudyFlow RM21 native web consumer qualification (non-release)

This is a **candidate** for real independent consumer execution, not a PASS.
Devtool RM21 first-party fixture and 24-shard Python regression are already verified at
`8cc7c6ab47076802369a2222a26b903dd19c125e`, merged into Devtool `main`
`bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c` (PR #13).

## Scope of this candidate

- Pin the actual `web` npm workspace to `npm@10.9.4` (no dependency-version changes).
- Remove StudyFlow's four-option inert `.npmrc`: RM11 currently refuses *all* tracked
  package-manager configuration. The read-only hosted run uses `npm ci --ignore-scripts`
  and `--no-audit --no-fund`, with npm pinned exactly. This change must not be
  misrepresented as identical to the removed `prefer-offline` user preference.
- Augment the existing `web/scripts/build_dist.mjs` with RM11's separate
  `dist/manifest.json` (`{ "files": { relative_name: sha256 } }`). Its hashes
  are derived after the existing `release-manifest.json` is finalized. Do not
  alter or reclassify StudyFlow's original release manifest or signing policy.
- Add a read-only-on-GitHub, exact-source-hosted Node acceptance lane using
  the real StudyFlow web build and merged Devtool code. Existing authoritative
  StudyFlow CI remains unchanged and must still pass on the candidate branch.
- No `.devtool.toml`, Gradle, Android, signing, device or release cutover changes.

## Honest acceptance gates

1. Devtool applies this schema-v2 overlay transactionally to an exact clean
   StudyFlow checkout at base `aceab03f0f3ac317d37799ddebd08da75701e78b`;
   its source validator and auto-commit must pass.
2. Hosted StudyFlow SFDT12 Node workflow must complete on the **exact new commit**,
   with genuine npm build, RM11 source/output verification, and artifact hashes.
   A green source-only test or green process exit does not satisfy this gate.
3. Run upstream Devtool RM21's **independent consumer** workflow manually with
   `external_repository=Mikeyphw/studyflow`, exact candidate 40-char SHA,
   `external_lane=node-build`, `node_project=web`. The two receipts must agree.
4. Existing StudyFlow CI must remain green on that same branch; release
   equivalence is separate. Even both Node acceptance lanes do not grant
   signing/device qualification or authoritative CI cutover.

## Important JVM hold

StudyFlow's JVM closure uses Gradle 9.7.1, Android siblings, included convention
builds, and nontrivial dependencies. RM21's hosted external JVM workflow is still
pinned to Gradle 9.1.0 and its reviewer requires a checked-in lock/verification
file. Do not fake a Gradle lockfile, substitute the internal fixture, or claim
that the JVM independent-consumer gate has passed. Audit and qualify it as a
separate upstream/consumer change, only on GitHub-hosted runners.

No automatic release or merge is performed by this candidate.
