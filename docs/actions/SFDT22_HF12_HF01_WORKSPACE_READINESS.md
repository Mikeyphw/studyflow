# SFDT22 HF12/HF01 — guarded topic editor and workspace readiness

Pinned to PR #15 HF11 commit `11a8a2089de36895e5776258d1a1adf6b23f38e7`. Latest hosted SFDT22 run 42495200757 failed with `topic_open_dialog` / `topic_dialog_missing` while the first real plan journey passed.

## Failure and repair
The error only proves Compose did not find the requested editor after `performClick()`. It does not prove which layer failed. In production, the Add Topic action is a nonmutating form opener. Add a stable nonvisible semantic test tag to the real Material3 topic-dialog container. After its first real Compose click, the acceptance test waits briefly for genuine dialog or real fields, then waits for the prior subject-save Snackbar to clear and attempts at most one fresh real pointer tap **only if no editor evidence exists**. If still not opened, reject acceptance with the existing `topic_dialog_missing` code. Distinguish guarded recovery touch failure from Snackbar obstruction with allowlisted enum codes.

The existing subject-card scoped Add Topic action, saved subject card, saved topic row, tagged editable name, and real-database checks remain. No direct production repository writes or test seeding are allowed. Production dialog placement, content, workflow, and business logic are unchanged: only a stable test tag is added.

## Remaining flows
Once topic creation succeeds, wait for the real Plan Overview -> Sessions action, Practice Questions action, Review Center entry (due/empty labels), and cold-restart workspace panes. Selected bottom navigation alone is not sufficient. Distinct stage-reason pairs remain sanitized in JSON, without retaining raw logs or failed screenshots. The receipt records completed journeys even on a later failure; overall remains failed until all five tests, cold restart, and seven unique screenshots are verified.

## Qualification
This package runs source pinning and all existing HF01–HF12, SFDT21, SFDT11 checks **on application**. Fixture and package checks do not replace full checkout tests. Hosted Android API36 instrumentation, including all five journeys and 7 screenshots, is still mandatory before merging.
