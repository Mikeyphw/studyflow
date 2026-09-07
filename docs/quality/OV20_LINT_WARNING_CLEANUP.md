# OV20 lint warning cleanup

This post-OV20 cleanup follows the successful full-project lint sweep after the SAF API-23 hotfix.

It removes the remaining source warnings reported by Devtool without suppressing lint:

- rewords the pt-BR restore-preview sentence so Android lint no longer misreads the valid verb `autentica` as an adjective typo;
- converts reference freshness age and weekly writing progress to real plural resources;
- rewrites the five-count import diff summary with count-neutral labels because one plural selector cannot correctly govern five independent counts;
- removes seven workspace strings confirmed unused by lint and repository-wide source search.

Environment-only NDK/ADB/Termux warnings are intentionally unchanged.

## v2 compile correction

`StudyReferenceFreshnessPolicy.PossiblyStaleAfterDays` is a `Long`, while Compose `pluralStringResource` requires an `Int` quantity selector. The stale-reference call now reuses the existing bounded `Long.toPluralQuantity()` helper for quantity selection and keeps the original `Long` as the formatted value. This preserves displayed data while satisfying Kotlin's API contract.
