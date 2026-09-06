# OV07 Portable `.studyplan` Resources Acceptance

OV07 closes the remaining complete-package resource gap without introducing a new database schema.

- Full exports now select `StudyPlanGraphDuplicator.PortabilityTableSpecs`, so `annexes` metadata is actually present when requested.
- Imported-copy annex bytes remain checksum-verified archive entries under `annex-payloads/`.
- Import preview exposes each packaged file's filename, MIME type, SHA-256 checksum, size, and deterministic app-side open-handler class.
- Handler routing covers PDF/DOC/DOCX, text/Markdown, images, audio, video, and generic files.
- Existing question, material, activity, exam-target/stage/revision, subject, and topic tables remain part of FULL portability.
- Linked external content URIs are not copied as bytes; only app-owned imported copies are portable payloads.

The package remains schema version 1: handler classification is derived from MIME metadata already carried by the archive and does not alter serialized package compatibility.
