# Debug Workbench D5: Database and Portability Lab

D5 adds a read-only database health inspector and non-destructive backup/restore drills. The database lab
opens only the app-private StudyFlow database in read-only mode and reports aggregate evidence from
`PRAGMA user_version`, `PRAGMA quick_check`, `PRAGMA foreign_key_check`, table counts, row counts, metadata,
and the declared Room migration chain. It never exports rows, names, notes, answers, URIs, or paths.

The portability lab calls the production `BackupRestoreDrillSeal`: encrypted export, wrong-password rejection,
tamper detection, private annex metadata, zero-write restore dry-run, retention truthfulness, and recovery report.
Every drill is required by the public model to be non-destructive.
