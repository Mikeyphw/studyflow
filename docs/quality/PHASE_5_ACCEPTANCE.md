# Phase 5 acceptance contract

Phase 5 is complete when StudyFlow supports structured syllabus management inside each
Study Plan.

## Required behavior

- Subjects can be created, edited, reordered, archived and deleted.
- Topics can be nested, edited, reordered, archived, deleted and bulk edited.
- Syllabus search includes subject and topic names/descriptions and keeps matching ancestors
  visible.
- Filters cover all, active, needs-attention, mastered and archived content.
- Sorting covers manual position, priority, name and progress.
- Subject progress is derived from active topic progress.
- Plan progress is recalculated from subject progress.
- Structured-text and CSV syllabus import append content transactionally.
- Subjects and topics can optionally link to an exam stage.
- Room schema 1 to 2 migration preserves existing rows and adds nullable stage links.
- Compose code reads strings through composition-aware APIs, not `LocalContext.getString`.

## Non-goals

- Drag-and-drop gestures.
- AI edital extraction.
- Full question bank tagging UI.
- Physical annex storage.
