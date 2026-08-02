# Debug Workbench D6 - Study Engine Lab

D6 adds an aggregate-only Study Engine Lab to the existing Debug Workbench Labs surface.

Implemented diagnostics:

- schedule expansion preview through `StudySchedulePlanner.generateActivities`
- review queue counts through `ManageStudyReview.observeQueue`
- practice deck and accuracy counts through `PracticeAnalytics` / `ManageStudyPractice.buildDeck`
- mock exam counts
- recommendation count and top score through `ManageStudyDiscovery.recommendations`
- search result count and type breadth through `ManageStudyDiscovery.search("study")`
- progress goal/note counts through `ManageStudyProgress.observeSnapshot`
- analytics insight/weak-area counts and consistency percent through `ManageStudyAnalytics.observeSnapshot`
- sync foundation plan counts through `ManageStudyCloudSync.inspectLocalChanges`

Privacy boundary:

- no plan names
- no note content
- no question text
- no answer text
- no search result titles
- no recommendation labels
- no raw sync tokens
- no object IDs beyond the caller-selected default-plan boundary

The lab is read-only and has no write, delete, import, alarm, notification, or sync mutation actions.
