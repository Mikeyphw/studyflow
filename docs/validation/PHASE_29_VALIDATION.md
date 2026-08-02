# Phase 29 Validation

Source checks:

- `scripts/verify_phase_29.py`
- `ReleaseCandidatePolishAuditorTest`
- Settings release-candidate polish card/resource checks
- Phase 28 verifier is phase-forward after the app version advances beyond Phase 28
- release seal evidence advances through Phase 29

Device checks still required:

- open Settings and verify the release-candidate polish card is visible;
- switch English and Brazilian Portuguese resources;
- run runtime smoke workbench;
- export and dry-run restore a backup from the UI;
- assemble and install the release candidate on target hardware.


## r2 validates

- No `__pycache__` or `.pyc` files are present.
- Text sources contain no literal NUL bytes.
- Release-candidate findings are backed by `ReleaseCandidatePolishEvidence`.
- The auditor blocks missing and partial evidence.
- EN/PT-BR release-candidate resource parity is parsed from XML, not inferred from marker text.
