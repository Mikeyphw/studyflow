# REL02 release evidence exporter / failure triage bundle

REL02 adds the post-SFLP evidence exporter that reads the REL01 target validation outputs and produces a final user-readable report plus a triage bundle.

## Scope

- Export `release-evidence-summary.json` with redacted JSON evidence.
- Export `release-failure-triage.json` with prioritized missing, pending, failed and blocked evidence.
- Export `release-evidence-report.md` for a human release review.
- Export `studyflow-release-evidence-bundle.zip` for sharing build/test evidence without plaintext signing secrets.
- Preserve fail-closed semantics: `PENDING_TARGET_EXECUTION` and `BLOCKED` never become `releaseReady=true`.

## Command

```bash
python3 scripts/export_release_evidence_report.py --mode all
```

To make CI fail when the evidence is incomplete:

```bash
python3 scripts/export_release_evidence_report.py --mode all --fail-when-blocked
```

## Devtool

Intermediate apply:

```bash
devtool --yes --copy -r ~/Code/studyflow --target studyflow_android   apply overlay /sdcard/Download/studyflow_rel02_evidence_report_triage_v1.zip   --no-validate --resource-profile standard --max-workers 4 --cpu-limit 4   --gradle-heap-mb 2048 --gradle-metaspace-mb 768 --memory-guard-mb 0 --parallel
```
