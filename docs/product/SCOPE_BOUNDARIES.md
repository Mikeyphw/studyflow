# Scope Boundaries

## Phase 1 foundation

Included:

- modular Android repository;
- Compose and Material 3;
- plan list, empty state, create form, dashboard, and settings;
- responsive navigation;
- English and Brazilian Portuguese UI;
- theme and language preferences;
- local plan repository seams;
- product and architecture contracts;
- validation and testing foundation.

## Local product scope through Phase 37

Included:

- Room-backed plan persistence;
- complete plan lifecycle;
- import, export, encrypted package inspection, and restore dry-run;
- subject/topic organization and syllabus import;
- schedules, sessions, reminders, and widgets;
- question bank, practice execution, reviews, wrong-answer notebook, and remediation;
- library resources, videos, annex metadata, and copied annex payloads;
- goals, notes, analytics, search, recommendations, privacy center, and local checkpoint status;
- developer diagnostics gated to debug builds;
- passive status UI for non-actionable labels;
- current README, changelog, and validation report;
- release-candidate identity, package policy, signing boundary, and real-device acceptance template.

## Current cross-platform scope

Later roadmaps have promoted the staged Phase 37 surfaces into the current shipped cross-platform product:

- Web is a shipped local-first `1.0.0` client with full browser-applicable functional parity and a browser-TV mode.
- Desktop is a `1.0.0` full-applicable workspace through DOV24, including durable persistence, authoring, execution,
  portability, localization, appearance, and native desktop integrations.
- Native Android TV is a separate size-constrained `1.0.0-tv31` 10-foot client through TVOV31, with viewer, goal,
  Error Notebook, search/input, and Android-TV/Web-TV semantic parity work complete.
- Optional sync remains off by default, with real manual SAF-folder transfer and optional WebDAV / Nextcloud provider
  I/O plus conservative merge/conflict-review infrastructure. Google Drive remains a provider/catalog shell rather than
  a fully claimed equivalent I/O lane.
- SFLP01-SFLP24 are complete, PRESENCE01 supplies optional ephemeral peer/activity presence, and PARITY04 seals the
  universal five-client Android/Desktop/Web/Android-TV/Web-TV mesh without requiring Android as a topology hub.


## Historical SFLP08/SFLP09 boundary

At the SFLP08/SFLP09 delivery point, **SFLP08/SFLP09 provide first-class direct Android↔Desktop** peer
connectivity while the **five-client universal mesh is still staged** for SFLP10-SFLP24 and later parity work.
That limitation is historical provenance only: the current scope above records the completed PARITY04 mesh.

## Post-1.0 staged scope

The following capabilities remain post-1.0 work unless a later roadmap explicitly promotes them:

- public template marketplace;
- automatic syllabus extraction;
- richer native-TV authoring beyond its intentional presentation/execution capability boundary;
- additional provider-specific cloud adapters beyond the currently delivered optional lanes; and
- operator publishing/distribution automation, which remains intentionally outside repository release tooling.

## Release scope still pending

REL-FINAL has completed the repository-wide static/product promise audit; only real target evidence remains open.

- actual target-machine Gradle validation results;
- signed APK/AAB artifact generated with external credentials;
- completed install/upgrade/device acceptance report with real evidence.


## SFLP10/SFLP11 boundary

Web and Android TV can now join SFLP as peers, but Web still cannot listen for raw TCP and Android TV still cannot own repository/database writes. Pairing, trust, routing, and journal exchange are shared; platform I/O remains bounded by each surface.
