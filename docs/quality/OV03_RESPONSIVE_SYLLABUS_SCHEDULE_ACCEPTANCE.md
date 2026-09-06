# OV03 — Responsive Syllabus / Schedule Acceptance

## Scope

OV03 establishes the narrow-screen and enlarged-font foundation for the Syllabus and Schedule surfaces without changing scheduling or syllabus domain behavior.

## Syllabus

- Filter and sort chip groups use wrapping `FlowRow` layouts instead of a single horizontally scrolling line.
- Subject priority and exam-weight metadata can wrap independently, preventing Weight/Priority compression at 360dp and larger font scales.
- Subject administration (move, archive/restore, edit, delete) is behind an accessible `⋮` menu.
- Topic administration (move, add child, archive/restore, edit, delete) is behind an accessible `⋮` menu.
- Topic status/progress/mastery metadata wraps instead of consuming one rigid row.
- Delete actions continue to use the existing confirmation path.
- `SubjectCard` receives stable model fields (`Subject`, visible topic count, and topic tree) instead of depending on the inferred visible-projection type.
- Archive toggling reads `visible.subject.archived` at the caller, preventing the prior unresolved-reference regression.

## Schedule

- Study-cycle controls, strategy chips, weekly actions, planned-activity controls, and card actions wrap with `FlowRow`.
- Rule/activity deletion is moved behind an accessible `⋮` menu.
- Destructive confirmation remains mandatory after opening the overflow menu.
- Completion and missed-state actions remain directly reachable and wrap when space is constrained.

## Accessibility / responsive contract

- Overflow triggers use localized `more_actions` content descriptions in English and PT-BR.
- The layout no longer depends on a fixed single-line action row for the screenshot-confirmed problem areas.
- The source contract is designed for 360dp and font scale 1.3/1.5+; screenshot-regression coverage remains a later roadmap item.

## Validation

Required Gradle tasks:

- `:feature:schedule:testDebugUnitTest`
- `:feature:subjects:testDebugUnitTest`

Source/regression validation:

- `scripts/verify_ov03_responsive_layout.py`
- `scripts/verify_ov02_evidence_readiness.py`
- `scripts/verify_ov01_today_priority.py`
- `scripts/verify_bh01_08_bug_hunt_audit.py`
