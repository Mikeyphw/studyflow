# Debug Workbench D5: Database and Portability Lab

D5 adds a read-only database health query surface and non-destructive backup/restore drills. The Room
inspector executes only read statements against StudyFlow's existing database and reports aggregate
evidence from the schema version, `PRAGMA quick_check`, `PRAGMA foreign_key_check`, user-table count,
aggregate row count, metadata presence, and the declared Room migration chain. It does not export rows,
names, notes, answers, URIs, or paths.

The portability lab calls the production `BackupRestoreDrillSeal`: encrypted export, wrong-password
rejection, tamper detection, private annex metadata, zero-write restore dry-run, retention truthfulness,
and recovery reporting. The public diagnostic model rejects destructive drills. Cancellation remains
cancellation and fatal JVM errors are not converted into normal diagnostic failures.
