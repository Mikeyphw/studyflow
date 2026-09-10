# TVOV31 compile integration hotfix v2

Repairs the two Android-TV compile failures exposed by the TVOV31 terminal gate after TVOV29/TVOV30.

- Adds explicit UI labels for `RECLASSIFY_ERROR`, `RESOLVE_ERROR`, and `REOPEN_ERROR` in the Activity action-label mapping.
- Uses the typed `StudyFlowTvLibraryReference.sourceLabel` property when indexing library references for TV search.
- Strengthens the TVOV29 and TVOV30 verifier scripts so both integration gaps are checked before Kotlin compilation.
- Does not broaden TV authority: Error Notebook mutations remain delegated to the paired data-owning client and TV search remains repository-safe.

Apply with `--no-validate` after `studyflow_tvov29_domain_action_exhaustiveness_hotfix_v1.zip`, then rerun the unchanged TVOV31 terminal seal.
