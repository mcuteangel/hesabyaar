# Rust Lint Policy (agent guide)

> Part of the agent guide. Start at `AGENTS.md`; it says when to read this file.

## Rust Lints: Parity with Detekt

The Rust core (`rust/hesabyar-core`) enforces size, complexity, and formatting rules that mirror the Detekt rules enforced on Kotlin code:

| Rule Category | Detekt (Kotlin) | Clippy / Rustfmt / Script (Rust) | Threshold |
|---|---|---|---|
| Function length | `LongMethod` | `clippy::too_many_lines` | 60 lines |
| Cognitive complexity | `CyclomaticComplexMethod` | `clippy::cognitive_complexity` | 15 |
| Parameter count | `LongParameterList` | `clippy::too_many_arguments` | 6 parameters |
| Line length | `MaxLineLength` | `rustfmt.toml: max_width` | 120 chars |
| Module size | `LargeClass` (600) | `scripts/check-rust-module-size.sh` | 800 lines |
| Module functions | `TooManyFunctions` | `scripts/check-rust-module-size.sh` | 25 top-level fns |

## Frozen Baseline: Fix on Touch, Never Re-Baseline

The baseline markers (`#[allow(...)] // RUST-LINT-BASELINE` and `rust/.module-size-baseline.json`) are a frozen snapshot of pre-existing legacy code. They keep CI green (`-D warnings`). They are NOT a place to hide new work.

### Rule: editing a file obligates fixing its findings

When you modify a file, every pre-existing clippy allowance or oversized module entry in that file must be addressed:

- Remove the `#[allow(...)] // RUST-LINT-BASELINE` and refactor the function.
- If the file is listed in `rust/.module-size-baseline.json`, reduce its lines/functions below the 800 lines / 25 functions threshold and remove its entry from the baseline.
- The baseline NEVER grows. Never add new files to `.module-size-baseline.json`.

### Allowed Suppressions (with a justification)

The only permitted `#[allow]` suppressions in Rust core are:

1. **Baseline markers on untouched legacy code**: Must have the explicit `// RUST-LINT-BASELINE` comment.
2. **Test harness allowances**: Unit test functions are naturally longer and use assertion macros. In `lib.rs`, `#![cfg_attr(test, allow(...))]` exempts unit test modules from `too_many_lines`, `cognitive_complexity`, `unwrap_used`, `expect_used`, and `panic` (mirrors Detekt's `@Suppress("LongMethod")` in test files).
3. **FFI surface compatibility**: Functions exported through UniFFI that mirror external interfaces (e.g. `compute_dashboard_data` argument counts).
4. **Panic logging hook**: `ffi/mod.rs` installs the emergency panic logger before Android catches the panic and allows `clippy::print_stderr`.
5. **CLI generator crate**: `rust/uniffi-gen` is a command-line tool and is allowed to print to standard streams.

### Forbidden Suppressions

- Suppressing `too_many_lines` or `cognitive_complexity` on new or modified production functions.
- Suppressing `print_stdout` or `print_stderr` in core business logic (use structured errors or FFI return types).
- Adding new files to `rust/.module-size-baseline.json`.

## Refactoring Strategy

When a function or module crosses a threshold:

1. **Extract helpers**: Split long procedural functions into focused private helper functions.
2. **Decompose conditionals**: Move nested validation, JSON parsing, or calculation steps into sub-routines.
3. **Split test modules**: Extract large inline `#[cfg(test)] mod tests` blocks into sibling test modules (e.g. `src/validation.rs` delegates to `#[cfg(test)] mod tests;` in `src/validation/tests.rs`).
4. **Child-module pattern**: When splitting tests into `module/tests.rs`, keep the parent file at `module.rs` to maintain stable file paths, and annotate with `#![allow(clippy::self_named_module_files)]` if required.
