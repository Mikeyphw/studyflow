# TVOV17 — Android TV accessibility and 10-foot UI seal

TVOV17 converts the earlier focus baseline into an accessibility/10-foot presentation contract for the real TV shell.

Implemented:
- one pure Kotlin `StudyFlowTvAccessibilityPolicy` for typography, safe-area/layout metrics, contrast, and focus strength;
- native semantic headings and a polite live continuity/status region without adding Compose or AndroidX TV UI;
- scrollable focus-following content so dense StudyFlow surfaces remain reachable instead of clipping on shorter TV viewports;
- the TVOV15 high-visibility-focus preference now changes the actual focus drawable used by navigation, cards, and actions;
- 64dp minimum focus targets, 20sp body text, 18sp controls, and palette contrast checks are separately unit-tested;
- `verifyStudyFlowTvAccessibilityTenFootUi` proves source, shell, resource, tests, docs, Gradle, and Devtool wiring.

Physical-device viewing distance and screen-reader behavior remain part of the TVOV20 real-device acceptance gate; TVOV17 seals the implementation and automated source/unit-test boundary.
