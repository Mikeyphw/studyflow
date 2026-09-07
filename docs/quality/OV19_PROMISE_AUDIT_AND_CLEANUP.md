# OV19 — Promise audit and cleanup

This overlay audits the literal `REVIEW_SCREENSHOTS_E_BACKLOG.md` promises and the retained OV01–OV18 acceptance contracts against the post-OV18 repository.

## Gaps found and fixed

- P0 manual activity creation now persists subject/topic plus explicit local date/time.
- P0 weekly recurring blocks now persist the selected subject instead of always `null`.
- P1 Schedule exposes all seven `StudyStrategyType` values instead of `entries.take(4)`.
- P1 Schedule adds title/subject/topic search and keeps past Calendar entries collapsed until explicitly requested.
- P1 concurso dashboard removes the raw debug-style matrix line and repeated unknown placeholders from the default summary; subject cards expose known evidence and the detailed importance/mastery matrix is opt-in.
- Removes the OV18 unnecessary safe-call compiler warning in `PublicExamDateResolver`.

## Audit results already satisfied before OV19

- Today distinguishes today versus future scheduled activity.
- Topic priority does not duplicate subject-level session evidence into every topic.
- Shell labels are short Today / Plan / Practice / Progress equivalents.
- Canonical exam-date resolver, truthful topic-count coverage goal, and Study Cycle strategy gating are present.
- BB preset, pre-edital cockpit, writing workspace, BB mock blueprint, typed deep-links, phases, reference freshness, question quota planner and readable schedule are present.

## Truthfulness exceptions

The supplied BB plan source provides point weights and the essay minimum, but not an official per-subject objective-question count or an objective cutoff. StudyFlow therefore keeps derived question allocation explicitly labeled as derived and does not invent an objective minimum.

## OV19 v5 compatibility seal

- Schedule day-label localization stays Compose-observable through `LocalConfiguration`, while guarding `Configuration.locales` behind API 24 and using the deprecated `Configuration.locale` fallback only on API 23.
- The three new pt-BR plural resources define `many` with the same wording as `other`, satisfying Android locale quantity coverage without changing user-visible semantics.

## v6 locale-observation correction

Focused workspace lint exposed a second pre-existing non-observable locale read in
`TodayWorkspaceScreen.activityMetadata`. OV19 v6 makes all activity metadata date/time
formatting observe `LocalConfiguration.current`, uses the API 24+ `locales[0]` branch,
and retains the API 23 `configuration.locale` fallback. The verifier now rejects
`Locale.getDefault()` in both schedule and Today workspace formatting paths.
