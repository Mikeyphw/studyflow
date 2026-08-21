# CMOV-10 — Mode-Aware Analytics

Adds mode-aware analytics profiles on top of the existing StudyFlow analytics snapshot.

## Delivered

- Exam plans keep the existing analytics rows.
- Programming plans surface module progress, exercises, debugging accuracy, project progress, and weak concepts.
- Language decks surface idiom progress, due idioms, usage accuracy, mistake patterns, and register coverage.
- Reading and general skill modes get safer fallback profiles instead of exam-only labels.
- The Analytics screen uses the selected plan content mode when choosing metric labels.

This overlay does not change analytics storage or raw SQL aggregates; it adapts presentation and recommendation labels
using the already-computed snapshot so it remains backward-compatible with existing plans.
