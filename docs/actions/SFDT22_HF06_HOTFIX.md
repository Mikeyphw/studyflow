# SFDT22 HF06 — subject/topic dialog semantics

## Diagnosed live failure

HF05 on commit c3b6440f, GitHub Actions run 42445616803, passed plan creation,
then failed on `realUserAddsSubjectAndTopic` with `ambiguous_compose_node`
and `failure_step=unavailable`. The artifact proves second-journey failure,
but not the exact UI action. Consequently this patch fixes the known ambiguity
in generic subject/topic name lookups and adds distinct stage markers.

## Changes

- Attach distinct semantics test tags to the existing Material 3 subject/topic
  `Name` fields, without altering layout or app state.
- Exercise real on-device editable controls by tag; no injected repositories,
  fake navigation, bypassed saves or shortcut state mutation.
- Use the real dialog's Save button and preserve screenshot captures.
- Add scoped stage markers for subject + topic so any next failure identifies
  the UI step, while sanitized output never keeps instrumentation logs.
- Preserve all five real journeys, process force-stop, seven captures, host trust,
  release-policy denial, and fail-closed gates.

## Qualification status

Offline regression gates only. Hosted API36 five-journey acceptance has not
run for HF06; do not merge PR #15 until the host gate succeeds.
