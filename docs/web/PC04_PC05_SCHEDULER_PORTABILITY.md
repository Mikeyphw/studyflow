# PC04 / PC05 — scheduler and portable ownership closure

## PC04 — full scheduling intelligence

The adaptive scheduler remains preview-first. It now reads plan-level daily capacity, optional weekday availability windows and fixed commitments. Existing planned work consumes capacity; user-locked activities are immutable; overdue locked work is reported instead of moved. The preview reports overlap conflicts, overloaded days and work that could not fit in the requested horizon.

Missed unlocked activities and unscheduled backlog are considered before new cycle suggestions. Due reviews remain evidence-backed, while cycle suggestions begin from the persisted per-plan cursor and prefer high-priority active topics. When clock windows exist, the scheduler assigns a non-overlapping start minute around fixed commitments. When only capacity is configured, the date is scheduled without fabricating a clock time.

Applying the preview may only add or move planned work. Completed/cancelled history is never rewritten. Every applied adaptive placement stores reason, preview timestamp and base revision provenance. A forged preview cannot move a locked activity.

## PC05 — portable package v2

Portable v1 remains readable and can still be exported as a state-only compatibility file. Portable **v2** is the new full-ownership format used by the main Export action.

A v2 JSON package contains:

- complete structured StudyFlow state with local attachment references preserved;
- attachment bytes encoded in the local package;
- attachment name, MIME type, byte size and local metadata;
- SHA-256 for the structured payload and every attachment;
- manifest counts, attachment count/reference count and total bytes.

Parsing v2 verifies all checksums and sizes before an import preview is created. Missing bytes, duplicate IDs, count mismatches, payload tampering and attachment tampering fail closed.

Two preview-first import modes exist:

1. **Replace local state** — replaces structured state and attachment storage together. The attachment repository is snapshotted first so a failed apply can restore the previous local bytes.
2. **Import active package plan as new** — appends the package's active plan to the current workspace, remaps global entity/reference IDs and attachment IDs, keeps the current plan/data, and activates the imported plan.

Both modes remain revision guarded. A stale preview writes neither state nor attachment bytes.

The structured state schema remains **v6**; the portable format advances independently to **v2**.
