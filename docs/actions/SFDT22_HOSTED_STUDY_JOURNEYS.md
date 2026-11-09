# SFDT22 — Hosted real study journeys (proposed, NOT YET QUALIFIED)

## Provenance and current qualification

- Reviewed source: `Mikeyphw/studyflow` `main` at `6e54256befc2f17a9886ebd0ff461e9de8153088`.
- This proposal adds **only** new test/orchestrator/workflow/document files, preserving the SFDT21 hosted UI gate and existing Gradle debug-only opt-in.
- SFDT21 post-merge API 36 passed: <https://github.com/Mikeyphw/studyflow/actions/runs/42424780035>.
- Native Devtool CI phone lane **failed** on post-merge `main` at `:app:packageDebug`: <https://github.com/Mikeyphw/studyflow/actions/runs/42424780036>. The exception's underlying detail was not available in the job log; do **not** mark fixed based on these new tests.
- Repository `main` is unprotected at the time of this preparation. Add existing checks plus `SFDT21 hosted emulator UI gate` and `SFDT22 hosted real-study UI gate` via a GitHub principal with branch administration rights.

## New acceptance gate

The new `.github/workflows/studyflow-sfdt22-hosted-journeys.yml` workflow boots a disposable GitHub-hosted Android 16 / API 36 / x86_64 Google APIs emulator; disables Wi-Fi/data, normalizes font scale, installs **real debug** application and instrumentation APKs, verifies both APK hashes and signing certificates, and launches **production MainActivity**. It does not use a fake navigation controller or a domain-repository fixture.

The runner executes five allowlisted real-UI instrumentation methods **in order** (one fresh activity per method, all backed by the same installed app database):

1. Create a new named plan through the three-step wizard.
2. Add a subject and a topic through the real syllabus manager.
3. Start a quick session, pause, resume and finish it through production controls.
4. Author a true/false question, create an attempt, answer it and reach review/flashcard UI.
5. Force-stop the app via ADB, verify process disappearance, relaunch and prove its new PID differs, then open the persisted plan, topic, session, question and review data through real UI again.

Seven PNG captures are required: `plan`, `subject`, `topic`, `session`, `question`, `review`, `restart`. The verifier checks actual instrumentation status, installed APK digest, signer SHA-256, each PNG magic/dimensions/hash, uniqueness, and the exact commit/run/attempt identity. No raw logs are retained in the artifact. **Source-only and JVM tests are never counted as hosted acceptance.**

All five cases and seven screenshots must be independently verified by the hosted runner, followed by the `SFDT22 hosted real-study UI gate` job. If any UI selector/flow or application behavior fails, this gate MUST fail. The suite is intentionally stringent and has not been executed against an emulator yet; it may expose app defects, and then those defects should be fixed rather than weakening assertions.

This milestone does **not** establish physical-device, release-build, app-signing or Play publication qualification.

## Use

Extract the provided *consolidated portable overlay* outside the repository and run the included `APPLY_SFDT22.zsh` script from Termux/Zsh on the clean pinned `main` commit. The installer verifies the local source SHA, repository, payload hashes and source contracts, then creates a feature branch with the new files. Optional `--push --pr` publishes a pull request with GitHub CLI after local checks. See `README_APPLY.md` at the archive root.

To qualify, watch both the new hosted SFDT22 gate and pre-existing StudyFlow/Devtool/SFDT11/SFDT21 checks on the branch and, separately, `main` after merge. Do not merge on a false green from only the source job.
