# Debug Workbench D1 r2 Validation

## Base

D1 r2 was rebuilt against the uploaded `studyflow-20260802-131201.tar.gz` snapshot. The combined replacement overlay
uses source SHA-256 values from that exact tree and supersedes the original D1 ZIP.

## Executed before packaging

- all Phase 0 through Phase 29 repository validators;
- `scripts/verify_debug_workbench_d1_r2.py`;
- repository formatting checks for tabs, trailing whitespace, final newlines, and Kotlin lines over 120 characters;
- strict `kotlinc -Werror` compilation of all `:core:diagnostics` production and test sources;
- execution of all 18 D1 r2 behavior tests;
- adversarial checks for free-form values under generic keys and forged exception summaries;
- deterministic queue-overflow and dropped-event accounting;
- fixed-clock rotation stress proving five unique retained sessions;
- writer-storage failure followed by successful health-manifest export;
- JSON parsing, support-bundle fixed-entry allowlist, redaction, and checksum verification;
- clean-apply source-state, inventory hash, duplicate-path, cache, NUL-byte, reserved-metadata, and byte-for-byte audits.

## Not executed locally

The artifact-building environment does not contain the project Gradle wrapper JAR, Android SDK, or complete cached
dependency graph. It therefore does not claim Android Gradle configuration, app compilation, lint, resource merge,
packaging, or device runtime success. Those gates remain assigned to the target-specific Devtool validation session.

## Required target-side order

1. `compile`
2. `resources`
3. `unit-test`
4. `lint`
5. `package`

D2 must not begin until those gates pass for D1 r2.
