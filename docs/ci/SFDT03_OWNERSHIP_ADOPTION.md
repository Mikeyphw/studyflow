# SFDT-03 — StudyFlow explicit affected-target ownership (advisory)

This source-only migration requires SFDT-02/R5 in `~/Code/devtool`.
The transactional preflight checks those changed source symbols, not Git HEAD.
It does **not** turn on
GitHub Actions job-skipping, replace hosted Gradle profiles, modify signing,
install Gradle on Termux, or assert native hosted EXO qualification.

Eight existing target identities, runners, execution roots, Gradle task lists,
wrapper aliases and existing jobs remain unchanged. `affected_ownership =
"inputs-only"` prevents repository-root executors from claiming every file,
while precise inputs and the existing conservative unknown-path fallback
still fail closed. Missing shared roots are added deliberately:

- `.github/workflows/*.yml` (all eight; relative from `web`)
- `.devtool.toml` and `protocol/**` (web relative to web root)
- `core/**`, `feature/**`, `build-logic/**`, shared Gradle setup (parity)
- shared scripts/Gradle wrapper settings for Gradle targets

## Source-only acceptance

```sh
cd ~/Code/studyflow
python3 -B scripts/sfdt03_ownership_contract.py --check
python3 -B scripts/test_sfdt03_ownership_contract.py
```

## Actual Devtool engine acceptance (after SFDT-02/R5 installs successfully)

```sh
cd ~/Code/studyflow
PYTHONPATH="$HOME/Code/devtool${PYTHONPATH:+:$PYTHONPATH}" \
  python3 -B scripts/sfdt03_ownership_contract.py --verify-devtool
```

The actual-engine command requires the installed Devtool runtime and its Python
dependencies to be available. If not, source-only checks may pass, but native
selection remains **unqualified**. Do not use the matrix to skip hosted jobs
until a real Devtool selector run passes and both repositories have been
published and qualified. Run `devtool affected --explain --json` on reviewed
changed files to inspect real reasons on an accepted revision.

The advisory test covers phone/TV/desktop/Web isolation, shared Android
and Gradle roots, cross-client protocol, CI files, and unknown-path fallback.
It does not prove cross-target workflow dependency propagation, native EXO,
release admission, signing lineage, or complete hosted CI.
