# StudyFlow SFDT22 HF08: dialog-scoped topic-name entry

Evidence: Hosted run `42461351831`, PR #15 commit `cdc1b3b7057aecfd13f7e57f9ae87951ee3c76fd`; SFDT22 receipt reported `failure_phase=subjects`, `failure_step=topic_fill_name`, `failure_reason_code=ambiguous_compose_node`. The plan and subject stages had already passed. HF07 restored Kotlin compilation for all other phone lanes.

The test had relied on `onNodeWithTag("studyflow.topic_editor.name")`, whose exact-one-match semantics are not guaranteed by a Material3 `OutlinedTextField`'s merged semantics tree. In this update:

1. Test the real `AlertDialog` with `isDialog()` in the unmerged semantics tree.
2. Scope editable nodes with `hasSetTextAction() and hasAnyAncestor(isDialog())`.
3. Prefer the uniquely tagged editable descendant. If the tag is ambiguous or hidden by merged semantics, target the first **editable inside the dialog**, matching the production `TopicEditorDialog` field order (name first, then description/number controls).
4. Assert that the input text exists on a real editable control inside the active dialog before the Save action.
5. Produce sanitized enum diagnostics for missing/ambiguous dialog, missing editable or failed text write. Preserve existing fail-closed real runtime checks and data persistence.

The patch changes only test navigation and verifier semantics, not production app data or UI. All five test methods, seven screenshot checks, forced app restart, source pinning and release prohibition remain. Confirm through hosted Android 16 instrumentation on GitHub before merging.

## HF08 HF01 installer compatibility repair

Update HF06 test_11 to assert that topicName reaches the dialog-scoped helper and that the helper uses performTextReplacement(value). No application production change or CI gate suppression.
