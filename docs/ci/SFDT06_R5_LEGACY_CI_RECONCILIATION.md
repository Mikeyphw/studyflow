# SFDT-06 R5 — source-only Web ownership compatibility and same-SHA status comparison

## Why this patch exists

The hosted Devtool shadow passed on StudyFlow source SHA `ecd4ac9bfa14b18af4558e0afedf739a1d681f16`, run `41753341677`. The legacy StudyFlow CI failed at the source gate on the same commit, run `41753333420`, with `AssertionError: Web inputs contains doubly-rooted or unsafe path`. The earlier OV01 regression deliberately rejected **all** parent-relative paths under the `web` target. The newer SFDT-03 precise ownership model legitimately needs **only** three bounded shared-source bridges from the Web-local execution root:

- `../.devtool.toml`
- `../protocol/**`
- `../.github/workflows/*.yml`

R5 keeps all ordinary target-local inputs, refuses every other `..`, rejects absolute and doubly-rooted paths, and still disallows **all** parent-relative outputs. It changes the historic OV01 regression test rather than changing StudyFlow's actual task/execution/source ownership. R5 does not touch any source, test, release, or signing workflow.

## On-device source validation

The native Devtool overlay uses a Bash preflight hook to modify only `scripts/test_studyflow_ov01_r4_contracts.py`. Its only source identity pin is the exact touched-file blob checksum; it does **not** pin the repo commit/tree. Automatic Git commit and rollback are enabled. The validation hook exercises both the older OV01 source regression and the exact SFDT-01/03/06 safety checks without invoking Gradle.

## Same-source GitHub reconciliation

Read-only command, from the StudyFlow repo after pushing R5 and obtaining both run IDs:

```sh
python3 scripts/sfdt06_compare_runs.py --shadow-run SHADOW_ID --legacy-run CI_ID --json
```

This uses **four GET requests** through `gh api` (two workflow run objects and their latest job inventories); no GitHub mutation is performed. It requires identical complete 40-character source SHA, exact workflow identities, completed runs, an unambiguous native shadow node, and expected legacy fast CI lanes. Failure/skip/missing data produce a non-PASS verdict. Even if both jobs are green, the result retains `cutover_authorized=false`, `native_source_tasks_equivalent=false`, `gradle_native_equivalent=false`, and `deep_profiles_qualified=false`, since successful GitHub job metadata is not sufficient to establish those claims.

## Out of scope / later SFDT-06 phases

Native coverage expansion to Web and all Android/TV/desktop/quality/parity Gradle workflows remains pending. The existing legacy CI is authoritative. No change to hosted signing secrets, package artifacts, deep profile task lists or release gates. The comparison script is read-only and uses `gh` only when explicitly called, not during overlay application or validation.
