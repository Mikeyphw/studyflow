# REL09 Kotlin test classpath hotfix

REL09 fixes the final release validation failure where SFLP16 through SFLP24 and REL desktop/core tests import `kotlin.test.*` but the affected modules only exposed JUnit4 on the test classpath.

## Scope

- Add `testImplementation(kotlin("test-junit"))` to `core/link`.
- Add `testImplementation(kotlin("test-junit"))` to `desktopApp`.
- Keep existing `libs.junit4` and `useJUnit()` behaviour intact.
- Do not rewrite the tests, weaken assertions, or remove final release validation coverage.

## Expected result

The previously failing `:core:link:compileTestKotlin` and `:desktopApp:compileTestKotlin` lanes can resolve `kotlin.test.Test`, `assertEquals`, `assertTrue`, `assertFalse`, `assertIs`, `assertNotNull`, and `assertFailsWith`.
