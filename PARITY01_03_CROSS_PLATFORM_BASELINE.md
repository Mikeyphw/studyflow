# PARITY01-PARITY03 cross-platform baseline

This overlay starts the StudyFlow cross-platform parity and full-connectivity roadmap.

## PARITY01 — canonical capability truth

Added `docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md` as the current-state source of truth for:

- Android;
- Web;
- Desktop;
- Android TV;
- Web TV;
- intentional platform-specific boundaries;
- current connectivity limitations;
- the target SFLP full-mesh connectivity matrix.

The matrix explicitly distinguishes shipped capability from roadmap targets so future documentation cannot
claim universal connectivity before SFLP is actually delivered.

## PARITY02 — documentation reconciliation

Updated current-facing scope and README language so the repository no longer presents Web/Desktop as
absent, optional provider I/O as wholly staged, or Android TV as a pre-TVOV01 placeholder. Historical
Android RC1 notes remain historical and now point to the canonical current-state matrix.

Added `docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md` with PARITY, SFLP, Desktop, TV
convergence, semantic-golden-vector, and final acceptance tracks. SFLP requires connectivity between every
Android/Desktop/Web/Android-TV/Web-TV pair rather than treating Android phone as the hub.

## PARITY03 — restored integration seal

Restored the existing 120-character Kotlin/KTS source-quality contract after later TVOV21-TVOV26 work
introduced overlong signatures, expressions, tests, and Gradle descriptions.

The changes are formatting-only for production Kotlin behavior. The TVOV24 validator was made
formatting-insensitive for the same strongly typed `sendPresentation` gateway signature because its former
one-line token requirement contradicted the repository line-length gate.

## Validation

Run:

```bash
python3 scripts/verify_parity01_03_cross_platform_baseline.py
```

The validator:

- checks the canonical matrix and roadmap milestone coverage;
- rejects the specific stale product statements repaired by PARITY02;
- requires the existing OV20 final integration seal to pass;
- re-runs the affected TVOV21-TVOV26 source seals.

No validator is weakened to ignore source-quality violations.
