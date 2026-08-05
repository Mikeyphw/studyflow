# ADR-0029: Workspace Navigation and Persistent Focus Mode

## Status

Accepted.

## Context

The original application shell exposed Plans, Search, and Settings as primary destinations while
most learner actions lived behind a long secondary menu. Feature screens were individually complete,
but the shell reflected the module inventory rather than the learner's daily loop.

## Decision

StudyFlow uses four equal primary workspaces: Today, Plan, Practice, and Progress. The app retains
Navigation Compose 2 typed routes and uses the official saved multiple-back-stack navigation pattern.
Route payloads contain only stable identifiers and small enums. Domain objects are reloaded from
repositories at each destination.

`NavigationSuiteScaffold` owns compact and larger-width primary navigation. Search, plan switching,
settings, reminders, portability, privacy, checkpoint status, and diagnostics are global actions.
Legacy routes translate into workspace tabs, while separate manager routes open full existing
editors and prevent compatibility redirect loops.

Focus mode is a dedicated destination backed by the persistent `StudySession`. Its target duration is
stored as nullable minutes in Room schema 3. Countdown and elapsed time are reconstructed from
persisted timestamps, pause duration, and status; timer ticks are never serialized as UI state.

## Consequences

- The learner's next action is visible without navigating a feature catalog.
- Existing domain engines and mutation-heavy editors are reused rather than rewritten.
- Each root preserves its own navigation state.
- Old reminder, widget, search, and deep-link destinations remain compatible.
- Room migration 2 to 3 is required for durable countdown restoration.
- Navigation 3 is deliberately outside this change to avoid combining two architecture migrations.
