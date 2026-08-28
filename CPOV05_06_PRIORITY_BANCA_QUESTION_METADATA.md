# CPOV05 + CPOV06 — Explainable priority + banca-aware questions

- Adds a pure deterministic public-exam next-action priority planner with normalized evidence, reason codes, cooldown/starvation handling, locked-session exclusion and deterministic ties.
- Extends existing Question with optional organization, role, exam stage, authorship, edital revision, external reference and points provenance.
- Room schema advances 11→12 and adds local composite indexes for plan+banca/year, plan+subject/topic and plan+organization/role.
- CSV/TSV/Markdown imports remain backward compatible; new provenance columns are optional.
- Pure filters combine banca, organization, role, subject/topic, year range, authorship, difficulty, favorite/disputed and correct/wrong/unseen evidence.
- Legacy portability question rows are normalized to UNKNOWN/null provenance on import.
