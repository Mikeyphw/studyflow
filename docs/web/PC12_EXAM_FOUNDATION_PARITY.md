# PC12 — Cockpit, reference freshness, and study-phase foundation

This post-audit closure implements the first remaining parity block identified after PC11: OV09 + OV14 + OV15 browser-applicable semantics.

## Truthful pre-edital cockpit (OV09)

- The cockpit is derived from the active `EXAM_PLAN` target and accepted edital revision stream; it does not create a second import path or a second source of truth.
- A confirmed target date exits pre-edital mode. Expected dates remain assumptions and **never produce an official countdown**.
- The latest accepted revision wins. A revision can persist its reference label and publication date; the target's edital reference is the fallback only when no accepted revision exists.
- Missing confirmed date, missing reference, unknown publication date, and a reference older than the configured product window stay visible.
- The web default for cockpit notice age is 365 days, matching the original OV09 domain contract.
- The cockpit links back to the existing edital revision workflow for assumption review.
- Issue copy is provided in English and pt-BR.

## Editable study-phase planning (OV14)

- Dated phase definitions live inside the existing plan configuration. No parallel scheduler or persistence backend is introduced.
- Each phase has start/end dates, theory/questions/review percentages totaling 100%, and optional question/mock targets.
- Phases cannot overlap and phase kinds are unique.
- The adaptive generated-week planner reads the active dated phase and uses its allocation to choose generated study/question/review blocks while keeping the same plan and scheduler identity.
- The Banco do Brasil Sep–Dec 2026 phase suggestions carry the original dates and targets: 300/500/700/1000 questions and 3/5/4/4 mocks.
- The 60/25/15, 45/40/15, 25/55/20, and 10/65/25 allocations are explicitly labeled **StudyFlow suggestions**, not official edital values, and remain locally editable.

## Reference freshness and provenance (OV15)

- Material reference provenance is encoded in the existing material `tags` array with the same managed prefixes used by the original StudyFlow contract.
- `EDITAL_2022` references can be tracked and stamped with a verification date.
- StudyFlow warns after more than 90 days without re-verification. This is explicitly a product freshness policy, not an edital rule.
- Replacement creates a successor material, marks the old material archived, and records `supersedesMaterialId`; the old reference is not silently deleted.
- Tags and archived history travel through the existing portable package payload with no new persistence store.

## Validation

Run from `web/`:

```text
python3 scripts/validate_web.py --parity-exam-foundation
python3 -m unittest tests.test_pc12_exam_foundation
npm run lint
npm test
npm run build
npm run release:check
```

PC12 closes only this foundation block. The post-PC11 audit's remaining parity rows are still tracked and must not be described as globally closed yet.
