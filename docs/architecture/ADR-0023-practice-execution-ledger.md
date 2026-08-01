# ADR-0023: Practice execution ledger

## Decision

StudyFlow stores practice/mock execution state in the existing `configurationJson` payload on
`StudyTest` and `MockExam` records. The payload contains ordered question IDs plus answer records.

## Rationale

The Phase 2 schema already included tests and mocks. Reusing their payload keeps Phase 19
schema-neutral while enabling persisted execution, resume, and summary behavior.

## Consequences

A later richer attempt-history phase may add dedicated attempt/answer tables. Until then, the ledger is
small, deterministic, and owned by the domain `PracticeAttemptCodec`.
