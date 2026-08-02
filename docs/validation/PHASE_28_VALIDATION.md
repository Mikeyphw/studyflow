# Phase 28 Validation

Source checks:

- `scripts/verify_phase_28.py`
- `BackupRestoreDrillSealTest`
- Phase 27 verifier is phase-forward after the app version advances beyond Phase 27
- release seal evidence advances through Phase 28

Device or host checks still required:

- export an encrypted package from the UI;
- try a wrong password;
- corrupt a copied package and verify the error path;
- run restore dry-run before import;
- confirm user-facing recovery guidance is understandable.


## r2 validates

- No `__pycache__` or `.pyc` files are present.
- Text sources contain no literal NUL bytes.
- Backup/restore drills call the inspection and dry-run callbacks instead of constructing a fake successful dry-run in production source.
