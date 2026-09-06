# OV02 — Evidence-aware readiness and no-data semantics

## Scope

OV02 removes fabricated readiness evidence and makes missing evidence explicit throughout the readiness flow.

### Readiness engine

- No answered questions => Practice is unavailable, not 45%.
- No submitted mock => Mock exam health is unavailable, not 50%.
- No confirmed exam date => Time feasibility is unavailable, not 70%.
- No review evidence => Review health is unavailable instead of an automatic 100%.
- No observed schedule execution => Schedule adherence is unavailable instead of a synthetic 0%.
- No recent study activity => Consistency is unavailable instead of a synthetic 0%.
- Overall score/band are absent until a minimum evidence threshold is reached.

Minimum readiness evidence is intentionally conservative. A score becomes available after at least one strong source (submitted mock, 30 answered questions, or 3 completed sessions), or a sufficiently broad combination of behavioral evidence. Eight questions alone must not produce an overall readiness score.

### Confidence

A scored readiness result carries `LOW`, `MEDIUM`, or `HIGH` confidence based on both evidence breadth and sample volume. Confidence is absent when the score itself is unavailable.

### UI truthfulness

- Missing readiness score renders `Not enough data` / `Dados insuficientes`.
- Missing individual metrics render `No data` / `Sem dados`.
- Pre-edital plans without an exam date do not display a fabricated time-feasibility percentage.
- Schedule and goal cards distinguish missing evidence from a measured 0%.
- Scored readiness shows its confidence level.

### Downstream safety

- Adaptive reschedule accepts nullable readiness and does not label unknown readiness as risky.
- Weekly coach does not emit a readiness risk solely because readiness is unavailable.
- Exam report exports nullable readiness signal values rather than synthetic percentages.
- Mock-related recommendations are emitted only when mock evidence actually exists.

## Regression requirements

- `ExamReadinessEngineTest.freshImportedPlanDoesNotInventReadinessFromMissingEvidence`
- `ExamReadinessEngineTest.eightQuestionsRemainInsufficientEvidence`
- `ExamReadinessEngineTest.enoughPracticeProducesLowConfidenceInsteadOfFalsePrecision`
- `ExamReadinessEngineTest.broadEvidenceCanReachHighConfidence`
- Existing BH01–BH08 and OV01 seals remain green.
