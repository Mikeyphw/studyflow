# SFDT22 / HF27 FIX-03 HF02 — Bounded SFLP instrumentation failure taxonomy

## Verified origin and limitation

The exact-SHA GitHub Actions job `42810952089` at `fd014ee1c38a5302cfb8e6631ac84081da063d9b` built the phone and AndroidTest APKs successfully. Its first SFLP replay instrumentation ended with `runner_bad_status` (twice), after three real UI journeys passed. The sanitized receipt cannot distinguish a `@Before`/`@After` failure, replay failure, or cold-reopen failure. Raw Android logs must not be exported. This phase **does not claim** the SFLP replay regression is resolved.

## Changes

- Wrap real Android JUnit `@Before` fixture creation and `@After` cleanup with fixed `SFLP_SETUP`, `SFLP_FIXTURE`, `SFLP_BRIDGE`, `SFLP_CLEANUP` categories, without printing exceptions, user data, identifiers, raw SQL or bind arguments.
- Split the existing real-bridge SFLP replay into `SFLP_CAPTURE`, `SFLP_REPLAY`, `SFLP_VERIFY` and existing `SFLP_REOPEN`. Keep the real database open/close and customized settings/content assertions. No default-row repair.
- Preserve exact one-start/one-successful-finish instrumentation qualification. Add only **red** failure categories `runner_test_failed_unstaged` and `runner_instrumentation_failed` for failures that do not match a known test stage.
- Update historical FIX-03 source assertions, add adversarial HF02 parser/source tests and immutable full gate SHA-256 coverage. Run the new source test in both focused and full Android workflows.

## Safety and exit criteria

PR #15 must remain open. No autofix, merge, weakened gate, synthetic test pass, physical-device qualification or release. Run the exact hosted Android 36 focused test, collect only the sanitized receipt. If it fails, the receipt must identify one allowlisted stage/reason. If it passes, the complete five UI journeys, all SFLP regressions, force-stop/new PID and seven screenshots still require the independent full acceptance workflow.
