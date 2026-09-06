# OV10 — BB Agente Comercial pre-edital preset

## Reference contract

This preset is a local reference configuration derived from **Seleção Externa BB 2022/001 — Escriturário, Agente Comercial — Fundação Cesgranrio**. It is not a claim that a 2026 edital or exam date exists.

## Delivered

- First-class onboarding choice `BB_AGENTE_COMERCIAL_PRE_EDITAL`.
- Eight objective subjects with the reference point weights: 15 / 5 / 7.5 / 5 / 7.5 / 15 / 22.5 / 22.5 = 100 points.
- Objective stage with 100 reference points and separate eliminatory essay stage with minimum 70/100.
- Banco do Brasil / Escriturário — Agente Comercial / Fundação Cesgranrio target metadata.
- Reference notice URL for Seleção Externa 2022/001.
- No confirmed or expected exam date.
- Fixed-weekly 19-hour goal and the supplied weekday/Saturday reference rhythm (1,130 scheduled minutes; the source describes this as 19 hours/week).
- Native 1/7/30 review policy and 80% weekly accuracy goal.
- Atomic provisioning now carries existing `ExamStage` records in the same Room transaction as target, subjects, schedule, strategy and goals.
- Objective subjects reference the objective `ExamStage`.
- English and pt-BR resource parity.

## Deliberately not fabricated

- No 2026 exam date/countdown.
- No topic incidence percentages.
- No invented 99-topic syllabus text: the preset starts with the eight official-weight subjects and leaves topic population to the existing edital import/revision-diff pipeline.
- No invented objective minimum scores that are not represented by the supplied reference material.
- Saturday generic blocks are schedule availability/rhythm only; MOCK_EXAM and WRITING specialization remains a later roadmap overlay.
