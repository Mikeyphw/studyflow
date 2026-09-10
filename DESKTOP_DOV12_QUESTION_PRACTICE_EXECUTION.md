# Desktop DOV12 — Question practice execution

DOV12 replaces the Desktop practice preview with a durable execution workflow.

- Questions, Practice Tests, encoded answers, favorite/disputed state, and terminal attempt evidence persist in `practice-v1.properties` under the DOV08 workspace root.
- Practice Tests use the shared `ManageStudyPractice`/`PracticeExecutionEngine` semantics rather than a Desktop-only scorer.
- Active attempts expose statement/options and only reveal explanations according to the shared execution mode; the Desktop UI does not render the answer key during active execution.
- Permanent Study Plan deletion purges Plan-owned practice data.
- The production workspace uses atomic replacement; the test/embedded composition root remains side-effect-free when no workspace persistence is supplied.

DOV13 builds Mock Exam Studio on the same persistence store.
