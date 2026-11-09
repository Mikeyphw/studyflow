# SFDT22 HF13 — trustworthy topic persistence and exact-card expansion

Base PR #15 SHA: `cf6d116c8c81283581138760061aae1d1b33a1a8`.
Failure: [hosted run 42497613558](https://github.com/Mikeyphw/studyflow/actions/runs/42497613558),
`subjects / topic_confirm / topic_row_missing`, first journey passed.

## Evidence versus hypothesis

Confirmed: `TopicNodeRow` is composed only when its subject is expanded;
`expandedSubjectIds` defaults to empty; a generic "Expand" click never proves
which card received that action. The save callback closes the form and then
schedules the real asynchronous `content.saveTopic` call. The evidence neither
proves successful persistence nor proves a broken production database.

HF13 adds a conditional, invisible test tag to the **production** subject-card
expand/collapse button, leaving its real action, layout, and app persistence
unchanged. The instrumented test finds that button under the *saved subject's*
unique card, checks whether it is already expanded, taps **once** only when
collapsed, and requires the collapse tag to appear before observing its topic
row. The same idempotent routine runs after cold restart. No mock, fixture,
direct DAO write, or bypass is introduced.

The test also observes the actual success/failure Snackbar immediately after
saving. A real failure Snackbar is fatal. A transient success Snackbar is
corroborating evidence but **never** substitutes for the real tagged topic row;
missing a transient Snackbar alone does not pass or fail the test. Diagnostics
are allowlisted (`topic_save_failed`, `subject_expand_action_missing`,
`subject_not_expanded`, `topic_row_not_composed`, `topic_row_missing`) and do
not include arbitrary user-content strings. `topic_row_not_composed` means a
success signal was observed but the row was not in the Compose tree after the
parent was confirmed expanded; it does not conclusively prove why.

## Acceptance

The HF01–HF12 source checks remain intact. HF13 adds independent source and
negative diagnostic checks and new exact source pins. Hosted API36 must still
run **five actual production UI journeys**, prove process death and new PID,
and produce seven distinct hashed PNG screenshots. Neither source tests nor a
passing Android debug emulator qualifies a release or physical ARM64 target.

## Follow-up

Inspect the next SFDT22 evidence before fixing any later Session/Practice/
Reviews/Restart failures. Do not merge PR #15 until the entire hosted gate is
successful. Separately investigate native Devtool packaging on main run
42424780036; PR Devtool success does not resolve a different main job.
