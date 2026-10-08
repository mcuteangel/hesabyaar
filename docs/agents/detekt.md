# Detekt Policy (agent guide)

> Part of the agent guide. Start at `AGENTS.md`; it says when to read this file.

## Detekt Findings: Fix on Touch, Never Re-Baseline

The `config/detekt/detekt-baseline.xml` is a frozen snapshot of pre-existing findings in files that are NOT being changed. It keeps the build and CI green for legacy code. It is NOT a place to hide new work.

The only new Detekt `@Suppress` annotations permitted are the two documented exceptions under Allowed Suppressions below. Existing inline suppressions outside these two remain as legacy. Remove each Detekt suppression when you touch its file and fix the underlying finding.

### Rule: editing a file obligates fixing its findings

When you modify a file, every detekt finding in that file — even one that was previously baselined — must be fixed by splitting and refactoring. Do not:

- Add a `@Suppress` (except the two documented exceptions).
- Re-add or keep the finding's entry in `detekt-baseline.xml`.

Mechanism: a baseline entry is keyed by signature. When you edit a signature, the old entry stops matching. The finding surfaces. Fix it in the same change. If the signature is unchanged and the entry still matches, fix the finding anyway. Then remove the entry from the baseline.

Pre-existing findings in files you do NOT touch may stay baselined. You must never grow the baseline. Never add a new entry for code you introduce or modify.

If your change makes a class or function cross a threshold (for example, detekt `LargeClass` on a test class), split it into a new, smaller class or file, remove the old baseline entry for that class, and fix the findings.


### Allowed Suppressions (with a justification)

- `@Suppress("LongMethod")` in test files. Test functions are naturally longer due to Arrange-Act-Assert blocks, multiple assertions, and test data setup. This is the only acceptable context for this suppression.
- `@Suppress("TooGenericExceptionCaught")` in only two cases:
  1. Rethrowing `CancellationException` in coroutine scopes (structured concurrency).
  2. Safety-net `catch` blocks where an API layer can throw unchecked runtime exceptions. Examples are Rust FFI `RustBridge.rustCallSync` rethrowing `RuntimeException`, and org.json `opt*` accessors throwing NPE on malformed JSON. Put the annotation on the enclosing function, not inside the catch body. Always add a justification comment. See `ExportViewModel.exportExcel()` and `BackupJsonParser.parseBackupJsonKotlin()` for the pattern.
- Use camelCase test names per the [Test Naming Convention](#5-test-naming-convention-codacy-compliance). Do not use backtick-quoted names.

### Forbidden Suppressions

- `@Suppress("MagicNumber")` — extract constants or use descriptive variables.
- `@Suppress("UnusedPrivateMember")` — remove dead code; do not hide it.
- `@Suppress("LongParameterList")` — refactor into data classes or builder patterns.
- `@Suppress("ComplexMethod")` / `@Suppress("CognitiveComplexMethod")` — decompose into smaller, named functions.
- Any suppression used to avoid fixing the underlying issue.

### Refactoring Strategy for Detekt Failures

1. Long functions — extract named helper functions until the main function reads as a high-level workflow.
2. Magic numbers — extract to `companion object` constants or named `val`s with descriptive names.
3. Long parameter lists — group related parameters into data classes or use a builder.
4. Complex methods — decompose the conditional logic into small, well-named functions.
5. Cognitive complexity — restructure the control flow. Prefer early returns over deep nesting.

If a detekt rule does not apply to a specific file, the only sanctioned response is the documented `@Suppress("LongMethod")` exception in test files. Other suppressions are forbidden except the documented `@Suppress("TooGenericExceptionCaught")` cases (Rust FFI rethrow and org.json malformed-JSON access).
