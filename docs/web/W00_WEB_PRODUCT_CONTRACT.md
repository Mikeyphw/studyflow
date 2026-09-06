# WEB-W00 — StudyFlow Local web product contract

## Product boundary

StudyFlow Local is a mobile-first, local-first study operating system that runs in a modern browser. Its normal study loop must remain useful without an account or required server. Network-backed features, if ever added, are optional extensions.

## Non-negotiable constraints

1. **Mobile first.** Design and acceptance begin at 360–430 CSS px; larger layouts are progressive enhancements.
2. **Local ownership.** Core study records, evidence, plan configuration, questions and future attachment indexes are locally controlled and portable.
3. **Offline core.** After the later PWA installation phase, the core experience must open and work without a network connection.
4. **No account dependency.** Authentication must never become a prerequisite for the local product.
5. **Truthful evidence.** Unknown/no-data states remain distinct from measured zero; confidence and provenance are visible where relevant.
6. **Preview before mutation.** Imports, adaptive reschedules and bulk plan changes use validate/preview/diff/apply flows instead of silent mutation.
7. **Framework-independent domain.** Scheduling, readiness, review and evidence algorithms do not live inside UI components.
8. **Portable by design.** Full export/import eventually includes structured records, materials/attachments, MIME metadata, checksums and versioned manifests.

## Primary information architecture

- **Today:** recommended next action, focus, due reviews, today/future activity distinction, cycle/recovery.
- **Plan:** Overview, Schedule, Content, Library.
- **Practice:** reviews, questions, tests/mocks, weakness drills and error notebook.
- **Progress:** Summary, Performance, Goals, Tools.

## Planned product improvements carried forward

The web roadmap explicitly preserves the planned StudyFlow improvements that remain meaningful locally: metric truthfulness/confidence/content-integrity semantics; schedule provenance/conflict detection/adaptive-reschedule preview; edital revisions/diffs; pre-edital cockpit; study phases; writing workflow; configurable mock blueprints; legislation verification metadata; evidence timeline; weekly coach; content cleanup; portable backups; and mode-aware programming/language/general study workflows.

Android-only mechanics such as exact alarms, ADB qualification, FileProvider and Termux execution are not web requirements. Browser-native equivalents may be added only where their guarantees can be stated accurately.

## W00 architecture

```text
web/ semantic/static surface
  ↓
future vanilla JS application/domain boundary
  ↓
future typed repository interfaces
  ↓
future IndexedDB + attachment storage
  ↓
versioned portable StudyFlow package
```

W00 intentionally adds no production web framework dependency.


## Devtool baseline

The `studyflow_web` target already exists in `.devtool.toml` before W00 and is a Node target rooted at `web/`. This overlay must not add, replace, or reformat that repository configuration. Devtool uses the Node/npm runner for restore and the package scripts for build/test/lint.
