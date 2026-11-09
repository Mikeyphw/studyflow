# StudyFlow SFDT22 HF26 — Fast hotfix staging (never release qualified)

HF26 folds HF25's plan/workspace identity and ownership fix into a safer, faster
GitHub-hosted diagnostic loop. Each iteration goes to `sfdt22-hotfix/...`
without changing PR #15's head. Do not open a PR for the staging branch.

A push to the staging branch runs the read-only StudyFlow CI source policy and
`StudyFlow SFDT26 Fast Hotfix Staging (nonqualifying)`, which calls the existing
focused Android 36 workflow: exactly the real plan, subjects/topics, and Quick
Session journeys with source SHA verification and sanitized evidence.

The staging run **does not qualify** a release or authorize merging. It does not
run complete Devtool, SFDT21 or SFDT22 on each hotfix. All original full PR
workflows are unchanged and automatically run when the reviewed commit is
published to the PR's feature branch. SFDT22 five journeys, 7 screenshots,
real process restart, and all other PR checks remain mandatory.

The installer operates from an exact, clean, source-pinned feature branch; its
source contracts and simulated Git transactions run first in `$TMPDIR`. Native
Devtool applies the overlay and makes a normal local commit. It pushes that
commit to a new owner-scoped staging ref, **not** PR #15. No force-push, no
release credentials, no local Gradle and no `pull_request_target`.

`QUALIFY.zsh` subsequently verifies the exact clean staging checkout, checks
that the focused GitHub-hosted staging workflow has passed at that very SHA
using the authenticated `gh` CLI, verifies the PR remote branch has not moved,
and pushes normally to the PR branch. If staging fails, fix and iterate there
instead of running all PR jobs. Qualification does not bypass branch protection,
full CI, review, or GitHub release policy.

If the staging branch or PR base has moved unexpectedly, refuse and reconcile;
never reset a user's worktree or rewrite the remote branch.
