# TVOV00 — Android TV architecture baseline

TVOV00 establishes Android TV as a future presentation target with a strict size budget, a
placeholder Devtool target, and a frozen phone-to-TV control/cast contract. It does not create
`:app-tv` yet and does not add Android TV UI libraries to shared modules.

## Frozen boundaries

Allowed shared dependencies for the future TV app:

- `:core:model`
- `:core:domain`
- `:core:data`
- `:core:database`
- `:core:navigation`
- `:core:portability`
- selected feature modules whose ViewModels and presenters are reused without phone-only UI

Forbidden in TVOV00:

- creating a duplicate database or repository layer for TV;
- adding `androidx.tv` or TV Material dependencies to shared modules;
- adding a fake or empty `:app-tv` module without packaging contracts;
- adding TV tasks to `studyflow_android` validation;
- making phone/tablet APKs carry TV-only UI dependencies;
- implementing phone-to-TV control as raw database writes or arbitrary UI mirroring.

## Phone-to-TV control/cast baseline

The Android mobile app is a first-class controller for the TV app. Later overlays must support
paired-phone control and cast mode without turning the TV APK into a second product line.

Required future capabilities:

- explicit pairing by local code, QR, or deep link;
- phone-driven TV navigation across every TV destination;
- phone-driven study session, question practice, review, mock exam, progress, and schedule flows;
- casting StudyFlow intents/presentation state to TV instead of duplicating storage or shipping raw
  database rows;
- revocable, expiring control sessions;
- shared domain/use-case execution for phone commands and TV remote actions.

## Devtool target

`.devtool.toml` now contains `studyflow_tv` as a source-contract Gradle target. Because `:app-tv`
does not exist yet, the target runs only:

- `verifyStudyFlowTvArchitecture`
- `verifyStudyFlowTvSizeBudget`
- `verifyStudyFlowTvControlCast`
- `verifyFormatting` for lint-style source hygiene

TVOV01 may expand `studyflow_tv` to compile the real `:app-tv` module once that module exists.


## TVOV01 follow-up

TVOV01 is the first implementation overlay after this baseline. It creates `:app-tv` as a small,
separate Android TV application module and upgrades the `studyflow_tv` target from source-contract
validation to real module compile/test/package/lint validation. The core TVOV00 promises remain
active: shared business logic is not forked, the size budget remains 25 MiB target / 35 MiB hard
ceiling, and phone-to-TV control/cast stays mandatory for later overlays.
