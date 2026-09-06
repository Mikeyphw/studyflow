# OV06 Schedule Intelligence Acceptance

OV06 upgrades the Plan Schedule surface without introducing a second schedule store.

- Planned activities are grouped by local day; unscheduled work is grouped separately.
- Filters cover subject, activity type, status, and provenance.
- Provenance is truthful: planner-generated activities use the durable existing `generated:` ID prefix; all other activities are manual.
- Overlapping active scheduled activities are flagged as conflicts.
- Active scheduled load above 240 minutes on one local day is flagged as overloaded.
- Bulk complete/missed/delete operates only on unlocked PLANNED or MISSED items.
- Locked, in-progress, completed, skipped, and cancelled history remains protected.
- Bulk deletion requires confirmation.
- Existing generated-week, missed-rebalance, study-cycle, and per-item actions remain available.
- Existing adaptive/Study Sprint preview-apply flows remain the canonical proposal mechanism; OV06 does not create a parallel mutation engine.
