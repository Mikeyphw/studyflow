# TVOV15 — Minimal TV settings

TVOV15 replaces the static Settings placeholder with a native Android TV settings surface backed by
`StudyFlowTvSettingsSurface`.

The surface is deliberately presentation-only. It shows pairing status and the revocation entry
point, represents the high-visibility focus preference, repeats the local-first privacy boundary,
and exposes only TV-safe package/protocol diagnostics. There is no account management, SAF picker,
file import/export, Room/database access, repository access, or phone feature-module dependency in
`:app-tv`.

Local D-pad actions and paired-phone Settings intents use the shared `:core:tvcontrol` command model.
Pairing revocation is represented explicitly as `StudyFlowTvControlAction.REVOKE_PAIRING`.

Validation is provided by `StudyFlowTvSettingsSurfaceTest` and `verifyStudyFlowTvSettings`.
