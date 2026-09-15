# Remediation OV27 — Termux execution pipeline & sandbox correctness

Overlay 27 of 30 closes the Termux execution defects found after OV26 by turning the Android/Termux bridge from a volatile launch helper into a durable, correlated execution pipeline.

## Closed findings

- **S11-02**: Implements the four-stage Android→Termux pipeline. The repository durably queues `CREATE_WORKSPACE`, `WRITE_EXERCISE_FILES`, `RUN_COMMAND`, and `READ_RESULT` requests before Android launches Termux.
- **S11-03**: Adds explicit package visibility for `com.termux` and `com.termux.RUN_COMMAND` while keeping the narrow `RUN_COMMAND` permission.
- **S11-04**: Removes the volatile latest-result cache. The result receiver now persists results through the app container/repository path.
- **S11-09**: Replaces whitespace splitting with a quote-aware tokenizer and rejects inline interpreter execution such as `python -c`, `node -e`, and `bash -c`.
- **S11-10**: Confines workspace roots to `~`/`$HOME` StudyFlow roots and resolves work directories through the configured `TermuxWorkspace` instead of hard-coded paths.
- **S11-11**: Preserves quoted command arguments without invoking a shell, so filenames with spaces stay intact and shell fragments remain inert/rejected.
- **S11-12**: Results update exercises only through exact durable `requestId` correlation and the stored plan/exercise relationship.
- **S11-13**: Replaces hashCode-derived PendingIntent request codes with SHA-256-derived, data-URI-scoped one-shot result intents.

## Validation

Run:

```bash
bash scripts/validate_ov27_termux_execution_pipeline_sandbox_correctness.sh
```

The validator checks source contracts, compiles the model package with `kotlinc`, and validates the Termux security/runtime tests when Gradle is available.
