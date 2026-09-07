# PC08 / PC09 — Content modes and coaching parity closure

## PC08 — Programming and language parity

Programming course plans now support multiple local code files per exercise, syntax-token rendering, expected-output comparison, repeated attempt evidence, solution-reveal provenance, project checkpoint progress, and preview-first CSV/Markdown course imports. The browser safety boundary remains explicit: StudyFlow does not execute arbitrary shell commands or pasted code. Learners may record observed output from a trusted external/runtime environment and StudyFlow compares it to stored expectations without claiming execution.

Language decks now expose all six review directions: recognition, production, fill-blank, natural usage, mistake spotting, and sentence creation. Fill-blank and mistake-spotting refuse to invent a prompt when an example sentence/common mistake is missing. Natural-usage and sentence-creation are explicitly manual-review modes. Language review evidence links back to the expression and due-review creation is duplicate-safe. CSV/Markdown imports are preview-first and duplicate-aware.

## PC09 — Coach, readiness, cleanup, report, runbook and sprint

The weekly coach is derived only from persisted evidence: study minutes, graded accuracy, coverage, review completion, weakness signals and the configured weekly question quota. Unknown evidence stays unknown. Every coach item includes a reason and an actionable destination when appropriate.

Smart content cleanup is preview-first. It reports normalized duplicate questions, orphan topics, detached reviews, invalid planned activities, and detached materials. Automatic Apply is intentionally limited to safe actions: archive a truly unused topic, dismiss a detached pending review, or cancel a planned activity whose content is no longer active. Duplicate questions and detached materials remain manual-review findings.

The HTML exam report is generated entirely locally from structured state/evidence and escapes user content before rendering. The exam runbook requires a confirmed exam date before showing date-based urgency. The seven-day sprint reuses the PC04 adaptive scheduling engine, so locked/completed history and capacity/conflict guarantees remain intact and changes stay preview-first until explicit Apply.

State schema remains **v6** and portable v1/v2 compatibility is unchanged.
