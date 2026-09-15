# REMEDIATION OV 28 IMPLEMENTATION

Overlay 28 of 30 closes the final specialized-content remediation group: language review correctness and Desktop specialized parity.

## Scope

- S11-14: Free-form language review no longer rewards gibberish, repeated tokens, placeholder nonsense, or target-only echoing above valid target usage.
- S11-15: Spot-the-mistake prompts reject repeated mistakes and literal-only answers; answers must explain what is wrong or unnatural.
- S11-16: Natural-usage multiple choice answers are placed with deterministic non-fixed ordering instead of always first.
- S11-19: Desktop content-mode persistence stores the complete specialized graph used by Android: programming profiles, snippets, exercises, mini-projects, Termux runtime preferences, language decks, expressions, idiom cards, vocabulary cards, and attempts.
- S11-20: Desktop programming execution uses exercise command templates, resolves placeholders, writes exercise file bundles, checks expected output, and persists expected-output match state.

## Validation

Run:

```bash
bash scripts/validate_ov28_language_review_desktop_specialized_parity.sh
```

The validator is network-independent by default. It performs source-contract checks and compiles the shared model sources with `kotlinc` when available. Set `STUDYFLOW_RUN_GRADLE_OV28=1` to also run the focused Gradle tests in environments with a ready Gradle cache/toolchain.
