# OV15 — Android product-flow and UI/domain honesty

Overlay 15 closes the Android feature-module honesty findings by moving UI affordances behind the same rules that domain writes enforce.

## Closed findings

- `S6-01`: optionless short-answer and essay questions no longer create a single answer button from `correctAnswer`; they render an input field and submit action instead.
- `S6-02`: the manual question editor validates multiple-choice answers against the displayed options and keeps the dialog open until the asynchronous save succeeds.
- `S6-03`: naked UI writes are routed through validation-backed state and recoverable operations before reaching domain mutation APIs.
- `S6-04`: archived and trashed plan cards cannot open workspaces from the all-plans collection.
- `S6-05`: optional workspace streams fall back to safe empty values instead of blocking unrelated tabs when one stream fails.
- `S6-06`: the focus screen no longer traps system/back navigation while the session stream is still loading.
- `S6-07`: failed session loads are presented as a load failure, not as session-not-found.
- `S6-08`: archived plan cards no longer expose an Open button.
- `S6-09`: plan cards no longer fabricate a next-activity promise without a concrete scheduled activity.
- `S6-10`: exam countdowns now compare local calendar dates instead of UTC-day flooring.
- `S6-11`: manual syllabus/exam-notice drafts are saveable across Activity recreation.
- `S6-12`: pasted and document-based imports have explicit local bounds before preview/parse.
- `S6-13`: the manual question editor exposes all supported question types: multiple-choice, true/false, short answer, and essay.

## Validation

The Devtool artifact validation hook runs `verifyOv15AndroidProductFlowHonesty`. That Gradle task compiles the affected Android feature modules, runs the new feature-level unit tests, compiles the Android app, and then executes `scripts/validate_ov15_android_product_flow_honesty.sh` for structural source evidence. The hook intentionally does not stop Gradle daemons.
