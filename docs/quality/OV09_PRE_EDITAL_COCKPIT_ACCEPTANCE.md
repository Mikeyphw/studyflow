# OV09 — Truthful pre-edital cockpit

## Contract

- Public-exam plans derive pre-edital state from the canonical `StudyTarget` and `ExamNoticeRevision` streams.
- A confirmed target date exits pre-edital mode.
- Expected target dates and legacy plan dates remain assumptions; they are never presented by the cockpit as an official countdown.
- The latest applied edital revision is the reference when available.
- Missing references, unknown publication dates, and references older than the configured freshness window remain visible verification issues.
- Reviewing assumptions routes to the existing content/edit-al revision workflow instead of introducing a second import path.
- No schema or persistence layer is added.
- English and pt-BR copy have parity.
