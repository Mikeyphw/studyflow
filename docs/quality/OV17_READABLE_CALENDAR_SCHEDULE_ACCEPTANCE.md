# OV17 — Readable calendar schedule acceptance

- Schedule exposes Today, This week, Next week, and Calendar views.
- Week windows are Monday through Sunday in the device zone.
- Today/week views exclude unscheduled and out-of-window activities; Calendar retains the full schedule.
- Existing subject/type/status/provenance filters compose with the selected calendar view.
- Conflict, overload, selection, and bulk actions operate on the visible window only.
- The view reports exact visible activity count and planned minutes.
- No new persistence layer or Room migration is introduced.
