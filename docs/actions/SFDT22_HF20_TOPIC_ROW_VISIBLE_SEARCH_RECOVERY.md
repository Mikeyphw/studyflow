# SFDT22 HF20 — real-topic-row verification using production search

## Hosted finding

HF19 run 42546476776 compiled the real production and instrumented APKs, and
saw successful topic-save feedback, but timed out in `topic_confirm` at
`topic_row_not_composed` (only one of five journeys had completed).

## Corrective change

The source confirms a `LazyColumn` with a production syllabus search field, a
filter-dependent subject/topic tree, and a real topic text tag. A successful
Snackbar alone does not establish visibility or cold-start persistence.

HF20 adds a stable test tag to the existing production search field (no new
UX behavior). If the topic row cannot be found within the original timeout,
the test scrolls the actual LazyColumn to the search box, enters the unique
saved topic name through the real editor, and waits for the real row to become
visible. A nonempty search query already expands matching subjects through
the existing production state. No direct Room read/write or synthetic content
is used. Both the unchanged initial row proof and fallback require a truly
visible saved-topic Text node; the test still fails if no row appears.

Search setup failures are classified as `topic_search_editor_missing` or
`topic_search_write_failed`, with only fixed reason codes in CI evidence.
The existing `topic_row_not_composed`, `topic_row_missing`, and save-failure
checks remain fail-closed.

## Scope, tests and limitations

Source-pinned: StudyContentScreen.kt, Sfdt22HostedStudyJourneyTest.kt,
sfdt22_emulator_verify.py, the hosted SFDT22 workflow, and this HF20 contract
and documentation. Previous HF13/HF15/HF19 contracts are retained.
All five real Android journeys, seven screenshots and cold process restart
remain mandatory. Local offline tests cannot claim API36 pass. No application
storage or domain logic changes have been made. PR #15 remains unmerged.
