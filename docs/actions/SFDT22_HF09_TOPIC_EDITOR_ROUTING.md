# StudyFlow SFDT22 HF09 — real add-topic action and editable semantics

Source-pinned to PR #15 commit `c921c997a078acc49e41ecd79da42bb6d97f0300`.

## Hosted failure

SFDT22 run 42483528724 returned `topic_dialog_missing` at `topic_fill_name` after `topic_open_dialog` reported success. The previous test required Compose `isDialog()` semantics. Failure to find that semantics node is not enough to conclude the production dialog is absent.

## Repair

The existing subject-card Add Topic TextButton gets a stable test tag. The hosted MainActivity test touches that production button, waits for the TopicEditorDialog's existing tagged name field, and enters text through a tag-scoped SetText semantics action without relying on `isDialog()`. It verifies the entered value before invoking the real Save button. The verifier distinguishes missing add button, missing editor, missing editable action and failed text write. Existing five journeys, seven PNGs and cold-restart check remain mandatory. No fixture injection, screenshots from failures or release qualification.

## Qualification

The source-pinned installer verifies the exact Git blob identities, clean branch and no payload tampering, then executes every existing SFDT22 contract, SFDT21 source contract, Devtool source tests, SFDT11 source policy, and Git diff validation before auto-committing. Hosted Android API36 is still needed to prove acceptance.
