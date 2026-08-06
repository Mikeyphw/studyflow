# ADR-0031: Weakness drills and question import

## Status

Accepted.

## Context

StudyFlow already tracks questions, tests, mock exams, reviews, and wrong-answer remediation. The next study-coach step is to transform that evidence into targeted practice, and to let users bring offline question banks into the app without a cloud dependency.

## Decision

Weakness drills are generated in `core:domain` from repository-backed questions, wrong-answer remediations, and due reviews. The generator ranks exact missed questions first, then topic and subject matches, and returns an explicit empty reason when it cannot safely build a drill.

Question import is also a pure domain pipeline. It supports CSV, TSV/Anki-style rows, and Markdown blocks, then detects existing and intra-import duplicates before `ManageStudyPractice` saves accepted rows as normal questions.

## Consequences

The implementation avoids a new database schema, avoids cloud/file dependencies, and keeps all persistence through the existing practice repository. UI strings remain localized in `feature:workspace` resources.


## Audit refinement

Recurrence strength is a priority boost only after a question matches the exact missed question, topic, or subject driver. It must not make unrelated questions eligible. Optionless TSV/Anki rows are imported as short-answer questions so the importer does not force flashcard-style rows through the multiple-choice option validator.
