# StudyFlow final acceptance matrix

This matrix maps the completed roadmap to the new source-level final acceptance seal.

| Area | Source seal | Target-machine gate |
| --- | --- | --- |
| Startup and route graph | `verifyFinalAppAcceptanceSeal` checks V2 Today startup, typed roots, legacy redirects, picker, quick-add, system hub, and onboarding route wiring. | Devtool runs Android compile, assemble, release startup smoke, and back navigation checks. |
| Today/Plan/Practice/Progress | The seal rejects decorative empty actions and requires concrete workspace callbacks, search, and system access. | Real device checks compact portrait, 200% font, TalkBack traversal, and scrolling sanity. |
| Readiness/reschedule/drills/imports/mocks | The seal checks readiness signals, adaptive-reschedule preview/apply wiring, report/coach reschedule inputs, domain engines, and V2 entry points. | Devtool executes unit tests and Android UI/resource compilation. |
| Exam runbook / seven-day sprint | The Phase 63/64 seal checks ownership, prioritization, preview freshness, mutation envelope, CAS apply, retry, and localization. | The Workspace connected suite exercises runbook/sprint plus the version-bound apply boundary. The signed Release journey requires explicit Phase 63/64 instrumentation markers, applies the sprint, exports/restores the plan, restarts the process, and verifies the sprint mutation remains durable. |
| Widget and reminders | The seal checks widget route targets, `app:tint`, reminder diagnostics, launch routing, blocked/degraded/action-needed/ready states, exact-alarm availability, battery optimization, and notification permission inputs. | Device pass checks widget appearance, tap behavior, notification permission states, exact-alarm and battery-degraded states. |
| Evidence/coach/cleanup/report | The seal checks Evidence Timeline inputs, weekly coach, cleanup issue kinds, preview-only cleanup, and local HTML/PDF report export. | Device pass checks report share sheets, local FileProvider access, and export results. |
| Backup/export/import | The seal checks full export options, inspect/dry-run flow, and payload coverage for V2 domain data. | Device pass checks actual backup creation, inspect, dry-run restore, and upgrade from pre-V2 database. |
| Localization/warnings | The seal checks principal English/pt-BR resource parity, duplicate string/plurals names, XML parsing, warning sentries, and stack versions. | Devtool lint remains the authority for diagnostics count and release/debug lint reports. |

PENDING TARGET EXECUTION: this file is a source acceptance matrix, not device evidence. Devtool must still run the final automated signed-artifact/connected-device gate and the manual device evidence pass before any release claim.

R2 audit closure: the final verifier explicitly covers adaptive reschedule wiring, reminder degraded-state dimensions, and duplicate resource names.

REL01 adds `scripts/run_target_release_validation.py` as an operational wrapper around the existing
signed-artifact, connected-suite, restore/restart, manual-device, and `verifyFinalReleaseVerdict`
evidence chain. The matrix remains fail-closed: the runner may report `PENDING_TARGET_EXECUTION` or
`BLOCKED`, but it must not replace target evidence with a synthetic PASS.

