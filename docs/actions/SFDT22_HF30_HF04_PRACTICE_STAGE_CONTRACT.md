# StudyFlow HF30 HF04 — Historical HF28 practice stage contract migration

HF28's standalone practice/review contract required exactly 25 total stage markers.
HF30 legitimately introduces **three** mandatory safety stages before and around
`practice_open_root`: `practice_session_closed`, `practice_shell_ready`, and
`practice_tab_selected`. The original 25 user interactions remain present.

HF04 migrates the historical HF28 test to demand the **exact 28-stage sequence**
(25 original, 3 new) rather than incorrectly demanding that the entire test
still have 25 markers. The original 25 production control assertions, real
screenshots, five production tests and strict CI guard remain mandatory.

The real HF28 contract is run in the temporary source preflight. A new HF04
contract checks the exact augmented step sequence, no stage omission or
reordering, immutable source pins and the full Android 36 mandatory gate.

Both historical HF28 test and the new HF04 test are pinned by SHA256 inside the
full workflow. Any missing, incorrect, duplicate, or stale old SHA pin is a
refusal. This is a source-only correction; no Android runtime success or
release qualification is inferred.

`--prepare` on the exact source checkout runs all historical source contracts
against the complete overlay in a disposable Git worktree before Devtool apply.
The packaging offline tests run on limited fixtures and **cannot substitute**
for that full checkout-dependent preflight.
