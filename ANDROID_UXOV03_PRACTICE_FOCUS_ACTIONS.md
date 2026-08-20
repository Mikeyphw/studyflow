# Android UXOV-03 — Practice, Focus, and Quick Actions

UXOV-03 builds on UXOV-01 and UXOV-02. It gives StudyFlow's Android learning loop its own execution identity without changing domain or persistence contracts.

## Delivered

- Practice root no longer owns a nested Material `Scaffold`, stock FAB, or stock workspace tabs.
- Reviews use a dedicated StudyFlow practice hero while Questions and Errors keep their existing domain workflows.
- Focus Session is immersive: the Material `TopAppBar`, root `Scaffold`, and timer `Card` are replaced by StudyFlow execution chrome.
- Pause, resume, finish, cancel, timer, note autosave, and back interception remain backed by the existing `ManageStudySession` APIs.
- The root StudyFlow shell observes the authoritative active-session stream and morphs the compact dock into a real active-session bar.
- Active-session pause/resume delegates to existing domain transitions; completion remains inside Focus because it requires completion metrics.
- On expanded layouts the navigation rail remains available while the active-session bar is surfaced over the workspace.
- Quick Add keeps all existing routes but no longer renders through the generic utility scaffold; it uses compact StudyFlow action rows.

## Non-goals

- No database/schema changes.
- No scheduling/review/practice-engine changes.
- No Progress redesign; that remains UXOV-04.
- No full-project validation in this interim overlay. Apply with `--no-validate`; UXOV-04 owns the final validation seal.
