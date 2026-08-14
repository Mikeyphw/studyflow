# StudyFlow Final Acceptance Report

Target execution status: **PENDING TARGET EXECUTION**

This report is a template. It must be filled after the `1.0.0-rc1` artifact is built, installed, and
exercised on the target Android device or emulator. Source validation alone must not change this
status to passed.

## Build identity

- Application ID: `com.mikeyphw.studyflow`
- Version code: `37`
- Version name: `1.0.0-rc1`
- Artifact types: pending Release APK and AAB paths from target host
- Build host: pending
- Device/emulator: pending
- Android API level: pending
- Commit: pending

## Target-machine validation

- Gradle configuration: pending
- Main source compilation: pending
- Test source compilation: pending
- Unit tests: pending
- Room schema/migration validation: pending
- Resource merge: pending
- Lint: pending
- Assemble/package: pending
- Release signing and signer-certificate evidence: pending

## Device acceptance checklist

Use `docs/release/FINAL_ACCEPTANCE_CHECKLIST.md` and replace each pending item below with evidence.

- Fresh install: pending
- First launch: pending
- Plan creation: pending
- Dashboard session action: pending
- Sessions: pending
- Practice and reviews: pending
- Library and annexes: pending
- Reminders and notification permission: pending
- Widget render/refresh: pending
- Backup export: pending
- Restore dry-run: pending
- Upgrade install: pending
- Data survival after upgrade: pending
- Background/foreground: pending
- Kill/reopen: pending
- Date/time behavior: pending
- EN/PT-BR language switch: pending
- Light/dark/system theme: pending
- Accessibility sanity pass: pending

## Final verdict

Pending. Do not mark StudyFlow release-ready until this section names the exact artifact, device,
validation commands, and completed checklist evidence.

## OV-13 generated evidence (still pending until target execution)

Do not synthesize success from source checks. The authoritative generated files are:

- `build/reports/studyflow/release-host-gates.json`
- `build/reports/studyflow/release-artifact-identity.json`
- `build/reports/studyflow/connected-test-gates.json`
- `build/reports/studyflow/full-journey.json`
- `build/reports/studyflow/device-acceptance.json`
- `build/reports/studyflow/final-release-verdict.json`

Record exact APK/AAB SHA-256 values, signer-certificate SHA-256, and AAB signature verification. The connected
journey must include real `REPLACE_EXISTING` and `CREATE_AS_NEW` restores and a post-restore process restart. Device evidence separately covers fresh install,
upgrade/data survival, process restart, reminders, Today widget, EN/PT-BR, light/dark/system theme,
and TalkBack/focus/touch-target behavior.
