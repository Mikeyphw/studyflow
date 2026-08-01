# Phase 22: Global Search and Smart Recommendations

Phase 22 adds a Room-backed, offline-first discovery layer without AppSearch, kapt, or a schema migration.

Delivered:
- global search across plans, subjects, topics, questions, notes, materials, flashcards, and errors;
- content-type filters, bounded recent searches, active-content defaults, and typed destination routing;
- archived-plan and archived-parent exclusion across direct and linked content;
- deterministic recommendations for the next study item, weak-topic review, practice deck, and overdue resource;
- structured and localized recommendation reasons using mastery, due reviews, schedule adherence,
  wrong-answer frequency, question accuracy, goal deadlines, and unfinished materials;
- Room SQL validated against exported schema version 2;
- domain, data, UI, bilingual resources, tests, and Phase 22 contract coverage.

The implementation keeps Room as the canonical source of truth and does not depend on pending Phase 21 code.
