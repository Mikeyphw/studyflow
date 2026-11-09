# SFDT22 HF07: source compiler hotfix

Evidence: GitHub Actions runs 42448089820 (Devtool phone), 42448089859 (SFDT21), 42448089748 (SFDT22). All compile `feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentDialogs.kt` and fail at line 608-609: absent comma between the `modifier` and `label` arguments of the production Compose `OutlinedTextField`.

## Patch

- Restore the missing Kotlin comma after `Modifier.fillMaxWidth().testTag(testTag)`.
- Keep HF06's subject/topic field identifiers and actual UI instrumentation unchanged.
- Add a targeted negative regression test, including a mutated input with the old invalid source syntax.
- Re-pin the modified production source and the new regression file in SFDT22's fail-closed workflow.
- Do not suppress any emulator failures, remove tests, change Gradle or alter release gates.

## Qualification

Offline checks and a git transaction are performed by the installer. Hosted Gradle and Android 16 instrumentation must still complete on the new commit. Do not merge PR #15 until the mandatory checks are green.
