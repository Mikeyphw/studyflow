# CMOV-06 — Coding Exercises + Termux Execution

Adds the execution layer for programming exercises without allowing arbitrary shell input.

Implemented:
- exercise-to-Termux execution planning;
- workspace, file-write, run-command and result-read request chain;
- result-to-exercise status mapping;
- Termux request/result Room persistence;
- visible programming workspace copy for safe Termux execution;
- verifier and unit-test contracts for command allow-listing and manual completion.

The runtime remains explicit-user-action based: a successful command can mark an exercise as ran successfully, but it does not silently mark the exercise completed.
