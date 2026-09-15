# OV28 — Language review correctness & Desktop specialized parity

OV28 makes the specialized-content remediation path truthful on both language review and Desktop execution.

## Language review corrections

Findings closed: S11-14, S11-15, S11-16.

- Free-form sentence scoring now rejects gibberish and repeated placeholder tokens.
- Spot-the-mistake review now rejects answer echoes and literal-only responses.
- Natural-usage multiple choice now uses stable deterministic ordering so the correct answer is not fixed at index 0.
- Multiple-choice grading requires the expected choice label instead of falling through to looser accepted-answer matching.

## Desktop parity corrections

Findings closed: S11-19, S11-20.

- Desktop persistence schema v2 now includes code snippets, mini-projects, Termux runtime preferences, idiom cards, and vocabulary cards in addition to profiles, exercises, decks, expressions, and attempts.
- Desktop sample data creates and persists the same specialized graph classes used by Android.
- Desktop programming execution uses configured exercise command templates, resolves `{mainFile}`, `{exerciseFile}`, and `{expectedOutput}`, writes the actual exercise file bundle, checks expected output, and persists expected-output match evidence.
