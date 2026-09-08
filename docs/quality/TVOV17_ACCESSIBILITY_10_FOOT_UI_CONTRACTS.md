# TVOV17 accessibility and 10-foot UI contracts

- `StudyFlowTvAccessibilityPolicy` is pure Kotlin and separately unit-tested.
- TV text uses a centralized 10-foot scale: title >= 34sp, body >= 20sp, controls >= 18sp, status >= 16sp.
- Every D-pad target retains the 64dp minimum established in TVOV04.
- Primary, secondary, muted, and focused text combinations meet a 4.5:1 contrast threshold in the policy tests.
- The main content column is hosted in a native `ScrollView`, allowing focused descendants to remain reachable on shorter TV viewports.
- Main content titles are semantic accessibility headings; continuity status is a polite live region.
- The TVOV15 high-visibility-focus setting switches to a 5dp yellow focus ring, while standard focus keeps the existing 3dp white ring.
- Touch remains optional and no Compose Runtime, AndroidX TV UI, Cast SDK, or phone feature-module fanout is introduced.
