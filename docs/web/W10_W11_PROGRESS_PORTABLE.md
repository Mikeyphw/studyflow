# WEB W10/W11 — Truthful Progress and Portable Packages

## W10 Progress

Progress is evidence-backed. `No data` means the measurement cannot yet be calculated; **measured zero** remains `0` or `0%`. Weekly study minutes come from completed sessions. Accuracy uses only graded question-attempt evidence. Topic coverage counts active topics with persisted evidence. Review health comes from due/completed review records. Readiness is withheld until at least five graded attempts and two evidenced topics exist, and its confidence is shown rather than hidden.

Goals support study minutes, graded questions, evidenced topics, and completed reviews across daily, weekly, monthly, or plan periods. Evidence Timeline is read-only history. Content Integrity reports current reference validation rather than silently repairing data.

## W11 Portable package

A package is JSON with `format = studyflow-local-package`, format version, export timestamp, manifest counts, and a validated StudyFlow state payload. The portable core is independent of DOM, browser storage, IndexedDB, and network APIs.

Import is **preview before apply**. A package is parsed and validated first, then an add/update/remove preview is generated without mutating local data. Apply uses a revision guard so a stale preview is rejected. W11 deliberately uses replace semantics: applying a package replaces the local structured state only after explicit confirmation. The UI describes this clearly.

Browser import is capped at **5 MiB**. Unknown/future package versions, malformed JSON, invalid state, or manifest-count mismatches are refused. Attachments, IndexedDB and OPFS storage are W12 work and are not pretended to exist here.
