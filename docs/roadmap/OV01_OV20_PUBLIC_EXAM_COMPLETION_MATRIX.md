# OV01–OV20 Public-Exam / BB Pre-Edital Completion Matrix

This matrix records the post-CPOV product-specialization sequence. It does not replace the older
CPOV00–CPOV13 architecture matrix; the overlays below reuse those same target, syllabus, schedule,
practice, review, analytics, portability, and legislation engines.

| Overlay | Delivered outcome | Persistent source of truth |
|---|---|---|
| OV01 | Today timing and topic-aware priority | Existing schedule/session graph |
| OV02 | Evidence-aware readiness | Derived from existing analytics evidence |
| OV03 | Responsive syllabus and schedule | Existing syllabus/schedule graph |
| OV04 | Workspace navigation and Progress hub | Existing navigation/workspace state |
| OV05 | Truthful metric presentation | Derived analytics only |
| OV06 | Schedule intelligence | Existing schedule engine |
| OV07 | Portable `.studyplan` resources | Existing portability package |
| OV08 | Import diff and rollback preview | Existing import/revision pipeline |
| OV09 | Pre-edital cockpit and date-source truthfulness | Existing StudyTarget + reference revisions |
| OV10 | Native BB Agente Comercial pre-edital preset | Existing onboarding/target/stages/rules |
| OV11 | Dedicated Redação workspace | Existing StudySession notes/history |
| OV12 | BB reference mock blueprint | Existing ExamBlueprint / practice engine |
| OV13 | Activity-type deep links | Existing navigation destinations |
| OV14 | Phase-based planning | Existing StudyStrategy configuration |
| OV15 | Reference freshness/provenance | Existing StudyMaterial tags/history |
| OV16 | Adaptive question quota planner | Derived from phases + existing evidence |
| OV17 | Today/week/next-week/calendar schedule views | Existing schedule graph |
| OV18 | Date/coverage/Study-Cycle correctness seal | Existing StudyTarget/goals/strategy graph |
| OV19 | Promise audit: manual scheduling, strategy access, search/past readability, dashboard cleanup | Existing engines only |
| OV20 | Integrated source/build validation seal, plural-warning cleanup, and release-evidence boundary | No new product persistence |

## BB reference truths retained

- The BB 2022/001 reference supplies objective point weights, not trustworthy per-subject objective
  item counts for this specialization. OV12 therefore labels its 70-question subject allocation as
  derived from the supplied point weights rather than official item counts.
- Redação remains a separate eliminatory reference stage with the supplied 70/100 minimum.
- The 2026 plan is pre-edital: expected dates never become an official countdown.
- Unknown incidence, cutoff, question-bank evidence, or official future exam data stays unknown.

## Release boundary

OV20 source/static verification proves repository contracts only. Ordinary Devtool validation keeps
Release compilation/resources/tests/lint but packages only the Debug APK, preventing uncredentialed
`app-release-unsigned.apk` output from being mistaken for release evidence. Signed Release APK/AAB
packaging remains isolated in the fail-closed external-credential release gate. Devtool/Gradle
target-machine results are recorded separately. Connected/instrumented and manual device acceptance
remain conditional on a usable device and must never be inferred from source verification.
