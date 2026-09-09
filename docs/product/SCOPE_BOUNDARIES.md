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

Later roadmaps have promoted several capabilities that were staged when Phase 37 shipped:

- Web is a shipped local-first client with full applicable functional parity and a browser-TV mode.
- Desktop exists as a `0.1.0` preview through DOV07; durable persistence and full workspace execution are
  still explicit roadmap work.
- Native Android TV exists through TVOV26 as a separate size-constrained 10-foot client.
- Optional sync remains off by default, but real manual SAF-folder transfer and optional WebDAV /
  Nextcloud provider I/O now exist together with conservative merge/conflict-review infrastructure.
- Google Drive remains a provider/catalog shell rather than a fully claimed equivalent I/O lane.
- SFLP08/SFLP09 provide first-class direct Android↔Desktop discovery, pairing, trust, persistent peer state,
  offline queueing and authenticated operation reconciliation; the five-client universal mesh is still staged.

## Explicit staged scope

The following capabilities remain staged unless a later roadmap promotes them:

- public template marketplace;
- automatic syllabus extraction;
- full Desktop applicable parity beyond the DOV01-DOV07 preview;
- universal five-client Android/Desktop/Web/Android-TV/Web-TV synchronization and handoff beyond the shipped
  Android↔Desktop SFLP08/SFLP09 link, tracked by SFLP10-SFLP19.

## Release scope still pending

- actual target-machine Gradle validation results;
- signed APK/AAB artifact generated with external credentials;
- completed install/upgrade/device acceptance report with real evidence.


## SFLP10/SFLP11 boundary

Web and Android TV can now join SFLP as peers, but Web still cannot listen for raw TCP and Android TV still cannot own repository/database writes. Pairing, trust, routing, and journal exchange are shared; platform I/O remains bounded by each surface.
