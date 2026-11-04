# SFDT-07 R3 — GitHub setup-node correction

On GitHub Actions run `41762114165` (StudyFlow `3be1f1e…`), the expanded native Web pytest job failed before tests because the `actions/setup-node@249970…` `cache` input was `false`. The action accepts a package manager name (such as `npm`), not Boolean false. This incremental overlay removes the `cache` key entirely. The JDK/SDK/NDK, wrapper, test artifact verification and unchanged legacy CI remain as in SFDT-07 R2.

R3 additionally extends the `sfdt07_consolidated_contract` gate to require exactly one pinned Node 22 setup step scoped only to Web pytest, without unreviewed `with:` inputs. Its 11 source-level tests check for boolean/string cache overrides, pin drift, an unwanted node version, misplaced runtime scope, and missing/duplicate steps.

This change is *not* a GitHub-hosted acceptance claim. Rerun the expanded native shadow against the new StudyFlow SHA. Compare its native artifacts to the same-SHA legacy CI and source shadow via the existing `sfdt07_compare_runs.py` script. CI cutover, signing, full Web npm-equivalence, desktop Gradle-equivalence, device acceptance and release qualification remain blocked.

Only the two touched source-file Git blob checksums are pinned; no repository commit or tree pin. Validation runs source-only Python tests; native Devtool transaction handles rollback and automatic commit.
