# Desktop DOV17 — Content modes and Desktop execution

DOV17 makes Study Plan content modes first-class on Desktop.

- `StudyPlan.contentMode` is user-selectable and remains persisted by the DOV08 workspace.
- Programming profiles/exercises and language decks/expressions/review attempts persist in `content-modes-v1.properties`.
- Programming uses a Desktop-native `ProcessBuilder` backend with fixed argument vectors and a Plan/exercise workspace boundary; it does not reuse Termux or interpolate user shell strings.
- Python, JavaScript, shell-script and Go starter execution have explicit local runners when those tools exist. Unsupported languages remain visible without a false execution claim.
- Language cards use the shared `buildLanguageReviewPrompt` and `evaluateLanguageReviewAnswer` semantics.
- Reading Study and General Skill use the shared Plan/Library/Session domain instead of Android-specific execution.
- Permanent Plan deletion purges mode-specific persisted data.

DOV18 adds Concurso Público authoring and decision workflows.
