# PC08 / PC09 — Content modes and coaching parity closure

## PC08 — Programming and language parity

Programming course plans now support multiple local code files per exercise, syntax-token rendering, expected-output comparison, repeated attempt evidence, solution-reveal provenance, project checkpoint progress, and preview-first CSV/Markdown course imports. The browser safety boundary remains explicit: StudyFlow does not execute arbitrary shell commands or pasted code. Learners may record observed output from a trusted external/runtime environment and StudyFlow compares it to stored expectations without claiming execution.

Language decks now expose all six review directions: recognition, production, fill-blank, natural usage, mistake spotting, and sentence creation. Fill-blank and mistake-spotting refuse to invent a prompt when an example sentence/common mistake is missing. Natural-usage and sentence-creation are explicitly manual-review modes. Language review evidence links back to the expression and due-review creation is duplicate-safe. CSV/Markdown imports are preview-first and duplicate-aware.

## PC09 — Coach, readiness, cleanup, report, runbook and sprint

The weekly coach is derived only from persisted evidence: study minutes, graded accuracy, coverage, review completion, weakness signals and the configured weekly question quota. Unknown evidence stays unknown. Every coach item includes a reason and an actionable destination when appropriate.

Smart content cleanup is preview-first. PC15 later expands this to the original seven issue classes and makes every cleanup preview action review-only and confirmation-gated; no cleanup preview directly mutates user data.

The HTML exam report is generated entirely locally from structured state/evidence and escapes user content before rendering. The exam runbook requires a confirmed exam date before showing date-based urgency. PC15 supersedes the earlier generic adaptive-schedule sprint: the seven-day sprint now only edits type/duration of existing unlocked planned activities and never creates IDs or moves starts.

State schema remains **v6** and portable v1/v2 compatibility is unchanged.
