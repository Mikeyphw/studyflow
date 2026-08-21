# CMOV-04 — Termux Programming Runtime Foundation

This overlay adds the safe programming-runtime contract for Termux-backed courses.

It does not execute commands from StudyFlow yet. It introduces the typed request,
result, workspace and command-template boundary that later overlays can connect to
Termux with explicit user action.

Delivered scope:

- Termux workspace and slug safety contracts.
- Whitelisted command-template policy for programming tools.
- Request/result envelopes for JSON bridge files.
- Runtime preference capability enabled for programming-course content mode.
- Static verifier and model tests that forbid arbitrary shell fragments.

Out of scope for this overlay:

- Launching Termux.
- Writing exercise files to device storage.
- Running or polling commands.
- Marking coding exercises complete from runtime results.
