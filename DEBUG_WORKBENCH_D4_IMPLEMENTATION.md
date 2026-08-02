# Debug Workbench D4: Reminder and Notification Lab

D4 adds a deterministic reminder and notification laboratory to the Settings-only Debug Workbench.
It reads the default plan through production repositories, invokes the production `StudyReminderPlanner`,
and reports only redacted machine evidence: candidate type, times, window/grace values, request code,
quiet-hour suppression, and whether the exact PendingIntent identity currently exists.

The lab does not enumerate Android alarms, does not request exact-alarm access, and does not claim that a
PendingIntent identity proves the system retained a trigger time. Reconciliation and posting a content-free
diagnostic notification are explicit visible-side-effect actions.

D4 r2 seal: fixes the BackupRestoreDrillSeal restore dry-run warning by replacing the nullable safe-call expression with an explicit null guard, and hardens the D4 verifier to reject the stale safe-call pattern.

