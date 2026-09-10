# TVOV29 Domain Action Exhaustiveness Hotfix

This hotfix closes the Kotlin exhaustiveness gap introduced when TVOV29 added Error Notebook mutation kinds.

- `ERROR_RESOLVE` now requires the same canonical target/context identity needed by the domain action initializer.
- `ERROR_RECLASSIFY` and `ERROR_REOPEN` additionally require a canonical error category before the action is admitted.
- Regression coverage exercises reclassify, resolve, and reopen mapping against a canonical projected Error Notebook entry.
- TV remains a presentation/execution surface; canonical mutation authority remains with the paired data-owning client.
