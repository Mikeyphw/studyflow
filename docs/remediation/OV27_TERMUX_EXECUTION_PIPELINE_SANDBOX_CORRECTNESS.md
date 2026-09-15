# OV27 Termux execution pipeline & sandbox correctness

OV27 upgrades Android Termux execution from a UI-adjacent launcher into a production-safe execution path:

1. The domain repository persists all four request stages before launching Termux.
2. The Android bridge checks Termux visibility/availability and launches only exact RUN_COMMAND rows.
3. Result broadcasts are one-shot, SHA-256 request-code scoped, and carry the request id in both extras and data URI.
4. The result receiver writes through `ManageProgrammingCourse.recordTermuxCommandResult` and no longer stores a volatile latest result.
5. Command templates are parsed with a quote-aware tokenizer and constrained to StudyFlow-approved execution shapes.
6. Workspace paths resolve from `TermuxWorkspace.root/courseRelativePath` and reject arbitrary absolute/private paths.

Use `scripts/validate_ov27_termux_execution_pipeline_sandbox_correctness.sh` to verify the overlay contract.
