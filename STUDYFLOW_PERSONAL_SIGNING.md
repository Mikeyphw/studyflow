# StudyFlow permanent personal APK signing

StudyFlow now supports the same persistent personal-signing model used by BKX. When `~/.config/studyflow/android-signing.properties` exists, the phone and TV Android modules use that permanent certificate for both Debug and Release. This makes Debug/Release installs share an Android signing identity while preserving their separate application IDs.

Run `bash tools/setup-personal-signing.sh` once. It creates an owner-only 4096-bit RSA PKCS#12 keystore at `~/.config/studyflow/studyflow-personal.p12` and an owner-only properties file at `~/.config/studyflow/android-signing.properties`. Back up both files privately; losing the keystore prevents future in-place updates of apps installed with it.

Phone tasks: `:app:studyFlowSigningStatus`, `:app:verifyStudyFlowPersonalSigningConfigured`, `:app:assemblePersonalDebug`, and `:app:assemblePersonalRelease`. TV has matching `:app-tv:studyFlowSigningStatus`, `:app-tv:verifyStudyFlowTvPersonalSigningConfigured`, `:app-tv:assemblePersonalDebug`, and `:app-tv:assemblePersonalRelease` tasks. Run `bash tools/print-personal-signing-fingerprint.sh` whenever you want to verify the permanent certificate fingerprints without exposing passwords on the command line.

The old `STUDYFLOW_RELEASE_*` variables remain a release-only compatibility fallback when personal signing is not configured. Personal signing takes precedence. The existing phone release preflight is fed from the personal properties when they are configured, so the release seal remains enforced without requiring duplicate environment setup.

The first install made with the permanent key cannot update an older build signed by Android's generated debug key; uninstall that older build once. After that transition, Debug and Release builds signed with the permanent key can update one another subject to normal Android version-code rules.

## Contract verification

Run `python3 scripts/verify_personal_signing_overlay.py` to execute the signing regression suite. It verifies the phone/TV shared-key contract, Debug/Release assignment, legacy release fallback, permanent-key setup requirements, task entry points, and fingerprint helper.

The TV `TV_RELEASE_SIGNING_CONFIGURED` build-time truth flag is true for either the permanent personal identity or the legacy complete Release credential set. The setup writer escapes Java-properties metacharacters so valid passwords and paths round-trip safely.

## Credential round-trip safety

The setup writer escapes Java-properties separators, backslashes, control characters, leading spaces, and non-ASCII Unicode. Gradle preserves credential values exactly rather than trimming passwords.

The fingerprint helper decodes the same Java-properties escaping as Gradle, including escaped separators, backslashes, leading spaces, and Unicode, so it remains correct for credentials and paths produced by the setup script.
