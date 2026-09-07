# WEB-W16/W17 — Concurso Público and content modes

## W16 — Full Concurso Público workflow

W16 completes the browser-side public-exam workflow without creating a parallel persistence model. A plan remains the ownership root and `Plan.configuration.publicExam` carries versioned exam-specific configuration while subjects, topics, questions, reviews, sessions, materials and evidence remain canonical StudyFlow entities.

### Target and edital

A Concurso target records organization, role/cargo, banca, edital reference, openings, estimated cutoff, stages and an exam date whose status is explicitly `confirmed`, `expected` or `unknown`. Organization, role and banca are validated as one truthful identity: partial target identities are rejected instead of guessed.

Edict text is normalized through preview-before-apply. Preview is non-mutating and revision-bound. Apply records revision provenance, creates or reactivates recognized canonical subjects/topics, archives content removed by the new revision, and refuses stale previews. Historical evidence and sessions are never deleted by edital reconciliation.

### Banca, legislation and mock exams

Questions can carry board, year, organization, role, stage, authorship, edital revision, external reference and points metadata. Filters use only supplied metadata.

Legislation records are local plan configuration and do not imply mastery or review evidence by themselves.

Mock Exam Studio uses an explicit blueprint with duration, question supply and scoring policy. Supported scoring includes no-penalty, fixed-negative, right-minus-wrong and weighted policies. Preview refuses insufficient question supply; unanswered questions are neutral. A completed mock creates evidence only from the submitted answers.

The Concurso dashboard derives coverage, graded accuracy, review backlog and mock/cutoff information from persisted data. Cutoff gap remains `No data` until both an estimated cutoff and a completed mock result exist.

### Final stretch

Confirmed exam dates enable staged 30/15/7/2/1-day guidance. Expected or unknown dates do not masquerade as a final-stretch countdown. New-content allocation falls as the confirmed exam approaches.

## W17 — Additional study modes

The same plan configuration now supports the remaining StudyFlow modes without changing the repository or evidence model.

### Programming course

Programming plans track language/level, exercises and projects. Exercises support prompt, starter code, expected output, hints, solution notes, difficulty and completion state. Web execution is explicitly `browser-only`; W17 does not expose arbitrary Termux/shell execution. Attempts/completions become evidence only after explicit user action.

### Language deck

Language plans store expressions/idioms with meaning, literal translation, region, register, pronunciation and common-mistake metadata. Six review directions are available: recognition, production, fill-in-the-blank, natural usage, mistake spotting and sentence creation. Sentence creation is deliberately marked for manual review rather than being auto-graded as semantic truth.

### Reading study

Reading plans track explicit units such as pages or chapters. Progress evidence is created only when the user records progress; opening content never counts as completion.

### General skill

General-skill plans contain drills with a named metric, unit and optional target. Results are evidence only when explicitly recorded.

### Shared analytics and portability

Mode-aware analytics derive only from persisted mode configuration and evidence. Plan configuration is part of schema v5 and therefore follows the existing IndexedDB, localStorage migration fallback and StudyFlow portable-package path. W16/W17 do not introduce a backend, account, telemetry service or second storage engine.

## State migration

The current StudyFlow state schema is v5. Existing v4 state is migrated by adding normalized mode configuration while preserving all canonical entities and history. Legacy localStorage migration continues to recognize the v4 key before older v3/v2/v1 keys.

## Next phase

W18 hardens accessibility, testing, offline behavior and storage recovery. W19 remains the StudyFlow Local 1.0 release seal.
