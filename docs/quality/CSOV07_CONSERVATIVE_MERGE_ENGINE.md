# CSOV07 Conservative Merge Engine Acceptance

StudyFlow resolves optional sync data using stable ids and conservative conflict behavior.

Required guarantees:

- Local-only entities are kept locally.
- Remote-only entities are imported.
- One-sided changes can be applied automatically.
- Both-sided changes produce manual conflicts.
- Delete-versus-change cases produce manual conflicts.
- The engine keys by stable ids such as studyPlanId, taskId, sessionId, questionId, deckId, courseId, and checkpointId, never by titles.
