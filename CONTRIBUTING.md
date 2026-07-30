# Contributing

## Source language

All code, identifiers, resource keys, commit messages, architecture records, and technical comments
must be written in English. User-facing strings must be localized in both `values/strings.xml` and
`values-pt-rBR/strings.xml`.

## Architecture rules

1. Features depend on core modules, never on other feature implementations.
2. UI code consumes repository interfaces from `core:domain`.
3. Android resources do not contain domain state values.
4. User-visible strings are never hardcoded in Kotlin source.
5. Every entity introduced after Phase 1 must explicitly declare its Study Plan ownership.
6. File bytes must not be stored in Room.
7. New migrations and serialized schemas require round-trip tests.

## Before opening a change

```bash
./scripts/validate_repo.sh
```

## Formatting

The repository uses `.editorconfig` plus the `verifyFormatting` Gradle task. Kotlin and Kotlin DSL
lines are limited to 120 characters. Tabs and trailing whitespace are rejected.
