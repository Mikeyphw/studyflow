# OV01-R3R2 — source-bound offline wrapper repair, run 41119482183

## What happened

All five deep CI jobs failed before Gradle was invoked, because GitHub's `gradle/actions/setup-gradle@v4` rejected StudyFlow's custom 5,361-byte wrapper JAR (SHA-256 `000a9d3e40bf33c4786ec759fc7dc79ca60f3a67b98902fd46e0f21c0464f250`). The official Gradle 9.7.1 wrapper SHA-256 is `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d`. Wrapper-validation must remain enabled.

The first R3 hotfix failed in Devtool `after_extract`: its network download followed a redirect to HTTP 404. Devtool recovered the interrupted application and verified the rollback of six modified files and one newly introduced document; there was no partial R3 application. **Do not reapply R3**. This replacement targets the committed R2 tree directly.

## R3R2 workflow

1. Run the separate `STUDYFLOW_STAGE_GRADLE_WRAPPER_R3R2.sh` script on Termux **before** calling Devtool. It attempts two independent official-source locations (Gradle's published distributions and the Gradle project's versioned GitHub source), refuses HTTP errors, checks the exact SHA-256, and stages the binary in `$HOME/.cache/studyflow-gradle/`. It never executes Gradle.
2. Apply this R3R2 overlay directly against committed R2 with `--static-validate --no-commit`. Its preflight is **offline** and accepts only the verified staged binary (or a wrapper that is already official) while requiring the exact original custom JAR and known pinned distribution otherwise. No preflight download or Gradle execution is permitted.
3. Source CI validates the actual committed official JAR; the deep matrix initializes a `blocked_before_gradle` receipt before hosted setup, and validates the successful completed receipt separately.
4. Commit and push all modifications including the binary `gradle/wrapper/gradle-wrapper.jar`, then rerun GitHub deep validation with the new SHA.

## Important limitations

The official binary is **not bundled** because it was unavailable in the offline audit environment. An end-to-end success of staged-official-JAR installation has not been demonstrated here; only missing-file failure and deterministic checks can be tested offline. Source-level suite success is not evidence of successful hosted Gradle builds, release signing, or native EXO execution.

Never disable or bypass Gradle wrapper validation. Never execute Gradle on Termux.
