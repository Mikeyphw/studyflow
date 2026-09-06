# WEB-W01 — Semantic mobile-first shell

W01 establishes the canonical StudyFlow Local information architecture in semantic HTML without styling or JavaScript.

## Delivered

- mobile viewport and document metadata;
- semantic header/main/section/article/nav hierarchy;
- Today, Plan, Practice and Progress primary destinations;
- Plan substructure: Overview, Schedule, Content, Library;
- Practice substructure: Reviews, Questions, Tests, Errors;
- Progress substructure: Summary, Performance, Goals, Tools;
- native anchor navigation whose targets all exist;
- explicit no-dead-control rule: features that need JavaScript are described rather than exposed as non-functional buttons;
- W01 contract tests and validation integrated into npm build/lint.

## Intentionally deferred

- **W02:** CSS/design system and responsive presentation;
- **W03:** JavaScript state, tabs, dialogs, forms, timer and dynamic navigation;
- later phases: database/PWA/TypeScript/React.

The shell is mobile-first structurally because the mobile viewport and four-destination hierarchy are the baseline; W02 owns the concrete 360–430px acceptance rules.


The repository Devtool target is a pre-existing Node baseline and remains untouched by WEB-W00/WEB-W01. Restore is owned by Devtool's Node/npm runner; W01 only advances the package scripts from contract-only checks to the integrated semantic-shell gate.
