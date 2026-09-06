# WEB-W02/W03 — Mobile visual system + vanilla interaction foundation

This combined overlay delivers the next two StudyFlow Local web milestones while preserving the W00/W01 contracts and the preconfigured Node Devtool target.

## W02 — mobile-first CSS/design system

Delivered:

- external `styles.css`; no inline-style ownership;
- reusable design tokens for background, surfaces, text, borders, accent, semantic statuses, spacing, radii and tap size;
- universal `border-box` sizing;
- canonical single-column phone layout with fixed bottom navigation and safe-area padding;
- minimum 44px control target baseline;
- cards, metrics, pills, buttons, chips, form fields, dialogs, empty states and schedule rows;
- horizontally scrollable subnavigation rather than squeezed tabs;
- responsive content enhancement only from `min-width: 700px` upward;
- `clamp()` typography and a three-column metric enhancement on wider screens;
- `prefers-reduced-motion` handling and visible keyboard focus.

The phone layout remains the source of truth. Wider screens only constrain/expand the existing composition; they do not introduce a separate desktop information architecture.

## W03 — vanilla DOM interaction layer

Delivered without a framework or persistence library:

- primary Today / Plan / Practice / Progress screen routing;
- Plan, Practice and Progress tab routing while retaining real fragment URLs;
- accessible `aria-current` state;
- Quick Add native `<dialog>` + form that creates an in-memory Today activity;
- schedule filter chips using `aria-pressed` and data attributes;
- 25-minute focus timer prototype based on `Date.now()` plus a UI interval;
- practice radio-question submission and live feedback;
- transient toast announcements;
- direct deep-link/hash restoration to primary screens and nested panels.

## Intentional boundary

W03 state is **ephemeral**. There is deliberately no `localStorage`, IndexedDB, backend, account or cloud sync. W04 introduces the application state model and temporary persistence; W06 later turns focus completion into durable session evidence.

The DOM event loop remains explicit:

`user event → handler → in-memory state → render/update DOM`
