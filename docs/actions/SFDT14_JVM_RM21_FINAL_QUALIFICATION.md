# SFDT14: complete independent RM21 JVM consumer qualification

StudyFlow is a distinct external consumer of pinned Devtool RM23. This is a
**nonrelease** hosted workflow: signed APK, device, identity trust and release
qualification are explicitly false. Local Termux runs source checks only.

## Two required, observable source revisions

1. Apply/push the consolidated RM23 Devtool and SFDT14 StudyFlow overlays to
   their existing feature branches. The StudyFlow overlay pins the exact
   resulting Devtool commit, not an unreviewed mutable branch ref.
2. The SFDT13 shadow workflow on StudyFlow PR #5 builds the real `:core:model`
   Gradle 9.7.1/JDK 21 JAR, runs pinned RM12, and generates a real SHA-256
   dependency verification XML candidate as a **nonrelease artifact**.
3. Download the successful shadow workflow artifact with
   `IMPORT_SFDT14_METADATA.zsh <run-id>` in the bundle. This validates the
   artifact hashes, exact StudyFlow source SHA and exact Devtool SHA; validates
   the XML with the project parser; commits and pushes the candidate on the
   *existing* StudyFlow PR #5 branch, without local Gradle or force-push.
4. The next PR #5 SHA has tracked verification metadata. SFDT14 runs a real
   independent RM21 `jvm-jar` build **offline after a hosted cache warm-up**,
   validates Git identity, project source digest, JVM class archive, copied
   JAR SHA-256, and independent-consumer receipt. It also asserts that release,
   signer and device qualification remain false.
5. Only a full green `SFDT14_INDEPENDENT_RM21_JVM_SOURCE_AND_OUTPUT_BYTES_PASS`
   hosted run on the **current StudyFlow head** completes JVM acceptance.
   Do not confuse the initial metadata-less PR run with acceptance.

Source policy: no synthetic XML; no workaround for missing locked metadata; no
Android/Gradle build on Termux; no `continue-on-error`; no reset or rebase.

RM23 retains maximum 4096 archive entries and existing per-entry, total ZIP,
case-insensitive duplicate, traversal, symlink, encryption, class-structure and
signature checks.
