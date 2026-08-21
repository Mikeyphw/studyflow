# CMOV-03 Code Blocks and Syntax Highlighting

Adds a reusable programming-content presentation layer for StudyFlow programming courses.

## Delivered

- `StudyFlowCodeBlock` composable for monospaced programming content.
- `StudyFlowSyntaxTheme` and `StudyFlowSyntaxHighlighter` for dark-mode-safe syntax spans.
- Language-aware keyword groups for Kotlin, Java, Python, JavaScript, TypeScript, Shell, SQL, HTML, CSS, JSON, YAML, Markdown, C, C++, Go and Rust.
- Copy-to-clipboard action.
- Horizontal scrolling for long code lines.
- Selectable code text.
- Optional line numbers.
- Source contract coverage and Python verifier.

## Boundary

This overlay does not run code and does not add the Termux bridge. Runtime execution starts in CMOV-04/CMOV-06.
