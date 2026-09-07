# OV13 — Activity-type deep links

Acceptance contract:

- launch routing is based on `StudyActivityType`, never activity-title heuristics;
- `MOCK_EXAM` opens the existing Mock Exam Studio;
- `QUESTIONS`/`FLASHCARDS` open Practice and preserve activity subject/topic context;
- `REVIEW` opens the matching review queue;
- `WRITING` creates/opens the canonical writing focus session from OV11;
- theory/video/reading/custom continue through the normal focus-session path;
- topic filters are narrower than subject filters and never silently broaden to unrelated content.

No Room schema change.
