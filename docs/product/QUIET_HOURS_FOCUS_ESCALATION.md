# Quiet hours, focus profiles, and escalation

ATT10 is StudyFlow's user-facing interruption policy for the unified Attention system. The full implementation contract and verifier are documented in `ATT10_QUIET_HOURS_FOCUS_PROFILES_ESCALATION_POLICY.md` at repository root.

## What users can control

- A focus profile: Balanced, Exam mode, Work mode, Sleep mode, Travel/offline, or Minimal/TV.
- A timezone-aware quiet interval, including overnight ranges such as 22:00–07:00.
- Whether high-priority and time-sensitive attention can bypass quiet hours.
- Category-specific behavior for study blocks, reviews, missed work, summaries, session events, exam countdowns, schedule changes, warnings, and sync attention.

Category behavior can be normal, silent, defer during quiet hours, compact-only, route to phone, mirror if unhandled, or require explicit user action. These settings alter delivery, not canonical reminder identity.

## What StudyFlow records

Every evaluated decision can be attached to the ATT01 ledger entry as `policyAudit`. The audit contains the focus profile, explicit outcome, evaluation time, optional defer-until time, and a short explanation. This gives ATT11 a stable source for reliability evidence without creating a parallel reminder database.

## Cross-device rules

ATT07 still chooses the owner and mirror targets. ATT10 may narrow that set, prefer a phone, force compact-capable surfaces, or allow a bounded mirror after an item remains unhandled. It never creates broadcast storms. Duplicate/handled ledger entries are suppressed before escalation rules are considered.

TV policy controls are intentionally read-only or remote-safe and remain foreground-only. Android TV and Web TV are foreground presentation clients and do not claim background alarms. Web similarly distinguishes foreground scanning from optional Push capability and does not promise closed-browser alarms.

## Troubleshooting

If a reminder appears later than expected, inspect its ATT01 `policyAudit` outcome first. `DEFER_UNTIL_QUIET_END` should include a target timestamp. If an item is visible only as a widget/badge/tray projection, check for `COMPACT_ONLY` or `SILENT_DELIVERY`. If TV does not own a reminder, a Minimal/TV or explicit route-to-phone decision is expected. If a mirror does not appear, remember that both ATT10 and ATT07 mirror caps must permit it.

### Upgrade and legacy quiet-hours compatibility

ATT10 does **not** invent a 22:00–07:00 quiet window on upgrade. A new focus policy starts with quiet hours off unless the existing Study Plan already has a configured quiet-hours interval. Android migrates and saves the legacy interval when ATT10 is first loaded; its Reminders screen saves the legacy planner fields and ATT10 policy together. Desktop performs the same one-time migration and updates the legacy planner fields when its ATT10 quiet-hours toggle changes. This prevents the pre-ATT10 scheduler and the ATT10 delivery evaluator from becoming two independent quiet-hours truths.

