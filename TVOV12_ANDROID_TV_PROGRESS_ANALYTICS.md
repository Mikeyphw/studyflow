# TVOV12 — Android TV Progress and analytics

TVOV12 implements the Android TV Progress and analytics surface as a real source-backed TV feature.

Delivered contracts:

- structured weekly progress snapshot with exam name, week label, metrics, subjects and weak-subject summary;
- TV metrics for weekly progress, accuracy, question volume and readiness;
- weak-subject cards with recommended next TV destination;
- remote and paired-phone actions for Today, Practice, Reviews and Mock Exam;
- paired-phone command generation through shared TV navigation/control protocol;
- cast-state generation through `StudyFlowTvCastState` using the `PROGRESS` surface;
- D-pad focus items for metric cards, subject cards and progress actions;
- native shell rendering inside `StudyFlowTvActivity`;
- unit tests and `verifyStudyFlowTvProgressAnalytics`.

Dependency boundary: no Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, `core:data`, `core:database`, or phone feature-module fanout is added to `:app-tv`.
