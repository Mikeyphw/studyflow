# OV11 — Writing Workspace acceptance

OV11 specializes the existing durable `StudySession` flow for `WRITING` rather than creating a second writing database.

Acceptance requires:

- a multiline writing editor backed by the current session;
- durable draft saves in a versioned `StudySession.notes` payload;
- a five-dimension 0–20 rubric totaling 0–100;
- theme/task, text type, cohesion, argumentation, and standard-language dimensions;
- a mandatory pre-finish checklist;
- completed writing-session history and BB weekly 1-session progress;
- the generic session Finish action cannot bypass writing validation;
- bilingual English / pt-BR resources;
- existing session completion remains the canonical activity/history mutation path.
