# Phase 62: Onboarding and sample plan wizard

StudyFlow now includes a local onboarding wizard for users who do not want to begin with a blank
workspace.

## Delivered behavior

- Adds a structured domain blueprint for sample Study Plans.
- Provides four starter templates: public exam, university semester, certification bootcamp and free-study habit.
- Keeps templates preview-first, local-only and user-initiated.
- Adds a Plan-list entry point named “Start with sample”.
- Adds a Compose onboarding screen that previews subjects, topics, weekly workload, and concrete weekly schedule blocks with weekday, start time, duration, and subject.
- Creates a real Study Plan only after the user presses the create button.
- Seeds localized subject and topic names through the UI layer so domain models do not leak English prose.
- Saves starter subjects, topics, plan settings and weekly schedule rules through existing local repositories.
- Sets the sample as default only when no default plan exists yet.
- Preserves cloudless behavior: no cloud account, network sync, remote fetch or automatic import path.

## Validation

`verifyPhase62OnboardingSeal` runs `scripts/verify_phase_62.py`. The validator checks the pure domain
contract, route and UI integration, app-level user-initiated creation path, English/pt-BR resource
parity, concrete workload/schedule-block preview rendering, unchanged Navigation 2.9.8, unchanged Compose BOM, and that the deferred runbook/sprint
direction did not leak into Phase 62 onboarding.


## r3 compile hotfix

The wizard now reuses the existing `formatMinuteOfDay` helper from plan settings instead of redeclaring a same-signature helper in the onboarding screen. The Phase 62 validator checks this explicitly.
