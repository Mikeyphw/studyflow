# CPOV09 + CPOV10 — Legislation study and concurso dashboard

- Room 12→13 adds explicit legislation documents, provisions, and evidence links.
- Imports are local-only, preview-first, and accept pasted text, TXT/Markdown, and structured CSV.
- Question/error/review links are plan-scoped and included in portability/duplication.
- The concurso dashboard is derived-only: no dashboard persistence table is introduced.
- Unknown incidence/cutoff values remain unknown; cutoff gap appears only when both a user-provided estimate and a completed mock score exist.

## Dashboard formulas

- Edital coverage is the mean persisted syllabus progress of active topics (falling back to active subjects).
- Weighted mastery uses subject exam weight × known incidence; unknown incidence remains displayed as unknown and receives a neutral subject-weight fallback in the aggregate.
- Matrix priority = 35% relative subject weight + 20% incidence (neutral midpoint when unknown) + 30% mastery gap + 15% review/error backlog.
- Accuracy uses completed Study Sessions only in rolling 7/30/90-day windows.
- Cutoff gap is emitted only when a completed mock score and the user-supplied estimated cutoff both exist.
