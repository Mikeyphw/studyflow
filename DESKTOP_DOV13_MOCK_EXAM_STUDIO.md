# Desktop DOV13 — Mock Exam Studio

DOV13 makes Mock Exam Studio executable and restart-safe on Desktop.

- The shared `MockExamStudioPlanner` selects a deterministic eligible question set.
- Creation freezes that selection into the persisted Mock Exam configuration in the DOV12 practice store.
- Start time, answers, timing, terminal score, negative marking, and minimum-score policy use shared practice execution semantics.
- Active exam UI hides answer/explanation truth until the shared mode permits disclosure.
- Reopening the Desktop workspace restores an in-progress Mock Exam from persisted evidence.

DOV14 connects wrong-answer submission to the persistent review/Error Notebook domain.
