# SFDT-08 — Devtool native test evidence and remaining StudyFlow CI coverage

## Delivery structure

Two independent source-bound Devtool overlay transactions must be applied in order:

1. **Devtool**: `SFDT_W4_R1_DEVTOOL_NATIVE_JUNIT_TEST_GATES.overlay.zip`. Adds local JUnit testcase parsing and byte-bound native typed test gates, plus narrowly reviewed unsigned `:app:testReleaseUnitTest` admission. Apply, validate, commit and push Devtool first. Its *new* commit SHA is needed for the next workflow.
2. **StudyFlow**: `SFDT_W4_R2_STUDYFLOW_NATIVE_COVERAGE_GAPS.overlay.zip`. Adds 8 target-owned native EXO workflows with no change to existing StudyFlow CI, releases, signer, default wrapper or affected ownership. Apply, validate, commit and push.

## Native coverage matrix

| Lane | Review owner | Actual command / expectation | Native JUnit gate |
|---|---|---|---|
| phone-release-jvm | Android Gradle provider | `:app:testReleaseUnitTest` only, unsigned, no APK/AAB | Required |
| desktop-fast | Devtool DAG / SHA-pinned Python bridge | `:desktopApp:classes :desktopApp:test` | Required |
| web-full | Devtool DAG / SHA-pinned Python bridge | `npm ci`, `npm test`, lint, build, release:check, plus `pytest` | Required for pytest portion |
| android-contracts | Devtool DAG / existing reviewed StudyFlow deep profile | Exact full phone deep task list | Required for phone JVM portion |
| tv-contracts | Devtool DAG / existing reviewed StudyFlow deep profile | Exact full TV deep task list | Required for TV JVM portion |
| desktop-contracts | Devtool DAG / existing reviewed StudyFlow deep profile | Exact full desktop deep task list | Required for desktop JVM portion |
| quality-contracts | Devtool DAG / existing reviewed StudyFlow deep profile | Exact full quality deep task list | `NOT_RUN` unless independently parsed; no fabricated PASS |
| parity-contracts | Devtool DAG / existing reviewed StudyFlow deep profile | Exact parity source-only/Gradle preflight list | `NOT_RUN` unless independently parsed; no fabricated PASS |

The fast Android test is a **native Gradle-provider operation**. Other Gradle and Web commands remain in strict, source SHA-pinned Python bridge jobs *owned by* the native Devtool DAG. They are not advertised as first-class native Gradle providers. Legacy CI task semantics are preserved, but only actual hosted evidence can establish equivalence.

## Dispatch after both overlays are applied and pushed

```bash
cd "$HOME/Code/studyflow"
STUDYFLOW_SHA="$(git rev-parse HEAD)"
DEVTOOL_SHA="$(git -C "$HOME/Code/devtool" rev-parse HEAD)"

gh workflow run studyflow-devtool-native-coverage.yml -R Mikeyphw/studyflow --ref main \
  -f expected_sha="$STUDYFLOW_SHA" -f devtool_sha="$DEVTOOL_SHA" -f scope=fast

# Later, explicitly request the expensive complete five-profile deep matrix:
gh workflow run studyflow-devtool-native-coverage.yml -R Mikeyphw/studyflow --ref main \
  -f expected_sha="$STUDYFLOW_SHA" -f devtool_sha="$DEVTOOL_SHA" -f scope=deep
```

Only the selected lanes are present in each GitHub matrix. Use `scope=all` for a single eight-lane attempt. `--scope=fast` acceptance **cannot** be used to claim deep coverage.

## Evidence and cutover gate

The workflow uploads the runner-local native receipt, exact typed node receipt, and copied JUnit XML members for lanes that declare required tests. Missing, stale, symlinked or mismatched test reports prevent native success. It does *not* set any signer, device, native-release, or remote-authentication gate to PASS. `quality` and `parity` source verifier runs never get a test PASS merely from process exit.

Compare four same-SHA workflows (legacy CI, source shadow, SFDT07 expanded shadow, and this coverage workflow) with:

```bash
python3 scripts/sfdt08_compare_coverage.py \
  --legacy-run <LEGACY_ID> --source-run <SOURCE_ID> \
  --expanded-run <SFDT07_ID> --coverage-run <SFDT08_ID> \
  --scope fast --json
```

The comparator uses `gh` read-only API calls, downloads per-lane ZIPs into a private temporary directory, revalidates exact GitHub identities, typed source/run/graph hash, local JUnit member bytes, and actual testcase counts. Its result is **unqualified** even when green.

**Do not cut over CI** until every required fast and deep lane passes at the same source SHA, task/test/diagnostic coverage matches the legacy baseline, and remaining first-class provider work (desktop and npm) is accepted. Avoid silent skips and never remove the current signing/environment gates. No local Gradle is run by either overlay.
