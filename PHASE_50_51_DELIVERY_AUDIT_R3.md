# Phase 50-51 delivery audit r3

## Verdict

The r2 feature implementation was functionally intact, but target compilation exposed one Kotlin module-boundary defect in the Practice workspace review-title formatter.

## Root cause

`ReviewItem` is declared in another module. Its nullable `flashcardId`, `questionId`, `errorEntryId`, and `topicId` properties were read once in each `when` condition and again in the selected branch. Kotlin cannot guarantee that a public cross-module property remains unchanged between those reads, so it refuses the smart cast.

## Fix

`reviewTitle` now snapshots all four nullable identifiers into local `val`s before the `when` expression. Local immutable values are stable smart-cast subjects, preserving the existing display priority without assertions or unsafe casts.

## R3 hardening

- The Phase 50-51 verifier requires all four local snapshots.
- The verifier requires branch rendering to use the local values.
- The verifier rejects the four former direct-property smart-cast patterns.
- The complete r2 weakness-drill, importer, resources, tests, and audit fixes remain included.

## Boundary

The overlay was clean-applied and source-audited in the construction environment. Devtool remains responsible for the configured Kotlin compilation, domain and workspace unit tests, Android resource processing, APK assembly, and device checks.
