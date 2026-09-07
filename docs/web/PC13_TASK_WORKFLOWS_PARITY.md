# PC13 — BB preset, Writing Workspace, and activity deep links

PC13 closes the next audited browser-applicable parity block: **OV10 + OV11 + OV13**.

## OV10 — BB Agente Comercial pre-edital preset

`BB_AGENTE_COMERCIAL_PRE_EDITAL` is a first-class onboarding and in-product preset. Provisioning is one canonical state mutation and creates a new Concurso plan with the historical **Seleção Externa 2022/001** reference, organization **Banco do Brasil**, role **Escriturário — Agente Comercial**, board **Fundação Cesgranrio**, and **no fabricated confirmed or expected exam date**.

The preset creates exactly eight objective subjects with weights **15 / 5 / 7.5 / 5 / 7.5 / 15 / 22.5 / 22.5 = 100 points**, an objective reference stage of **70 questions / 100 points**, a separate essay stage with **70/100 minimum**, weekly goals of **19 study hours**, **80% accuracy**, and **1 writing session**, plus **1/7/30** review intervals. The supplied Monday–Saturday reference rhythm totals **1,140 scheduled minutes**; this remains distinct from the 19-hour weekly goal.

No topics or incidence values are invented. The 2022 notice is recorded as historical reference provenance only.

## OV11 — Writing Workspace

A `WRITING` activity opens a canonical writing focus session. The draft is stored durably in the existing versioned `StudySession.note` payload; PC13 does not introduce a second writing database.

The workspace provides a multiline prompt/draft, five **0–20** rubric dimensions (theme/task, text type, cohesion, argumentation, standard language), four mandatory completion checks, a **0–100** total, weekly writing progress, and writing history. The generic Focus completion path refuses to complete a writing session; completion must pass the Writing Workspace checklist/rubric flow and produces canonical session/evidence records.

## OV13 — Activity deep links

Routing is driven by the explicit `StudyActivityType`, never by title text:

- `MOCK_EXAM` → existing Practice / Tests studio.
- `QUESTIONS` and `FLASHCARDS` → Practice / Questions with subject/topic context preserved.
- `REVIEW` → Practice / Reviews, selecting the matching due review when available.
- `WRITING` → Writing Workspace.
- `THEORY`, `VIDEO`, `READING`, and `CUSTOM` → regular Focus.

Topic context is narrower than subject context and never broadens to unrelated topics. Legacy activities without an explicit type migrate deterministically from their legacy `kind`, not their title.

`FLASHCARDS` here only closes the **deep-link routing** contract. It does **not** claim the separate generic persisted Flashcard entity/UI parity gap is closed.
