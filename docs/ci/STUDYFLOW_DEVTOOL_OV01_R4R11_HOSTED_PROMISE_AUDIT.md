# OV01-R4R11 hosted promise audit — 2026-10-31

## Reviewed base
- GitHub main exact source: `099b5d483cc2178d6cb2aea7005d77422643fd90`.
- [Standard CI #41167610308](https://github.com/Mikeyphw/studyflow/actions/runs/41167610308): five standard jobs passed.
- [Deep CI #41167610742](https://github.com/Mikeyphw/studyflow/actions/runs/41167610742): four deep profiles passed, one failed.
- Exact deep Android failure: `:verifyStudyFlowWebFullRegression` returned `No module named pytest` on the hosted Python 3.12 runner. GitHub Android and desktop had already cleared SFLP08/09.

## Promise-by-promise evidence (before GitHub hosted rerun)

| Promise | Evidence | Assessment |
| --- | --- | --- |
| Preserve full Web pytest suite, including function-level tests | `build.gradle.kts` retains `python3 -m pytest tests -q`; source verifier rejects removal. No switch to `unittest`. | Delivered in source; hosted execution pending |
| Exact pinned runtime for missing hosted pytest | `web/requirements-deep-pytest.txt`: pytest 9.0.2, iniconfig 2.3.0, packaging 25.0, pluggy 1.6.0, Pygments 2.20.0; Pillow remains separately pinned. | Delivered in source; wheel install on GitHub pending |
| Install before Gradle, Android deep only | `.github/workflows/studyflow-ci.yml` has conditional `matrix.profile == 'android-contracts'`, after `setup-python`, before canonical Gradle profile. Uses `--no-deps --only-binary=:all:` and interpreter probe; no `continue-on-error`. | Delivered in source; hosted execution pending |
| Fail closed on absent, widened, duplicate, misplaced pins | `scripts/test_studyflow_ov01_r4r11_contracts.py`: nine mutation-negative scenarios passed. | Passed source-only |
| Keep previously green lanes unchanged | No changes to standard Android/Web/Desktop/TV jobs, Devtool task selection or Gradle source. | Delivered by reviewed diff; hosted regression pending |
| Cumulative improvement tracking | `docs/ci/DEVTOOL_IMPROVEMENT_LEDGER.md` appends SF-DT-55. | Delivered |
| Exact-source transactional overlay | GitHub Git blob IDs checked for three modified source paths; SHA-256 source and output digests in overlay inventory; disposable Devtool apply and deliberate source-drift negative executed. | Pending final transaction results |
| Release / device acceptance | No local Gradle, no signed release, no hardware tests. | Intentionally open |

## Scope and limitations
The code is source-validated offline. The full repository-wide `studyflow_ci_gradle_profile.py --check-source` cannot be represented faithfully by the old disposable snapshot, whose Android build workflow is not the authoritative GitHub checkout; the latest GitHub source job succeeded before this patch, and the new focused source guard runs in GitHub as part of that source job. Only the new hosted 5/5 deep and standard CI checks can establish complete CI acceptance. No test or release gate is skipped.
