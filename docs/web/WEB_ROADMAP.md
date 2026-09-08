# StudyFlow Local Web roadmap

| Phase | Milestone |
| --- | --- |
| W00 | Product/architecture contract + adopt preconfigured Devtool web target |
| W01 | Semantic mobile-first HTML shell |
| W02 | Mobile-first CSS/design system |
| W03 | Vanilla DOM interactions/navigation/forms |
| W04 | Application state + temporary persistence |
| W05 | Core study domain model |
| W06 | Today + Focus loop |
| W07 | Plan Content + import preview |
| W08 | Schedule intelligence + adaptive preview |
| W09 | Practice/questions/reviews/error notebook |
| W10 | Progress/evidence/goals/tools + no-data truthfulness |
| W11 | Portable import/export package |
| W12 | IndexedDB + attachment storage |
| W13 | Installable offline PWA |
| W14 | TypeScript migration |
| W15 | React UI migration |
| W16 | Full Concurso Público workflow + planned improvements — delivered |
| W17 | Programming/language/reading/general modes — delivered |
| W18 | Accessibility/testing/offline/storage hardening — delivered |
| W19 | StudyFlow Local 1.0 release seal — delivered |

Each phase must preserve mobile-first behavior, local ownership, truthful evidence semantics and framework-independent domain rules.

## Post-W19 parity closure

- PC00 | Mock scoring correctness — delivered: weighted scoring now honors question points and section weights, penalty modes are value-aware, unanswered questions remain neutral, and score evidence includes section/question breakdowns.
- PC01 | Configurable review progression — delivered: review intervals are actionable, persisted review stages advance through the configured policy, failed reviews reset progression, and successor scheduling is duplicate-safe.

- PC02 | Generic Plan Content import — delivered: TXT/Markdown/CSV/TSV/JSON parse/normalize/validate/preview/diff/apply, merge/reconcile modes, and import-as-new-plan.
- PC03 | Plan lifecycle + nested topics — delivered: create/rename/switch/duplicate/archive/restore/safe-delete, nested topic hierarchy, and per-plan study-cycle persistence.
- PC04 | Full scheduler/conflicts/locks/overload/rescheduling — delivered: capacity/windows/fixed commitments, user locks, explicit conflicts/overload/unscheduled work, missed-session recovery, cursor-aware adaptive placement and provenance.
- PC05 | Portable package v2 — delivered: full attachment bytes + MIME/name/size metadata + SHA-256 verification, v1 compatibility, rollback-aware restore, and import-active-plan-as-new with ID remapping.

| PC06 | Practice parity: question import/filtering/saved sets/weakness drills — delivered |
| PC07 | Full Concurso Público parity closure — delivered |

| PC08 | Programming + Language content-mode parity closure — delivered |
| PC09 | Weekly coach/readiness/cleanup/report/runbook/sprint parity — delivered |
| PC10 | Product shell parity: search/settings/privacy/i18n/onboarding — delivered |
| PC11 | Self-contained production artifact + parity-closure release seal — delivered |
| PC12 | Cockpit/reference/phase foundation (OV09 + OV14 + OV15) — delivered |
| PC13 | BB Agente Comercial preset + Writing Workspace + activity deep links (OV10 + OV11 + OV13) — delivered |
| PC14 | Multi-signal readiness + subject quota + unified Evidence Timeline + local readable schedule (Phase 48/55 + OV16 + OV17) — delivered |
| PC15 | Intelligence refinement: coach + cleanup + source-backed report + runbook + safe 7-day sprint + onboarding blueprints (Phases 58–64) — delivered |

> Post-PC11 audit note: PC12–PC18 close the audited browser-applicable feature blocks, but PC19 supersedes the runtime-validity portion of the PC18 seal after a real-device interaction audit found main-thread starvation in localization. With PC19 applied, full applicable functional parity includes behavioral browser interaction evidence; platform-specific distribution and native Android/Termux/provider behavior remain explicit divergences.


| PC16 | Flashcards + Material Viewer parity — delivered | Persisted BASIC/CLOZE flashcards with SRS links/suspension/search/portable ownership; browser-local MIME viewer for attachments and explicit HTTP(S) resources. |

| PC17 | OV18 + OV19 correctness/promise cleanup parity — delivered (including the dark-only shell contract) | Canonical official-date resolver, truthful topic-count coverage goals, seven persisted strategies, Study Cycle gating, manual/recurring subject-topic ownership, calendar search/past collapse and dashboard evidence cleanup. |

