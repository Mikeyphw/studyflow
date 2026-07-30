# ADR-0003: English code and bilingual interface

- Status: Accepted
- Date: 2026-07-30

## Decision

Code, APIs, schemas, resource keys, and technical documents use English. User-facing text is provided
in English and Brazilian Portuguese. User-created content is never automatically translated.

## Consequences

- English is the Android base locale.
- Portuguese resources live under `values-pt-rBR`.
- Serialized enums remain stable across UI-language changes.
- CI rejects missing or mismatched translations.
