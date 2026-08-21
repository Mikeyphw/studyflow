# CMOV-08 — Language Review Modes

Adds first-class language review prompts, answer evaluation, review-attempt persistence, and spaced-review bridge helpers for idiom and vocabulary practice.

Implemented review directions:

- expression → meaning
- meaning → expression
- fill in the blank
- choose natural usage
- spot the mistake
- create your own sentence

The overlay keeps language practice explicit: sentence creation requires manual review, and review outcomes are converted into scheduled ReviewItem data through the existing spaced-review model.
