# SFDT22 HF11 — Production UI / instrumentation deep audit

Base: `Mikeyphw/studyflow` PR #15, commit `50985bb72f24695fe5e786af8d656a1d0cef5180`.

## Evidence and causal findings

Run 42490678664: four non-SFDT22 workflows passed. SFDT22 compiled and reached its second real journey. The allowlisted receipt reports `failure_phase=subjects`, `failure_step=topic_open_dialog`, `failure_reason_code=topic_dialog_missing`. This is a **locator failure**, not proof of a missing production modal: HF09/HF10 treated absence of `hasTestTag(..., useUnmergedTree=true)` as absence of the editor.

Source trace: `StudyContentScreen` owns `topicEditorSubjectId` as saveable UI state. `SubjectCard` calls `onAddTopic(null)` through a Material3 `TextButton`; `openTopicEditor` selects the clicked subject; `topicEditorRequest` resolves the existing syllabus subject and renders the real `TopicEditorDialog`; `TextFieldLine` tags the `OutlinedTextField`. The previous test targeted the first generically tagged button by coordinate click, rather than a semantics click scoped to the subject it had actually saved.

**Additional assertion flaw:** `visible(subjectName)` could be satisfied by text still inside the subject editor. Likewise `visible(topicName)` could match an unsaved field. Neither alone proves persistence. These are corrected with production subject-card and topic-row tags and explicit editor-closed checks. The real repository remains the source of data; no tests seed or manipulate repositories directly.

**Navigation readiness:** HF10's `openPlanContentTab()` addressed the earlier `subject_open_content` race. This fix preserves it and reuses it for the cold-restart content check.

## HF11 adjustments

1. Identify the specifically created subject card by its visible name (test tag only; no UI change), and require its rendered card after the save action.
2. Find the Add Topic click action *under that card*, scroll into view and invoke its real Compose click semantics. Do not use unverified touch coordinates or select the first arbitrary subject.
3. Distinguish no editor transition from an editor that opened with a missing semantics tag: observe added real editable controls and both merged/unmerged tags. Never use `isDialog()` as a hard dependency.
4. Fill only the real tagged topic-name field, preferring merged semantics and using unmerged descendants as a fallback. Confirm its text.
5. Verify a real rendered topic row after save/expand, and verify both persisted views again after the forced process restart.
6. Map each newly introduced diagnostic to a closed enum; do not retain logs, screenshots on failure, account data, or arbitrary raw text.

## Non-goals and remaining qualifications

The source does not establish that the Material3 dialog itself is defective. No production data/storage logic is altered. The later session, practice, review and cold-restart journeys must still pass on hosted Android API 36: offline contracts are not acceptance. SFDT21, SFDT11, Devtool and standard CI remain mandatory. Do not merge/release based solely on these checks.