| PC18 | Encrypted portable backup + final applicable parity seal — delivered (dark-only) | Android-compatible WebCrypto envelope, local Today correctness, confirmed-exam proximity, generic Mock Studio source/tag/disputed/cutoff controls, and Banco do Brasil 70-question reference mock gating. |


| PC19 | Interaction runtime audit/hotfix — delivered (dark-only) | Idempotent dynamic localization, browser liveness/interaction smoke, content-addressed PWA cache identity, dark install manifest and corrected encrypted-backup copy. |


## TV-browser adapter roadmap

- **WTV00 — TV-browser compatibility baseline — delivered.** Explicit `?tv=1` / `?tv=0` overrides, conservative Smart-TV user-agent detection, runtime capability flags, and TV shell tokens without forking StudyFlow state/domain logic.
- **WTV01 — LAN TV server — delivered.** Dependency-free Node static server, `0.0.0.0` binding, printed LAN addresses, `/tv` redirect, and normal loopback development preserved.
- **WTV02 — older Smart-TV runtime compatibility — delivered.** TypeScript output downleveled to ES2019 and IndexedDB state cloning no longer requires `structuredClone`.
- **WTV03 — deterministic D-pad spatial navigation — delivered.** Page-wide Arrow navigation, modal focus scoping, native input/select arrow ownership, existing tablist semantics preservation, and deterministic geometry scoring.
- **WTV04 — 10-foot visual polish — delivered.** Larger metric/hero hierarchy, readable line lengths, stronger rail/tab hierarchy, focused-control scale cues, 720p/1080p-friendly density, TV modal sizing, and material/question presentation.
- **WTV05 — TV interaction simplification — delivered.** Remote-first Plan/Practice/Progress/Settings surfaces preserve viewing and low-input actions while moving authoring, imports, backup passwords, file selection, destructive state reset and typed grading to phone/computer.
- **WTV06 — TV Back/history semantics — delivered.** TV screens plus Search, Settings and Material Viewer use explicit History API entries; PopState restores UI state and Back unwinds overlays before leaving the current surface. BrowserBack/GoBack plus common TV key codes are handled when exposed by the browser.
- **WTV07 — phone-controlled browser receiver — delivered.** Protocol-v1 TypeScript mirrors the `core:tvcontrol` surfaces/actions/cast state, the LAN server provides short-lived six-digit pairing plus revocable in-memory controller tokens, SSE with long-poll fallback, a `/remote` phone controller, and presentation-state/control relay without database-row transport.
- **WTV08 — dedicated TV Study Session — delivered.** `STUDY_SESSION` now has a TV-only History API surface with canonical focus start/pause/resume/complete controls, deterministic presentation segments, large timer/progress UI, phone-command bindings and common media Play/Pause key handling.
- **WTV09 — dedicated TV Question Set — delivered.** `QUESTION_SET` now has a TV-only D-pad answer-choice surface with persisted canonical grading/evidence, explanation reveal, previous/next/completion, phone A–D shortcuts, and typed-answer fallback to phone/computer.
- **WTV10 — dedicated TV Mock Exam Studio — delivered.** `MOCK_EXAM` now has a TV-only History API surface that freezes the active plan's saved mock blueprint, runs a pausable countdown, supports D-pad choice selection, previous/next, mark-for-review, guarded Back, submit confirmation, canonical `recordMockExam` persistence and score/result review. Phone commands reuse protocol-v1 START/PAUSE/RESUME/NEXT/PREVIOUS/SELECT_ANSWER/MARK_FOR_REVIEW/SUBMIT/REVIEW_RESULTS/BACK.
- **WTV11 — dedicated TV Review + Error Notebook — delivered.** `REVIEW` now has a TV-only review queue and Error Notebook surface with large prompt/answer presentation, Again/Hard/Good/Easy grading through canonical review scheduling, deterministic next-item focus, D-pad category reclassification, error resolution and phone-control parity through protocol-v1 action + option identifiers.
- **WTV12 — dedicated TV Progress & analytics — delivered.** The TV `PROGRESS` surface now presents evidence-backed weekly study minutes, seven-day question volume/accuracy, readiness, coverage, review health and per-subject weakness signals with deterministic next-flow actions. Unknown metrics remain `No data`, and the surface is navigation-only rather than an evidence/goal mutation workspace.
- **WTV13 — dedicated read-only TV Schedule — delivered.** `SCHEDULE` now has its own `#schedule` History surface with Today/This week modes, canonical date/time ordering, planned/completed/cancelled summaries, selected-item Previous/Next/Start controls, activity routing into existing TV study flows and explicit phone/computer handoff for writing. No schedule mutation API is exposed on TV.
