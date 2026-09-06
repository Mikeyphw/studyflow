# BH08 Late Reminder Reconciliation Acceptance

BH08 closes the remaining boundary case between the domain late-delivery grace and Android alarm reconciliation.

## Problem closed

A planned reminder can still be valid near the end of the two-hour late-delivery grace. Android installs it with a minimum future delay and an inexact `setWindow` delivery window. A later reconciliation could cross the normal freshness boundary, drop that still-installed durable occurrence, cancel its alarm, and recreate unchanged alarms in a way that slides delivery again.

## Contracts

- A currently scheduled planned occurrence gets a bounded reconciliation allowance only until its original `fireAt` plus the normal late-delivery grace, the coordinator minimum-future delay, and the inexact alarm delivery window.
- The allowance is keyed to the exact durable occurrence ID. It cannot resurrect a completed activity, a delivered occurrence, or an activity rescheduled to a new occurrence identity.
- Reconciliation keeps an unchanged scheduled alarm installed instead of canceling/recreating it.
- Obsolete or materially changed occurrences are still canceled.
- Desired bookkeeping is replaced before newly required alarms are installed, preserving restart recovery semantics.
- Outside the bounded allowance, the existing normal late-delivery suppression remains unchanged.

## Validation

```sh
python3 scripts/verify_bh08_late_reminder_reconciliation.py
./gradlew :core:domain:test :app:testReleaseUnitTest --no-daemon
```

The focused domain regressions cover bounded survival, completed/delivered suppression, and exact occurrence identity after rescheduling. App unit tests compile the coordinator and retain the existing OV09 bookkeeping-order contract.
