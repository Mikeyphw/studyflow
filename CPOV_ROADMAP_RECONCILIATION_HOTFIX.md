# CPOV roadmap reconciliation hotfix

This hotfix reconciles the applied CPOV00-CPOV13 tree against the literal concurso-publico roadmap after a delivery audit found gaps that the original overlay-specific source verifiers did not detect.

## Corrected delivery gaps

- Restored a compilable `PublicExamRepository`/`ManagePublicExam` boundary for legislation APIs.
- Fed persistent study-cycle position and recent 30-day practice evidence into the explainable priority integration.
- Exposed correct/wrong/unseen question filters backed by persisted attempt evidence.
- Preserved wrong-answer classification through the attempt codec and stopped inventing `CONCEPT_CONFUSION` for unclassified errors.
- Exposed configurable banca blueprint scoring, penalties, time/minimums, per-section counts/weights, strict mode and allowed question types before frozen mock creation.
- Added the promised weak Lei Seca filter and explicit question/error/review evidence-link actions.
- Added the Importance x Mastery dashboard matrix and syllabus status in edital matrix rows with a compact-width layout.
- Completed public-exam onboarding with confirmed/expected date semantics, edital import now/later, overdue-review and edital-coverage goals, subject-bound accuracy goals, and the full local TXT/CSV/TSV/Markdown template set promised by the roadmap.
- Kept Room at schema 13; the reconciliation adds no duplicate persistence engine.

## Regression contract

`verifyCpovRoadmapReconciliation` is wired into Android and desktop Devtool preflight in addition to the original CPOV00-CPOV13 seals. The corrective overlay retains the full CPOV13 target-machine validation gate; source checks alone do not constitute release acceptance.
