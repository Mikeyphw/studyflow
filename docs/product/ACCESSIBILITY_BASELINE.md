# Accessibility Baseline

StudyFlow targets WCAG-informed Android accessibility from the first screen.

## Required behavior

- Interactive targets are at least 48 dp.
- Every icon-only action has an accessible label.
- Screen order matches visual reading order.
- Content remains usable at 200% font scale.
- Color is never the sole carrier of status.
- Light and dark themes maintain readable contrast.
- Error messages identify the field and the correction.
- Motion is brief and non-essential.
- Navigation remains reachable with keyboard and switch access.
- Dates, durations, and quantities use locale-aware formatting.

## Phase 1 checks

- Plans can be created without relying on gesture-only interactions.
- Empty, loading, and error states expose text descriptions.
- Navigation labels are visible in compact and large layouts.
- Language and theme controls use radio-button semantics.
